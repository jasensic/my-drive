use domain::media::{audio_needs_mobile_transcode, mobile_audio_object_key};
use domain::model::FileRecord;
use domain::{JobKind, JobState, MediaKind};
use sha2::{Digest, Sha256};

use crate::{AppError, Deps};

/// Makes the AAC copy now. Called by the background job; requests use [`request_mobile_audio`].
pub async fn ensure_mobile_audio(deps: &Deps, file: FileRecord) -> Result<FileRecord, AppError> {
    if file.media_kind != MediaKind::Audio || file.has_mobile_audio() {
        return Ok(file);
    }
    if !audio_needs_mobile_transcode(&file.mime, &file.name) {
        return Ok(file);
    }

    let _guard = deps.transcode_locks.lock(file.id).await;
    let current = deps
        .files
        .find_by_id(file.id)
        .await?
        .ok_or_else(|| AppError::not_found("file not found"))?;
    if current.has_mobile_audio() {
        return Ok(current);
    }

    let original = deps.objects.get(&current.object_key).await?;
    let cover = if let Some(key) = &current.thumbnail_key {
        deps.objects.get(key).await.ok()
    } else {
        None
    };
    let transcoded = deps
        .audio_transcoder
        .transcode_aac_256(&original, &current.mime, cover.as_deref())
        .await?;
    let object_key = mobile_audio_object_key(current.id);
    let checksum = format!("sha256:{:x}", Sha256::digest(&transcoded.bytes));
    let size = transcoded.bytes.len() as u64;
    deps.objects
        .put(&object_key, transcoded.bytes, &transcoded.mime)
        .await?;
    deps.files
        .set_mobile_variant(current.id, &object_key, &checksum, size, &transcoded.mime)
        .await?;
    let mut updated = current;
    updated.mobile_object_key = Some(object_key);
    updated.mobile_checksum = Some(checksum);
    updated.mobile_size = Some(size);
    updated.mobile_mime = Some(transcoded.mime);
    Ok(updated)
}

/// Where a file stands on its phone-friendly AAC copy.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum MobileAudio {
    /// Not audio, or a format phones play as it is.
    NotNeeded,
    Ready,
    /// A background job is making it (or was just queued to).
    Pending,
    /// The job gave up; serve the original.
    Failed,
}

/// Asks for the AAC copy of a lossless audio file without waiting for it: queues the background
/// job unless there is one already or it already gave up.
pub async fn request_mobile_audio(deps: &Deps, file: &FileRecord) -> Result<MobileAudio, AppError> {
    if file.media_kind != MediaKind::Audio || !audio_needs_mobile_transcode(&file.mime, &file.name) {
        return Ok(MobileAudio::NotNeeded);
    }
    if file.has_mobile_audio() {
        return Ok(MobileAudio::Ready);
    }
    match deps.jobs.state(JobKind::MobileAudio, Some(file.id.0)).await? {
        Some(JobState::Failed) => Ok(MobileAudio::Failed),
        Some(_) => Ok(MobileAudio::Pending),
        None => {
            deps.jobs
                .enqueue(JobKind::MobileAudio, Some(file.id.0), deps.clock.now())
                .await?;
            Ok(MobileAudio::Pending)
        }
    }
}
