use application::{FileContentVariant, UploadCommand};
use axum::body::Body;
use axum::extract::{Multipart, Path, Query, State};
use axum::http::{header, HeaderMap, StatusCode};
use axum::response::Response;
use axum::Json;
use bytes::Bytes;
use domain::{AlbumId, DomainError, FileId, LibrarySilo};
use futures_util::StreamExt;
use uuid::Uuid;

use crate::dto::{ContentQuery, EmptyTrashResponse, FileDto, FileListQuery, UpdateFileRequest};
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
    path = "/v1/files",
    params(("silo" = Option<String>, Query, description = "music, photos, or files")),
    responses((status = 200, body = [FileDto])),
    security(("bearer" = []))
)]
pub async fn list(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Query(query): Query<FileListQuery>,
) -> Result<Json<Vec<FileDto>>, ApiError> {
    let silo = parse_silo(query.silo.as_deref())?;
    let files = state.services.list_files.execute(user.user_id, silo).await?;
    Ok(Json(files.into_iter().map(FileDto::from_accessible).collect()))
}

#[utoipa::path(
    get,
    path = "/v1/files/trash",
    params(("silo" = Option<String>, Query, description = "music, photos, or files")),
    responses((status = 200, body = [FileDto])),
    security(("bearer" = []))
)]
pub async fn list_trash(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Query(query): Query<FileListQuery>,
) -> Result<Json<Vec<FileDto>>, ApiError> {
    let silo = parse_silo(query.silo.as_deref())?;
    let files = state.services.list_trash.execute(user.user_id, silo).await?;
    Ok(Json(files.into_iter().map(FileDto::from_record).collect()))
}

#[utoipa::path(
    delete,
    path = "/v1/files/trash",
    params(("silo" = Option<String>, Query, description = "music, photos, or files")),
    responses((status = 200, body = EmptyTrashResponse)),
    security(("bearer" = []))
)]
pub async fn empty_trash(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Query(query): Query<FileListQuery>,
) -> Result<Json<EmptyTrashResponse>, ApiError> {
    let silo = parse_silo(query.silo.as_deref())?;
    let deleted = state.services.empty_trash.execute(user.user_id, silo).await?;
    Ok(Json(EmptyTrashResponse { deleted }))
}

#[utoipa::path(
    post,
    path = "/v1/files",
    request_body(content_type = "multipart/form-data", description = "Optional `album_id` and `created_at` fields, then the `file` field last"),
    responses((status = 200, body = FileDto)),
    security(("bearer" = []))
)]
pub async fn upload(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    mut multipart: Multipart,
) -> Result<Json<FileDto>, ApiError> {
    let mut album_id = None;
    let mut created_at = None;
    let mut stored: Option<domain::model::FileRecord> = None;
    let mut sent_late = false;

    while let Some(mut field) = multipart.next_field().await.map_err(bad_multipart)? {
        match field.name().unwrap_or_default() {
            "album_id" => {
                let text = field.text().await.unwrap_or_default();
                if text.is_empty() {
                    continue;
                }
                sent_late |= stored.is_some();
                album_id = Some(
                    Uuid::parse_str(&text)
                        .map_err(|_| ApiError(application::AppError::validation("invalid album_id")))?,
                );
            }
            "created_at" => {
                let text = field.text().await.unwrap_or_default();
                if text.is_empty() {
                    continue;
                }
                sent_late |= stored.is_some();
                created_at = Some(
                    chrono::DateTime::parse_from_rfc3339(&text)
                        .map_err(|_| ApiError(application::AppError::validation("invalid created_at")))?
                        .with_timezone(&chrono::Utc),
                );
            }
            "file" if stored.is_none() => {
                let name = field.file_name().unwrap_or("upload.bin").to_string();
                let mime = field
                    .content_type()
                    .unwrap_or("application/octet-stream")
                    .to_string();
                // The use case reads from a channel while this loop feeds it from the request body,
                // so the file never has to fit in memory.
                let (tx, mut rx) = tokio::sync::mpsc::channel::<Result<Bytes, DomainError>>(8);
                let content = futures_util::stream::poll_fn(move |cx| rx.poll_recv(cx)).boxed();
                let upload = state.services.upload_file.execute(UploadCommand {
                    owner_id: user.user_id,
                    album_id: album_id.map(AlbumId::from_uuid),
                    name,
                    mime,
                    content,
                    created_at,
                });
                let pump = async move {
                    loop {
                        match field.chunk().await {
                            Ok(Some(chunk)) => {
                                // The receiver is gone once the use case has given up.
                                if tx.send(Ok(chunk)).await.is_err() {
                                    break;
                                }
                            }
                            Ok(None) => break,
                            Err(err) => {
                                let _ = tx.send(Err(DomainError::validation(err.to_string()))).await;
                                break;
                            }
                        }
                    }
                };
                let (record, ()) = tokio::join!(upload, pump);
                stored = Some(record?);
            }
            _ => {}
        }
    }

    let record = stored.ok_or_else(|| ApiError(application::AppError::validation("file is required")))?;
    if sent_late {
        // Too late to use them: the file was already stored without them. Undo it rather than
        // leave it in the wrong album or with the wrong date.
        // Purging needs the file in the trash first.
        let _ = state.services.trash_file.execute(user.user_id, record.id).await;
        let _ = state.services.purge_file.execute(user.user_id, record.id).await;
        return Err(ApiError(application::AppError::validation(
            "send album_id and created_at before the file field",
        )));
    }
    Ok(Json(FileDto::from_record(record)))
}

fn bad_multipart(err: axum::extract::multipart::MultipartError) -> ApiError {
    ApiError(application::AppError::validation(err.to_string()))
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
    Ok(Json(FileDto::from_accessible(file)))
}

#[utoipa::path(patch, path = "/v1/files/{id}", params(("id" = Uuid, Path)), request_body = UpdateFileRequest, responses((status = 200, body = FileDto)), security(("bearer" = [])))]
pub async fn update(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
    Json(body): Json<UpdateFileRequest>,
) -> Result<Json<FileDto>, ApiError> {
    let file = state
        .services
        .update_file
        .execute(
            user.user_id,
            FileId::from_uuid(id),
            body.name,
            body.album_id.map(|album_id| album_id.map(AlbumId::from_uuid)),
        )
        .await?;
    Ok(Json(FileDto::from_accessible(file)))
}

#[utoipa::path(
    post,
    path = "/v1/files/{id}/trash",
    params(("id" = Uuid, Path)),
    responses((status = 200, body = FileDto)),
    security(("bearer" = []))
)]
pub async fn trash(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
) -> Result<Json<FileDto>, ApiError> {
    let file = state
        .services
        .trash_file
        .execute(user.user_id, FileId::from_uuid(id))
        .await?;
    Ok(Json(FileDto::from_record(file)))
}

#[utoipa::path(
    post,
    path = "/v1/files/{id}/restore",
    params(("id" = Uuid, Path)),
    responses((status = 200, body = FileDto)),
    security(("bearer" = []))
)]
pub async fn restore(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
) -> Result<Json<FileDto>, ApiError> {
    let file = state
        .services
        .restore_file
        .execute(user.user_id, FileId::from_uuid(id))
        .await?;
    Ok(Json(FileDto::from_record(file)))
}

#[utoipa::path(
    delete,
    path = "/v1/files/{id}",
    params(("id" = Uuid, Path)),
    responses((status = 204)),
    security(("bearer" = []))
)]
pub async fn purge(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
) -> Result<StatusCode, ApiError> {
    state
        .services
        .purge_file
        .execute(user.user_id, FileId::from_uuid(id))
        .await?;
    Ok(StatusCode::NO_CONTENT)
}

#[utoipa::path(
    get,
    path = "/v1/files/{id}/content",
    params(
        ("id" = Uuid, Path),
        ("variant" = Option<String>, Query, description = "original (default) or mobile")
    ),
    responses((status = 200), (status = 206)),
    security(("bearer" = []))
)]
pub async fn content(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
    Query(query): Query<ContentQuery>,
    headers: HeaderMap,
) -> Result<Response, ApiError> {
    stream_file(&state, user, id, headers, false, query.variant.as_deref()).await
}

#[utoipa::path(get, path = "/v1/files/{id}/thumbnail", params(("id" = Uuid, Path)), responses((status = 200)), security(("bearer" = [])))]
pub async fn thumbnail(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Path(id): Path<Uuid>,
    headers: HeaderMap,
) -> Result<Response, ApiError> {
    stream_file(&state, user, id, headers, true, None).await
}

async fn stream_file(
    state: &AppState,
    user: domain::model::AuthSession,
    id: Uuid,
    headers: HeaderMap,
    thumbnail: bool,
    variant: Option<&str>,
) -> Result<Response, ApiError> {
    let (start, end) = parse_range(headers.get(header::RANGE).and_then(|v| v.to_str().ok()));
    let variant = if variant == Some("mobile") {
        FileContentVariant::Mobile
    } else {
        FileContentVariant::Original
    };
    let content = state
        .services
        .get_file_content
        .execute(user.user_id, FileId::from_uuid(id), start, end, thumbnail, variant)
        .await?;
    let body = content.body;
    let mut builder = Response::builder().header(header::CONTENT_TYPE, content.mime);
    builder = builder.header(header::ACCEPT_RANGES, "bytes");
    if start.is_some() {
        let end_inclusive = body.start + body.len.saturating_sub(1);
        builder = builder.status(StatusCode::PARTIAL_CONTENT).header(
            header::CONTENT_RANGE,
            format!("bytes {}-{}/{}", body.start, end_inclusive, body.total),
        );
    }
    Ok(builder
        .header(header::CONTENT_LENGTH, body.len)
        .body(Body::from_stream(body.stream))
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
