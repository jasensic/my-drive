use std::collections::{HashMap, HashSet};

use domain::model::{Album, FileRecord, Share, SharePermission, ShareResourceType};
use domain::{AlbumId, FileId, UserId};

use crate::{AppError, Deps};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ResourceAccess {
    Owner,
    Read,
    Write,
}

impl ResourceAccess {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Owner => "owner",
            Self::Read => "read",
            Self::Write => "write",
        }
    }

    pub fn is_owner(self) -> bool {
        matches!(self, Self::Owner)
    }

    pub fn can_write_album(self) -> bool {
        matches!(self, Self::Owner | Self::Write)
    }
}

#[derive(Debug, Clone)]
pub struct AccessibleFile {
    pub file: FileRecord,
    pub access: ResourceAccess,
    pub shared: bool,
}

#[derive(Debug, Clone)]
pub struct AccessibleAlbum {
    pub album: Album,
    pub access: ResourceAccess,
    pub shared: bool,
}

pub(crate) fn permission_access(permission: SharePermission) -> ResourceAccess {
    match permission {
        SharePermission::Read => ResourceAccess::Read,
        SharePermission::Write => ResourceAccess::Write,
    }
}

pub(crate) fn share_covers_file(share: &Share, file: &FileRecord) -> bool {
    match share.resource_type {
        ShareResourceType::File => share.resource_id == file.id.0,
        ShareResourceType::Album => file.album_id.is_some_and(|album_id| share.resource_id == album_id.0),
    }
}

pub(crate) fn file_access(
    user_id: UserId,
    file: &FileRecord,
    incoming: &[Share],
    owned_album_ids: &HashSet<AlbumId>,
) -> Option<ResourceAccess> {
    if file.owner_id == user_id {
        return Some(ResourceAccess::Owner);
    }
    let mut access: Option<ResourceAccess> = None;
    if file
        .album_id
        .is_some_and(|album_id| owned_album_ids.contains(&album_id))
    {
        access = Some(ResourceAccess::Read);
    }
    for share in incoming {
        if !share_covers_file(share, file) {
            continue;
        }
        let next = permission_access(share.permission);
        access = Some(match access {
            None => next,
            Some(ResourceAccess::Owner) => ResourceAccess::Owner,
            Some(ResourceAccess::Write) => ResourceAccess::Write,
            Some(ResourceAccess::Read) if next == ResourceAccess::Write => ResourceAccess::Write,
            Some(ResourceAccess::Read) => ResourceAccess::Read,
        });
    }
    access
}

pub(crate) async fn list_accessible_files(
    deps: &Deps,
    user_id: UserId,
) -> Result<Vec<AccessibleFile>, AppError> {
    let owned = deps.files.list_by_owner(user_id).await?;
    let owned_albums = deps.albums.list_by_owner(user_id).await?;
    let outgoing = deps.shares.list_by_owner(user_id).await?;
    let incoming = deps.shares.list_by_grantee(user_id).await?;
    let owned_album_ids: HashSet<AlbumId> = owned_albums.iter().map(|album| album.id).collect();

    let mut extra_file_ids: HashSet<FileId> = HashSet::new();
    let mut album_ids: HashSet<AlbumId> = owned_album_ids.clone();
    for share in &incoming {
        match share.resource_type {
            ShareResourceType::File => {
                extra_file_ids.insert(FileId::from_uuid(share.resource_id));
            }
            ShareResourceType::Album => {
                album_ids.insert(AlbumId::from_uuid(share.resource_id));
            }
        }
    }

    let album_id_list: Vec<AlbumId> = album_ids.into_iter().collect();
    let album_files = if album_id_list.is_empty() {
        Vec::new()
    } else {
        deps.files.list_by_album_ids(&album_id_list).await?
    };
    for file in &album_files {
        extra_file_ids.insert(file.id);
    }

    let owned_ids: HashSet<FileId> = owned.iter().map(|file| file.id).collect();
    extra_file_ids.retain(|id| !owned_ids.contains(id));
    let extra_list: Vec<FileId> = extra_file_ids.into_iter().collect();
    let extra = if extra_list.is_empty() {
        Vec::new()
    } else {
        deps.files.list_by_ids(&extra_list).await?
    };

    let mut by_id: HashMap<FileId, FileRecord> = HashMap::new();
    for file in owned.into_iter().chain(extra).chain(album_files) {
        if !file.is_trashed() {
            by_id.insert(file.id, file);
        }
    }

    let mut result = Vec::new();
    for file in by_id.into_values() {
        let Some(access) = file_access(user_id, &file, &incoming, &owned_album_ids) else {
            continue;
        };
        let shared = !access.is_owner()
            || outgoing.iter().any(|share| share_covers_file(share, &file));
        result.push(AccessibleFile {
            file,
            access,
            shared,
        });
    }
    result.sort_by(|a, b| b.file.created_at.cmp(&a.file.created_at));
    Ok(result)
}

pub(crate) async fn list_accessible_albums(
    deps: &Deps,
    user_id: UserId,
) -> Result<Vec<AccessibleAlbum>, AppError> {
    let owned = deps.albums.list_by_owner(user_id).await?;
    let outgoing = deps.shares.list_by_owner(user_id).await?;
    let incoming = deps.shares.list_by_grantee(user_id).await?;
    let mut extra_ids: Vec<AlbumId> = incoming
        .iter()
        .filter(|share| share.resource_type == ShareResourceType::Album)
        .map(|share| AlbumId::from_uuid(share.resource_id))
        .collect();
    extra_ids.sort_by_key(|id| id.0);
    extra_ids.dedup();
    let extra = if extra_ids.is_empty() {
        Vec::new()
    } else {
        deps.albums.list_by_ids(&extra_ids).await?
    };

    let mut by_id: HashMap<AlbumId, Album> = HashMap::new();
    for album in owned.into_iter().chain(extra) {
        by_id.insert(album.id, album);
    }

    let mut result = Vec::new();
    for album in by_id.into_values() {
        let access = if album.owner_id == user_id {
            ResourceAccess::Owner
        } else {
            incoming
                .iter()
                .find(|share| {
                    share.resource_type == ShareResourceType::Album && share.resource_id == album.id.0
                })
                .map(|share| permission_access(share.permission))
                .unwrap_or(ResourceAccess::Read)
        };
        let shared = !access.is_owner()
            || outgoing.iter().any(|share| {
                share.resource_type == ShareResourceType::Album && share.resource_id == album.id.0
            });
        result.push(AccessibleAlbum {
            album,
            access,
            shared,
        });
    }
    result.sort_by(|a, b| b.album.created_at.cmp(&a.album.created_at));
    Ok(result)
}

pub(crate) async fn accessible_file(
    deps: &Deps,
    user_id: UserId,
    id: FileId,
) -> Result<AccessibleFile, AppError> {
    let file = deps
        .files
        .find_by_id(id)
        .await?
        .ok_or_else(|| AppError::not_found("file not found"))?;
    let owned_albums = deps.albums.list_by_owner(user_id).await?;
    let owned_album_ids: HashSet<AlbumId> = owned_albums.iter().map(|album| album.id).collect();
    let incoming = deps.shares.list_by_grantee(user_id).await?;
    let outgoing = deps.shares.list_by_owner(user_id).await?;
    let Some(access) = file_access(user_id, &file, &incoming, &owned_album_ids) else {
        return Err(AppError::forbidden("file not shared with you"));
    };
    let shared = !access.is_owner() || outgoing.iter().any(|share| share_covers_file(share, &file));
    Ok(AccessibleFile {
        file,
        access,
        shared,
    })
}

pub(crate) async fn require_file_owner(
    deps: &Deps,
    user_id: UserId,
    id: FileId,
) -> Result<FileRecord, AppError> {
    let accessible = accessible_file(deps, user_id, id).await?;
    if !accessible.access.is_owner() {
        return Err(AppError::forbidden("only the owner can do that"));
    }
    Ok(accessible.file)
}

pub(crate) async fn require_file_read(
    deps: &Deps,
    user_id: UserId,
    id: FileId,
) -> Result<FileRecord, AppError> {
    Ok(accessible_file(deps, user_id, id).await?.file)
}

pub(crate) async fn accessible_album(
    deps: &Deps,
    user_id: UserId,
    id: AlbumId,
) -> Result<AccessibleAlbum, AppError> {
    let album = deps
        .albums
        .find_by_id(id)
        .await?
        .ok_or_else(|| AppError::not_found("album not found"))?;
    if album.owner_id == user_id {
        let outgoing = deps.shares.list_by_resource(ShareResourceType::Album, album.id.0).await?;
        return Ok(AccessibleAlbum {
            shared: !outgoing.is_empty(),
            album,
            access: ResourceAccess::Owner,
        });
    }
    let incoming = deps.shares.list_by_grantee(user_id).await?;
    let Some(share) = incoming.iter().find(|share| {
        share.resource_type == ShareResourceType::Album && share.resource_id == album.id.0
    }) else {
        return Err(AppError::forbidden("album not shared with you"));
    };
    Ok(AccessibleAlbum {
        album,
        access: permission_access(share.permission),
        shared: true,
    })
}

pub(crate) async fn require_album_owner(
    deps: &Deps,
    user_id: UserId,
    id: AlbumId,
) -> Result<Album, AppError> {
    let accessible = accessible_album(deps, user_id, id).await?;
    if !accessible.access.is_owner() {
        return Err(AppError::forbidden("only the owner can do that"));
    }
    Ok(accessible.album)
}

pub(crate) async fn require_album_write(
    deps: &Deps,
    user_id: UserId,
    id: AlbumId,
) -> Result<Album, AppError> {
    let accessible = accessible_album(deps, user_id, id).await?;
    if !accessible.access.can_write_album() {
        return Err(AppError::forbidden("album is read-only"));
    }
    Ok(accessible.album)
}

#[cfg(test)]
mod tests {
    use chrono::Utc;
    use domain::ids::ShareId;
    use domain::{MediaKind, SharePermission, ShareResourceType};

    use super::*;

    fn file(owner: UserId, album_id: Option<AlbumId>) -> FileRecord {
        let now = Utc::now();
        FileRecord {
            id: FileId::new(),
            owner_id: owner,
            album_id,
            name: "f".into(),
            size: 1,
            mime: "image/jpeg".into(),
            checksum: "sha256:x".into(),
            object_key: "k".into(),
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

    fn share(
        resource_type: ShareResourceType,
        resource_id: uuid::Uuid,
        permission: SharePermission,
        grantee: UserId,
    ) -> Share {
        Share {
            id: ShareId::new(),
            resource_type,
            resource_id,
            owner_id: UserId::new(),
            grantee_id: grantee,
            permission,
            created_at: Utc::now(),
        }
    }

    #[test]
    fn owner_keeps_owner_access() {
        let user = UserId::new();
        let record = file(user, None);
        assert_eq!(
            file_access(user, &record, &[], &HashSet::new()),
            Some(ResourceAccess::Owner)
        );
    }

    #[test]
    fn album_share_grants_read_or_write() {
        let owner = UserId::new();
        let grantee = UserId::new();
        let album = AlbumId::new();
        let record = file(owner, Some(album));
        let incoming = vec![share(
            ShareResourceType::Album,
            album.0,
            SharePermission::Write,
            grantee,
        )];
        assert_eq!(
            file_access(grantee, &record, &incoming, &HashSet::new()),
            Some(ResourceAccess::Write)
        );
    }

    #[test]
    fn stranger_has_no_access() {
        let record = file(UserId::new(), None);
        assert_eq!(
            file_access(UserId::new(), &record, &[], &HashSet::new()),
            None
        );
    }
}
