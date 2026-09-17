use application::PublishAppReleaseCommand;
use axum::body::Body;
use axum::extract::{Multipart, Path, State};
use axum::http::header;
use axum::response::Response;
use axum::Json;
use bytes::Bytes;
use domain::AppReleaseId;
use uuid::Uuid;

use crate::dto::AppReleaseDto;
use crate::error::ApiError;
use crate::extract::CurrentUser;
use crate::state::AppState;

#[utoipa::path(get, path = "/v1/app/releases", responses((status = 200, body = [AppReleaseDto])), security(("bearer" = [])))]
pub async fn list(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
) -> Result<Json<Vec<AppReleaseDto>>, ApiError> {
    let releases = state
        .services
        .list_app_releases
        .execute(user.user_id)
        .await?;
    Ok(Json(
        releases
            .into_iter()
            .map(|r| AppReleaseDto::from_release(r, &state.public_url))
            .collect(),
    ))
}

#[utoipa::path(get, path = "/v1/app/releases/latest", responses((status = 200, body = AppReleaseDto)), security(("bearer" = [])))]
pub async fn latest(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
) -> Result<Json<AppReleaseDto>, ApiError> {
    let release = state
        .services
        .get_latest_app_release
        .execute(user.user_id)
        .await?;
    Ok(Json(AppReleaseDto::from_release(release, &state.public_url)))
}

#[utoipa::path(post, path = "/v1/app/releases", responses((status = 200, body = AppReleaseDto)), security(("bearer" = [])))]
pub async fn publish(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    mut multipart: Multipart,
) -> Result<Json<AppReleaseDto>, ApiError> {
    let mut version_code: Option<i32> = None;
    let mut version_name = String::new();
    let mut changelog = String::new();
    let mut file_name = String::new();
    let mut data: Option<Bytes> = None;

    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| ApiError(application::AppError::validation(e.to_string())))?
    {
        match field.name().unwrap_or_default() {
            "version_code" => {
                let text = field.text().await.unwrap_or_default();
                version_code = Some(text.parse().map_err(|_| {
                    ApiError(application::AppError::validation("invalid version_code"))
                })?);
            }
            "version_name" => {
                version_name = field.text().await.unwrap_or_default();
            }
            "changelog" => {
                changelog = field.text().await.unwrap_or_default();
            }
            "apk" => {
                file_name = field.file_name().unwrap_or("my-drive.apk").to_string();
                data = Some(
                    field
                        .bytes()
                        .await
                        .map_err(|e| ApiError(application::AppError::validation(e.to_string())))?,
                );
            }
            _ => {}
        }
    }

    let bytes = data.ok_or_else(|| ApiError(application::AppError::validation("apk is required")))?;
    let version_code = version_code
        .ok_or_else(|| ApiError(application::AppError::validation("version_code is required")))?;
    let release = state
        .services
        .publish_app_release
        .execute(PublishAppReleaseCommand {
            actor_id: user.user_id,
            version_code,
            version_name,
            changelog,
            file_name,
            bytes,
        })
        .await?;
    Ok(Json(AppReleaseDto::from_release(release, &state.public_url)))
}

#[utoipa::path(get, path = "/v1/app/releases/{id}/apk", params(("id" = Uuid, Path)), responses((status = 200)), security(("bearer" = [])))]
pub async fn apk(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
) -> Result<Response, ApiError> {
    let apk = state
        .services
        .get_app_release_apk
        .execute(user.user_id, AppReleaseId::from_uuid(id))
        .await?;
    Ok(Response::builder()
        .header(header::CONTENT_TYPE, apk.mime)
        .header(
            header::CONTENT_DISPOSITION,
            format!("attachment; filename=\"{}\"", apk.file_name),
        )
        .header(header::CONTENT_LENGTH, apk.data.len())
        .body(Body::from(apk.data))
        .unwrap_or_else(|_| Response::new(Body::empty())))
}
