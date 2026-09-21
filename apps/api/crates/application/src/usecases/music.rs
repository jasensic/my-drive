use std::sync::Arc;

use async_trait::async_trait;
use domain::model::FileRecord;
use domain::music::MusicSearch;
use domain::ports::MusicDownloader;
use domain::UserId;

use super::files::{UploadCommand, UploadFile};
use crate::AppError;

const MAX_KEYWORD: usize = 200;

#[async_trait]
pub trait SearchMusic: Send + Sync {
    async fn execute(&self, keyword: &str) -> Result<MusicSearch, AppError>;
}

#[async_trait]
pub trait ImportMusic: Send + Sync {
    async fn execute(
        &self,
        owner_id: UserId,
        search_id: &str,
        track_id: &str,
    ) -> Result<FileRecord, AppError>;
}

pub struct SearchMusicService {
    music: Arc<dyn MusicDownloader>,
}

impl SearchMusicService {
    pub fn new(music: Arc<dyn MusicDownloader>) -> Self {
        Self { music }
    }
}

#[async_trait]
impl SearchMusic for SearchMusicService {
    async fn execute(&self, keyword: &str) -> Result<MusicSearch, AppError> {
        let keyword = keyword.trim();
        if keyword.is_empty() {
            return Err(AppError::validation("keyword is required"));
        }
        if keyword.chars().count() > MAX_KEYWORD {
            return Err(AppError::validation(format!(
                "keyword must be at most {MAX_KEYWORD} characters"
            )));
        }
        Ok(self.music.search(keyword).await?)
    }
}

pub struct ImportMusicService {
    music: Arc<dyn MusicDownloader>,
    upload: Arc<dyn UploadFile>,
}

impl ImportMusicService {
    pub fn new(music: Arc<dyn MusicDownloader>, upload: Arc<dyn UploadFile>) -> Self {
        Self { music, upload }
    }
}

#[async_trait]
impl ImportMusic for ImportMusicService {
    async fn execute(
        &self,
        owner_id: UserId,
        search_id: &str,
        track_id: &str,
    ) -> Result<FileRecord, AppError> {
        let search_id = search_id.trim();
        let track_id = track_id.trim();
        if search_id.is_empty() || track_id.is_empty() {
            return Err(AppError::validation("search_id and track_id are required"));
        }
        let audio = self.music.download(search_id, track_id).await?;
        if audio.bytes.is_empty() {
            return Err(AppError::validation("downloaded file is empty"));
        }
        if !audio.mime.to_ascii_lowercase().starts_with("audio/") {
            return Err(AppError::validation("downloaded file is not audio"));
        }
        if audio.name.trim().is_empty() {
            return Err(AppError::validation("downloaded file name is empty"));
        }
        self.upload
            .execute(UploadCommand {
                owner_id,
                album_id: None,
                name: audio.name,
                mime: audio.mime,
                bytes: audio.bytes,
                created_at: None,
            })
            .await
    }
}

#[cfg(test)]
mod tests {
    use std::sync::Mutex;

    use async_trait::async_trait;
    use bytes::Bytes;
    use chrono::Utc;
    use domain::model::FileRecord;
    use domain::music::{DownloadedAudio, MusicSearch, MusicTrack};
    use domain::ports::MusicDownloader;
    use domain::{DomainError, FileId, MediaKind, UserId};

    use super::*;
    use crate::usecases::files::{UploadCommand, UploadFile};

    struct FakeMusic {
        bytes: Bytes,
    }

    #[async_trait]
    impl MusicDownloader for FakeMusic {
        async fn search(&self, keyword: &str) -> Result<MusicSearch, DomainError> {
            Ok(MusicSearch {
                search_id: "s1".into(),
                tracks: vec![MusicTrack {
                    id: "0".into(),
                    source: "NeteaseMusicClient".into(),
                    song_name: keyword.into(),
                    singers: "Artist".into(),
                    album: "Album".into(),
                    duration: "1:00".into(),
                    file_size: "1MB".into(),
                    ext: "mp3".into(),
                    cover_url: String::new(),
                }],
            })
        }

        async fn download(
            &self,
            _search_id: &str,
            _track_id: &str,
        ) -> Result<DownloadedAudio, DomainError> {
            Ok(DownloadedAudio {
                name: "Artist - Song.mp3".into(),
                mime: "audio/mpeg".into(),
                bytes: self.bytes.clone(),
            })
        }
    }

    struct RecordingUpload {
        last: Mutex<Option<UploadCommand>>,
    }

    #[async_trait]
    impl UploadFile for RecordingUpload {
        async fn execute(&self, cmd: UploadCommand) -> Result<FileRecord, crate::AppError> {
            let size = cmd.bytes.len() as u64;
            let name = cmd.name.clone();
            let mime = cmd.mime.clone();
            let owner_id = cmd.owner_id;
            *self.last.lock().unwrap() = Some(cmd);
            let now = Utc::now();
            Ok(FileRecord {
                id: FileId::new(),
                owner_id,
                album_id: None,
                name,
                size,
                mime,
                checksum: "sha256:test".into(),
                object_key: "music/test".into(),
                thumbnail_key: None,
                media_kind: MediaKind::Audio,
                created_at: now,
                uploaded_at: now,
                deleted_at: None,
            })
        }
    }

    #[tokio::test]
    async fn search_rejects_a_blank_keyword() {
        let svc = SearchMusicService::new(Arc::new(FakeMusic {
            bytes: Bytes::from_static(b"mp3"),
        }));
        let err = svc.execute("   ").await.unwrap_err();
        assert!(err.to_string().contains("keyword"));
    }

    #[tokio::test]
    async fn import_uploads_the_audio_bytes() {
        let upload = Arc::new(RecordingUpload {
            last: Mutex::new(None),
        });
        let svc = ImportMusicService::new(
            Arc::new(FakeMusic {
                bytes: Bytes::from_static(b"ID3"),
            }),
            upload.clone(),
        );
        let owner = UserId::new();
        let file = svc.execute(owner, "s1", "0").await.unwrap();
        assert_eq!(file.name, "Artist - Song.mp3");
        assert_eq!(file.size, 3);
        assert_eq!(file.mime, "audio/mpeg");
        let stored = upload.last.lock().unwrap().take().unwrap();
        assert_eq!(stored.owner_id, owner);
        assert_eq!(&stored.bytes[..], b"ID3");
    }
}
