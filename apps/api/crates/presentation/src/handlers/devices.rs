use axum::extract::{Path, State};
use axum::Json;
use domain::DeviceId;
use uuid::Uuid;

use crate::dto::{DeviceDto, RegisterDeviceRequest, SyncProfileDto, UpsertProfileRequest};
use crate::error::ApiError;
use crate::extract::CurrentUser;
use crate::state::AppState;

#[utoipa::path(get, path = "/v1/devices", responses((status = 200, body = [DeviceDto])), security(("bearer" = [])))]
pub async fn list(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
) -> Result<Json<Vec<DeviceDto>>, ApiError> {
    let devices = state.services.list_devices.execute(user.user_id).await?;
    Ok(Json(devices.into_iter().map(Into::into).collect()))
}

#[utoipa::path(post, path = "/v1/devices", request_body = RegisterDeviceRequest, responses((status = 200, body = DeviceDto)), security(("bearer" = [])))]
pub async fn register(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Json(body): Json<RegisterDeviceRequest>,
) -> Result<Json<DeviceDto>, ApiError> {
    let device = state
        .services
        .register_device
        .execute(user.user_id, body.name)
        .await?;
    Ok(Json(device.into()))
}

#[utoipa::path(get, path = "/v1/devices/{id}/sync-profile", params(("id" = Uuid, Path)), responses((status = 200, body = SyncProfileDto)), security(("bearer" = [])))]
pub async fn get_profile(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
) -> Result<Json<SyncProfileDto>, ApiError> {
    let profile = state
        .services
        .get_sync_profile
        .execute(user.user_id, DeviceId::from_uuid(id))
        .await?;
    Ok(Json(profile.into()))
}

#[utoipa::path(put, path = "/v1/devices/{id}/sync-profile", params(("id" = Uuid, Path)), request_body = UpsertProfileRequest, responses((status = 200, body = SyncProfileDto)), security(("bearer" = [])))]
pub async fn put_profile(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
    Json(body): Json<UpsertProfileRequest>,
) -> Result<Json<SyncProfileDto>, ApiError> {
    let profile = state
        .services
        .upsert_sync_profile
        .execute(
            user.user_id,
            DeviceId::from_uuid(id),
            body.name,
            body.rules.into_iter().map(Into::into).collect(),
        )
        .await?;
    Ok(Json(profile.into()))
}
