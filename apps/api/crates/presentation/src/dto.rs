use chrono::{DateTime, Utc};
use domain::model::{Album, Device, FileRecord, ManifestEntry, SyncManifest, SyncProfile, SyncRule, User};
use domain::MediaKind;
use serde::{Deserialize, Serialize};
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

#[derive(Deserialize, ToSchema)]
pub struct CreateAlbumRequest {
    pub name: String,
}

#[derive(Serialize, ToSchema)]
pub struct AlbumDto {
    pub id: Uuid,
    pub name: String,
    pub created_at: DateTime<Utc>,
}

impl From<Album> for AlbumDto {
    fn from(value: Album) -> Self {
        Self {
            id: value.id.0,
            name: value.name,
            created_at: value.created_at,
        }
    }
}

#[derive(Serialize, ToSchema)]
pub struct FileDto {
    pub id: Uuid,
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
}

impl FileDto {
    pub fn from_record(file: FileRecord) -> Self {
        let purge_at = file.purge_at();
        Self {
            id: file.id.0,
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
        }
    }
}

#[derive(Deserialize, ToSchema)]
pub struct AssignAlbumRequest {
    pub album_id: Option<Uuid>,
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
        Self {
            id: entry.id.0,
            name: entry.name,
            size: entry.size,
            mime: entry.mime,
            checksum: entry.checksum,
            url: format!("/v1/files/{}/content", entry.id),
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
