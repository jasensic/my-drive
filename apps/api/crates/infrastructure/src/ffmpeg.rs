use std::fs;
use std::path::PathBuf;
use std::process::Command;

use async_trait::async_trait;
use bytes::Bytes;
use domain::model::TranscodedAudio;
use domain::ports::AudioTranscoder;
use domain::DomainError;
use uuid::Uuid;

pub struct FfmpegAudioTranscoder;

impl FfmpegAudioTranscoder {
    pub fn new() -> Self {
        Self
    }
}

pub struct FakeAudioTranscoder;

#[async_trait]
impl AudioTranscoder for FakeAudioTranscoder {
    async fn transcode_aac_256(
        &self,
        _original: &[u8],
        _source_mime: &str,
        _cover_jpeg: Option<&[u8]>,
    ) -> Result<TranscodedAudio, DomainError> {
        Ok(TranscodedAudio {
            bytes: Bytes::from_static(b"fake-aac"),
            mime: domain::media::MOBILE_AUDIO_MIME.into(),
        })
    }
}

#[async_trait]
impl AudioTranscoder for FfmpegAudioTranscoder {
    async fn transcode_aac_256(
        &self,
        original: &[u8],
        _source_mime: &str,
        cover_jpeg: Option<&[u8]>,
    ) -> Result<TranscodedAudio, DomainError> {
        let original = original.to_vec();
        let cover = cover_jpeg.map(|bytes| bytes.to_vec());
        tokio::task::spawn_blocking(move || run_ffmpeg(&original, cover.as_deref()))
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?
    }
}

fn run_ffmpeg(original: &[u8], cover_jpeg: Option<&[u8]>) -> Result<TranscodedAudio, DomainError> {
    let dir = std::env::temp_dir().join(format!("mydrive-aac-{}", Uuid::new_v4()));
    fs::create_dir_all(&dir).map_err(|e| DomainError::infra(e.to_string()))?;
    let input = dir.join("source.bin");
    let output = dir.join("mobile.m4a");
    fs::write(&input, original).map_err(|e| DomainError::infra(e.to_string()))?;

    let mut cmd = Command::new("ffmpeg");
    cmd.arg("-hide_banner")
        .arg("-loglevel")
        .arg("error")
        .arg("-y")
        .arg("-i")
        .arg(&input);

    let cover_path: Option<PathBuf> = if let Some(cover) = cover_jpeg {
        let path = dir.join("cover.jpg");
        fs::write(&path, cover).map_err(|e| DomainError::infra(e.to_string()))?;
        Some(path)
    } else {
        None
    };
    if let Some(cover) = &cover_path {
        cmd.arg("-i").arg(cover);
    }
    cmd.arg("-map").arg("0:a:0");
    if cover_path.is_some() {
        cmd.arg("-map")
            .arg("1:0")
            .arg("-c:v")
            .arg("mjpeg")
            .arg("-disposition:v:0")
            .arg("attached_pic");
    }
    cmd.arg("-c:a")
        .arg("aac")
        .arg("-b:a")
        .arg("256k")
        .arg("-ac")
        .arg("2")
        .arg("-ar")
        .arg("48000")
        .arg("-movflags")
        .arg("+faststart")
        .arg("-f")
        .arg("mp4")
        .arg(&output);

    let status = cmd.status().map_err(|e| {
        DomainError::infra(format!("ffmpeg is not available: {e}"))
    })?;
    let result = if status.success() {
        let bytes = fs::read(&output).map_err(|e| DomainError::infra(e.to_string()))?;
        if bytes.is_empty() {
            Err(DomainError::infra("ffmpeg produced an empty file"))
        } else {
            Ok(TranscodedAudio {
                bytes: Bytes::from(bytes),
                mime: domain::media::MOBILE_AUDIO_MIME.into(),
            })
        }
    } else {
        Err(DomainError::infra(format!(
            "ffmpeg failed with status {status}"
        )))
    };
    let _ = fs::remove_dir_all(&dir);
    result
}
