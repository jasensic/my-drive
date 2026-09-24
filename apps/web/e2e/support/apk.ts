import { crc32 } from 'node:zlib';

const XML_TYPE = 0x0003;
const STRING_POOL_TYPE = 0x0001;
const RESOURCE_MAP_TYPE = 0x0180;
const START_ELEMENT_TYPE = 0x0102;
const UTF8_FLAG = 1 << 8;
const ATTR_VERSION_CODE = 0x0101021b;
const ATTR_VERSION_NAME = 0x0101021c;
const TYPE_STRING = 0x03;
const TYPE_INT_DEC = 0x10;

function u16(value: number): Buffer {
  const buf = Buffer.alloc(2);
  buf.writeUInt16LE(value);
  return buf;
}

function u32(value: number): Buffer {
  const buf = Buffer.alloc(4);
  buf.writeUInt32LE(value >>> 0);
  return buf;
}

function encodeUtf8Len(len: number): Buffer {
  if (len > 0x7f) {
    return Buffer.from([((len >> 8) & 0xff) | 0x80, len & 0xff]);
  }
  return Buffer.from([len]);
}

function encodeUtf8String(value: string): Buffer {
  const bytes = Buffer.from(value, 'utf8');
  return Buffer.concat([
    encodeUtf8Len([...value].length),
    encodeUtf8Len(bytes.length),
    bytes,
    Buffer.from([0]),
  ]);
}

function encodeStringPool(strings: string[]): Buffer {
  const encodedParts: Buffer[] = [];
  const offsets: number[] = [];
  let encodedLen = 0;
  for (const value of strings) {
    offsets.push(encodedLen);
    const part = encodeUtf8String(value);
    encodedParts.push(part);
    encodedLen += part.length;
  }
  const pad = (4 - (encodedLen % 4)) % 4;
  if (pad) {
    encodedParts.push(Buffer.alloc(pad));
    encodedLen += pad;
  }
  const encoded = Buffer.concat(encodedParts);
  const headerSize = 0x1c;
  const stringsStart = headerSize + 4 * strings.length;
  const chunkSize = stringsStart + encoded.length;
  return Buffer.concat([
    u16(STRING_POOL_TYPE),
    u16(headerSize),
    u32(chunkSize),
    u32(strings.length),
    u32(0),
    u32(UTF8_FLAG),
    u32(stringsStart),
    u32(0),
    ...offsets.map((off) => u32(off)),
    encoded,
  ]);
}

function encodeIntAttr(nameIdx: number, value: number): Buffer {
  return Buffer.concat([
    u32(0xffffffff),
    u32(nameIdx),
    u32(0xffffffff),
    u16(8),
    Buffer.from([0, TYPE_INT_DEC]),
    u32(value),
  ]);
}

function encodeStringAttr(nameIdx: number, stringIdx: number): Buffer {
  return Buffer.concat([
    u32(0xffffffff),
    u32(nameIdx),
    u32(stringIdx),
    u16(8),
    Buffer.from([0, TYPE_STRING]),
    u32(stringIdx),
  ]);
}

function encodeStartElement(versionCode: number, versionNameIdx: number): Buffer {
  const attrCount = 2;
  const nodeHeader = 16;
  const attrExt = 20;
  const attrSize = 20;
  const chunkSize = nodeHeader + attrExt + attrSize * attrCount;
  return Buffer.concat([
    u16(START_ELEMENT_TYPE),
    u16(nodeHeader),
    u32(chunkSize),
    u32(1),
    u32(0xffffffff),
    u32(0xffffffff),
    u32(2),
    u16(20),
    u16(attrSize),
    u16(attrCount),
    u16(0),
    u16(0),
    u16(0),
    encodeIntAttr(0, versionCode),
    encodeStringAttr(1, versionNameIdx),
  ]);
}

export function encodeAndroidManifest(versionCode: number, versionName: string): Buffer {
  const pool = encodeStringPool(['versionCode', 'versionName', 'manifest', versionName]);
  const resourceMap = Buffer.concat([
    u16(RESOURCE_MAP_TYPE),
    u16(8),
    u32(16),
    u32(ATTR_VERSION_CODE),
    u32(ATTR_VERSION_NAME),
  ]);
  const start = encodeStartElement(versionCode, 3);
  const body = Buffer.concat([pool, resourceMap, start]);
  return Buffer.concat([u16(XML_TYPE), u16(8), u32(8 + body.length), body]);
}

export function zipStore(files: Array<{ name: string; data: Buffer }>): Buffer {
  const locals: Buffer[] = [];
  const centrals: Buffer[] = [];
  let offset = 0;
  for (const file of files) {
    const name = Buffer.from(file.name, 'utf8');
    const crc = crc32(file.data);
    const localHeader = Buffer.alloc(30);
    localHeader.writeUInt32LE(0x04034b50, 0);
    localHeader.writeUInt16LE(20, 4);
    localHeader.writeUInt32LE(crc, 14);
    localHeader.writeUInt32LE(file.data.length, 18);
    localHeader.writeUInt32LE(file.data.length, 22);
    localHeader.writeUInt16LE(name.length, 26);
    const local = Buffer.concat([localHeader, name, file.data]);
    locals.push(local);

    const centralHeader = Buffer.alloc(46);
    centralHeader.writeUInt32LE(0x02014b50, 0);
    centralHeader.writeUInt16LE(20, 4);
    centralHeader.writeUInt16LE(20, 6);
    centralHeader.writeUInt32LE(crc, 16);
    centralHeader.writeUInt32LE(file.data.length, 20);
    centralHeader.writeUInt32LE(file.data.length, 24);
    centralHeader.writeUInt16LE(name.length, 28);
    centralHeader.writeUInt32LE(offset, 42);
    centrals.push(Buffer.concat([centralHeader, name]));
    offset += local.length;
  }
  const localPart = Buffer.concat(locals);
  const centralPart = Buffer.concat(centrals);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(files.length, 8);
  eocd.writeUInt16LE(files.length, 10);
  eocd.writeUInt32LE(centralPart.length, 12);
  eocd.writeUInt32LE(localPart.length, 16);
  return Buffer.concat([localPart, centralPart, eocd]);
}

export function packageApk(versionCode: number, versionName: string): Buffer {
  return zipStore([{ name: 'AndroidManifest.xml', data: encodeAndroidManifest(versionCode, versionName) }]);
}
