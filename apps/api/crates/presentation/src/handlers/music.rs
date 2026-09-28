use axum::extract::{Path, State};
use axum::Json;

use crate::dto::{
    FileDto, ImportMusicRequest, MusicSearchRequest, MusicSearchResponse, MusicTrackDto,
    SpotifyPlaylistRequest,
};
use crate::error::ApiError;
use crate::extract::CurrentUser;
use crate::state::AppState;
use domain::music::MusicSearch;

fn search_response(found: MusicSearch) -> MusicSearchResponse {
    MusicSearchResponse {
        search_id: found.search_id,
        tracks: found
            .tracks
            .into_iter()
            .map(|track| MusicTrackDto {
                id: track.id,
                source: track.source,
                song_name: track.song_name,
                singers: track.singers,
                album: track.album,
                duration: track.duration,
                file_size: track.file_size,
                ext: track.ext,
                cover_url: track.cover_url,
            })
            .collect(),
        done: found.done,
        error: found.error,
    }
}

#[utoipa::path(
    post,
    path = "/v1/music/search",
    request_body = MusicSearchRequest,
    responses((status = 200, body = MusicSearchResponse)),
    security(("bearer" = []))
)]
pub async fn search(
    State(state): State<AppState>,
    CurrentUser(_user): CurrentUser,
    Json(body): Json<MusicSearchRequest>,
) -> Result<Json<MusicSearchResponse>, ApiError> {
    let found = state.services.search_music.execute(&body.keyword).await?;
    Ok(Json(search_response(found)))
}

#[utoipa::path(
    get,
    path = "/v1/music/search/{id}",
    params(("id" = String, Path, description = "Search id from POST /v1/music/search")),
    responses((status = 200, body = MusicSearchResponse)),
    security(("bearer" = []))
)]
pub async fn search_status(
    State(state): State<AppState>,
    CurrentUser(_user): CurrentUser,
    Path(id): Path<String>,
) -> Result<Json<MusicSearchResponse>, ApiError> {
    let found = state.services.poll_music_search.execute(&id).await?;
    Ok(Json(search_response(found)))
}

#[utoipa::path(
    post,
    path = "/v1/music/playlist",
    request_body = SpotifyPlaylistRequest,
    responses((status = 200, body = MusicSearchResponse)),
    security(("bearer" = []))
)]
pub async fn playlist(
    State(state): State<AppState>,
    CurrentUser(_user): CurrentUser,
    Json(body): Json<SpotifyPlaylistRequest>,
) -> Result<Json<MusicSearchResponse>, ApiError> {
    let found = state
        .services
        .parse_spotify_playlist
        .execute(&body.url)
        .await?;
    Ok(Json(search_response(found)))
}

#[utoipa::path(
    post,
    path = "/v1/music/import",
    request_body = ImportMusicRequest,
    responses((status = 200, body = FileDto)),
    security(("bearer" = []))
)]
pub async fn import(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Json(body): Json<ImportMusicRequest>,
) -> Result<Json<FileDto>, ApiError> {
    let file = state
        .services
        .import_music
        .execute(user.user_id, &body.search_id, &body.track_id)
        .await?;
    Ok(Json(FileDto::from_record(file)))
}
