use serde::{Deserialize, Serialize};

use crate::media::MediaKind;
use crate::model::FileRecord;

pub const TRASH_RETENTION_DAYS: i64 = 30;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum LibrarySilo {
    Music,
    Photos,
    Files,
}

impl LibrarySilo {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Music => "music",
            Self::Photos => "photos",
            Self::Files => "files",
        }
    }

    pub fn parse(value: &str) -> Option<Self> {
        match value {
            "music" => Some(Self::Music),
            "photos" | "photos-videos" | "media" => Some(Self::Photos),
            "files" | "other" => Some(Self::Files),
            _ => None,
        }
    }

    pub fn contains(self, kind: MediaKind) -> bool {
        match self {
            Self::Music => kind == MediaKind::Audio,
            Self::Photos => matches!(kind, MediaKind::Photo | MediaKind::Video),
            Self::Files => kind == MediaKind::Other,
        }
    }

    pub fn from_kind(kind: MediaKind) -> Self {
        match kind {
            MediaKind::Audio => Self::Music,
            MediaKind::Photo | MediaKind::Video => Self::Photos,
            MediaKind::Other => Self::Files,
        }
    }
}

pub fn files_in_silo(files: &[FileRecord], silo: LibrarySilo) -> Vec<FileRecord> {
    files
        .iter()
        .filter(|file| silo.contains(file.media_kind))
        .cloned()
        .collect()
}

pub fn partition_expired_trash(
    files: Vec<FileRecord>,
    now: chrono::DateTime<chrono::Utc>,
    silo: Option<LibrarySilo>,
) -> (Vec<FileRecord>, Vec<FileRecord>) {
    let mut expired = Vec::new();
    let mut keep = Vec::new();
    for file in files {
        if file.trash_expired(now) {
            expired.push(file);
        } else if silo.is_none_or(|silo| silo.contains(file.media_kind)) {
            keep.push(file);
        }
    }
    (keep, expired)
}

impl FileRecord {
    pub fn is_trashed(&self) -> bool {
        self.deleted_at.is_some()
    }

    pub fn purge_at(&self) -> Option<chrono::DateTime<chrono::Utc>> {
        self.deleted_at
            .map(|deleted_at| deleted_at + chrono::Duration::days(TRASH_RETENTION_DAYS))
    }

    pub fn trash_expired(&self, now: chrono::DateTime<chrono::Utc>) -> bool {
        self.purge_at().is_some_and(|purge_at| now >= purge_at)
    }
}

#[cfg(test)]
mod tests {
    use chrono::{TimeZone, Utc};
    use uuid::Uuid;

    use super::*;
    use crate::ids::{FileId, UserId};
    use crate::media::MediaKind;

    fn file(kind: MediaKind) -> FileRecord {
        FileRecord {
            id: FileId::from_uuid(Uuid::nil()),
            owner_id: UserId::from_uuid(Uuid::nil()),
            album_id: None,
            name: "f".into(),
            size: 1,
            mime: "application/octet-stream".into(),
            checksum: "sha256:x".into(),
            object_key: "k".into(),
            thumbnail_key: None,
            media_kind: kind,
            created_at: Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap(),
            uploaded_at: Utc.with_ymd_and_hms(2026, 9, 15, 12, 0, 0).unwrap(),
            deleted_at: None,
        }
    }

    #[test]
    fn silos_group_music_photos_and_generic_files() {
        assert!(LibrarySilo::Music.contains(MediaKind::Audio));
        assert!(!LibrarySilo::Music.contains(MediaKind::Photo));
        assert!(LibrarySilo::Photos.contains(MediaKind::Photo));
        assert!(LibrarySilo::Photos.contains(MediaKind::Video));
        assert!(!LibrarySilo::Photos.contains(MediaKind::Other));
        assert!(LibrarySilo::Files.contains(MediaKind::Other));
        assert_eq!(LibrarySilo::from_kind(MediaKind::Video), LibrarySilo::Photos);
        assert_eq!(LibrarySilo::parse("photos-videos"), Some(LibrarySilo::Photos));
        assert_eq!(LibrarySilo::Music.as_str(), "music");
    }

    #[test]
    fn trash_expires_after_retention() {
        let mut record = file(MediaKind::Audio);
        let deleted = Utc.with_ymd_and_hms(2026, 9, 1, 0, 0, 0).unwrap();
        record.deleted_at = Some(deleted);
        assert!(!record.trash_expired(deleted + chrono::Duration::days(29)));
        assert!(record.trash_expired(deleted + chrono::Duration::days(30)));
    }

    #[test]
    fn partition_keeps_silo_and_splits_expired() {
        let now = Utc.with_ymd_and_hms(2026, 9, 18, 0, 0, 0).unwrap();
        let mut music = file(MediaKind::Audio);
        music.deleted_at = Some(now - chrono::Duration::days(2));
        let mut expired = file(MediaKind::Photo);
        expired.deleted_at = Some(now - chrono::Duration::days(40));
        let (keep, gone) = partition_expired_trash(
            vec![music.clone(), expired.clone()],
            now,
            Some(LibrarySilo::Music),
        );
        assert_eq!(keep.iter().map(|f| f.media_kind).collect::<Vec<_>>(), vec![MediaKind::Audio]);
        assert_eq!(gone.len(), 1);
        assert_eq!(gone[0].media_kind, MediaKind::Photo);
    }
}
