use axum::extract::State;
use axum::Json;

use crate::dto::{AlbumDto, CreateAlbumRequest};
use crate::error::ApiError;
use crate::extract::CurrentUser;
use crate::state::AppState;

#[utoipa::path(get, path = "/v1/albums", responses((status = 200, body = [AlbumDto])), security(("bearer" = [])))]
pub async fn list(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
) -> Result<Json<Vec<AlbumDto>>, ApiError> {
    let albums = state.services.list_albums.execute(user.user_id).await?;
    Ok(Json(albums.into_iter().map(Into::into).collect()))
}

#[utoipa::path(post, path = "/v1/albums", request_body = CreateAlbumRequest, responses((status = 200, body = AlbumDto)), security(("bearer" = [])))]
pub async fn create(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Json(body): Json<CreateAlbumRequest>,
) -> Result<Json<AlbumDto>, ApiError> {
    let album = state
        .services
        .create_album
        .execute(user.user_id, body.name)
        .await?;
    Ok(Json(album.into()))
}
