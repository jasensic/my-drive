use std::collections::HashSet;

use application::ManifestQuery;
use axum::extract::State;
use axum::Json;
use domain::{DeviceId, FileId};

use crate::dto::{ManifestRequest, ManifestResponse};
use crate::error::ApiError;
use crate::extract::CurrentUser;
use crate::state::AppState;

#[utoipa::path(post, path = "/v1/sync/manifest", request_body = ManifestRequest, responses((status = 200, body = ManifestResponse)), security(("bearer" = [])))]
pub async fn manifest(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Json(body): Json<ManifestRequest>,
) -> Result<Json<ManifestResponse>, ApiError> {
    let have = body
        .have_file_ids
        .unwrap_or_default()
        .into_iter()
        .map(FileId::from_uuid)
        .collect::<HashSet<_>>();
    let manifest = state
        .services
        .build_sync_manifest
        .execute(ManifestQuery {
            user_id: user.user_id,
            device_id: DeviceId::from_uuid(body.device_id),
            last_sync_at: body.last_sync_at,
            have_file_ids: have,
        })
        .await?;
    Ok(Json(ManifestResponse::from_manifest(
        manifest,
        &state.public_url,
    )))
}
