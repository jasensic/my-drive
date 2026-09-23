use std::collections::{HashMap, HashSet};
use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use bytes::Bytes;
use chrono::{DateTime, Utc};
use domain::model::{
    Album, AppRelease, AuthSession, Device, FileRecord, Share, ShareResourceType, SyncProfile,
    TranscodedAudio, User, UserSummary,
};
use domain::ports::*;
use domain::{AlbumId, AppReleaseId, DeviceId, DomainError, FileId, ShareId, UserId};

use crate::{Deps, FileTranscodeLocks};

#[derive(Clone, Default)]
pub struct TestMem {
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
    exclusions: HashSet<(DeviceId, FileId)>,
    shares: Vec<Share>,
    objects: HashMap<String, Vec<u8>>,
}

impl TestMem {
    pub fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }

    pub async fn add_user(self: &Arc<Self>, username: &str) -> UserId {
        let user = User {
            id: UserId::new(),
            username: username.into(),
            password_hash: "hash".into(),
            created_at: Utc::now(),
        };
        UserRepository::insert(self.as_ref(), &user).await.unwrap();
        user.id
    }

    pub async fn insert_file(self: &Arc<Self>, file: FileRecord) {
        FileRepository::insert(self.as_ref(), &file).await.unwrap();
    }

    pub async fn insert_album(self: &Arc<Self>, album: Album) {
        AlbumRepository::insert(self.as_ref(), &album).await.unwrap();
    }
}

pub struct FakeClock(pub DateTime<Utc>);
impl Clock for FakeClock {
    fn now(&self) -> DateTime<Utc> {
        self.0
    }
}

pub struct NoopHasher;
impl PasswordHasher for NoopHasher {
    fn hash(&self, password: &str) -> Result<String, DomainError> {
        Ok(password.into())
    }
    fn verify(&self, password: &str, hash: &str) -> Result<bool, DomainError> {
        Ok(password == hash)
    }
}

pub struct NoopTokens;
impl TokenService for NoopTokens {
    fn issue(&self, _user: &User) -> Result<String, DomainError> {
        Ok("t".into())
    }
    fn verify(&self, _token: &str) -> Result<AuthSession, DomainError> {
        unimplemented!()
    }
}

pub struct NoopThumbs;
impl Thumbnailer for NoopThumbs {
    fn jpeg_thumbnail(&self, _bytes: &[u8], _mime: &str) -> Option<Vec<u8>> {
        None
    }
}

pub struct NoopApk;
impl ApkInspector for NoopApk {
    fn inspect(&self, _apk: &[u8]) -> Result<domain::apk::ApkIdentity, DomainError> {
        Err(DomainError::validation("no apk"))
    }
}

pub struct NoMusic;
#[async_trait]
impl MusicDownloader for NoMusic {
    async fn search(&self, _keyword: &str) -> Result<domain::music::MusicSearch, DomainError> {
        Err(DomainError::infra("unused"))
    }
    async fn download(
        &self,
        _search_id: &str,
        _track_id: &str,
    ) -> Result<domain::music::DownloadedAudio, DomainError> {
        Err(DomainError::infra("unused"))
    }
}

pub struct FakeTranscoder;
#[async_trait]
impl AudioTranscoder for FakeTranscoder {
    async fn transcode_aac_256(
        &self,
        _original: &[u8],
        _source_mime: &str,
        _cover_jpeg: Option<&[u8]>,
    ) -> Result<TranscodedAudio, DomainError> {
        Ok(TranscodedAudio {
            bytes: Bytes::from_static(b"fake-aac"),
            mime: "audio/mp4".into(),
        })
    }
}

pub fn deps_from(mem: Arc<TestMem>) -> Arc<Deps> {
    Arc::new(Deps {
        users: mem.clone(),
        albums: mem.clone(),
        files: mem.clone(),
        devices: mem.clone(),
        exclusions: mem.clone(),
        shares: mem.clone(),
        profiles: mem.clone(),
        app_releases: mem.clone(),
        objects: mem,
        hasher: Arc::new(NoopHasher),
        tokens: Arc::new(NoopTokens),
        clock: Arc::new(FakeClock(Utc::now())),
        thumbnailer: Arc::new(NoopThumbs),
        apk_inspector: Arc::new(NoopApk),
        music: Arc::new(NoMusic),
        audio_transcoder: Arc::new(FakeTranscoder),
        transcode_locks: Arc::new(FileTranscodeLocks::new()),
    })
}

#[async_trait]
impl UserRepository for TestMem {
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
    async fn list_summaries(&self) -> Result<Vec<UserSummary>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .users
            .iter()
            .map(|u| UserSummary {
                id: u.id,
                username: u.username.clone(),
            })
            .collect())
    }
}

#[async_trait]
impl AlbumRepository for TestMem {
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
    async fn list_by_ids(&self, ids: &[AlbumId]) -> Result<Vec<Album>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .albums
            .iter()
            .filter(|a| ids.contains(&a.id))
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
    async fn update_name(&self, id: AlbumId, name: &str) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        let album = inner
            .albums
            .iter_mut()
            .find(|a| a.id == id)
            .ok_or_else(|| DomainError::not_found("album not found"))?;
        album.name = name.to_string();
        Ok(())
    }
    async fn delete(&self, id: AlbumId) -> Result<(), DomainError> {
        self.inner.lock().unwrap().albums.retain(|a| a.id != id);
        Ok(())
    }
}

#[async_trait]
impl FileRepository for TestMem {
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
            .filter(|f| f.owner_id == owner_id && f.deleted_at.is_none())
            .cloned()
            .collect())
    }
    async fn list_trashed_by_owner(&self, owner_id: UserId) -> Result<Vec<FileRecord>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .files
            .iter()
            .filter(|f| f.owner_id == owner_id && f.deleted_at.is_some())
            .cloned()
            .collect())
    }
    async fn list_by_ids(&self, ids: &[FileId]) -> Result<Vec<FileRecord>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .files
            .iter()
            .filter(|f| ids.contains(&f.id))
            .cloned()
            .collect())
    }
    async fn list_by_album_ids(&self, ids: &[AlbumId]) -> Result<Vec<FileRecord>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .files
            .iter()
            .filter(|f| f.album_id.is_some_and(|id| ids.contains(&id)))
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
        if let Some(file) = inner.files.iter_mut().find(|f| f.id == id) {
            file.album_id = album_id;
        }
        Ok(())
    }
    async fn update_name(&self, id: FileId, name: &str) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        if let Some(file) = inner.files.iter_mut().find(|f| f.id == id) {
            file.name = name.to_string();
        }
        Ok(())
    }
    async fn set_thumbnail_key(&self, id: FileId, thumbnail_key: &str) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        if let Some(file) = inner.files.iter_mut().find(|f| f.id == id) {
            file.thumbnail_key = Some(thumbnail_key.to_string());
        }
        Ok(())
    }
    async fn set_mobile_variant(
        &self,
        id: FileId,
        object_key: &str,
        checksum: &str,
        size: u64,
        mime: &str,
    ) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        if let Some(file) = inner.files.iter_mut().find(|f| f.id == id) {
            file.mobile_object_key = Some(object_key.to_string());
            file.mobile_checksum = Some(checksum.to_string());
            file.mobile_size = Some(size);
            file.mobile_mime = Some(mime.to_string());
        }
        Ok(())
    }
    async fn set_deleted_at(
        &self,
        id: FileId,
        deleted_at: Option<DateTime<Utc>>,
    ) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        if let Some(file) = inner.files.iter_mut().find(|f| f.id == id) {
            file.deleted_at = deleted_at;
        }
        Ok(())
    }
    async fn delete(&self, id: FileId) -> Result<(), DomainError> {
        self.inner.lock().unwrap().files.retain(|f| f.id != id);
        Ok(())
    }
}

#[async_trait]
impl DeviceRepository for TestMem {
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
impl DeviceExclusionRepository for TestMem {
    async fn merge(&self, device_id: DeviceId, file_ids: &[FileId]) -> Result<(), DomainError> {
        let mut inner = self.inner.lock().unwrap();
        for file_id in file_ids {
            inner.exclusions.insert((device_id, *file_id));
        }
        Ok(())
    }
    async fn list(&self, device_id: DeviceId) -> Result<Vec<FileId>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .exclusions
            .iter()
            .filter(|(d, _)| *d == device_id)
            .map(|(_, f)| *f)
            .collect())
    }
}

#[async_trait]
impl ShareRepository for TestMem {
    async fn upsert(&self, share: &Share) -> Result<Share, DomainError> {
        let mut inner = self.inner.lock().unwrap();
        if let Some(existing) = inner.shares.iter_mut().find(|s| {
            s.resource_type == share.resource_type
                && s.resource_id == share.resource_id
                && s.grantee_id == share.grantee_id
        }) {
            existing.permission = share.permission;
            return Ok(existing.clone());
        }
        inner.shares.push(share.clone());
        Ok(share.clone())
    }
    async fn find_by_id(&self, id: ShareId) -> Result<Option<Share>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .shares
            .iter()
            .find(|s| s.id == id)
            .cloned())
    }
    async fn delete(&self, id: ShareId) -> Result<(), DomainError> {
        self.inner.lock().unwrap().shares.retain(|s| s.id != id);
        Ok(())
    }
    async fn delete_for_resource(
        &self,
        resource_type: ShareResourceType,
        resource_id: uuid::Uuid,
    ) -> Result<(), DomainError> {
        self.inner
            .lock()
            .unwrap()
            .shares
            .retain(|s| !(s.resource_type == resource_type && s.resource_id == resource_id));
        Ok(())
    }
    async fn list_by_owner(&self, owner_id: UserId) -> Result<Vec<Share>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .shares
            .iter()
            .filter(|s| s.owner_id == owner_id)
            .cloned()
            .collect())
    }
    async fn list_by_grantee(&self, grantee_id: UserId) -> Result<Vec<Share>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .shares
            .iter()
            .filter(|s| s.grantee_id == grantee_id)
            .cloned()
            .collect())
    }
    async fn list_by_resource(
        &self,
        resource_type: ShareResourceType,
        resource_id: uuid::Uuid,
    ) -> Result<Vec<Share>, DomainError> {
        Ok(self
            .inner
            .lock()
            .unwrap()
            .shares
            .iter()
            .filter(|s| s.resource_type == resource_type && s.resource_id == resource_id)
            .cloned()
            .collect())
    }
}

#[async_trait]
impl SyncProfileRepository for TestMem {
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
impl AppReleaseRepository for TestMem {
    async fn insert(&self, release: &AppRelease) -> Result<(), DomainError> {
        self.inner.lock().unwrap().app_releases.push(release.clone());
        Ok(())
    }
    async fn latest(&self) -> Result<Option<AppRelease>, DomainError> {
        Ok(None)
    }
    async fn list(&self) -> Result<Vec<AppRelease>, DomainError> {
        Ok(vec![])
    }
    async fn find_by_id(&self, _id: AppReleaseId) -> Result<Option<AppRelease>, DomainError> {
        Ok(None)
    }
    async fn find_by_version_code(&self, _version_code: i32) -> Result<Option<AppRelease>, DomainError> {
        Ok(None)
    }
    async fn update(&self, _release: &AppRelease) -> Result<(), DomainError> {
        Ok(())
    }
    async fn delete(&self, _id: AppReleaseId) -> Result<(), DomainError> {
        Ok(())
    }
}

#[async_trait]
impl ObjectStore for TestMem {
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
    async fn delete(&self, key: &str) -> Result<(), DomainError> {
        self.inner.lock().unwrap().objects.remove(key);
        Ok(())
    }
}
