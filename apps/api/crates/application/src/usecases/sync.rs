use std::collections::HashSet;
use std::sync::Arc;

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use domain::model::SyncManifest;
use domain::sync::evaluate_manifest;
use domain::{DeviceId, FileId, UserId};

use super::devices::owned_device;
use crate::access::list_accessible_files;
use crate::mobile::ensure_mobile_audio_best_effort;
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
        let accessible = list_accessible_files(&self.deps, query.user_id).await?;
        let mut files = Vec::new();
        for item in accessible {
            files.push(ensure_mobile_audio_best_effort(&self.deps, item.file).await);
        }
        let excluded: HashSet<FileId> = self
            .deps
            .exclusions
            .list(device.id)
            .await?
            .into_iter()
            .collect();
        let mut manifest = evaluate_manifest(
            &files,
            &profile.rules,
            &query.have_file_ids,
            &excluded,
            now,
        );
        let albums = crate::access::list_accessible_albums(&self.deps, query.user_id).await?;
        manifest.albums = albums.into_iter().map(|item| item.album).collect();
        self.deps.devices.touch_sync(device.id, now).await?;
        Ok(manifest)
    }
}

#[cfg(test)]
mod tests {
    use chrono::TimeZone;
    use domain::model::FileRecord;
    use domain::ports::*;
    use domain::{FileId, MediaKind, UserId};

    use super::*;
    use crate::test_support::{deps_from, TestMem};
    use crate::usecases::devices::default_profile;

    fn photo(owner: UserId, created_at: DateTime<Utc>, name: &str) -> FileRecord {
        FileRecord {
            id: FileId::new(),
            owner_id: owner,
            album_id: None,
            name: name.into(),
            size: 10,
            mime: "image/jpeg".into(),
            checksum: "sha256:y".into(),
            object_key: name.into(),
            thumbnail_key: None,
            media_kind: MediaKind::Photo,
            created_at,
            uploaded_at: created_at,
            deleted_at: None,
            mobile_object_key: None,
            mobile_checksum: None,
            mobile_size: None,
            mobile_mime: None,
        }
    }

    #[tokio::test]
    async fn manifest_applies_default_household_rules() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let mem = TestMem::new();
        let user_id = mem.add_user("owner").await;
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

        let old_photo = photo(user_id, now - chrono::Duration::days(400), "old.jpg");
        let new_photo = photo(user_id, now - chrono::Duration::days(3), "new.jpg");
        mem.insert_file(old_photo).await;
        mem.insert_file(new_photo.clone()).await;

        let deps = deps_from(mem);
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
    async fn manifest_omits_device_exclusions() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let mem = TestMem::new();
        let user_id = mem.add_user("owner").await;
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
        let keep = photo(user_id, now - chrono::Duration::days(3), "keep.jpg");
        let skip = photo(user_id, now - chrono::Duration::days(3), "skip.jpg");
        mem.insert_file(keep.clone()).await;
        mem.insert_file(skip.clone()).await;
        DeviceExclusionRepository::merge(mem.as_ref(), device.id, &[skip.id])
            .await
            .unwrap();

        let deps = deps_from(mem);
        let manifest = BuildSyncManifestService::new(deps)
            .execute(ManifestQuery {
                user_id,
                device_id: device.id,
                last_sync_at: None,
                have_file_ids: HashSet::new(),
            })
            .await
            .unwrap();
        assert_eq!(manifest.files.len(), 1);
        assert_eq!(manifest.files[0].id, keep.id);
    }

    #[tokio::test]
    async fn manifest_still_returns_files_the_device_does_not_have_after_last_sync() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let mem = TestMem::new();
        let user_id = mem.add_user("owner").await;
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
        let photo = photo(user_id, now - chrono::Duration::days(3), "kept.jpg");
        mem.insert_file(photo.clone()).await;

        let deps = deps_from(mem);
        let manifest = BuildSyncManifestService::new(deps)
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
