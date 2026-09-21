use domain::ports::Thumbnailer;
use image::imageops::FilterType;
use image::ImageFormat;
use std::io::Cursor;

pub struct ImageThumbnailer;

impl Thumbnailer for ImageThumbnailer {
    fn jpeg_thumbnail(&self, bytes: &[u8], mime: &str) -> Option<Vec<u8>> {
        let mime = mime.to_ascii_lowercase();
        let raw = if mime.starts_with("image/") {
            bytes.to_vec()
        } else if mime.starts_with("audio/") {
            embedded_cover(bytes)?
        } else {
            return None;
        };
        encode_jpeg(&raw)
    }
}

fn encode_jpeg(bytes: &[u8]) -> Option<Vec<u8>> {
    let img = image::load_from_memory(bytes).ok()?;
    let thumb = img.resize(320, 320, FilterType::Triangle);
    let mut out = Cursor::new(Vec::new());
    thumb.write_to(&mut out, ImageFormat::Jpeg).ok()?;
    Some(out.into_inner())
}

fn embedded_cover(bytes: &[u8]) -> Option<Vec<u8>> {
    flac_picture(bytes)
        .or_else(|| id3_apic(bytes))
        .or_else(|| mp4_cover(bytes))
        .filter(|data| looks_like_image(data))
}

fn looks_like_image(data: &[u8]) -> bool {
    data.starts_with(&[0xff, 0xd8]) || data.starts_with(b"\x89PNG\r\n\x1a\n")
}

fn flac_picture(bytes: &[u8]) -> Option<Vec<u8>> {
    if bytes.len() < 8 || &bytes[..4] != b"fLaC" {
        return None;
    }
    let mut offset = 4;
    loop {
        if offset + 4 > bytes.len() {
            return None;
        }
        let last = bytes[offset] & 0x80 != 0;
        let kind = bytes[offset] & 0x7f;
        let len = u32::from_be_bytes([0, bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]]) as usize;
        offset += 4;
        if offset + len > bytes.len() {
            return None;
        }
        if kind == 6 {
            if let Some(picture) = picture_block(&bytes[offset..offset + len]) {
                return Some(picture);
            }
        }
        offset += len;
        if last {
            return None;
        }
    }
}

fn picture_block(block: &[u8]) -> Option<Vec<u8>> {
    let mime_len = read_u32(block, 4)? as usize;
    let mut index = 8 + mime_len;
    let desc_len = read_u32(block, index)? as usize;
    index += 4 + desc_len + 16;
    let data_len = read_u32(block, index)? as usize;
    index += 4;
    block.get(index..index + data_len).map(|data| data.to_vec())
}

fn id3_apic(bytes: &[u8]) -> Option<Vec<u8>> {
    if bytes.len() < 10 || &bytes[..3] != b"ID3" || !matches!(bytes[3], 3 | 4) {
        return None;
    }
    let tag_size = synchsafe(&bytes[6..10])?;
    let end = (10 + tag_size).min(bytes.len());
    let mut index = 10;
    while index + 10 <= end {
        let id = &bytes[index..index + 4];
        if id == b"\0\0\0\0" {
            break;
        }
        let frame_size = if bytes[3] == 4 {
            synchsafe(&bytes[index + 4..index + 8])?
        } else {
            u32::from_be_bytes(bytes[index + 4..index + 8].try_into().ok()?) as usize
        };
        index += 10;
        if index + frame_size > end {
            break;
        }
        if id == b"APIC" {
            if let Some(picture) = apic_payload(&bytes[index..index + frame_size]) {
                return Some(picture);
            }
        }
        index += frame_size;
    }
    None
}

fn apic_payload(frame: &[u8]) -> Option<Vec<u8>> {
    if frame.is_empty() {
        return None;
    }
    let encoding = frame[0];
    let mime_end = frame[1..].iter().position(|byte| *byte == 0)? + 1;
    let mut index = 1 + mime_end + 1;
    if encoding == 1 || encoding == 2 {
        while index + 1 < frame.len() {
            if frame[index] == 0 && frame[index + 1] == 0 {
                index += 2;
                break;
            }
            index += 2;
        }
    } else {
        let end = frame[index..].iter().position(|byte| *byte == 0)?;
        index += end + 1;
    }
    frame.get(index..).map(|data| data.to_vec())
}

fn mp4_cover(bytes: &[u8]) -> Option<Vec<u8>> {
    let cover = find_atom(bytes, b"covr")?;
    let mut index = 0;
    while index + 16 <= cover.len() {
        let size = u32::from_be_bytes(cover[index..index + 4].try_into().ok()?) as usize;
        if size < 16 || index + size > cover.len() {
            break;
        }
        if &cover[index + 4..index + 8] == b"data" {
            let payload = &cover[index + 16..index + size];
            if looks_like_image(payload) {
                return Some(payload.to_vec());
            }
        }
        index += size;
    }
    None
}

fn find_atom<'a>(bytes: &'a [u8], name: &[u8; 4]) -> Option<&'a [u8]> {
    let mut index = 0;
    while index + 8 <= bytes.len() {
        let size = u32::from_be_bytes(bytes[index..index + 4].try_into().ok()?) as usize;
        if size < 8 || index + size > bytes.len() {
            return None;
        }
        let kind = &bytes[index + 4..index + 8];
        let body = &bytes[index + 8..index + size];
        if kind == name {
            return Some(body);
        }
        if matches!(kind, b"moov" | b"udta" | b"meta" | b"ilst" | b"trak") {
            let children = if kind == b"meta" { body.get(4..)? } else { body };
            if let Some(found) = find_atom(children, name) {
                return Some(found);
            }
        }
        index += size;
    }
    None
}

fn read_u32(bytes: &[u8], offset: usize) -> Option<u32> {
    bytes.get(offset..offset + 4).and_then(|chunk| chunk.try_into().ok()).map(u32::from_be_bytes)
}

fn synchsafe(bytes: &[u8]) -> Option<usize> {
    if bytes.len() != 4 || bytes.iter().any(|byte| byte & 0x80 != 0) {
        return None;
    }
    Some(((bytes[0] as usize) << 21) | ((bytes[1] as usize) << 14) | ((bytes[2] as usize) << 7) | bytes[3] as usize)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tiny_jpeg() -> Vec<u8> {
        let image = image::DynamicImage::ImageRgb8(image::RgbImage::from_pixel(2, 2, image::Rgb([12, 34, 56])));
        let mut bytes = Cursor::new(Vec::new());
        image.write_to(&mut bytes, ImageFormat::Jpeg).unwrap();
        bytes.into_inner()
    }

    fn flac_with_picture(jpeg: &[u8]) -> Vec<u8> {
        let mime = b"image/jpeg";
        let mut picture = Vec::new();
        picture.extend_from_slice(&3u32.to_be_bytes());
        picture.extend_from_slice(&(mime.len() as u32).to_be_bytes());
        picture.extend_from_slice(mime);
        picture.extend_from_slice(&0u32.to_be_bytes());
        picture.extend_from_slice(&2u32.to_be_bytes());
        picture.extend_from_slice(&2u32.to_be_bytes());
        picture.extend_from_slice(&24u32.to_be_bytes());
        picture.extend_from_slice(&0u32.to_be_bytes());
        picture.extend_from_slice(&(jpeg.len() as u32).to_be_bytes());
        picture.extend_from_slice(jpeg);
        let mut file = b"fLaC".to_vec();
        file.push(0x86);
        let len = picture.len() as u32;
        file.extend_from_slice(&len.to_be_bytes()[1..]);
        file.extend_from_slice(&picture);
        file
    }

    #[test]
    fn extracts_a_jpeg_cover_from_flac() {
        let jpeg = tiny_jpeg();
        let thumb = ImageThumbnailer.jpeg_thumbnail(&flac_with_picture(&jpeg), "audio/flac");
        let thumb = thumb.expect("cover");
        assert!(thumb.starts_with(&[0xff, 0xd8]));
    }
}
