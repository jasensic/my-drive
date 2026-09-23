use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum MediaKind {
    Photo,
    Video,
    Audio,
    Other,
}

impl MediaKind {
    pub fn from_mime(mime: &str) -> Self {
        let lowered = mime.to_ascii_lowercase();
        if lowered.starts_with("image/") {
            Self::Photo
        } else if lowered.starts_with("video/") {
            Self::Video
        } else if lowered.starts_with("audio/") {
            Self::Audio
        } else {
            Self::Other
        }
    }

    pub fn as_str(self) -> &'static str {
        match self {
            Self::Photo => "photo",
            Self::Video => "video",
            Self::Audio => "audio",
            Self::Other => "other",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "photo" => Some(Self::Photo),
            "video" => Some(Self::Video),
            "audio" => Some(Self::Audio),
            "other" => Some(Self::Other),
            _ => None,
        }
    }

    pub fn object_prefix(self) -> &'static str {
        match self {
            Self::Photo => "images",
            Self::Video => "videos",
            Self::Audio => "music",
            Self::Other => "other",
        }
    }
}

pub fn object_key(kind: MediaKind, id: crate::FileId, name: &str) -> String {
    format!("{}/{}/{}", kind.object_prefix(), id, sanitize_object_name(name))
}

pub fn thumbnail_object_key(id: crate::FileId) -> String {
    format!("images/thumbs/{id}.jpg")
}

pub const MOBILE_AUDIO_MIME: &str = "audio/mp4";

pub fn mobile_audio_object_key(id: crate::FileId) -> String {
    format!("music/{id}/mobile.m4a")
}

/// Lossless audio needs an AAC derivative for Android sync. MP3/M4A/AAC stay as-is.
pub fn audio_needs_mobile_transcode(mime: &str, name: &str) -> bool {
    let mime = mime.to_ascii_lowercase();
    let name = name.to_ascii_lowercase();
    mime.contains("flac")
        || mime.contains("wav")
        || mime.contains("aiff")
        || mime.contains("alac")
        || name.ends_with(".flac")
        || name.ends_with(".wav")
        || name.ends_with(".aiff")
        || name.ends_with(".aif")
        || name.ends_with(".alac")
}

fn sanitize_object_name(name: &str) -> String {
    let cleaned: String = name
        .chars()
        .map(|c| match c {
            '/' | '\\' | '\0' => '_',
            c if c.is_control() => '_',
            c => c,
        })
        .collect();
    let trimmed = cleaned.trim().trim_start_matches('.');
    if trimmed.is_empty() {
        "file".into()
    } else {
        trimmed.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use uuid::Uuid;

    #[test]
    fn prefixes_split_minio_by_media_kind() {
        assert_eq!(MediaKind::Photo.object_prefix(), "images");
        assert_eq!(MediaKind::Video.object_prefix(), "videos");
        assert_eq!(MediaKind::Audio.object_prefix(), "music");
        assert_eq!(MediaKind::Other.object_prefix(), "other");
    }

    #[test]
    fn object_key_keeps_files_under_kind_folders() {
        let id = crate::FileId::from_uuid(Uuid::nil());
        assert_eq!(
            object_key(MediaKind::Photo, id, "shot.png"),
            "images/00000000-0000-0000-0000-000000000000/shot.png"
        );
        assert_eq!(
            object_key(MediaKind::Audio, id, "a/b.mp3"),
            "music/00000000-0000-0000-0000-000000000000/a_b.mp3"
        );
        assert_eq!(
            mobile_audio_object_key(id),
            "music/00000000-0000-0000-0000-000000000000/mobile.m4a"
        );
    }

    #[test]
    fn lossless_audio_needs_a_mobile_derivative() {
        assert!(audio_needs_mobile_transcode("audio/flac", "song.flac"));
        assert!(audio_needs_mobile_transcode("audio/wav", "song.wav"));
        assert!(audio_needs_mobile_transcode("audio/aiff", "song.aiff"));
        assert!(audio_needs_mobile_transcode("audio/mp4", "song.alac"));
        assert!(!audio_needs_mobile_transcode("audio/mpeg", "song.mp3"));
        assert!(!audio_needs_mobile_transcode("audio/mp4", "song.m4a"));
        assert!(!audio_needs_mobile_transcode("audio/aac", "song.aac"));
    }
}
