use std::sync::Arc;

use async_trait::async_trait;
use domain::model::Album;
use domain::{AlbumId, LibrarySilo, ShareResourceType, UserId};

use crate::access::{
    list_accessible_albums, require_album_owner, AccessibleAlbum,
};
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
    ) -> Result<Vec<AccessibleAlbum>, AppError>;
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
    ) -> Result<Vec<AccessibleAlbum>, AppError> {
        let mut albums = list_accessible_albums(&self.deps, owner_id).await?;
        if let Some(silo) = silo {
            albums.retain(|item| item.album.silo == silo);
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
        let album = require_album_owner(&self.deps, owner_id, id).await?;
        self.deps.albums.update_name(album.id, &name).await?;
        require_album_owner(&self.deps, owner_id, id).await
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
        let album = require_album_owner(&self.deps, owner_id, id).await?;
        self.deps
            .shares
            .delete_for_resource(ShareResourceType::Album, album.id.0)
            .await?;
        self.deps.albums.delete(album.id).await?;
        Ok(())
    }
}
