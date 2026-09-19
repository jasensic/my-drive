use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::Json;
use domain::{AlbumId, LibrarySilo};
use uuid::Uuid;

use crate::dto::{AlbumDto, AlbumListQuery, CreateAlbumRequest, RenameAlbumRequest};
use crate::error::ApiError;
use crate::extract::CurrentUser;
use crate::state::AppState;

fn parse_silo(value: Option<&str>) -> Result<Option<LibrarySilo>, ApiError> {
    match value {
        None => Ok(None),
        Some(raw) if raw.is_empty() => Ok(None),
        Some(raw) => LibrarySilo::parse(raw)
            .map(Some)
            .ok_or_else(|| ApiError(application::AppError::validation("invalid silo"))),
    }
}

#[utoipa::path(
    get,
    path = "/v1/albums",
    params(("silo" = Option<String>, Query, description = "music, photos, or files")),
    responses((status = 200, body = [AlbumDto])),
    security(("bearer" = []))
)]
pub async fn list(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Query(query): Query<AlbumListQuery>,
) -> Result<Json<Vec<AlbumDto>>, ApiError> {
    let silo = parse_silo(query.silo.as_deref())?;
    let albums = state
        .services
        .list_albums
        .execute(user.user_id, silo)
        .await?;
    Ok(Json(albums.into_iter().map(Into::into).collect()))
}

#[utoipa::path(post, path = "/v1/albums", request_body = CreateAlbumRequest, responses((status = 200, body = AlbumDto)), security(("bearer" = [])))]
pub async fn create(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Json(body): Json<CreateAlbumRequest>,
) -> Result<Json<AlbumDto>, ApiError> {
    let silo = parse_silo(body.silo.as_deref())?.unwrap_or(LibrarySilo::Photos);
    let album = state
        .services
        .create_album
        .execute(user.user_id, body.name, silo)
        .await?;
    Ok(Json(album.into()))
}

#[utoipa::path(
    patch,
    path = "/v1/albums/{id}",
    params(("id" = Uuid, Path)),
    request_body = RenameAlbumRequest,
    responses((status = 200, body = AlbumDto)),
    security(("bearer" = []))
)]
pub async fn rename(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
    Json(body): Json<RenameAlbumRequest>,
) -> Result<Json<AlbumDto>, ApiError> {
    let album = state
        .services
        .rename_album
        .execute(user.user_id, AlbumId::from_uuid(id), body.name)
        .await?;
    Ok(Json(album.into()))
}

#[utoipa::path(
    delete,
    path = "/v1/albums/{id}",
    params(("id" = Uuid, Path)),
    responses((status = 204)),
    security(("bearer" = []))
)]
pub async fn delete(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    state
        .services
        .delete_album
        .execute(user.user_id, AlbumId::from_uuid(id))
        .await?;
    Ok(StatusCode::NO_CONTENT)
}
