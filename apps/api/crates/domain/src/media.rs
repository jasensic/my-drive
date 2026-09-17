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
    }
}
