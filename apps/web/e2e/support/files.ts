import { packageApk } from './apk';

export type UploadPayload = {
  name: string;
  mimeType: string;
  buffer: Buffer;
};

export function pngFile(name: string): UploadPayload {
  return {
    name,
    mimeType: 'image/png',
    buffer: Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
      'base64',
    ),
  };
}

export function jpegFile(name: string): UploadPayload {
  return {
    name,
    mimeType: 'image/jpeg',
    buffer: Buffer.from(
      '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAn/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAAAAX/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAGcP//Z',
      'base64',
    ),
  };
}

export function wavFile(name: string): UploadPayload {
  const samples = 8000;
  const dataSize = samples * 2;
  const buffer = Buffer.alloc(44 + dataSize);
  buffer.write('RIFF', 0);
  buffer.writeUInt32LE(36 + dataSize, 4);
  buffer.write('WAVE', 8);
  buffer.write('fmt ', 12);
  buffer.writeUInt32LE(16, 16);
  buffer.writeUInt16LE(1, 20);
  buffer.writeUInt16LE(1, 22);
  buffer.writeUInt32LE(8000, 24);
  buffer.writeUInt32LE(16000, 28);
  buffer.writeUInt16LE(2, 32);
  buffer.writeUInt16LE(16, 34);
  buffer.write('data', 36);
  buffer.writeUInt32LE(dataSize, 40);
  return { name, mimeType: 'audio/wav', buffer };
}

export function textFile(name: string, contents = 'hello my-drive'): UploadPayload {
  return {
    name,
    mimeType: 'text/plain',
    buffer: Buffer.from(contents, 'utf8'),
  };
}

export function apkFile(name: string, versionCode: number, versionName: string): UploadPayload {
  return {
    name,
    mimeType: 'application/vnd.android.package-archive',
    buffer: packageApk(versionCode, versionName),
  };
}
