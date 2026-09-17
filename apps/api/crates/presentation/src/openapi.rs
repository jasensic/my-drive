use utoipa::OpenApi;

use crate::dto::*;
use crate::handlers::{albums, app, auth, devices, files, health, sync};

#[derive(OpenApi)]
#[openapi(
    paths(
        health::health,
        auth::status,
        auth::setup,
        auth::login,
        auth::me,
        albums::list,
        albums::create,
        files::list,
        files::upload,
        files::get,
        files::assign,
        files::content,
        files::thumbnail,
        devices::list,
        devices::register,
        devices::get_profile,
        devices::put_profile,
        sync::manifest,
        app::list,
        app::latest,
        app::publish,
        app::apk,
    ),
    components(schemas(
        HealthResponse,
        StatusResponse,
        CredentialsRequest,
        AuthResponse,
        UserDto,
        CreateAlbumRequest,
        AlbumDto,
        FileDto,
        AssignAlbumRequest,
        RegisterDeviceRequest,
        DeviceDto,
        SyncRuleDto,
        UpsertProfileRequest,
        SyncProfileDto,
        ManifestRequest,
        ManifestFileDto,
        ManifestResponse,
        AppReleaseDto,
        crate::error::ErrorBody,
    )),
    tags(
        (name = "my-drive", description = "LAN media storage and sync")
    )
)]
pub struct ApiDoc;
