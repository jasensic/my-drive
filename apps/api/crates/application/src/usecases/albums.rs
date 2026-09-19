use std::sync::Arc;

use async_trait::async_trait;
use domain::model::Album;
use domain::{AlbumId, LibrarySilo, UserId};

use crate::{AppError, Deps};

#[async_trait]
pub trait CreateAlbum: Send + Sync {
    async fn execute(
        &self,
        owner_id: UserId,
        name: String,
        silo: LibrarySilo,
    ) -> Result<Album, AppError>;
}

#[async_trait]
pub trait ListAlbums: Send + Sync {
    async fn execute(
        &self,
        owner_id: UserId,
        silo: Option<LibrarySilo>,
    ) -> Result<Vec<Album>, AppError>;
}

#[async_trait]
pub trait RenameAlbum: Send + Sync {
    async fn execute(&self, owner_id: UserId, id: AlbumId, name: String) -> Result<Album, AppError>;
}

#[async_trait]
pub trait DeleteAlbum: Send + Sync {
    async fn execute(&self, owner_id: UserId, id: AlbumId) -> Result<(), AppError>;
}

pub struct CreateAlbumService {
    deps: Arc<Deps>,
}

impl CreateAlbumService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl CreateAlbum for CreateAlbumService {
    async fn execute(
        &self,
        owner_id: UserId,
        name: String,
        silo: LibrarySilo,
    ) -> Result<Album, AppError> {
        let name = name.trim().to_string();
        if name.is_empty() {
            return Err(AppError::validation("album name is required"));
        }
        let album = Album {
            id: AlbumId::new(),
            owner_id,
            name,
            silo,
            created_at: self.deps.clock.now(),
        };
        self.deps.albums.insert(&album).await?;
        Ok(album)
    }
}

pub struct ListAlbumsService {
    deps: Arc<Deps>,
}

impl ListAlbumsService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl ListAlbums for ListAlbumsService {
    async fn execute(
        &self,
        owner_id: UserId,
        silo: Option<LibrarySilo>,
    ) -> Result<Vec<Album>, AppError> {
        let mut albums = self.deps.albums.list_by_owner(owner_id).await?;
        if let Some(silo) = silo {
            albums.retain(|album| album.silo == silo);
        }
        Ok(albums)
    }
}

pub struct RenameAlbumService {
    deps: Arc<Deps>,
}

impl RenameAlbumService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl RenameAlbum for RenameAlbumService {
    async fn execute(&self, owner_id: UserId, id: AlbumId, name: String) -> Result<Album, AppError> {
        let name = name.trim().to_string();
        if name.is_empty() {
            return Err(AppError::validation("album name is required"));
        }
        let album = owned_album(&self.deps, owner_id, id).await?;
        self.deps.albums.update_name(album.id, &name).await?;
        owned_album(&self.deps, owner_id, id).await
    }
}

pub struct DeleteAlbumService {
    deps: Arc<Deps>,
}

impl DeleteAlbumService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl DeleteAlbum for DeleteAlbumService {
    async fn execute(&self, owner_id: UserId, id: AlbumId) -> Result<(), AppError> {
        let album = owned_album(&self.deps, owner_id, id).await?;
        self.deps.albums.delete(album.id).await?;
        Ok(())
    }
}

pub(crate) async fn owned_album(
    deps: &Deps,
    owner_id: UserId,
    id: AlbumId,
) -> Result<Album, AppError> {
    let album = deps
        .albums
        .find_by_id(id)
        .await?
        .ok_or_else(|| AppError::not_found("album not found"))?;
    if album.owner_id != owner_id {
        return Err(AppError::not_found("album not found"));
    }
    Ok(album)
}
