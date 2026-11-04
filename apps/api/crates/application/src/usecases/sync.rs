use std::collections::HashSet;
use std::sync::Arc;

use async_trait::async_trait;
use chrono::{DateTime, Utc};
use domain::model::SyncManifest;
use domain::sync::evaluate_manifest;
use domain::{DeviceId, FileId, UserId};

use super::devices::owned_device;
use crate::access::list_accessible_files;
use crate::mobile::{request_mobile_audio, MobileAudio};
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
            // The manifest never transcodes. A lossless file whose AAC copy is still being made waits
            // for the next sync, so no phone downloads the original just to replace it later.
            // One the phone already has is kept in the list so it is not mistaken for a deletion.
            let pending = matches!(
                request_mobile_audio(&self.deps, &item.file).await,
                Ok(MobileAudio::Pending)
            );
            if pending && !query.have_file_ids.contains(&item.file.id) {
                continue;
            }
            files.push(item.file);
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
    async fn default_profile_mirrors_the_whole_library() {
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

        let old_photo = photo(user_id, now - chrono::Duration::days(4000), "old.jpg");
        let new_photo = photo(user_id, now - chrono::Duration::days(3), "new.jpg");
        let mut big_video = photo(user_id, now - chrono::Duration::days(900), "film.mp4");
        big_video.media_kind = MediaKind::Video;
        big_video.mime = "video/mp4".into();
        big_video.size = 4 * 1024 * 1024 * 1024;
        let mut document = photo(user_id, now - chrono::Duration::days(2000), "tax.pdf");
        document.media_kind = MediaKind::Other;
        document.mime = "application/pdf".into();
        let expected: HashSet<FileId> = [&old_photo, &new_photo, &big_video, &document]
            .iter()
            .map(|file| file.id)
            .collect();
        for file in [old_photo, new_photo, big_video, document] {
            mem.insert_file(file).await;
        }

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
        let ids: HashSet<FileId> = manifest.files.iter().map(|entry| entry.id).collect();
        assert_eq!(ids, expected);
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

    #[tokio::test]
    async fn manifest_tombstones_files_trashed_in_the_backoffice() {
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
        let kept = photo(user_id, now - chrono::Duration::days(3), "kept.jpg");
        let gone = photo(user_id, now - chrono::Duration::days(3), "gone.jpg");
        mem.insert_file(kept.clone()).await;
        mem.insert_file(gone.clone()).await;
        FileRepository::set_deleted_at(mem.as_ref(), gone.id, Some(now))
            .await
            .unwrap();

        let mut have = HashSet::new();
        have.insert(kept.id);
        have.insert(gone.id);
        let deps = deps_from(mem);
        let manifest = BuildSyncManifestService::new(deps)
            .execute(ManifestQuery {
                user_id,
                device_id: device.id,
                last_sync_at: None,
                have_file_ids: have,
            })
            .await
            .unwrap();
        assert_eq!(manifest.files.len(), 1);
        assert_eq!(manifest.files[0].id, kept.id);
        assert_eq!(manifest.removed, vec![gone.id]);
    }
}
