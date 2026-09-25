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
    excluded_file_ids: &HashSet<FileId>,
    now: DateTime<Utc>,
) -> SyncManifest {
    let selected: Vec<ManifestEntry> = files
        .iter()
        .filter(|file| !file.is_trashed())
        .filter(|file| !excluded_file_ids.contains(&file.id))
        .filter(|file| rules.iter().any(|rule| rule.matches(file, now)))
        .map(ManifestEntry::from_file)
        .collect();
    let kept: HashSet<FileId> = selected.iter().map(|entry| entry.id).collect();
    let removed = tombstones(files, have_file_ids, excluded_file_ids, &kept);

    SyncManifest {
        generated_at: now,
        files: selected,
        albums: Vec::new(),
        removed,
    }
}

/// Ids the device already has that must disappear locally.
///
/// Live files that simply fall outside the sync rules stay on the device.
/// Trash, hard deletes (absent from `files`), and per-device exclusions do not.
pub fn tombstones(
    files: &[FileRecord],
    have_file_ids: &HashSet<FileId>,
    excluded_file_ids: &HashSet<FileId>,
    kept_ids: &HashSet<FileId>,
) -> Vec<FileId> {
    let mut removed: Vec<FileId> = have_file_ids
        .iter()
        .copied()
        .filter(|id| !kept_ids.contains(id))
        .filter(|id| {
            if excluded_file_ids.contains(id) {
                return true;
            }
            match files.iter().find(|file| file.id == *id) {
                Some(file) if file.is_trashed() => true,
                None => true,
                Some(_) => false,
            }
        })
        .collect();
    removed.sort_by_key(|id| id.0);
    removed
}

/// Public URL advertised in mDNS TXT when it is a real hostname.
/// Loopback defaults are omitted so clients keep the resolved LAN address.
pub fn public_base_url_for_discovery(public_url: &str) -> Option<String> {
    let trimmed = public_url.trim().trim_end_matches('/');
    if trimmed.is_empty() {
        return None;
    }
    let host = url_host(trimmed)?;
    if is_loopback_host(host) {
        None
    } else {
        Some(trimmed.to_string())
    }
}

fn url_host(url: &str) -> Option<&str> {
    let rest = url.split_once("://")?.1;
    let hostport = rest.split(['/', '?', '#']).next().unwrap_or(rest);
    if let Some(stripped) = hostport.strip_prefix('[') {
        return stripped.split(']').next().filter(|host| !host.is_empty());
    }
    let host = hostport.split(':').next().unwrap_or(hostport);
    if host.is_empty() { None } else { Some(host) }
}

fn is_loopback_host(host: &str) -> bool {
    let host = host.trim().trim_matches(['[', ']']);
    host.eq_ignore_ascii_case("localhost")
        || host == "127.0.0.1"
        || host == "::1"
        || host == "0.0.0.0"
}

#[cfg(test)]
mod tests {
    use chrono::TimeZone;
    use uuid::Uuid;

    use super::*;
    use crate::ids::{AlbumId, FileId, UserId};
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
            deleted_at: None,
            mobile_object_key: None,
            mobile_checksum: None,
            mobile_size: None,
            mobile_mime: None,
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
        let manifest = evaluate_manifest(&files, &rules, &HashSet::new(), &HashSet::new(), now);
        let ids: HashSet<_> = manifest.files.iter().map(|f| f.id).collect();
        assert!(ids.contains(&recent_photo.id));
        assert!(ids.contains(&small_video.id));
        assert!(ids.contains(&music.id));
        assert_eq!(manifest.files.len(), 3);
    }

    #[test]
    fn includes_files_the_device_already_has_so_album_metadata_can_sync() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let mut photo = file(MediaKind::Photo, 1, 10);
        photo.album_id = Some(AlbumId::from_uuid(Uuid::new_v4()));
        let rules = vec![SyncRule {
            media_kind: MediaKind::Photo,
            max_age_days: None,
            max_size_bytes: None,
            include_all: true,
        }];
        let mut have = HashSet::new();
        have.insert(photo.id);
        let manifest = evaluate_manifest(&[photo.clone()], &rules, &have, &HashSet::new(), now);
        assert_eq!(manifest.files.len(), 1);
        assert_eq!(manifest.files[0].album_id, photo.album_id);
        assert_eq!(manifest.files[0].name, photo.name);
    }

    #[test]
    fn copies_media_kind_and_album_onto_entries() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let mut photo = file(MediaKind::Photo, 1, 10);
        photo.album_id = Some(AlbumId::from_uuid(Uuid::new_v4()));
        let rules = vec![SyncRule {
            media_kind: MediaKind::Photo,
            max_age_days: None,
            max_size_bytes: None,
            include_all: true,
        }];
        let manifest = evaluate_manifest(&[photo.clone()], &rules, &HashSet::new(), &HashSet::new(), now);
        assert_eq!(manifest.files[0].media_kind, MediaKind::Photo);
        assert_eq!(manifest.files[0].album_id, photo.album_id);
        assert!(manifest.albums.is_empty());
        assert!(!manifest.files[0].mobile);
    }

    #[test]
    fn skips_trashed_files() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let mut photo = file(MediaKind::Photo, 1, 10);
        photo.deleted_at = Some(now);
        let rules = vec![SyncRule {
            media_kind: MediaKind::Photo,
            max_age_days: None,
            max_size_bytes: None,
            include_all: true,
        }];
        let manifest = evaluate_manifest(&[photo.clone()], &rules, &HashSet::new(), &HashSet::new(), now);
        assert!(manifest.files.is_empty());
        assert!(manifest.removed.is_empty());

        let mut have = HashSet::new();
        have.insert(photo.id);
        let manifest = evaluate_manifest(&[photo.clone()], &rules, &have, &HashSet::new(), now);
        assert!(manifest.files.is_empty());
        assert_eq!(manifest.removed, vec![photo.id]);
    }

    #[test]
    fn tombstones_cover_trash_purge_and_exclusion_but_not_rule_misses() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let rules = vec![SyncRule {
            media_kind: MediaKind::Photo,
            max_age_days: Some(365),
            max_size_bytes: None,
            include_all: false,
        }];
        let live = file(MediaKind::Photo, 10, 100);
        let mut trashed = file(MediaKind::Photo, 1, 100);
        trashed.deleted_at = Some(now);
        let old = file(MediaKind::Photo, 400, 100);
        let purged = FileId::from_uuid(Uuid::new_v4());
        let mut have = HashSet::new();
        have.insert(live.id);
        have.insert(trashed.id);
        have.insert(old.id);
        have.insert(purged);
        let manifest = evaluate_manifest(
            &[live.clone(), trashed.clone(), old.clone()],
            &rules,
            &have,
            &HashSet::new(),
            now,
        );
        assert_eq!(manifest.files.len(), 1);
        assert_eq!(manifest.files[0].id, live.id);
        let removed: HashSet<_> = manifest.removed.iter().copied().collect();
        assert!(removed.contains(&trashed.id));
        assert!(removed.contains(&purged));
        assert!(!removed.contains(&old.id));
        assert!(!removed.contains(&live.id));

        let mut excluded = HashSet::new();
        excluded.insert(live.id);
        let manifest = evaluate_manifest(&[live.clone()], &rules, &have, &excluded, now);
        assert!(manifest.files.is_empty());
        assert!(manifest.removed.contains(&live.id));
    }

    #[test]
    fn discovery_url_skips_loopback_and_keeps_hostnames() {
        assert_eq!(public_base_url_for_discovery("http://localhost:8080"), None);
        assert_eq!(public_base_url_for_discovery("http://127.0.0.1:8080/"), None);
        assert_eq!(public_base_url_for_discovery("http://[::1]:8080"), None);
        assert_eq!(
            public_base_url_for_discovery("https://api.mydrive.lan/"),
            Some("https://api.mydrive.lan".into())
        );
        assert_eq!(
            public_base_url_for_discovery("http://api.localhost"),
            Some("http://api.localhost".into())
        );
    }

    #[test]
    fn skips_device_excluded_files() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let keep = file(MediaKind::Photo, 1, 10);
        let skip = file(MediaKind::Photo, 1, 10);
        let rules = vec![SyncRule {
            media_kind: MediaKind::Photo,
            max_age_days: None,
            max_size_bytes: None,
            include_all: true,
        }];
        let mut excluded = HashSet::new();
        excluded.insert(skip.id);
        let manifest = evaluate_manifest(&[keep.clone(), skip], &rules, &HashSet::new(), &excluded, now);
        assert_eq!(manifest.files.len(), 1);
        assert_eq!(manifest.files[0].id, keep.id);
    }

    #[test]
    fn audio_entries_use_mobile_variant_when_present() {
        let now = Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap();
        let mut flac = file(MediaKind::Audio, 1, 80 * 1024 * 1024);
        flac.mime = "audio/flac".into();
        flac.checksum = "sha256:flac".into();
        flac.mobile_object_key = Some("music/id/mobile.m4a".into());
        flac.mobile_checksum = Some("sha256:aac".into());
        flac.mobile_size = Some(12_000_000);
        flac.mobile_mime = Some("audio/mp4".into());
        let mp3 = {
            let mut audio = file(MediaKind::Audio, 1, 4_000_000);
            audio.mime = "audio/mpeg".into();
            audio.checksum = "sha256:mp3".into();
            audio
        };
        let rules = vec![SyncRule {
            media_kind: MediaKind::Audio,
            max_age_days: None,
            max_size_bytes: None,
            include_all: true,
        }];
        let manifest = evaluate_manifest(
            &[flac.clone(), mp3.clone()],
            &rules,
            &HashSet::new(),
            &HashSet::new(),
            now,
        );
        let flac_entry = manifest.files.iter().find(|e| e.id == flac.id).unwrap();
        assert!(flac_entry.mobile);
        assert_eq!(flac_entry.size, 12_000_000);
        assert_eq!(flac_entry.mime, "audio/mp4");
        assert_eq!(flac_entry.checksum, "sha256:aac");
        let mp3_entry = manifest.files.iter().find(|e| e.id == mp3.id).unwrap();
        assert!(!mp3_entry.mobile);
        assert_eq!(mp3_entry.size, mp3.size);
        assert_eq!(mp3_entry.mime, "audio/mpeg");
    }
}
