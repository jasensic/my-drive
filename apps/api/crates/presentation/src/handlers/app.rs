use application::{
    InspectApkCommand, PublishAppReleaseCommand, UpdateAppReleaseCommand,
};
use axum::body::Body;
use axum::extract::{Multipart, Path, State};
use axum::http::{header, StatusCode};
use axum::response::Response;
use axum::Json;
use bytes::Bytes;
use domain::AppReleaseId;
use uuid::Uuid;

use crate::dto::{ApkIdentityDto, AppReleaseDto};
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
            .map(AppReleaseDto::from_release)
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
    Ok(Json(AppReleaseDto::from_release(release)))
}

#[utoipa::path(post, path = "/v1/app/releases/inspect", responses((status = 200, body = ApkIdentityDto)), security(("bearer" = [])))]
pub async fn inspect(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    mut multipart: Multipart,
) -> Result<Json<ApkIdentityDto>, ApiError> {
    let (file_name, bytes) = read_apk_field(&mut multipart).await?;
    let identity = state
        .services
        .inspect_apk
        .execute(InspectApkCommand {
            actor_id: user.user_id,
            file_name,
            bytes,
        })
        .await?;
    Ok(Json(ApkIdentityDto::from_identity(identity)))
}

#[utoipa::path(post, path = "/v1/app/releases", responses((status = 200, body = AppReleaseDto)), security(("bearer" = [])))]
pub async fn publish(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    mut multipart: Multipart,
) -> Result<Json<AppReleaseDto>, ApiError> {
    let mut changelog = String::new();
    let mut file_name = String::new();
    let mut data: Option<Bytes> = None;

    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| ApiError(application::AppError::validation(e.to_string())))?
    {
        match field.name().unwrap_or_default() {
            "changelog" => changelog = field.text().await.unwrap_or_default(),
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
    let release = state
        .services
        .publish_app_release
        .execute(PublishAppReleaseCommand {
            actor_id: user.user_id,
            changelog,
            file_name,
            bytes,
        })
        .await?;
    Ok(Json(AppReleaseDto::from_release(release)))
}

#[utoipa::path(patch, path = "/v1/app/releases/{id}", params(("id" = Uuid, Path)), responses((status = 200, body = AppReleaseDto)), security(("bearer" = [])))]
pub async fn update(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
    mut multipart: Multipart,
) -> Result<Json<AppReleaseDto>, ApiError> {
    let mut changelog = None;
    let mut file_name = None;
    let mut data = None;

    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| ApiError(application::AppError::validation(e.to_string())))?
    {
        match field.name().unwrap_or_default() {
            "changelog" => changelog = Some(field.text().await.unwrap_or_default()),
            "apk" => {
                file_name = Some(field.file_name().unwrap_or("my-drive.apk").to_string());
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

    let release = state
        .services
        .update_app_release
        .execute(UpdateAppReleaseCommand {
            actor_id: user.user_id,
            id: AppReleaseId::from_uuid(id),
            changelog,
            file_name,
            bytes: data,
        })
        .await?;
    Ok(Json(AppReleaseDto::from_release(release)))
}

#[utoipa::path(delete, path = "/v1/app/releases/{id}", params(("id" = Uuid, Path)), responses((status = 204)), security(("bearer" = [])))]
pub async fn delete(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    state
        .services
        .delete_app_release
        .execute(user.user_id, AppReleaseId::from_uuid(id))
        .await?;
    Ok(StatusCode::NO_CONTENT)
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

async fn read_apk_field(multipart: &mut Multipart) -> Result<(String, Bytes), ApiError> {
    let mut file_name = String::new();
    let mut data = None;
    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| ApiError(application::AppError::validation(e.to_string())))?
    {
        if field.name().unwrap_or_default() == "apk" {
            file_name = field.file_name().unwrap_or("my-drive.apk").to_string();
            data = Some(
                field
                    .bytes()
                    .await
                    .map_err(|e| ApiError(application::AppError::validation(e.to_string())))?,
            );
        }
    }
    let bytes = data.ok_or_else(|| ApiError(application::AppError::validation("apk is required")))?;
    Ok((file_name, bytes))
}
