use std::collections::HashMap;
use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use bytes::Bytes;
use chrono::{DateTime, Utc};
use domain::model::{Album, AppRelease, Device, FileRecord, SyncProfile, User};
use domain::ports::{
    AlbumRepository, AppReleaseRepository, DeviceRepository, FileRepository, ObjectStore,
    SyncProfileRepository, UserRepository,
};
use domain::{AlbumId, AppReleaseId, DeviceId, DomainError, FileId, UserId};

#[derive(Clone, Default)]
pub struct MemoryStore {
    inner: Arc<Mutex<Inner>>,
}

#[derive(Default)]
struct Inner {
    users: Vec<User>,
    albums: Vec<Album>,
    files: Vec<FileRecord>,
    devices: Vec<Device>,
    profiles: Vec<SyncProfile>,
    app_releases: Vec<AppRelease>,
    objects: HashMap<String, Vec<u8>>,
}

impl MemoryStore {
    pub fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }
}

#[async_trait]
impl UserRepository for MemoryStore {
    async fn count(&self) -> Result<i64, DomainError> {
        Ok(self.inner.lock().unwrap().users.len() as i64)
    }

    async fn insert(&self, user: &User) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        if inner.users.iter().any(|u| u.username == user.username) {
            return Err(DomainError::conflict("username already exists"));
        }
        inner.users.push(user.clone());
        Ok(())
    }

    async fn find_by_username(&self, username: &str) -> Result<Option<User>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .users
            .iter()
            .find(|u| u.username == username)
            .cloned())
    }

    async fn find_by_id(&self, id: UserId) -> Result<Option<User>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .users
            .iter()
            .find(|u| u.id == id)
            .cloned())
    }
}

#[async_trait]
impl AlbumRepository for MemoryStore {
    async fn insert(&self, album: &Album) -> Result<(), DomainError> {
        self.inner.lock().unwrap().albums.push(album.clone());
        Ok(())
    }

    async fn list_by_owner(&self, owner_id: UserId) -> Result<Vec<Album>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .albums
            .iter()
            .filter(|a| a.owner_id == owner_id)
            .cloned()
            .collect())
    }

    async fn find_by_id(&self, id: AlbumId) -> Result<Option<Album>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .albums
            .iter()
            .find(|a| a.id == id)
            .cloned())
    }
}

#[async_trait]
impl FileRepository for MemoryStore {
    async fn insert(&self, file: &FileRecord) -> Result<(), DomainError> {
        self.inner.lock().unwrap().files.push(file.clone());
        Ok(())
    }

    async fn list_by_owner(&self, owner_id: UserId) -> Result<Vec<FileRecord>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .files
            .iter()
            .filter(|f| f.owner_id == owner_id)
            .cloned()
            .collect())
    }

    async fn find_by_id(&self, id: FileId) -> Result<Option<FileRecord>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .files
            .iter()
            .find(|f| f.id == id)
            .cloned())
    }

    async fn assign_album(&self, id: FileId, album_id: Option<AlbumId>) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        let file = inner
            .files
            .iter_mut()
            .find(|f| f.id == id)
            .ok_or_else(|| DomainError::not_found("file not found"))?;
        file.album_id = album_id;
        Ok(())
    }
}

#[async_trait]
impl DeviceRepository for MemoryStore {
    async fn insert(&self, device: &Device) -> Result<(), DomainError> {
        self.inner.lock().unwrap().devices.push(device.clone());
        Ok(())
    }

    async fn list_by_user(&self, user_id: UserId) -> Result<Vec<Device>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .devices
            .iter()
            .filter(|d| d.user_id == user_id)
            .cloned()
            .collect())
    }

    async fn find_by_id(&self, id: DeviceId) -> Result<Option<Device>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .devices
            .iter()
            .find(|d| d.id == id)
            .cloned())
    }

    async fn touch_sync(&self, id: DeviceId, at: DateTime<Utc>) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        if let Some(d) = inner.devices.iter_mut().find(|d| d.id == id) {
            d.last_sync_at = Some(at);
        }
        Ok(())
    }
}

#[async_trait]
impl SyncProfileRepository for MemoryStore {
    async fn upsert(&self, profile: &SyncProfile) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        inner.profiles.retain(|p| p.device_id != profile.device_id);
        inner.profiles.push(profile.clone());
        Ok(())
    }

    async fn find_by_device(&self, device_id: DeviceId) -> Result<Option<SyncProfile>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .profiles
            .iter()
            .find(|p| p.device_id == device_id)
            .cloned())
    }
}

#[async_trait]
impl AppReleaseRepository for MemoryStore {
    async fn insert(&self, release: &AppRelease) -> Result<(), DomainError> {
        self.inner.lock().unwrap().app_releases.push(release.clone());
        Ok(())
    }

    async fn latest(&self) -> Result<Option<AppRelease>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .app_releases
            .iter()
            .max_by_key(|r| r.version_code)
            .cloned())
    }

    async fn list(&self) -> Result<Vec<AppRelease>, DomainError> {
        let mut releases = self.inner.lock().unwrap().app_releases.clone();
        releases.sort_by(|a, b| b.version_code.cmp(&a.version_code));
        Ok(releases)
    }

    async fn find_by_id(&self, id: AppReleaseId) -> Result<Option<AppRelease>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .app_releases
            .iter()
            .find(|r| r.id == id)
            .cloned())
    }

    async fn find_by_version_code(
        &self,
        version_code: i32,
    ) -> Result<Option<AppRelease>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .app_releases
            .iter()
            .find(|r| r.version_code == version_code)
            .cloned())
    }
}

#[async_trait]
impl ObjectStore for MemoryStore {
    async fn put(&self, key: &str, bytes: Bytes, _content_type: &str) -> Result<(), DomainError> {
        self.inner
            .lock()
            .unwrap()
            .objects
            .insert(key.to_string(), bytes.to_vec());
        Ok(())
    }

    async fn get(&self, key: &str) -> Result<Bytes, DomainError> {
        self.inner
            .lock()
            .unwrap()
            .objects
            .get(key)
            .cloned()
            .map(Bytes::from)
            .ok_or_else(|| DomainError::not_found("object not found"))
    }

    async fn get_range(
        &self,
        key: &str,
        start: u64,
        end: Option<u64>,
    ) -> Result<(Bytes, u64), DomainError> {
        let data = self.get(key).await?;
        let total = data.len() as u64;
        let end = end.unwrap_or(total.saturating_sub(1)).min(total.saturating_sub(1));
        let start = start.min(end);
        Ok((data.slice(start as usize..=end as usize), total))
    }
}
