use application::UploadCommand;
use axum::body::Body;
use axum::extract::{Multipart, Path, State};
use axum::http::{header, HeaderMap, StatusCode};
use axum::response::Response;
use axum::Json;
use bytes::Bytes;
use domain::{AlbumId, FileId};
use uuid::Uuid;

use crate::dto::{AssignAlbumRequest, FileDto};
use crate::error::ApiError;
use crate::extract::CurrentUser;
use crate::state::AppState;

#[utoipa::path(get, path = "/v1/files", responses((status = 200, body = [FileDto])), security(("bearer" = [])))]
pub async fn list(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
) -> Result<Json<Vec<FileDto>>, ApiError> {
    let files = state.services.list_files.execute(user.user_id).await?;
    Ok(Json(
        files
            .into_iter()
            .map(|f| FileDto::from_record(f, &state.public_url))
            .collect(),
    ))
}

#[utoipa::path(post, path = "/v1/files", responses((status = 200, body = FileDto)), security(("bearer" = [])))]
pub async fn upload(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    mut multipart: Multipart,
) -> Result<Json<FileDto>, ApiError> {
    let mut name = String::new();
    let mut mime = String::new();
    let mut album_id = None;
    let mut created_at = None;
    let mut data: Option<Bytes> = None;

    while let Some(field) = multipart
        .next_field()
        .await
        .map_err(|e| ApiError(application::AppError::validation(e.to_string())))?
    {
        match field.name().unwrap_or_default() {
            "album_id" => {
                let text = field.text().await.unwrap_or_default();
                if !text.is_empty() {
                    album_id = Some(
                        Uuid::parse_str(&text)
                            .map_err(|_| ApiError(application::AppError::validation("invalid album_id")))?,
                    );
                }
            }
            "created_at" => {
                let text = field.text().await.unwrap_or_default();
                if !text.is_empty() {
                    created_at = Some(
                        chrono::DateTime::parse_from_rfc3339(&text)
                            .map_err(|_| ApiError(application::AppError::validation("invalid created_at")))?
                            .with_timezone(&chrono::Utc),
                    );
                }
            }
            "file" => {
                name = field.file_name().unwrap_or("upload.bin").to_string();
                mime = field
                    .content_type()
                    .unwrap_or("application/octet-stream")
                    .to_string();
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

    let bytes = data.ok_or_else(|| ApiError(application::AppError::validation("file is required")))?;
    let record = state
        .services
        .upload_file
        .execute(UploadCommand {
            owner_id: user.user_id,
            album_id: album_id.map(AlbumId::from_uuid),
            name,
            mime,
            bytes,
            created_at,
        })
        .await?;
    Ok(Json(FileDto::from_record(record, &state.public_url)))
}

#[utoipa::path(get, path = "/v1/files/{id}", params(("id" = Uuid, Path)), responses((status = 200, body = FileDto)), security(("bearer" = [])))]
pub async fn get(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
) -> Result<Json<FileDto>, ApiError> {
    let file = state
        .services
        .get_file
        .execute(user.user_id, FileId::from_uuid(id))
        .await?;
    Ok(Json(FileDto::from_record(file, &state.public_url)))
}

#[utoipa::path(patch, path = "/v1/files/{id}", params(("id" = Uuid, Path)), request_body = AssignAlbumRequest, responses((status = 200, body = FileDto)), security(("bearer" = [])))]
pub async fn assign(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
    Json(body): Json<AssignAlbumRequest>,
) -> Result<Json<FileDto>, ApiError> {
    let file = state
        .services
        .assign_file_album
        .execute(
            user.user_id,
            FileId::from_uuid(id),
            body.album_id.map(AlbumId::from_uuid),
        )
        .await?;
    Ok(Json(FileDto::from_record(file, &state.public_url)))
}

#[utoipa::path(get, path = "/v1/files/{id}/content", params(("id" = Uuid, Path)), responses((status = 200), (status = 206)), security(("bearer" = [])))]
pub async fn content(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
    headers: HeaderMap,
) -> Result<Response, ApiError> {
    stream_file(&state, user, id, headers, false).await
}

#[utoipa::path(get, path = "/v1/files/{id}/thumbnail", params(("id" = Uuid, Path)), responses((status = 200)), security(("bearer" = [])))]
pub async fn thumbnail(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
    headers: HeaderMap,
) -> Result<Response, ApiError> {
    stream_file(&state, user, id, headers, true).await
}

async fn stream_file(
    state: &AppState,
    user: domain::model::AuthSession,
    id: Uuid,
    headers: HeaderMap,
    thumbnail: bool,
) -> Result<Response, ApiError> {
    let (start, end) = parse_range(headers.get(header::RANGE).and_then(|v| v.to_str().ok()));
    let content = state
        .services
        .get_file_content
        .execute(user.user_id, FileId::from_uuid(id), start, end, thumbnail)
        .await?;
    let len = content.data.len() as u64;
    let end_inclusive = content.start.saturating_add(len.saturating_sub(1));
    let mut builder = Response::builder().header(header::CONTENT_TYPE, content.mime.clone());
    if start.is_some() {
        builder = builder
            .status(StatusCode::PARTIAL_CONTENT)
            .header(
                header::CONTENT_RANGE,
                format!("bytes {}-{}/{}", content.start, end_inclusive, content.total_size),
            )
            .header(header::ACCEPT_RANGES, "bytes");
    } else {
        builder = builder.header(header::ACCEPT_RANGES, "bytes");
    }
    builder = builder.header(header::CONTENT_LENGTH, len);
    Ok(builder
        .body(Body::from(content.data))
        .unwrap_or_else(|_| Response::new(Body::empty())))
}

fn parse_range(header: Option<&str>) -> (Option<u64>, Option<u64>) {
    let Some(h) = header.and_then(|h| h.strip_prefix("bytes=")) else {
        return (None, None);
    };
    let mut parts = h.splitn(2, '-');
    let start = parts.next().and_then(|s| s.parse().ok());
    let end = parts.next().and_then(|s| if s.is_empty() { None } else { s.parse().ok() });
    (start, end)
}
