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
}
