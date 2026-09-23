use domain::media::{audio_needs_mobile_transcode, mobile_audio_object_key};
use domain::model::FileRecord;
use domain::MediaKind;
use sha2::{Digest, Sha256};

use crate::{AppError, Deps};

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

pub async fn ensure_mobile_audio_best_effort(deps: &Deps, file: FileRecord) -> FileRecord {
    match ensure_mobile_audio(deps, file.clone()).await {
        Ok(updated) => updated,
        Err(_) => file,
    }
}
