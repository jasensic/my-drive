use std::collections::HashSet;
use std::sync::Arc;

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use domain::model::SyncManifest;
use domain::sync::evaluate_manifest;
use domain::{DeviceId, FileId, UserId};

use super::devices::owned_device;
use crate::{AppError, Deps};

pub struct ManifestQuery {
    pub user_id: UserId,
    pub device_id: DeviceId,
    pub last_sync_at: Option<DateTime<Utc>>,
    pub have_file_ids: HashSet<FileId>,
}

#[async_trait]
pub trait BuildSyncManifest: Send + Sync {
    async fn execute(&self, query: ManifestQuery) -> Result<SyncManifest, AppError>;
}

pub struct BuildSyncManifestService {
    deps: Arc<Deps>,
}

impl BuildSyncManifestService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl BuildSyncManifest for BuildSyncManifestService {
    async fn execute(&self, query: ManifestQuery) -> Result<SyncManifest, AppError> {
        let device = owned_device(&self.deps, query.user_id, query.device_id).await?;
        let profile = self
            .deps
            .profiles
            .find_by_device(device.id)
            .await?
            .ok_or_else(|| AppError::not_found("sync profile not found"))?;
        let now = self.deps.clock.now();
        let files = self.deps.files.list_by_owner(query.user_id).await?;
        let mut manifest = evaluate_manifest(&files, &profile.rules, &query.have_file_ids, now);
        manifest.albums = self.deps.albums.list_by_owner(query.user_id).await?;
        self.deps.devices.touch_sync(device.id, now).await?;
        Ok(manifest)
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use bytes::Bytes;
    use chrono::TimeZone;
    use domain::model::{FileRecord, SyncProfile, User};
    use domain::ports::*;
    use domain::{DomainError, FileId, MediaKind, UserId};

    use super::*;
    use crate::usecases::devices::default_profile;

    struct FakeClock(DateTime<Utc>);
    impl Clock for FakeClock {
        fn now(&self) -> DateTime<Utc> {
            self.0
        }
    }

    struct NoopHasher;
    impl PasswordHasher for NoopHasher {
        fn hash(&self, password: &str) -> Result<String, DomainError> {
            Ok(password.into())
        }
        fn verify(&self, password: &str, hash: &str) -> Result<bool, DomainError> {
            Ok(password == hash)
        }
    }

    struct NoopTokens;
    impl TokenService for NoopTokens {
        fn issue(&self, _user: &User) -> Result<String, DomainError> {
            Ok("t".into())
        }
        fn verify(&self, _token: &str) -> Result<domain::model::AuthSession, DomainError> {
            unimplemented!()
        }
    }

    struct NoopThumbs;
    impl Thumbnailer for NoopThumbs {
        fn jpeg_thumbnail(&self, _bytes: &[u8], _mime: &str) -> Option<Vec<u8>> {
            None
        }
    }

    struct NoopObjects;
    #[async_trait]
    impl ObjectStore for NoopObjects {
        async fn put(&self, _k: &str, _b: Bytes, _c: &str) -> Result<(), DomainError> {
            Ok(())
        }
        async fn get(&self, _k: &str) -> Result<Bytes, DomainError> {
            Ok(Bytes::new())
        }
        async fn get_range(
            &self,
            _k: &str,
            _s: u64,
            _e: Option<u64>,
        ) -> Result<(Bytes, u64), DomainError> {
            Ok((Bytes::new(), 0))
        }
        async fn delete(&self, _k: &str) -> Result<(), DomainError> {
            Ok(())
        }
    }

    #[derive(Default)]
    struct Mem {
        files: Mutex<Vec<FileRecord>>,
        devices: Mutex<Vec<domain::model::Device>>,
        profiles: Mutex<Vec<SyncProfile>>,
    }

    #[async_trait]
    impl UserRepository for Mem {
        async fn count(&self) -> Result<i64, DomainError> {
            Ok(1)
        }
        async fn insert(&self, _user: &User) -> Result<(), DomainError> {
            Ok(())
        }
        async fn find_by_username(&self, _u: &str) -> Result<Option<User>, DomainError> {
            Ok(None)
        }
        async fn find_by_id(&self, _id: UserId) -> Result<Option<User>, DomainError> {
            Ok(None)
        }
    }
    #[async_trait]
    impl AlbumRepository for Mem {
        async fn insert(&self, _a: &domain::model::Album) -> Result<(), DomainError> {
            Ok(())
        }
        async fn list_by_owner(
            &self,
            _o: UserId,
        ) -> Result<Vec<domain::model::Album>, DomainError> {
            Ok(vec![])
        }
        async fn find_by_id(
            &self,
            _id: domain::AlbumId,
        ) -> Result<Option<domain::model::Album>, DomainError> {
            Ok(None)
        }
    }
    #[async_trait]
    impl FileRepository for Mem {
        async fn insert(&self, file: &FileRecord) -> Result<(), DomainError> {
            self.files.lock().unwrap().push(file.clone());
            Ok(())
        }
        async fn list_by_owner(&self, owner_id: UserId) -> Result<Vec<FileRecord>, DomainError> {
            Ok(self
                .files
                .lock()
                .unwrap()
                .iter()
                .filter(|f| f.owner_id == owner_id && f.deleted_at.is_none())
                .cloned()
                .collect())
        }
        async fn list_trashed_by_owner(&self, owner_id: UserId) -> Result<Vec<FileRecord>, DomainError> {
            Ok(self
                .files
                .lock()
                .unwrap()
                .iter()
                .filter(|f| f.owner_id == owner_id && f.deleted_at.is_some())
                .cloned()
                .collect())
        }
        async fn find_by_id(&self, id: FileId) -> Result<Option<FileRecord>, DomainError> {
            Ok(self.files.lock().unwrap().iter().find(|f| f.id == id).cloned())
        }
        async fn assign_album(
            &self,
            _id: FileId,
            _album_id: Option<domain::AlbumId>,
        ) -> Result<(), DomainError> {
            Ok(())
        }
        async fn set_deleted_at(
            &self,
            _id: FileId,
            _deleted_at: Option<chrono::DateTime<chrono::Utc>>,
        ) -> Result<(), DomainError> {
            Ok(())
        }
        async fn delete(&self, _id: FileId) -> Result<(), DomainError> {
            Ok(())
        }
    }
    #[async_trait]
    impl DeviceRepository for Mem {
        async fn insert(&self, device: &domain::model::Device) -> Result<(), DomainError> {
            self.devices.lock().unwrap().push(device.clone());
            Ok(())
        }
        async fn list_by_user(
            &self,
            _u: UserId,
        ) -> Result<Vec<domain::model::Device>, DomainError> {
            Ok(self.devices.lock().unwrap().clone())
        }
        async fn find_by_id(
            &self,
            id: DeviceId,
        ) -> Result<Option<domain::model::Device>, DomainError> {
            Ok(self
                .devices
                .lock()
                .unwrap()
                .iter()
                .find(|d| d.id == id)
                .cloned())
        }
        async fn touch_sync(&self, id: DeviceId, at: DateTime<Utc>) -> Result<(), DomainError> {
            let mut ds = self.devices.lock().unwrap();
            if let Some(d) = ds.iter_mut().find(|d| d.id == id) {
                d.last_sync_at = Some(at);
            }
            Ok(())
        }
    }
    #[async_trait]
    impl SyncProfileRepository for Mem {
        async fn upsert(&self, profile: &SyncProfile) -> Result<(), DomainError> {
            let mut ps = self.profiles.lock().unwrap();
            ps.retain(|p| p.device_id != profile.device_id);
            ps.push(profile.clone());
            Ok(())
        }
        async fn find_by_device(
            &self,
            device_id: DeviceId,
        ) -> Result<Option<SyncProfile>, DomainError> {
            Ok(self
                .profiles
                .lock()
                .unwrap()
                .iter()
                .find(|p| p.device_id == device_id)
                .cloned())
        }
    }

    #[async_trait]
    impl AppReleaseRepository for Mem {
        async fn insert(&self, _release: &domain::model::AppRelease) -> Result<(), DomainError> {
            Ok(())
        }
        async fn latest(&self) -> Result<Option<domain::model::AppRelease>, DomainError> {
            Ok(None)
        }
        async fn list(&self) -> Result<Vec<domain::model::AppRelease>, DomainError> {
            Ok(vec![])
        }
        async fn find_by_id(
            &self,
            _id: domain::AppReleaseId,
        ) -> Result<Option<domain::model::AppRelease>, DomainError> {
            Ok(None)
        }
        async fn find_by_version_code(
            &self,
            _version_code: i32,
        ) -> Result<Option<domain::model::AppRelease>, DomainError> {
            Ok(None)
        }
        async fn update(&self, _release: &domain::model::AppRelease) -> Result<(), DomainError> {
            Ok(())
        }
        async fn delete(&self, _id: domain::AppReleaseId) -> Result<(), DomainError> {
            Ok(())
        }
    }

    struct NoopApk;
    impl domain::ports::ApkInspector for NoopApk {
        fn inspect(&self, _apk: &[u8]) -> Result<domain::apk::ApkIdentity, DomainError> {
            Err(DomainError::validation("no apk"))
        }
    }

    #[tokio::test]
    async fn manifest_applies_default_household_rules() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let user_id = UserId::new();
        let mem = Arc::new(Mem::default());
        let device = domain::model::Device {
            id: DeviceId::new(),
            user_id,
            name: "Phone A".into(),
            last_sync_at: None,
            created_at: now,
        };
        DeviceRepository::insert(mem.as_ref(), &device).await.unwrap();
        SyncProfileRepository::upsert(mem.as_ref(), &default_profile(device.id))
            .await
            .unwrap();

        let old_photo = FileRecord {
            id: FileId::new(),
            owner_id: user_id,
            album_id: None,
            name: "old.jpg".into(),
            size: 10,
            mime: "image/jpeg".into(),
            checksum: "sha256:x".into(),
            object_key: "a".into(),
            thumbnail_key: None,
            media_kind: MediaKind::Photo,
            created_at: now - chrono::Duration::days(400),
            uploaded_at: now,
            deleted_at: None,
        };
        let new_photo = FileRecord {
            id: FileId::new(),
            owner_id: user_id,
            album_id: None,
            name: "new.jpg".into(),
            size: 10,
            mime: "image/jpeg".into(),
            checksum: "sha256:y".into(),
            object_key: "b".into(),
            thumbnail_key: None,
            media_kind: MediaKind::Photo,
            created_at: now - chrono::Duration::days(3),
            uploaded_at: now,
            deleted_at: None,
        };
        FileRepository::insert(mem.as_ref(), &old_photo).await.unwrap();
        FileRepository::insert(mem.as_ref(), &new_photo).await.unwrap();

        let deps = Arc::new(Deps {
            users: mem.clone(),
            albums: mem.clone(),
            files: mem.clone(),
            devices: mem.clone(),
            profiles: mem.clone(),
            app_releases: mem.clone(),
            objects: Arc::new(NoopObjects),
            hasher: Arc::new(NoopHasher),
            tokens: Arc::new(NoopTokens),
            clock: Arc::new(FakeClock(now)),
            thumbnailer: Arc::new(NoopThumbs),
            apk_inspector: Arc::new(NoopApk),
        });
        let svc = BuildSyncManifestService::new(deps);
        let manifest = svc
            .execute(ManifestQuery {
                user_id,
                device_id: device.id,
                last_sync_at: None,
                have_file_ids: HashSet::new(),
            })
            .await
            .unwrap();
        assert_eq!(manifest.files.len(), 1);
        assert_eq!(manifest.files[0].id, new_photo.id);
    }

    #[tokio::test]
    async fn manifest_still_returns_files_the_device_does_not_have_after_last_sync() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let user_id = UserId::new();
        let mem = Arc::new(Mem::default());
        let device = domain::model::Device {
            id: DeviceId::new(),
            user_id,
            name: "Phone A".into(),
            last_sync_at: Some(now),
            created_at: now,
        };
        DeviceRepository::insert(mem.as_ref(), &device).await.unwrap();
        SyncProfileRepository::upsert(mem.as_ref(), &default_profile(device.id))
            .await
            .unwrap();

        let photo = FileRecord {
            id: FileId::new(),
            owner_id: user_id,
            album_id: None,
            name: "kept.jpg".into(),
            size: 10,
            mime: "image/jpeg".into(),
            checksum: "sha256:y".into(),
            object_key: "b".into(),
            thumbnail_key: None,
            media_kind: MediaKind::Photo,
            created_at: now - chrono::Duration::days(3),
            uploaded_at: now - chrono::Duration::days(3),
        };
        FileRepository::insert(mem.as_ref(), &photo).await.unwrap();

        let deps = Arc::new(Deps {
            users: mem.clone(),
            albums: mem.clone(),
            files: mem.clone(),
            devices: mem.clone(),
            profiles: mem.clone(),
            app_releases: mem.clone(),
            objects: Arc::new(NoopObjects),
            hasher: Arc::new(NoopHasher),
            tokens: Arc::new(NoopTokens),
            clock: Arc::new(FakeClock(now)),
            thumbnailer: Arc::new(NoopThumbs),
            apk_inspector: Arc::new(NoopApk),
        });
        let svc = BuildSyncManifestService::new(deps);
        let manifest = svc
            .execute(ManifestQuery {
                user_id,
                device_id: device.id,
                last_sync_at: Some(now),
                have_file_ids: HashSet::new(),
            })
            .await
            .unwrap();
        assert_eq!(manifest.files.len(), 1);
        assert_eq!(manifest.files[0].id, photo.id);
    }
}
