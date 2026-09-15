use std::sync::Arc;

use async_trait::async_trait;
use domain::model::Album;
use domain::{AlbumId, UserId};

use crate::{AppError, Deps};

#[async_trait]
pub trait CreateAlbum: Send + Sync {
    async fn execute(&self, owner_id: UserId, name: String) -> Result<Album, AppError>;
}

#[async_trait]
pub trait ListAlbums: Send + Sync {
    async fn execute(&self, owner_id: UserId) -> Result<Vec<Album>, AppError>;
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
    async fn execute(&self, owner_id: UserId, name: String) -> Result<Album, AppError> {
        let name = name.trim().to_string();
        if name.is_empty() {
            return Err(AppError::validation("album name is required"));
        }
        let album = Album {
            id: AlbumId::new(),
            owner_id,
            name,
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
    async fn execute(&self, owner_id: UserId) -> Result<Vec<Album>, AppError> {
        Ok(self.deps.albums.list_by_owner(owner_id).await?)
    }
}
