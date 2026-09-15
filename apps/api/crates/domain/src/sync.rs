use chrono::{DateTime, Duration, Utc};
use std::collections::HashSet;

use crate::ids::FileId;
use crate::model::{FileRecord, ManifestEntry, SyncManifest, SyncRule};

impl SyncRule {
    pub fn matches(&self, file: &FileRecord, now: DateTime<Utc>) -> bool {
        if file.media_kind != self.media_kind {
            return false;
        }
        if self.include_all {
            return true;
        }
        if let Some(max_age_days) = self.max_age_days {
            let age = now.signed_duration_since(file.created_at);
            if age > Duration::days(i64::from(max_age_days)) {
                return false;
            }
        }
        if let Some(max_size) = self.max_size_bytes
            && file.size > max_size
        {
            return false;
        }
        true
    }
}

pub fn evaluate_manifest(
    files: &[FileRecord],
    rules: &[SyncRule],
    have_file_ids: &HashSet<FileId>,
    now: DateTime<Utc>,
) -> SyncManifest {
    let selected = files
        .iter()
        .filter(|file| !have_file_ids.contains(&file.id))
        .filter(|file| rules.iter().any(|rule| rule.matches(file, now)))
        .map(|file| ManifestEntry {
            id: file.id,
            name: file.name.clone(),
            size: file.size,
            mime: file.mime.clone(),
            checksum: file.checksum.clone(),
        })
        .collect();

    SyncManifest {
        generated_at: now,
        files: selected,
    }
}

#[cfg(test)]
mod tests {
    use chrono::TimeZone;
    use uuid::Uuid;

    use super::*;
    use crate::ids::{FileId, UserId};
    use crate::media::MediaKind;

    fn file(kind: MediaKind, days_ago: i64, size: u64) -> FileRecord {
        let created = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap()
            - Duration::days(days_ago);
        FileRecord {
            id: FileId::from_uuid(Uuid::new_v4()),
            owner_id: UserId::from_uuid(Uuid::new_v4()),
            album_id: None,
            name: "f".into(),
            size,
            mime: "application/octet-stream".into(),
            checksum: "sha256:abc".into(),
            object_key: "k".into(),
            thumbnail_key: None,
            media_kind: kind,
            created_at: created,
            uploaded_at: created,
        }
    }

    #[test]
    fn photos_last_year_videos_under_10mb_music_all() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let rules = vec![
            SyncRule {
                media_kind: MediaKind::Photo,
                max_age_days: Some(365),
                max_size_bytes: None,
                include_all: false,
            },
            SyncRule {
                media_kind: MediaKind::Video,
                max_age_days: None,
                max_size_bytes: Some(10 * 1024 * 1024),
                include_all: false,
            },
            SyncRule {
                media_kind: MediaKind::Audio,
                max_age_days: None,
                max_size_bytes: None,
                include_all: true,
            },
        ];

        let recent_photo = file(MediaKind::Photo, 10, 100);
        let old_photo = file(MediaKind::Photo, 400, 100);
        let small_video = file(MediaKind::Video, 1, 1024);
        let large_video = file(MediaKind::Video, 1, 20 * 1024 * 1024);
        let music = file(MediaKind::Audio, 2000, 80 * 1024 * 1024);

        let files = vec![
            recent_photo.clone(),
            old_photo,
            small_video.clone(),
            large_video,
            music.clone(),
        ];
        let manifest = evaluate_manifest(&files, &rules, &HashSet::new(), now);
        let ids: HashSet<_> = manifest.files.iter().map(|f| f.id).collect();
        assert!(ids.contains(&recent_photo.id));
        assert!(ids.contains(&small_video.id));
        assert!(ids.contains(&music.id));
        assert_eq!(manifest.files.len(), 3);
    }

    #[test]
    fn skips_files_the_device_already_has() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let photo = file(MediaKind::Photo, 1, 10);
        let rules = vec![SyncRule {
            media_kind: MediaKind::Photo,
            max_age_days: None,
            max_size_bytes: None,
            include_all: true,
        }];
        let mut have = HashSet::new();
        have.insert(photo.id);
        let manifest = evaluate_manifest(&[photo], &rules, &have, now);
        assert!(manifest.files.is_empty());
    }
}
