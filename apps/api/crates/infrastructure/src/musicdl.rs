use std::time::Duration;

use async_trait::async_trait;
use bytes::Bytes;
use domain::music::{DownloadedAudio, MusicSearch, MusicTrack};
use domain::ports::MusicDownloader;
use domain::DomainError;
use serde::Deserialize;

pub struct HttpMusicDownloader {
    base_url: String,
    client: reqwest::Client,
}

impl HttpMusicDownloader {
    pub fn new(base_url: &str) -> Result<Self, DomainError> {
        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(300))
            .build()
            .map_err(|err| DomainError::infra(format!("musicdl client: {err}")))?;
        Ok(Self {
            base_url: base_url.trim_end_matches('/').to_string(),
            client,
        })
    }

    fn endpoint(&self, path: &str) -> String {
        format!("{}/{}", self.base_url, path.trim_start_matches('/'))
    }
}

#[async_trait]
impl MusicDownloader for HttpMusicDownloader {
    async fn search(&self, keyword: &str) -> Result<MusicSearch, DomainError> {
        let response = self
            .client
            .post(self.endpoint("search"))
            .json(&serde_json::json!({ "keyword": keyword }))
            .send()
            .await
            .map_err(downloader_unreachable)?;
        let body = read_ok(response).await?;
        let parsed: SearchPayload = serde_json::from_slice(&body)
            .map_err(|err| DomainError::validation(format!("music search response: {err}")))?;
        Ok(MusicSearch {
            search_id: parsed.search_id,
            tracks: parsed
                .tracks
                .into_iter()
                .map(|track| MusicTrack {
                    id: track.id,
                    source: track.source,
                    song_name: track.song_name,
                    singers: track.singers,
                    album: track.album,
                    duration: track.duration,
                    file_size: track.file_size,
                    ext: track.ext,
                    cover_url: public_cover(&track.cover_url),
                })
                .collect(),
        })
    }

    async fn download(&self, search_id: &str, track_id: &str) -> Result<DownloadedAudio, DomainError> {
        let response = self
            .client
            .post(self.endpoint("download"))
            .json(&serde_json::json!({
                "search_id": search_id,
                "track_id": track_id,
            }))
            .send()
            .await
            .map_err(downloader_unreachable)?;
        if !response.status().is_success() {
            return Err(error_from_response(response).await);
        }
        let mime = response
            .headers()
            .get(reqwest::header::CONTENT_TYPE)
            .and_then(|value| value.to_str().ok())
            .map(mime_of)
            .unwrap_or_else(|| "audio/mpeg".to_string());
        let name = response
            .headers()
            .get("x-song-name")
            .and_then(|value| value.to_str().ok())
            .map(percent_decode)
            .unwrap_or_else(|| "track".to_string());
        let bytes = response
            .bytes()
            .await
            .map_err(|err| DomainError::validation(format!("music download body: {err}")))?;
        Ok(DownloadedAudio {
            name: safe_filename(&name),
            mime,
            bytes,
        })
    }
}

#[derive(Deserialize)]
struct SearchPayload {
    search_id: String,
    tracks: Vec<TrackPayload>,
}

#[derive(Deserialize)]
struct TrackPayload {
    id: String,
    source: String,
    song_name: String,
    singers: String,
    album: String,
    duration: String,
    file_size: String,
    ext: String,
    #[serde(default)]
    cover_url: String,
}

#[derive(Deserialize)]
struct ErrorPayload {
    error: String,
}

fn downloader_unreachable(err: reqwest::Error) -> DomainError {
    DomainError::validation(format!(
        "Music downloader is not reachable at the configured MUSICDL_URL ({err})"
    ))
}

async fn read_ok(response: reqwest::Response) -> Result<Bytes, DomainError> {
    if !response.status().is_success() {
        return Err(error_from_response(response).await);
    }
    response
        .bytes()
        .await
        .map_err(|err| DomainError::validation(format!("music search body: {err}")))
}

async fn error_from_response(response: reqwest::Response) -> DomainError {
    let status = response.status();
    let text = response.text().await.unwrap_or_default();
    let message = serde_json::from_str::<ErrorPayload>(&text)
        .map(|body| body.error)
        .unwrap_or_else(|_| {
            let trimmed = text.trim();
            if trimmed.is_empty() {
                format!("musicdl returned {status}")
            } else {
                trimmed.chars().take(300).collect()
            }
        });
    match status.as_u16() {
        404 => DomainError::not_found(message),
        400 => DomainError::validation(message),
        _ => DomainError::validation(message),
    }
}

fn public_cover(raw: &str) -> String {
    let url = raw.trim();
    if url.starts_with("https://") || url.starts_with("http://") {
        url.to_string()
    } else {
        String::new()
    }
}

fn mime_of(header: &str) -> String {
    header
        .split(';')
        .next()
        .unwrap_or("audio/mpeg")
        .trim()
        .to_string()
}

fn percent_decode(input: &str) -> String {
    let bytes = input.as_bytes();
    let mut out = Vec::with_capacity(bytes.len());
    let mut index = 0;
    while index < bytes.len() {
        if bytes[index] == b'%' && index + 2 < bytes.len() {
            if let Ok(value) = u8::from_str_radix(
                std::str::from_utf8(&bytes[index + 1..index + 3]).unwrap_or(""),
                16,
            ) {
                out.push(value);
                index += 3;
                continue;
            }
        }
        out.push(bytes[index]);
        index += 1;
    }
    String::from_utf8_lossy(&out).into_owned()
}

fn safe_filename(raw: &str) -> String {
    let base = raw.rsplit(['/', '\\']).next().unwrap_or(raw);
    let cleaned: String = base
        .chars()
        .filter(|c| !c.is_control() && *c != '"')
        .take(180)
        .collect();
    let trimmed = cleaned.trim().trim_matches('.').trim();
    if trimmed.is_empty() {
        "track".to_string()
    } else {
        trimmed.to_string()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_search_payload() {
        let body = r#"{"search_id":"abc","tracks":[{"id":"0","source":"NeteaseMusicClient","song_name":"A","singers":"B","album":"C","duration":"1:00","file_size":"3MB","ext":"mp3"}]}"#;
        let parsed: SearchPayload = serde_json::from_str(body).unwrap();
        assert_eq!(parsed.search_id, "abc");
        assert_eq!(parsed.tracks[0].song_name, "A");
    }

    #[test]
    fn decodes_song_names() {
        assert_eq!(
            percent_decode("Artist%20-%20Song.mp3"),
            "Artist - Song.mp3"
        );
        assert_eq!(safe_filename("../../etc/passwd.mp3"), "passwd.mp3");
        assert_eq!(safe_filename("  "), "track");
    }
}
