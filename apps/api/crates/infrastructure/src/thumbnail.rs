use domain::ports::Thumbnailer;
use image::imageops::FilterType;
use image::ImageFormat;
use std::io::Cursor;

pub struct ImageThumbnailer;

impl Thumbnailer for ImageThumbnailer {
    fn jpeg_thumbnail(&self, bytes: &[u8], mime: &str) -> Option<Vec<u8>> {
        if !mime.to_ascii_lowercase().starts_with("image/") {
            return None;
        }
        let img = image::load_from_memory(bytes).ok()?;
        let thumb = img.resize(320, 320, FilterType::Triangle);
        let mut out = Cursor::new(Vec::new());
        thumb.write_to(&mut out, ImageFormat::Jpeg).ok()?;
        Some(out.into_inner())
    }
}
