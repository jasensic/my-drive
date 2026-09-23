use chrono::{DateTime, Utc};
use domain::model::{
    Album, Device, FileRecord, ManifestEntry, SharePermission, ShareResourceType, SyncManifest,
    SyncProfile, SyncRule, User, UserSummary,
};
use domain::MediaKind;
use serde::{Deserialize, Deserializer, Serialize};
use utoipa::ToSchema;
use uuid::Uuid;

#[derive(Serialize, ToSchema)]
pub struct StatusResponse {
    pub setup_required: bool,
}

#[derive(Deserialize, ToSchema)]
pub struct CredentialsRequest {
    pub username: String,
    pub password: String,
}

#[derive(Serialize, ToSchema)]
pub struct AuthResponse {
    pub token: String,
    pub user: UserDto,
}

#[derive(Serialize, ToSchema)]
pub struct UserDto {
    pub id: Uuid,
    pub username: String,
}

impl From<User> for UserDto {
    fn from(value: User) -> Self {
        Self {
            id: value.id.0,
            username: value.username,
        }
    }
}

impl From<UserSummary> for UserDto {
    fn from(value: UserSummary) -> Self {
        Self {
            id: value.id.0,
            username: value.username,
        }
    }
}

#[derive(Deserialize, ToSchema)]
pub struct CreateAlbumRequest {
    pub name: String,
    pub silo: Option<String>,
}

#[derive(Deserialize, ToSchema)]
pub struct RenameAlbumRequest {
    pub name: String,
}

#[derive(Deserialize, Default, ToSchema)]
pub struct AlbumListQuery {
    pub silo: Option<String>,
}

#[derive(Serialize, ToSchema)]
pub struct AlbumDto {
    pub id: Uuid,
    pub owner_id: Uuid,
    pub name: String,
    #[schema(value_type = String)]
    pub silo: domain::LibrarySilo,
    pub created_at: DateTime<Utc>,
    pub access: String,
    pub shared: bool,
}

impl From<Album> for AlbumDto {
    fn from(value: Album) -> Self {
        Self {
            id: value.id.0,
            owner_id: value.owner_id.0,
            name: value.name,
            silo: value.silo,
            created_at: value.created_at,
            access: "owner".into(),
            shared: false,
        }
    }
}

impl AlbumDto {
    pub fn from_accessible(item: application::AccessibleAlbum) -> Self {
        let mut dto = Self::from(item.album);
        dto.access = item.access.as_str().into();
        dto.shared = item.shared;
        dto
    }
}

#[derive(Serialize, ToSchema)]
pub struct FileDto {
    pub id: Uuid,
    pub owner_id: Uuid,
    pub album_id: Option<Uuid>,
    pub name: String,
    pub size: u64,
    pub mime: String,
    pub checksum: String,
    #[schema(value_type = String)]
    pub media_kind: MediaKind,
    pub created_at: DateTime<Utc>,
    pub uploaded_at: DateTime<Utc>,
    pub deleted_at: Option<DateTime<Utc>>,
    pub purge_at: Option<DateTime<Utc>>,
    pub content_url: String,
    pub thumbnail_url: Option<String>,
    pub access: String,
    pub shared: bool,
}

impl FileDto {
    pub fn from_record(file: FileRecord) -> Self {
        Self::from_accessible(application::AccessibleFile {
            file,
            access: application::ResourceAccess::Owner,
            shared: false,
        })
    }

    pub fn from_accessible(item: application::AccessibleFile) -> Self {
        let file = item.file;
        let purge_at = file.purge_at();
        Self {
            id: file.id.0,
            owner_id: file.owner_id.0,
            album_id: file.album_id.map(|a| a.0),
            name: file.name,
            size: file.size,
            mime: file.mime,
            checksum: file.checksum,
            media_kind: file.media_kind,
            created_at: file.created_at,
            uploaded_at: file.uploaded_at,
            deleted_at: file.deleted_at,
            purge_at,
            content_url: format!("/v1/files/{}/content", file.id),
            thumbnail_url: file
                .thumbnail_key
                .map(|_| format!("/v1/files/{}/thumbnail", file.id)),
            access: item.access.as_str().into(),
            shared: item.shared,
        }
    }
}

#[derive(Deserialize, ToSchema, Default)]
pub struct UpdateFileRequest {
    pub name: Option<String>,
    #[serde(default, deserialize_with = "deserialize_patch_album")]
    #[schema(value_type = Option<Uuid>)]
    pub album_id: Option<Option<Uuid>>,
}

fn deserialize_patch_album<'de, D>(deserializer: D) -> Result<Option<Option<Uuid>>, D::Error>
where
    D: Deserializer<'de>,
{
    Ok(Some(Option::deserialize(deserializer)?))
}

#[derive(Deserialize, Default, ToSchema)]
pub struct FileListQuery {
    pub silo: Option<String>,
}

#[derive(Serialize, ToSchema)]
pub struct EmptyTrashResponse {
    pub deleted: u64,
}

#[derive(Deserialize, ToSchema)]
pub struct RegisterDeviceRequest {
    pub name: String,
}

#[derive(Serialize, ToSchema)]
pub struct DeviceDto {
    pub id: Uuid,
    pub name: String,
    pub last_sync_at: Option<DateTime<Utc>>,
}

impl From<Device> for DeviceDto {
    fn from(value: Device) -> Self {
        Self {
            id: value.id.0,
            name: value.name,
            last_sync_at: value.last_sync_at,
        }
    }
}

#[derive(Deserialize, Serialize, ToSchema, Clone)]
pub struct SyncRuleDto {
    #[schema(value_type = String)]
    pub media_kind: MediaKind,
    pub max_age_days: Option<u32>,
    pub max_size_bytes: Option<u64>,
    pub include_all: bool,
}

impl From<SyncRule> for SyncRuleDto {
    fn from(value: SyncRule) -> Self {
        Self {
            media_kind: value.media_kind,
            max_age_days: value.max_age_days,
            max_size_bytes: value.max_size_bytes,
            include_all: value.include_all,
        }
    }
}

impl From<SyncRuleDto> for SyncRule {
    fn from(value: SyncRuleDto) -> Self {
        Self {
            media_kind: value.media_kind,
            max_age_days: value.max_age_days,
            max_size_bytes: value.max_size_bytes,
            include_all: value.include_all,
        }
    }
}

#[derive(Deserialize, ToSchema)]
pub struct UpsertProfileRequest {
    pub name: String,
    pub rules: Vec<SyncRuleDto>,
}

#[derive(Serialize, ToSchema)]
pub struct SyncProfileDto {
    pub id: Uuid,
    pub device_id: Uuid,
    pub name: String,
    pub rules: Vec<SyncRuleDto>,
}

impl From<SyncProfile> for SyncProfileDto {
    fn from(value: SyncProfile) -> Self {
        Self {
            id: value.id.0,
            device_id: value.device_id.0,
            name: value.name,
            rules: value.rules.into_iter().map(Into::into).collect(),
        }
    }
}

#[derive(Deserialize, ToSchema)]
pub struct ManifestRequest {
    pub device_id: Uuid,
    pub last_sync_at: Option<DateTime<Utc>>,
    pub have_file_ids: Option<Vec<Uuid>>,
}

#[derive(Serialize, ToSchema)]
pub struct ManifestFileDto {
    pub id: Uuid,
    pub name: String,
    pub size: u64,
    pub mime: String,
    pub checksum: String,
    pub url: String,
    #[schema(value_type = String)]
    pub media_kind: MediaKind,
    pub album_id: Option<Uuid>,
}

impl ManifestFileDto {
    pub fn from_entry(entry: ManifestEntry) -> Self {
        let url = if entry.mobile {
            format!("/v1/files/{}/content?variant=mobile", entry.id)
        } else {
            format!("/v1/files/{}/content", entry.id)
        };
        Self {
            id: entry.id.0,
            name: entry.name,
            size: entry.size,
            mime: entry.mime,
            checksum: entry.checksum,
            url,
            media_kind: entry.media_kind,
            album_id: entry.album_id.map(|a| a.0),
        }
    }
}

#[derive(Serialize, ToSchema)]
pub struct ManifestResponse {
    pub generated_at: DateTime<Utc>,
    pub files: Vec<ManifestFileDto>,
    pub albums: Vec<AlbumDto>,
}

impl ManifestResponse {
    pub fn from_manifest(manifest: SyncManifest) -> Self {
        Self {
            generated_at: manifest.generated_at,
            files: manifest
                .files
                .into_iter()
                .map(ManifestFileDto::from_entry)
                .collect(),
            albums: manifest.albums.into_iter().map(Into::into).collect(),
        }
    }
}

#[derive(Serialize, ToSchema)]
pub struct ApkIdentityDto {
    pub version_code: i32,
    pub version_name: String,
}

impl ApkIdentityDto {
    pub fn from_identity(identity: domain::apk::ApkIdentity) -> Self {
        Self {
            version_code: identity.version_code,
            version_name: identity.version_name,
        }
    }
}

#[derive(Serialize, ToSchema)]
pub struct AppReleaseDto {
    pub id: Uuid,
    pub version_code: i32,
    pub version_name: String,
    pub changelog: String,
    pub checksum: String,
    pub size: u64,
    pub published_at: DateTime<Utc>,
    pub download_url: String,
}

impl AppReleaseDto {
    pub fn from_release(release: domain::model::AppRelease) -> Self {
        Self {
            id: release.id.0,
            version_code: release.version_code,
            version_name: release.version_name,
            changelog: release.changelog,
            checksum: release.checksum,
            size: release.size,
            published_at: release.published_at,
            download_url: format!("/v1/app/releases/{}/apk", release.id),
        }
    }
}

#[derive(Serialize, ToSchema)]
pub struct HealthResponse {
    pub status: String,
}

#[derive(Deserialize, ToSchema)]
pub struct MusicSearchRequest {
    pub keyword: String,
}

#[derive(Serialize, ToSchema)]
pub struct MusicTrackDto {
    pub id: String,
    pub source: String,
    pub song_name: String,
    pub singers: String,
    pub album: String,
    pub duration: String,
    pub file_size: String,
    pub ext: String,
    pub cover_url: String,
}

#[derive(Serialize, ToSchema)]
pub struct MusicSearchResponse {
    pub search_id: String,
    pub tracks: Vec<MusicTrackDto>,
}

#[derive(Deserialize, ToSchema)]
pub struct ImportMusicRequest {
    pub search_id: String,
    pub track_id: String,
}

#[derive(Deserialize, ToSchema)]
pub struct DeviceExclusionsRequest {
    pub file_ids: Vec<Uuid>,
}

#[derive(Serialize, ToSchema)]
pub struct DeviceExclusionsDto {
    pub file_ids: Vec<Uuid>,
}

#[derive(Deserialize, Default, ToSchema)]
pub struct ContentQuery {
    pub variant: Option<String>,
}

#[derive(Deserialize, ToSchema)]
pub struct CreateShareRequest {
    pub resource_type: String,
    pub resource_id: Uuid,
    pub grantee_id: Uuid,
    pub permission: String,
}

#[derive(Deserialize, Default, ToSchema)]
pub struct ShareListQuery {
    pub resource_type: Option<String>,
    pub resource_id: Option<Uuid>,
}

#[derive(Serialize, ToSchema)]
pub struct ShareDto {
    pub id: Uuid,
    #[schema(value_type = String)]
    pub resource_type: ShareResourceType,
    pub resource_id: Uuid,
    pub owner_id: Uuid,
    pub grantee_id: Uuid,
    pub grantee_username: String,
    #[schema(value_type = String)]
    pub permission: SharePermission,
    pub created_at: DateTime<Utc>,
}

impl ShareDto {
    pub fn from_view(view: application::ShareView) -> Self {
        Self {
            id: view.share.id.0,
            resource_type: view.share.resource_type,
            resource_id: view.share.resource_id,
            owner_id: view.share.owner_id.0,
            grantee_id: view.share.grantee_id.0,
            grantee_username: view.grantee_username,
            permission: view.share.permission,
            created_at: view.share.created_at,
        }
    }
}
