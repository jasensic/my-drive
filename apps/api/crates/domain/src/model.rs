use chrono::{DateTime, Utc};
use serde::{Deserialize, Serialize};

use crate::ids::{AlbumId, AppReleaseId, DeviceId, FileId, SyncProfileId, UserId};
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

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ManifestEntry {
    pub id: FileId,
    pub name: String,
    pub size: u64,
    pub mime: String,
    pub checksum: String,
    pub media_kind: MediaKind,
    pub album_id: Option<AlbumId>,
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
