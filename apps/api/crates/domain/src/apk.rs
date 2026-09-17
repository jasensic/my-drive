use crate::DomainError;

const XML_TYPE: u16 = 0x0003;
const STRING_POOL_TYPE: u16 = 0x0001;
const RESOURCE_MAP_TYPE: u16 = 0x0180;
const START_ELEMENT_TYPE: u16 = 0x0102;
const UTF8_FLAG: u32 = 1 << 8;
const ATTR_VERSION_CODE: u32 = 0x0101_021b;
const ATTR_VERSION_NAME: u32 = 0x0101_021c;
const TYPE_STRING: u8 = 0x03;
const TYPE_INT_DEC: u8 = 0x10;
const TYPE_INT_HEX: u8 = 0x11;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ApkIdentity {
    pub version_code: i32,
    pub version_name: String,
}

pub fn parse_android_manifest(axml: &[u8]) -> Result<ApkIdentity, DomainError> {
    if axml.len() < 8 {
        return Err(DomainError::validation("AndroidManifest.xml is truncated"));
    }
    let xml_type = u16_at(axml, 0)?;
    if xml_type != XML_TYPE {
        return Err(DomainError::validation("file is not a binary AndroidManifest.xml"));
    }

    let mut offset = 8usize;
    let mut strings = Vec::new();
    let mut resource_map = Vec::new();
    let mut version_code = None;
    let mut version_name = None;

    while offset + 8 <= axml.len() {
        let chunk_type = u16_at(axml, offset)?;
        let chunk_size = u32_at(axml, offset + 4)? as usize;
        if chunk_size < 8 || offset + chunk_size > axml.len() {
            break;
        }
        let chunk = &axml[offset..offset + chunk_size];
        match chunk_type {
            STRING_POOL_TYPE => strings = parse_string_pool(chunk)?,
            RESOURCE_MAP_TYPE => {
                resource_map = chunk[8..]
                    .chunks_exact(4)
                    .map(|c| u32::from_le_bytes(c.try_into().unwrap()))
                    .collect();
            }
            START_ELEMENT_TYPE => {
                read_start_element(
                    chunk,
                    &strings,
                    &resource_map,
                    &mut version_code,
                    &mut version_name,
                )?;
            }
            _ => {}
        }
        offset += chunk_size;
    }

    let version_code = version_code
        .ok_or_else(|| DomainError::validation("APK is missing android:versionCode"))?;
    if version_code <= 0 {
        return Err(DomainError::validation("versionCode must be greater than 0"));
    }
    let version_name = version_name
        .unwrap_or_default()
        .trim()
        .to_string();
    if version_name.is_empty() {
        return Err(DomainError::validation("APK is missing android:versionName"));
    }
    Ok(ApkIdentity {
        version_code,
        version_name,
    })
}

/// Builds a minimal binary manifest for tests and HTTP fixtures.
pub fn encode_android_manifest(version_code: i32, version_name: &str) -> Vec<u8> {
    let strings = vec![
        "versionCode".to_string(),
        "versionName".to_string(),
        "manifest".to_string(),
        version_name.to_string(),
    ];
    let pool = encode_string_pool(&strings);
    let mut resource_map = Vec::new();
    resource_map.extend_from_slice(&RESOURCE_MAP_TYPE.to_le_bytes());
    resource_map.extend_from_slice(&8u16.to_le_bytes());
    resource_map.extend_from_slice(&16u32.to_le_bytes());
    resource_map.extend_from_slice(&ATTR_VERSION_CODE.to_le_bytes());
    resource_map.extend_from_slice(&ATTR_VERSION_NAME.to_le_bytes());

    let start = encode_start_element(version_code, 3);

    let mut body = Vec::new();
    body.extend_from_slice(&pool);
    body.extend_from_slice(&resource_map);
    body.extend_from_slice(&start);

    let mut out = Vec::new();
    out.extend_from_slice(&XML_TYPE.to_le_bytes());
    out.extend_from_slice(&8u16.to_le_bytes());
    out.extend_from_slice(&((8 + body.len()) as u32).to_le_bytes());
    out.extend_from_slice(&body);
    out
}

fn encode_string_pool(strings: &[String]) -> Vec<u8> {
    let mut encoded = Vec::new();
    let mut offsets = Vec::new();
    for s in strings {
        offsets.push(encoded.len() as u32);
        encoded.extend_from_slice(&encode_utf8_string(s));
    }
    while encoded.len() % 4 != 0 {
        encoded.push(0);
    }
    let header_size = 0x1Cu16;
    let strings_start = header_size as u32 + 4 * strings.len() as u32;
    let chunk_size = strings_start + encoded.len() as u32;
    let mut out = Vec::new();
    out.extend_from_slice(&STRING_POOL_TYPE.to_le_bytes());
    out.extend_from_slice(&header_size.to_le_bytes());
    out.extend_from_slice(&chunk_size.to_le_bytes());
    out.extend_from_slice(&(strings.len() as u32).to_le_bytes());
    out.extend_from_slice(&0u32.to_le_bytes());
    out.extend_from_slice(&UTF8_FLAG.to_le_bytes());
    out.extend_from_slice(&strings_start.to_le_bytes());
    out.extend_from_slice(&0u32.to_le_bytes());
    for off in offsets {
        out.extend_from_slice(&off.to_le_bytes());
    }
    out.extend_from_slice(&encoded);
    out
}

fn encode_utf8_string(value: &str) -> Vec<u8> {
    let bytes = value.as_bytes();
    let mut out = Vec::new();
    out.extend_from_slice(&encode_utf8_len(value.chars().count() as u32));
    out.extend_from_slice(&encode_utf8_len(bytes.len() as u32));
    out.extend_from_slice(bytes);
    out.push(0);
    out
}

fn encode_utf8_len(len: u32) -> Vec<u8> {
    if len > 0x7f {
        vec![((len >> 8) as u8) | 0x80, (len & 0xff) as u8]
    } else {
        vec![len as u8]
    }
}

fn encode_start_element(version_code: i32, version_name_idx: u32) -> Vec<u8> {
    let attr_count = 2u16;
    let node_header = 16u16;
    let attr_ext = 20u16;
    let attr_size = 20u16;
    let chunk_size = u32::from(node_header + attr_ext + attr_size * attr_count);
    let mut out = Vec::new();
    out.extend_from_slice(&START_ELEMENT_TYPE.to_le_bytes());
    out.extend_from_slice(&node_header.to_le_bytes());
    out.extend_from_slice(&chunk_size.to_le_bytes());
    out.extend_from_slice(&1u32.to_le_bytes());
    out.extend_from_slice(&u32::MAX.to_le_bytes());
    out.extend_from_slice(&u32::MAX.to_le_bytes());
    out.extend_from_slice(&2u32.to_le_bytes());
    out.extend_from_slice(&20u16.to_le_bytes());
    out.extend_from_slice(&attr_size.to_le_bytes());
    out.extend_from_slice(&attr_count.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes());
    out.extend_from_slice(&0u16.to_le_bytes());
    out.extend_from_slice(&encode_int_attr(0, version_code as u32));
    out.extend_from_slice(&encode_string_attr(1, version_name_idx));
    out
}

fn encode_int_attr(name_idx: u32, value: u32) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(&u32::MAX.to_le_bytes());
    out.extend_from_slice(&name_idx.to_le_bytes());
    out.extend_from_slice(&u32::MAX.to_le_bytes());
    out.extend_from_slice(&8u16.to_le_bytes());
    out.push(0);
    out.push(TYPE_INT_DEC);
    out.extend_from_slice(&value.to_le_bytes());
    out
}

fn encode_string_attr(name_idx: u32, string_idx: u32) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(&u32::MAX.to_le_bytes());
    out.extend_from_slice(&name_idx.to_le_bytes());
    out.extend_from_slice(&string_idx.to_le_bytes());
    out.extend_from_slice(&8u16.to_le_bytes());
    out.push(0);
    out.push(TYPE_STRING);
    out.extend_from_slice(&string_idx.to_le_bytes());
    out
}

fn parse_string_pool(chunk: &[u8]) -> Result<Vec<String>, DomainError> {
    if chunk.len() < 28 {
        return Err(DomainError::validation("string pool is truncated"));
    }
    let string_count = u32_at(chunk, 8)? as usize;
    let flags = u32_at(chunk, 16)?;
    let strings_start = u32_at(chunk, 20)? as usize;
    let utf8 = flags & UTF8_FLAG != 0;
    let mut out = Vec::with_capacity(string_count);
    for i in 0..string_count {
        let rel = u32_at(chunk, 28 + i * 4)? as usize;
        let at = strings_start.saturating_add(rel);
        out.push(if utf8 {
            decode_utf8_string(chunk, at)?
        } else {
            decode_utf16_string(chunk, at)?
        });
    }
    Ok(out)
}

fn decode_utf8_string(data: &[u8], mut at: usize) -> Result<String, DomainError> {
    let (_chars, next) = read_utf8_len(data, at)?;
    at = next;
    let (bytes, next) = read_utf8_len(data, at)?;
    at = next;
    let end = at
        .checked_add(bytes as usize)
        .filter(|e| *e <= data.len())
        .ok_or_else(|| DomainError::validation("utf-8 string overruns pool"))?;
    String::from_utf8(data[at..end].to_vec())
        .map_err(|_| DomainError::validation("utf-8 string is invalid"))
}

fn decode_utf16_string(data: &[u8], at: usize) -> Result<String, DomainError> {
    if at + 2 > data.len() {
        return Err(DomainError::validation("utf-16 string is truncated"));
    }
    let mut len = u16_at(data, at)? as usize;
    let mut start = at + 2;
    if len & 0x8000 != 0 {
        if start + 2 > data.len() {
            return Err(DomainError::validation("utf-16 string is truncated"));
        }
        len = (((len & 0x7fff) << 16) | u16_at(data, start)? as usize) as usize;
        start += 2;
    }
    let end = start
        .checked_add(len * 2)
        .filter(|e| *e <= data.len())
        .ok_or_else(|| DomainError::validation("utf-16 string overruns pool"))?;
    let units: Vec<u16> = data[start..end]
        .chunks_exact(2)
        .map(|c| u16::from_le_bytes([c[0], c[1]]))
        .collect();
    Ok(String::from_utf16_lossy(&units))
}

fn read_utf8_len(data: &[u8], at: usize) -> Result<(u32, usize), DomainError> {
    let b0 = *data
        .get(at)
        .ok_or_else(|| DomainError::validation("utf-8 length is truncated"))?;
    if b0 & 0x80 != 0 {
        let b1 = *data
            .get(at + 1)
            .ok_or_else(|| DomainError::validation("utf-8 length is truncated"))?;
        Ok((((u32::from(b0 & 0x7f) << 8) | u32::from(b1)), at + 2))
    } else {
        Ok((u32::from(b0), at + 1))
    }
}

fn read_start_element(
    chunk: &[u8],
    strings: &[String],
    resource_map: &[u32],
    version_code: &mut Option<i32>,
    version_name: &mut Option<String>,
) -> Result<(), DomainError> {
    if chunk.len() < 36 {
        return Ok(());
    }
    let attr_start = u16_at(chunk, 24)? as usize;
    let attr_size = u16_at(chunk, 26)? as usize;
    let attr_count = u16_at(chunk, 28)? as usize;
    if attr_size < 20 {
        return Ok(());
    }
    for i in 0..attr_count {
        let at = 16 + attr_start + i * attr_size;
        if at + 20 > chunk.len() {
            break;
        }
        let name_idx = u32_at(chunk, at + 4)? as usize;
        let raw = u32_at(chunk, at + 8)?;
        let data_type = *chunk.get(at + 15).unwrap_or(&0);
        let data = u32_at(chunk, at + 16)?;
        let resource_id = resource_map.get(name_idx).copied();
        let name = strings.get(name_idx).map(|s| s.as_str()).unwrap_or("");
        let is_code = resource_id == Some(ATTR_VERSION_CODE) || name == "versionCode";
        let is_name = resource_id == Some(ATTR_VERSION_NAME) || name == "versionName";
        if is_code && (data_type == TYPE_INT_DEC || data_type == TYPE_INT_HEX) {
            *version_code = Some(data as i32);
        }
        if is_name {
            if data_type == TYPE_STRING {
                if let Some(value) = strings.get(data as usize) {
                    *version_name = Some(value.clone());
                }
            } else if raw != u32::MAX {
                if let Some(value) = strings.get(raw as usize) {
                    *version_name = Some(value.clone());
                }
            }
        }
    }
    Ok(())
}

fn u16_at(data: &[u8], at: usize) -> Result<u16, DomainError> {
    data.get(at..at + 2)
        .and_then(|s| s.try_into().ok())
        .map(u16::from_le_bytes)
        .ok_or_else(|| DomainError::validation("binary XML is truncated"))
}

fn u32_at(data: &[u8], at: usize) -> Result<u32, DomainError> {
    data.get(at..at + 4)
        .and_then(|s| s.try_into().ok())
        .map(u32::from_le_bytes)
        .ok_or_else(|| DomainError::validation("binary XML is truncated"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip_version_from_binary_manifest() {
        let axml = encode_android_manifest(3, "0.3.0");
        let parsed = parse_android_manifest(&axml).unwrap();
        assert_eq!(parsed.version_code, 3);
        assert_eq!(parsed.version_name, "0.3.0");
    }
}
