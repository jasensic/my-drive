use chrono::{DateTime, Utc};

use crate::apk::ApkIdentity;
use crate::ids::{AlbumId, AppReleaseId, DeviceId, FileId, UserId};
use crate::model::{Album, AppRelease, Device, FileRecord, SyncProfile, User};
use crate::music::{DownloadedAudio, MusicSearch};
use crate::DomainError;

#[async_trait::async_trait]
pub trait Clock: Send + Sync {
    fn now(&self) -> DateTime<Utc>;
}

#[async_trait::async_trait]
pub trait PasswordHasher: Send + Sync {
    fn hash(&self, password: &str) -> Result<String, DomainError>;
    fn verify(&self, password: &str, hash: &str) -> Result<bool, DomainError>;
}

pub trait TokenService: Send + Sync {
    fn issue(&self, user: &User) -> Result<String, DomainError>;
    fn verify(&self, token: &str) -> Result<crate::model::AuthSession, DomainError>;
}

#[async_trait::async_trait]
pub trait UserRepository: Send + Sync {
    async fn count(&self) -> Result<i64, DomainError>;
    async fn insert(&self, user: &User) -> Result<(), DomainError>;
    async fn find_by_username(&self, username: &str) -> Result<Option<User>, DomainError>;
    async fn find_by_id(&self, id: UserId) -> Result<Option<User>, DomainError>;
}

#[async_trait::async_trait]
pub trait AlbumRepository: Send + Sync {
    async fn insert(&self, album: &Album) -> Result<(), DomainError>;
    async fn list_by_owner(&self, owner_id: UserId) -> Result<Vec<Album>, DomainError>;
    async fn find_by_id(&self, id: AlbumId) -> Result<Option<Album>, DomainError>;
    async fn update_name(&self, id: AlbumId, name: &str) -> Result<(), DomainError>;
    async fn delete(&self, id: AlbumId) -> Result<(), DomainError>;
}

#[async_trait::async_trait]
pub trait FileRepository: Send + Sync {
    async fn insert(&self, file: &FileRecord) -> Result<(), DomainError>;
    async fn list_by_owner(&self, owner_id: UserId) -> Result<Vec<FileRecord>, DomainError>;
    async fn list_trashed_by_owner(&self, owner_id: UserId) -> Result<Vec<FileRecord>, DomainError>;
    async fn find_by_id(&self, id: FileId) -> Result<Option<FileRecord>, DomainError>;
    async fn assign_album(&self, id: FileId, album_id: Option<AlbumId>) -> Result<(), DomainError>;
    async fn update_name(&self, id: FileId, name: &str) -> Result<(), DomainError>;
    async fn set_thumbnail_key(&self, id: FileId, thumbnail_key: &str) -> Result<(), DomainError>;
    async fn set_deleted_at(
        &self,
        id: FileId,
        deleted_at: Option<DateTime<Utc>>,
    ) -> Result<(), DomainError>;
    async fn delete(&self, id: FileId) -> Result<(), DomainError>;
}

#[async_trait::async_trait]
pub trait DeviceRepository: Send + Sync {
    async fn insert(&self, device: &Device) -> Result<(), DomainError>;
    async fn list_by_user(&self, user_id: UserId) -> Result<Vec<Device>, DomainError>;
    async fn find_by_id(&self, id: DeviceId) -> Result<Option<Device>, DomainError>;
    async fn touch_sync(&self, id: DeviceId, at: DateTime<Utc>) -> Result<(), DomainError>;
}

#[async_trait::async_trait]
pub trait SyncProfileRepository: Send + Sync {
    async fn upsert(&self, profile: &SyncProfile) -> Result<(), DomainError>;
    async fn find_by_device(&self, device_id: DeviceId) -> Result<Option<SyncProfile>, DomainError>;
}

#[async_trait::async_trait]
pub trait AppReleaseRepository: Send + Sync {
    async fn insert(&self, release: &AppRelease) -> Result<(), DomainError>;
    async fn latest(&self) -> Result<Option<AppRelease>, DomainError>;
    async fn list(&self) -> Result<Vec<AppRelease>, DomainError>;
    async fn find_by_id(&self, id: AppReleaseId) -> Result<Option<AppRelease>, DomainError>;
    async fn find_by_version_code(&self, version_code: i32) -> Result<Option<AppRelease>, DomainError>;
    async fn update(&self, release: &AppRelease) -> Result<(), DomainError>;
    async fn delete(&self, id: AppReleaseId) -> Result<(), DomainError>;
}

pub trait ApkInspector: Send + Sync {
    fn inspect(&self, apk: &[u8]) -> Result<ApkIdentity, DomainError>;
}

#[async_trait::async_trait]
pub trait ObjectStore: Send + Sync {
    async fn put(&self, key: &str, bytes: bytes::Bytes, content_type: &str) -> Result<(), DomainError>;
    async fn get(&self, key: &str) -> Result<bytes::Bytes, DomainError>;
    async fn get_range(
        &self,
        key: &str,
        start: u64,
        end: Option<u64>,
    ) -> Result<(bytes::Bytes, u64), DomainError>;
    async fn delete(&self, key: &str) -> Result<(), DomainError>;
}

pub trait Thumbnailer: Send + Sync {
    fn jpeg_thumbnail(&self, bytes: &[u8], mime: &str) -> Option<Vec<u8>>;
}

/// Search and download via the musicdl-export sidecar. Search results stay on that
/// service; callers only pass `search_id` + `track_id` back to download.
#[async_trait::async_trait]
pub trait MusicDownloader: Send + Sync {
    async fn search(&self, keyword: &str) -> Result<MusicSearch, DomainError>;
    async fn download(&self, search_id: &str, track_id: &str) -> Result<DownloadedAudio, DomainError>;
}
