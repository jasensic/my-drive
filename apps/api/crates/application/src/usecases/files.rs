use std::sync::{Arc, Mutex};

use async_trait::async_trait;
use bytes::Bytes;
use domain::model::{Album, FileRecord};
use domain::{
    AlbumId, ByteRange, ByteStream, DomainError, FileId, LibrarySilo, MediaKind, ObjectBody,
    ShareResourceType, UserId,
};
use futures_util::StreamExt;
use sha2::{Digest, Sha256};

use crate::access::{
    accessible_file, list_accessible_files, require_album_write, require_file_owner, require_file_read,
    AccessibleFile,
};
use crate::mobile::request_mobile_audio;
use crate::{AppError, Deps};

pub struct UploadCommand {
    pub owner_id: UserId,
    pub album_id: Option<AlbumId>,
    pub name: String,
    pub mime: String,
    /// The file's bytes as they arrive. Nothing here holds them all at once.
    pub content: ByteStream,
    pub created_at: Option<chrono::DateTime<chrono::Utc>>,
}

impl UploadCommand {
    /// A file that is already in memory, such as a download from another service.
    pub fn content_from_bytes(bytes: Bytes) -> ByteStream {
        ObjectBody::from_bytes(bytes).stream
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FileContentVariant {
    Original,
    Mobile,
}

/// A file's bytes as a stream, so serving it never holds the whole file in memory.
pub struct FileContent {
    pub mime: String,
    pub body: ObjectBody,
}

#[async_trait]
pub trait UploadFile: Send + Sync {
    async fn execute(&self, cmd: UploadCommand) -> Result<FileRecord, AppError>;
}

#[async_trait]
pub trait ListFiles: Send + Sync {
    async fn execute(
        &self,
        owner_id: UserId,
        silo: Option<LibrarySilo>,
    ) -> Result<Vec<AccessibleFile>, AppError>;
}

#[async_trait]
pub trait GetFile: Send + Sync {
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<AccessibleFile, AppError>;
}

#[async_trait]
pub trait UpdateFile: Send + Sync {
    async fn execute(
        &self,
        owner_id: UserId,
        id: FileId,
        name: Option<String>,
        album_id: Option<Option<AlbumId>>,
    ) -> Result<AccessibleFile, AppError>;
}

#[async_trait]
pub trait GetFileContent: Send + Sync {
    async fn execute(
        &self,
        owner_id: UserId,
        id: FileId,
        start: Option<u64>,
        end: Option<u64>,
        thumbnail: bool,
        variant: FileContentVariant,
    ) -> Result<FileContent, AppError>;
}

#[async_trait]
pub trait ListTrash: Send + Sync {
    async fn execute(
        &self,
        owner_id: UserId,
        silo: Option<LibrarySilo>,
    ) -> Result<Vec<FileRecord>, AppError>;
}

#[async_trait]
pub trait TrashFile: Send + Sync {
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<FileRecord, AppError>;
}

#[async_trait]
pub trait RestoreFile: Send + Sync {
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<FileRecord, AppError>;
}

#[async_trait]
pub trait PurgeFile: Send + Sync {
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<(), AppError>;
}

#[async_trait]
pub trait EmptyTrash: Send + Sync {
    async fn execute(
        &self,
        owner_id: UserId,
        silo: Option<LibrarySilo>,
    ) -> Result<u64, AppError>;
}

pub struct UploadFileService {
    deps: Arc<Deps>,
}

impl UploadFileService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl UploadFile for UploadFileService {
    async fn execute(&self, cmd: UploadCommand) -> Result<FileRecord, AppError> {
        if cmd.name.trim().is_empty() {
            return Err(AppError::validation("file name is required"));
        }
        let now = self.deps.clock.now();
        let id = FileId::new();
        let mime = if cmd.mime.is_empty() {
            "application/octet-stream".to_string()
        } else {
            cmd.mime
        };
        let media_kind = MediaKind::from_mime(&mime);
        if let Some(album_id) = cmd.album_id {
            ensure_album_accepts(&self.deps, cmd.owner_id, album_id, media_kind).await?;
        }
        let object_key = domain::media::object_key(media_kind, id, &cmd.name);

        // Hash and measure the bytes as they pass on their way to the store.
        let tally = Arc::new(Mutex::new(UploadTally::new(matches!(
            media_kind,
            MediaKind::Photo | MediaKind::Audio
        ))));
        let counted = cmd.content.inspect({
            let tally = tally.clone();
            move |chunk| {
                if let Ok(bytes) = chunk {
                    tally.lock().unwrap().add(bytes);
                }
            }
        });
        if let Err(err) = self
            .deps
            .objects
            .put_stream(&object_key, &mime, Box::pin(counted))
            .await
        {
            let _ = self.deps.objects.delete(&object_key).await;
            return Err(err.into());
        }
        let (size, checksum, head) = tally.lock().unwrap().finish();
        if size == 0 {
            let _ = self.deps.objects.delete(&object_key).await;
            return Err(AppError::validation("file is empty"));
        }

        // Bigger files get their thumbnail the first time it is asked for.
        let thumbnail_key = match head {
            Some(head) => jpeg_thumbnail(&self.deps, Bytes::from(head), &mime)
                .await
                .map(|thumb| (domain::media::thumbnail_object_key(id), thumb)),
            None => None,
        };
        if let Some((key, thumb)) = &thumbnail_key {
            self.deps
                .objects
                .put(key, Bytes::from(thumb.clone()), "image/jpeg")
                .await?;
        }

        let record = FileRecord {
            id,
            owner_id: cmd.owner_id,
            album_id: cmd.album_id,
            name: cmd.name,
            size,
            mime,
            checksum,
            object_key,
            thumbnail_key: thumbnail_key.map(|(k, _)| k),
            media_kind,
            created_at: cmd.created_at.unwrap_or(now),
            uploaded_at: now,
            deleted_at: None,
            mobile_object_key: None,
            mobile_checksum: None,
            mobile_size: None,
            mobile_mime: None,
        };
        self.deps.files.insert(&record).await?;
        // The AAC copy for phones is made in the background; a failure to queue it is not the upload's.
        let _ = request_mobile_audio(&self.deps, &record).await;
        Ok(record)
    }
}

/// Largest photo or audio file whose bytes are kept while uploading to make a thumbnail.
const THUMBNAIL_SOURCE_LIMIT: u64 = 32 * 1024 * 1024;

/// Running checksum and size of an upload, plus its leading bytes while they are small enough
/// to decode a thumbnail from.
struct UploadTally {
    hasher: Sha256,
    size: u64,
    head: Option<Vec<u8>>,
}

impl UploadTally {
    fn new(keep_head: bool) -> Self {
        Self {
            hasher: Sha256::new(),
            size: 0,
            head: keep_head.then(Vec::new),
        }
    }

    fn add(&mut self, chunk: &Bytes) {
        self.hasher.update(chunk);
        self.size += chunk.len() as u64;
        if self.size > THUMBNAIL_SOURCE_LIMIT {
            self.head = None;
        } else if let Some(head) = &mut self.head {
            head.extend_from_slice(chunk);
        }
    }

    fn finish(&mut self) -> (u64, String, Option<Vec<u8>>) {
        let checksum = format!("sha256:{:x}", self.hasher.clone().finalize());
        (self.size, checksum, self.head.take())
    }
}

pub struct ListFilesService {
    deps: Arc<Deps>,
}

impl ListFilesService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl ListFiles for ListFilesService {
    async fn execute(
        &self,
        owner_id: UserId,
        silo: Option<LibrarySilo>,
    ) -> Result<Vec<AccessibleFile>, AppError> {
        let mut files = list_accessible_files(&self.deps, owner_id).await?;
        if let Some(silo) = silo {
            files.retain(|item| silo.contains(item.file.media_kind));
        }
        Ok(files)
    }
}

pub struct GetFileService {
    deps: Arc<Deps>,
}

impl GetFileService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl GetFile for GetFileService {
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<AccessibleFile, AppError> {
        accessible_file(&self.deps, owner_id, id).await
    }
}

pub struct UpdateFileService {
    deps: Arc<Deps>,
}

impl UpdateFileService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl UpdateFile for UpdateFileService {
    async fn execute(
        &self,
        owner_id: UserId,
        id: FileId,
        name: Option<String>,
        album_id: Option<Option<AlbumId>>,
    ) -> Result<AccessibleFile, AppError> {
        let file = require_file_owner(&self.deps, owner_id, id).await?;
        if file.is_trashed() {
            return Err(AppError::validation("restore the file before editing it"));
        }
        if let Some(name) = name {
            let name = name.trim().to_string();
            if name.is_empty() {
                return Err(AppError::validation("file name is required"));
            }
            self.deps.files.update_name(id, &name).await?;
        }
        if let Some(album_id) = album_id {
            if let Some(album_id) = album_id {
                ensure_album_accepts(&self.deps, owner_id, album_id, file.media_kind).await?;
            }
            self.deps.files.assign_album(id, album_id).await?;
        }
        accessible_file(&self.deps, owner_id, id).await
    }
}

async fn ensure_album_accepts(
    deps: &Deps,
    owner_id: UserId,
    album_id: AlbumId,
    media_kind: MediaKind,
) -> Result<Album, AppError> {
    let album = require_album_write(deps, owner_id, album_id).await?;
    if !album.silo.contains(media_kind) {
        return Err(AppError::validation(
            "album belongs to another library section",
        ));
    }
    Ok(album)
}

pub struct GetFileContentService {
    deps: Arc<Deps>,
}

impl GetFileContentService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }

    /// Makes the thumbnail of a photo or an audio cover the first time it is asked for. Files too big
    /// to decode while uploading end up here.
    async fn thumbnail_from_original(&self, file: &FileRecord) -> Result<Bytes, AppError> {
        let original = self.deps.objects.get(&file.object_key).await?;
        let Some(thumb) = jpeg_thumbnail(&self.deps, original, &file.mime).await else {
            return Err(AppError::not_found("thumbnail not available"));
        };
        let key = domain::media::thumbnail_object_key(file.id);
        self.deps
            .objects
            .put(&key, Bytes::from(thumb.clone()), "image/jpeg")
            .await?;
        self.deps.files.set_thumbnail_key(file.id, &key).await?;
        Ok(Bytes::from(thumb))
    }
}

#[async_trait]
impl GetFileContent for GetFileContentService {
    async fn execute(
        &self,
        owner_id: UserId,
        id: FileId,
        start: Option<u64>,
        end: Option<u64>,
        thumbnail: bool,
        variant: FileContentVariant,
    ) -> Result<FileContent, AppError> {
        let file = require_file_read(&self.deps, owner_id, id).await?;
        if thumbnail {
            let data = if let Some(key) = file.thumbnail_key.clone() {
                self.deps.objects.get(&key).await?
            } else if matches!(file.media_kind, MediaKind::Audio | MediaKind::Photo) {
                self.thumbnail_from_original(&file).await?
            } else {
                return Err(AppError::not_found("thumbnail not available"));
            };
            return Ok(FileContent {
                mime: "image/jpeg".into(),
                body: ObjectBody::from_bytes(data),
            });
        }
        if variant == FileContentVariant::Mobile {
            // Serve the original until the background job has made the AAC copy.
            let _ = request_mobile_audio(&self.deps, &file).await;
        }
        let (object_key, mime, total_size) = if variant == FileContentVariant::Mobile
            && file.has_mobile_audio()
        {
            (
                file.mobile_object_key.clone().unwrap(),
                file.mobile_mime.clone().unwrap(),
                file.mobile_size.unwrap(),
            )
        } else {
            (file.object_key.clone(), file.mime.clone(), file.size)
        };
        let range = match start {
            Some(start) => {
                let range = ByteRange { start, end };
                // Answer an impossible range from the record, without asking the store.
                if range.resolve(total_size).is_none() {
                    return Err(DomainError::RangeNotSatisfiable { total: total_size }.into());
                }
                Some(range)
            }
            None => None,
        };
        let body = self.deps.objects.open(&object_key, range).await?;
        Ok(FileContent { mime, body })
    }
}

/// Image decoding is CPU-bound: keep it off the async workers that serve requests.
async fn jpeg_thumbnail(deps: &Deps, bytes: Bytes, mime: &str) -> Option<Vec<u8>> {
    let thumbnailer = deps.thumbnailer.clone();
    let mime = mime.to_string();
    tokio::task::spawn_blocking(move || thumbnailer.jpeg_thumbnail(&bytes, &mime))
        .await
        .ok()
        .flatten()
}

fn in_silo(file: &FileRecord, silo: Option<LibrarySilo>) -> bool {
    silo.is_none_or(|silo| silo.contains(file.media_kind))
}

pub(crate) async fn remove_stored_file(deps: &Deps, file: &FileRecord) -> Result<(), AppError> {
    deps.objects.delete(&file.object_key).await?;
    if let Some(thumb) = &file.thumbnail_key {
        let _ = deps.objects.delete(thumb).await;
    }
    if let Some(mobile) = &file.mobile_object_key {
        let _ = deps.objects.delete(mobile).await;
    }
    deps.shares
        .delete_for_resource(ShareResourceType::File, file.id.0)
        .await?;
    deps.files.delete(file.id).await?;
    Ok(())
}

pub struct ListTrashService {
    deps: Arc<Deps>,
}

impl ListTrashService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl ListTrash for ListTrashService {
    async fn execute(
        &self,
        owner_id: UserId,
        silo: Option<LibrarySilo>,
    ) -> Result<Vec<FileRecord>, AppError> {
        // Expired files are not shown; the background job deletes them.
        let now = self.deps.clock.now();
        let trashed = self.deps.files.list_trashed_by_owner(owner_id).await?;
        let (keep, _expired) = domain::library::partition_expired_trash(trashed, now, silo);
        Ok(keep)
    }
}

pub struct TrashFileService {
    deps: Arc<Deps>,
}

impl TrashFileService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl TrashFile for TrashFileService {
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<FileRecord, AppError> {
        let file = require_file_owner(&self.deps, owner_id, id).await?;
        if file.is_trashed() {
            return Err(AppError::validation("file is already in the trash"));
        }
        let now = self.deps.clock.now();
        self.deps.files.set_deleted_at(id, Some(now)).await?;
        require_file_owner(&self.deps, owner_id, id).await
    }
}

pub struct RestoreFileService {
    deps: Arc<Deps>,
}

impl RestoreFileService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl RestoreFile for RestoreFileService {
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<FileRecord, AppError> {
        let file = require_file_owner(&self.deps, owner_id, id).await?;
        if !file.is_trashed() {
            return Err(AppError::validation("file is not in the trash"));
        }
        self.deps.files.set_deleted_at(id, None).await?;
        require_file_owner(&self.deps, owner_id, id).await
    }
}

pub struct PurgeFileService {
    deps: Arc<Deps>,
}

impl PurgeFileService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl PurgeFile for PurgeFileService {
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<(), AppError> {
        let file = require_file_owner(&self.deps, owner_id, id).await?;
        if !file.is_trashed() {
            return Err(AppError::validation("move the file to trash before deleting it"));
        }
        remove_stored_file(&self.deps, &file).await
    }
}

pub struct EmptyTrashService {
    deps: Arc<Deps>,
}

impl EmptyTrashService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl EmptyTrash for EmptyTrashService {
    async fn execute(
        &self,
        owner_id: UserId,
        silo: Option<LibrarySilo>,
    ) -> Result<u64, AppError> {
        let trashed = self.deps.files.list_trashed_by_owner(owner_id).await?;
        let mut removed = 0u64;
        for file in trashed {
            if in_silo(&file, silo) {
                remove_stored_file(&self.deps, &file).await?;
                removed += 1;
            }
        }
        Ok(removed)
    }
}
