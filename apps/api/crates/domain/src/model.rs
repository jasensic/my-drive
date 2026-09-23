use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use crate::ids::{AlbumId, AppReleaseId, DeviceId, FileId, ShareId, SyncProfileId, UserId};
use crate::library::LibrarySilo;
use crate::media::MediaKind;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct User {
    pub id: UserId,
    pub username: String,
    pub password_hash: String,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserSummary {
    pub id: UserId,
    pub username: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AuthSession {
    pub user_id: UserId,
    pub username: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Album {
    pub id: AlbumId,
    pub owner_id: UserId,
    pub name: String,
    pub silo: LibrarySilo,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FileRecord {
    pub id: FileId,
    pub owner_id: UserId,
    pub album_id: Option<AlbumId>,
    pub name: String,
    pub size: u64,
    pub mime: String,
    pub checksum: String,
    pub object_key: String,
    pub thumbnail_key: Option<String>,
    pub media_kind: MediaKind,
    pub created_at: DateTime<Utc>,
    pub uploaded_at: DateTime<Utc>,
    pub deleted_at: Option<DateTime<Utc>>,
    pub mobile_object_key: Option<String>,
    pub mobile_checksum: Option<String>,
    pub mobile_size: Option<u64>,
    pub mobile_mime: Option<String>,
}

impl FileRecord {
    pub fn has_mobile_audio(&self) -> bool {
        self.media_kind == MediaKind::Audio
            && self.mobile_object_key.is_some()
            && self.mobile_checksum.is_some()
            && self.mobile_size.is_some()
            && self.mobile_mime.is_some()
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Device {
    pub id: DeviceId,
    pub user_id: UserId,
    pub name: String,
    pub last_sync_at: Option<DateTime<Utc>>,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncRule {
    pub media_kind: MediaKind,
    pub max_age_days: Option<u32>,
    pub max_size_bytes: Option<u64>,
    pub include_all: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncProfile {
    pub id: SyncProfileId,
    pub device_id: DeviceId,
    pub name: String,
    pub rules: Vec<SyncRule>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum ShareResourceType {
    File,
    Album,
}

impl ShareResourceType {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::File => "file",
            Self::Album => "album",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "file" => Some(Self::File),
            "album" => Some(Self::Album),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum SharePermission {
    Read,
    Write,
}

impl SharePermission {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Read => "read",
            Self::Write => "write",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "read" => Some(Self::Read),
            "write" => Some(Self::Write),
            _ => None,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Share {
    pub id: ShareId,
    pub resource_type: ShareResourceType,
    pub resource_id: uuid::Uuid,
    pub owner_id: UserId,
    pub grantee_id: UserId,
    pub permission: SharePermission,
    pub created_at: DateTime<Utc>,
}

#[derive(Debug, Clone)]
pub struct TranscodedAudio {
    pub bytes: bytes::Bytes,
    pub mime: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ManifestEntry {
    pub id: FileId,
    pub name: String,
    pub size: u64,
    pub mime: String,
    pub checksum: String,
    pub media_kind: MediaKind,
    pub album_id: Option<AlbumId>,
    pub mobile: bool,
}

impl ManifestEntry {
    pub fn from_file(file: &FileRecord) -> Self {
        let mobile = file.has_mobile_audio();
        Self {
            id: file.id,
            name: file.name.clone(),
            size: if mobile {
                file.mobile_size.unwrap_or(file.size)
            } else {
                file.size
            },
            mime: if mobile {
                file.mobile_mime.clone().unwrap_or_else(|| file.mime.clone())
            } else {
                file.mime.clone()
            },
            checksum: if mobile {
                file.mobile_checksum
                    .clone()
                    .unwrap_or_else(|| file.checksum.clone())
            } else {
                file.checksum.clone()
            },
            media_kind: file.media_kind,
            album_id: file.album_id,
            mobile,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncManifest {
    pub generated_at: DateTime<Utc>,
    pub files: Vec<ManifestEntry>,
    pub albums: Vec<Album>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppRelease {
    pub id: AppReleaseId,
    pub version_code: i32,
    pub version_name: String,
    pub changelog: String,
    pub object_key: String,
    pub checksum: String,
    pub size: u64,
    pub published_at: DateTime<Utc>,
}
