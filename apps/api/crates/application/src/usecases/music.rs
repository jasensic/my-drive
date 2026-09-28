use std::sync::Arc;

use async_trait::async_trait;
use domain::model::FileRecord;
use domain::music::MusicSearch;
use domain::ports::MusicDownloader;
use domain::UserId;

use super::files::{UploadCommand, UploadFile};
use crate::AppError;

const MAX_KEYWORD: usize = 200;
const MAX_PLAYLIST_URL: usize = 500;

#[async_trait]
pub trait SearchMusic: Send + Sync {
    async fn execute(&self, keyword: &str) -> Result<MusicSearch, AppError>;
}

#[async_trait]
pub trait PollMusicSearch: Send + Sync {
    async fn execute(&self, search_id: &str) -> Result<MusicSearch, AppError>;
}

#[async_trait]
pub trait ParseSpotifyPlaylist: Send + Sync {
    async fn execute(&self, url: &str) -> Result<MusicSearch, AppError>;
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

pub struct PollMusicSearchService {
    music: Arc<dyn MusicDownloader>,
}

impl PollMusicSearchService {
    pub fn new(music: Arc<dyn MusicDownloader>) -> Self {
        Self { music }
    }
}

#[async_trait]
impl PollMusicSearch for PollMusicSearchService {
    async fn execute(&self, search_id: &str) -> Result<MusicSearch, AppError> {
        let search_id = search_id.trim();
        if search_id.is_empty() {
            return Err(AppError::validation("search_id is required"));
        }
        Ok(self.music.search_snapshot(search_id).await?)
    }
}

pub struct ParseSpotifyPlaylistService {
    music: Arc<dyn MusicDownloader>,
}

impl ParseSpotifyPlaylistService {
    pub fn new(music: Arc<dyn MusicDownloader>) -> Self {
        Self { music }
    }
}

#[async_trait]
impl ParseSpotifyPlaylist for ParseSpotifyPlaylistService {
    async fn execute(&self, url: &str) -> Result<MusicSearch, AppError> {
        let url = normalize_spotify_playlist_url(url)?;
        Ok(self.music.parse_playlist(&url).await?)
    }
}

fn normalize_spotify_playlist_url(raw: &str) -> Result<String, AppError> {
    let raw = raw.trim();
    if raw.is_empty() {
        return Err(AppError::validation("A Spotify playlist link is required"));
    }
    if raw.chars().count() > MAX_PLAYLIST_URL {
        return Err(AppError::validation(format!(
            "playlist url must be at most {MAX_PLAYLIST_URL} characters"
        )));
    }
    if let Some(id) = raw.strip_prefix("spotify:playlist:") {
        let id = id.split(['?', '&']).next().unwrap_or("").trim();
        require_playlist_id(id)?;
        return Ok(format!("https://open.spotify.com/playlist/{id}"));
    }
    let rest = raw
        .strip_prefix("https://")
        .or_else(|| raw.strip_prefix("http://"))
        .ok_or_else(|| AppError::validation("A Spotify playlist link is required"))?;
    let without_fragment = rest.split('#').next().unwrap_or(rest);
    let (host_and_path, query) = without_fragment
        .split_once('?')
        .unwrap_or((without_fragment, ""));
    let (host_raw, path) = host_and_path.split_once('/').unwrap_or((host_and_path, ""));
    let host_raw = host_raw.rsplit('@').next().unwrap_or(host_raw);
    let host = host_raw
        .split(':')
        .next()
        .unwrap_or(host_raw)
        .to_ascii_lowercase();
    let host = host.strip_prefix("www.").unwrap_or(&host);
    match host {
        "spotify.link" => {
            if path.is_empty() {
                return Err(AppError::validation("A Spotify playlist link is required"));
            }
            let query = if query.is_empty() {
                String::new()
            } else {
                format!("?{query}")
            };
            Ok(format!("https://{host_raw}/{path}{query}"))
        }
        "open.spotify.com" | "play.spotify.com" | "spotify.com" => {
            let id = path
                .split('/')
                .skip_while(|part| *part != "playlist")
                .nth(1)
                .unwrap_or("");
            require_playlist_id(id)?;
            Ok(format!("https://open.spotify.com/playlist/{id}"))
        }
        _ => Err(AppError::validation("A Spotify playlist link is required")),
    }
}

fn require_playlist_id(id: &str) -> Result<(), AppError> {
    if (10..=32).contains(&id.len()) && id.chars().all(|c| c.is_ascii_alphanumeric()) {
        Ok(())
    } else {
        Err(AppError::validation("A Spotify playlist link is required"))
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
                done: true,
                error: None,
            })
        }

        async fn search_snapshot(&self, search_id: &str) -> Result<MusicSearch, DomainError> {
            self.search(search_id).await
        }

        async fn parse_playlist(&self, url: &str) -> Result<MusicSearch, DomainError> {
            Ok(MusicSearch {
                search_id: "playlist".into(),
                tracks: vec![MusicTrack {
                    id: "0".into(),
                    source: "SpotifyMusicClient".into(),
                    song_name: url.into(),
                    singers: "Artist".into(),
                    album: "Album".into(),
                    duration: "1:00".into(),
                    file_size: "3MB".into(),
                    ext: "mp3".into(),
                    cover_url: String::new(),
                }],
                done: false,
                error: None,
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
                mobile_object_key: None,
                mobile_checksum: None,
                mobile_size: None,
                mobile_mime: None,
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
    async fn poll_rejects_a_blank_search_id() {
        let svc = PollMusicSearchService::new(Arc::new(FakeMusic {
            bytes: Bytes::from_static(b"mp3"),
        }));
        let err = svc.execute("  ").await.unwrap_err();
        assert!(err.to_string().contains("search_id"));
    }

    #[test]
    fn playlist_url_accepts_spotify_playlist_links() {
        let canonical = "https://open.spotify.com/playlist/37i9dQZF1E8NWHOpySOxQd";
        assert_eq!(
            normalize_spotify_playlist_url(canonical).unwrap(),
            canonical
        );
        assert_eq!(
            normalize_spotify_playlist_url(
                "https://open.spotify.com/intl-es/playlist/37i9dQZF1E8NWHOpySOxQd?si=abc"
            )
            .unwrap(),
            canonical
        );
        assert_eq!(
            normalize_spotify_playlist_url(
                "https://open.spotify.com/embed/playlist/37i9dQZF1E8NWHOpySOxQd"
            )
            .unwrap(),
            canonical
        );
        assert_eq!(
            normalize_spotify_playlist_url("spotify:playlist:37i9dQZF1E8NWHOpySOxQd").unwrap(),
            canonical
        );
        assert_eq!(
            normalize_spotify_playlist_url("https://spotify.link/abcDEF123").unwrap(),
            "https://spotify.link/abcDEF123"
        );
    }

    #[test]
    fn playlist_url_rejects_other_links() {
        for raw in [
            "",
            "   ",
            "https://open.spotify.com/track/37i9dQZF1E8NWHOpySOxQd",
            "https://music.163.com/#/playlist?id=1",
            "https://evil.example/playlist/37i9dQZF1E8NWHOpySOxQd",
            "spotify:playlist:short",
        ] {
            assert!(normalize_spotify_playlist_url(raw).is_err(), "{raw}");
        }
    }

    #[tokio::test]
    async fn playlist_sends_the_canonical_url() {
        let svc = ParseSpotifyPlaylistService::new(Arc::new(FakeMusic {
            bytes: Bytes::from_static(b"mp3"),
        }));
        let found = svc
            .execute("https://open.spotify.com/playlist/37i9dQZF1E8NWHOpySOxQd?si=1")
            .await
            .unwrap();
        assert_eq!(found.search_id, "playlist");
        assert_eq!(
            found.tracks[0].song_name,
            "https://open.spotify.com/playlist/37i9dQZF1E8NWHOpySOxQd"
        );
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
