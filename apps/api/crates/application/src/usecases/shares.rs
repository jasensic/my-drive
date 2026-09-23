use std::sync::Arc;

use async_trait::async_trait;
use domain::model::{Share, SharePermission, ShareResourceType};
use domain::{AlbumId, FileId, ShareId, UserId};
use uuid::Uuid;

use crate::access::{require_album_owner, require_file_owner};
use crate::{AppError, Deps};

pub struct CreateShareCommand {
    pub actor_id: UserId,
    pub resource_type: ShareResourceType,
    pub resource_id: Uuid,
    pub grantee_id: UserId,
    pub permission: SharePermission,
}

pub struct ListSharesQuery {
    pub actor_id: UserId,
    pub resource_type: Option<ShareResourceType>,
    pub resource_id: Option<Uuid>,
}

#[derive(Debug, Clone)]
pub struct ShareView {
    pub share: Share,
    pub grantee_username: String,
}

#[async_trait]
pub trait CreateShare: Send + Sync {
    async fn execute(&self, cmd: CreateShareCommand) -> Result<ShareView, AppError>;
}

#[async_trait]
pub trait ListShares: Send + Sync {
    async fn execute(&self, query: ListSharesQuery) -> Result<Vec<ShareView>, AppError>;
}

#[async_trait]
pub trait DeleteShare: Send + Sync {
    async fn execute(&self, actor_id: UserId, id: ShareId) -> Result<(), AppError>;
}

pub struct CreateShareService {
    deps: Arc<Deps>,
}

impl CreateShareService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl CreateShare for CreateShareService {
    async fn execute(&self, cmd: CreateShareCommand) -> Result<ShareView, AppError> {
        if cmd.actor_id == cmd.grantee_id {
            return Err(AppError::validation("cannot share with yourself"));
        }
        match cmd.resource_type {
            ShareResourceType::File => {
                let _ = require_file_owner(&self.deps, cmd.actor_id, FileId::from_uuid(cmd.resource_id)).await?;
            }
            ShareResourceType::Album => {
                let _ = require_album_owner(&self.deps, cmd.actor_id, AlbumId::from_uuid(cmd.resource_id)).await?;
            }
        }
        let grantee = self
            .deps
            .users
            .find_by_id(cmd.grantee_id)
            .await?
            .ok_or_else(|| AppError::not_found("user not found"))?;
        let share = Share {
            id: ShareId::new(),
            resource_type: cmd.resource_type,
            resource_id: cmd.resource_id,
            owner_id: cmd.actor_id,
            grantee_id: cmd.grantee_id,
            permission: cmd.permission,
            created_at: self.deps.clock.now(),
        };
        let stored = self.deps.shares.upsert(&share).await?;
        Ok(ShareView {
            share: stored,
            grantee_username: grantee.username,
        })
    }
}

pub struct ListSharesService {
    deps: Arc<Deps>,
}

impl ListSharesService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl ListShares for ListSharesService {
    async fn execute(&self, query: ListSharesQuery) -> Result<Vec<ShareView>, AppError> {
        let shares = match (query.resource_type, query.resource_id) {
            (Some(resource_type), Some(resource_id)) => {
                match resource_type {
                    ShareResourceType::File => {
                        let _ = require_file_owner(
                            &self.deps,
                            query.actor_id,
                            FileId::from_uuid(resource_id),
                        )
                        .await?;
                    }
                    ShareResourceType::Album => {
                        let _ = require_album_owner(
                            &self.deps,
                            query.actor_id,
                            AlbumId::from_uuid(resource_id),
                        )
                        .await?;
                    }
                }
                self.deps.shares.list_by_resource(resource_type, resource_id).await?
            }
            _ => self.deps.shares.list_by_owner(query.actor_id).await?,
        };
        let mut views = Vec::new();
        for share in shares {
            let username = self
                .deps
                .users
                .find_by_id(share.grantee_id)
                .await?
                .map(|user| user.username)
                .unwrap_or_default();
            views.push(ShareView {
                share,
                grantee_username: username,
            });
        }
        Ok(views)
    }
}

pub struct DeleteShareService {
    deps: Arc<Deps>,
}

impl DeleteShareService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl DeleteShare for DeleteShareService {
    async fn execute(&self, actor_id: UserId, id: ShareId) -> Result<(), AppError> {
        let share = self
            .deps
            .shares
            .find_by_id(id)
            .await?
            .ok_or_else(|| AppError::not_found("share not found"))?;
        if share.owner_id != actor_id {
            return Err(AppError::forbidden("only the owner can unshare"));
        }
        self.deps.shares.delete(id).await?;
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::access::ResourceAccess;
    use crate::test_support::{deps_from, TestMem};
    use domain::model::{Album, FileRecord};
    use domain::{LibrarySilo, MediaKind};

    async fn two_users() -> (Arc<TestMem>, domain::UserId, domain::UserId, Arc<crate::Deps>) {
        let mem = TestMem::new();
        let owner = mem.add_user("owner").await;
        let grantee = mem.add_user("friend").await;
        let deps = deps_from(mem.clone());
        (mem, owner, grantee, deps)
    }

    fn photo(owner: UserId) -> FileRecord {
        let now = chrono::Utc::now();
        FileRecord {
            id: FileId::new(),
            owner_id: owner,
            album_id: None,
            name: "shot.jpg".into(),
            size: 10,
            mime: "image/jpeg".into(),
            checksum: "sha256:x".into(),
            object_key: "images/a".into(),
            thumbnail_key: None,
            media_kind: MediaKind::Photo,
            created_at: now,
            uploaded_at: now,
            deleted_at: None,
            mobile_object_key: None,
            mobile_checksum: None,
            mobile_size: None,
            mobile_mime: None,
        }
    }

    #[tokio::test]
    async fn owner_can_share_and_stranger_gets_forbidden() {
        let (mem, owner, grantee, deps) = two_users().await;
        let file = photo(owner);
        mem.insert_file(file.clone()).await;
        let svc = CreateShareService::new(deps.clone());
        let view = svc
            .execute(CreateShareCommand {
                actor_id: owner,
                resource_type: ShareResourceType::File,
                resource_id: file.id.0,
                grantee_id: grantee,
                permission: SharePermission::Read,
            })
            .await
            .unwrap();
        assert_eq!(view.grantee_username, "friend");

        let stranger = mem.add_user("other").await;
        let err = svc
            .execute(CreateShareCommand {
                actor_id: stranger,
                resource_type: ShareResourceType::File,
                resource_id: file.id.0,
                grantee_id: grantee,
                permission: SharePermission::Read,
            })
            .await
            .unwrap_err();
        assert!(err.to_string().contains("not shared") || err.to_string().contains("only the owner"));
    }

    #[tokio::test]
    async fn album_write_share_lets_grantee_see_files() {
        let (mem, owner, grantee, deps) = two_users().await;
        let now = chrono::Utc::now();
        let album = Album {
            id: AlbumId::new(),
            owner_id: owner,
            name: "Trip".into(),
            silo: LibrarySilo::Photos,
            created_at: now,
        };
        mem.insert_album(album.clone()).await;
        let mut file = photo(owner);
        file.album_id = Some(album.id);
        mem.insert_file(file.clone()).await;
        CreateShareService::new(deps.clone())
            .execute(CreateShareCommand {
                actor_id: owner,
                resource_type: ShareResourceType::Album,
                resource_id: album.id.0,
                grantee_id: grantee,
                permission: SharePermission::Write,
            })
            .await
            .unwrap();
        let listed = crate::access::list_accessible_files(&deps, grantee)
            .await
            .unwrap();
        assert_eq!(listed.len(), 1);
        assert_eq!(listed[0].file.id, file.id);
        assert_eq!(listed[0].access, ResourceAccess::Write);
    }
}
