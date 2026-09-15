use chrono::{DateTime, Utc};

use crate::ids::{AlbumId, DeviceId, FileId, UserId};
use crate::model::{Album, Device, FileRecord, SyncProfile, User};
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
}

#[async_trait::async_trait]
pub trait FileRepository: Send + Sync {
    async fn insert(&self, file: &FileRecord) -> Result<(), DomainError>;
    async fn list_by_owner(&self, owner_id: UserId) -> Result<Vec<FileRecord>, DomainError>;
    async fn find_by_id(&self, id: FileId) -> Result<Option<FileRecord>, DomainError>;
    async fn assign_album(&self, id: FileId, album_id: Option<AlbumId>) -> Result<(), DomainError>;
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
pub trait ObjectStore: Send + Sync {
    async fn put(&self, key: &str, bytes: bytes::Bytes, content_type: &str) -> Result<(), DomainError>;
    async fn get(&self, key: &str) -> Result<bytes::Bytes, DomainError>;
    async fn get_range(
        &self,
        key: &str,
        start: u64,
        end: Option<u64>,
    ) -> Result<(bytes::Bytes, u64), DomainError>;
}

pub trait Thumbnailer: Send + Sync {
    fn jpeg_thumbnail(&self, bytes: &[u8], mime: &str) -> Option<Vec<u8>>;
}
