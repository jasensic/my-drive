use std::sync::Arc;

use async_trait::async_trait;
use bytes::Bytes;
use domain::model::FileRecord;
use domain::{AlbumId, FileId, LibrarySilo, MediaKind, UserId};
use sha2::{Digest, Sha256};

use crate::{AppError, Deps};

pub struct UploadCommand {
    pub owner_id: UserId,
    pub album_id: Option<AlbumId>,
    pub name: String,
    pub mime: String,
    pub bytes: Bytes,
    pub created_at: Option<chrono::DateTime<chrono::Utc>>,
}

pub struct FileContent {
    pub mime: String,
    pub total_size: u64,
    pub data: Bytes,
    pub start: u64,
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
    ) -> Result<Vec<FileRecord>, AppError>;
}

#[async_trait]
pub trait GetFile: Send + Sync {
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<FileRecord, AppError>;
}

#[async_trait]
pub trait AssignFileAlbum: Send + Sync {
    async fn execute(
        &self,
        owner_id: UserId,
        id: FileId,
        album_id: Option<AlbumId>,
    ) -> Result<FileRecord, AppError>;
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
        if cmd.bytes.is_empty() {
            return Err(AppError::validation("file is empty"));
        }
        let now = self.deps.clock.now();
        let id = FileId::new();
        let mime = if cmd.mime.is_empty() {
            "application/octet-stream".to_string()
        } else {
            cmd.mime
        };
        let media_kind = MediaKind::from_mime(&mime);
        let checksum = format!("sha256:{:x}", Sha256::digest(&cmd.bytes));
        let object_key = domain::media::object_key(media_kind, id, &cmd.name);
        self.deps
            .objects
            .put(&object_key, cmd.bytes.clone(), &mime)
            .await?;

        let thumbnail_key = self
            .deps
            .thumbnailer
            .jpeg_thumbnail(&cmd.bytes, &mime)
            .map(|thumb| {
                let key = domain::media::thumbnail_object_key(id);
                (key, thumb)
            });
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
            size: cmd.bytes.len() as u64,
            mime,
            checksum,
            object_key,
            thumbnail_key: thumbnail_key.map(|(k, _)| k),
            media_kind,
            created_at: cmd.created_at.unwrap_or(now),
            uploaded_at: now,
            deleted_at: None,
        };
        self.deps.files.insert(&record).await?;
        Ok(record)
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
    ) -> Result<Vec<FileRecord>, AppError> {
        let mut files = self.deps.files.list_by_owner(owner_id).await?;
        if let Some(silo) = silo {
            files.retain(|file| silo.contains(file.media_kind));
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
    async fn execute(&self, owner_id: UserId, id: FileId) -> Result<FileRecord, AppError> {
        owned_file(&self.deps, owner_id, id).await
    }
}

pub struct AssignFileAlbumService {
    deps: Arc<Deps>,
}

impl AssignFileAlbumService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl AssignFileAlbum for AssignFileAlbumService {
    async fn execute(
        &self,
        owner_id: UserId,
        id: FileId,
        album_id: Option<AlbumId>,
    ) -> Result<FileRecord, AppError> {
        let _ = owned_file(&self.deps, owner_id, id).await?;
        if let Some(album_id) = album_id {
            let album = self
                .deps
                .albums
                .find_by_id(album_id)
                .await?
                .ok_or_else(|| AppError::not_found("album not found"))?;
            if album.owner_id != owner_id {
                return Err(AppError::not_found("album not found"));
            }
        }
        self.deps.files.assign_album(id, album_id).await?;
        owned_file(&self.deps, owner_id, id).await
    }
}

pub struct GetFileContentService {
    deps: Arc<Deps>,
}

impl GetFileContentService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
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
    ) -> Result<FileContent, AppError> {
        let file = owned_file(&self.deps, owner_id, id).await?;
        if thumbnail {
            let key = file
                .thumbnail_key
                .ok_or_else(|| AppError::not_found("thumbnail not available"))?;
            let data = self.deps.objects.get(&key).await?;
            let len = data.len() as u64;
            return Ok(FileContent {
                mime: "image/jpeg".into(),
                total_size: len,
                data,
                start: 0,
            });
        }
        if let Some(start) = start {
            let (data, total) = self.deps.objects.get_range(&file.object_key, start, end).await?;
            Ok(FileContent {
                mime: file.mime,
                total_size: total,
                data,
                start,
            })
        } else {
            let data = self.deps.objects.get(&file.object_key).await?;
            Ok(FileContent {
                mime: file.mime,
                total_size: file.size,
                data,
                start: 0,
            })
        }
    }
}

async fn owned_file(deps: &Deps, owner_id: UserId, id: FileId) -> Result<FileRecord, AppError> {
    let file = deps
        .files
        .find_by_id(id)
        .await?
        .ok_or_else(|| AppError::not_found("file not found"))?;
    if file.owner_id != owner_id {
        return Err(AppError::not_found("file not found"));
    }
    Ok(file)
}

fn in_silo(file: &FileRecord, silo: Option<LibrarySilo>) -> bool {
    silo.is_none_or(|silo| silo.contains(file.media_kind))
}

async fn remove_stored_file(deps: &Deps, file: &FileRecord) -> Result<(), AppError> {
    deps.objects.delete(&file.object_key).await?;
    if let Some(thumb) = &file.thumbnail_key {
        let _ = deps.objects.delete(thumb).await;
    }
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
        let now = self.deps.clock.now();
        let trashed = self.deps.files.list_trashed_by_owner(owner_id).await?;
        let (keep, expired) = domain::library::partition_expired_trash(trashed, now, silo);
        for file in expired {
            remove_stored_file(&self.deps, &file).await?;
        }
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
        let file = owned_file(&self.deps, owner_id, id).await?;
        if file.is_trashed() {
            return Err(AppError::validation("file is already in the trash"));
        }
        let now = self.deps.clock.now();
        self.deps.files.set_deleted_at(id, Some(now)).await?;
        owned_file(&self.deps, owner_id, id).await
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
        let file = owned_file(&self.deps, owner_id, id).await?;
        if !file.is_trashed() {
            return Err(AppError::validation("file is not in the trash"));
        }
        self.deps.files.set_deleted_at(id, None).await?;
        owned_file(&self.deps, owner_id, id).await
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
        let file = owned_file(&self.deps, owner_id, id).await?;
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

