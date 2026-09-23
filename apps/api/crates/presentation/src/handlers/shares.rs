use application::{CreateShareCommand, ListSharesQuery};
use axum::extract::{Path, Query, State};
use axum::http::StatusCode;
use axum::Json;
use domain::model::{SharePermission, ShareResourceType};
use domain::ShareId;
use uuid::Uuid;

use crate::dto::{CreateShareRequest, ShareDto, ShareListQuery};
use crate::error::ApiError;
use crate::extract::CurrentUser;
use crate::state::AppState;

fn parse_resource_type(value: &str) -> Result<ShareResourceType, ApiError> {
    ShareResourceType::parse(value)
        .ok_or_else(|| ApiError(application::AppError::validation("invalid resource_type")))
}

fn parse_permission(value: &str) -> Result<SharePermission, ApiError> {
    SharePermission::parse(value)
        .ok_or_else(|| ApiError(application::AppError::validation("invalid permission")))
}

#[utoipa::path(
    get,
    path = "/v1/shares",
    params(
        ("resource_type" = Option<String>, Query),
        ("resource_id" = Option<Uuid>, Query)
    ),
    responses((status = 200, body = [ShareDto])),
    security(("bearer" = []))
)]
pub async fn list(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Query(query): Query<ShareListQuery>,
) -> Result<Json<Vec<ShareDto>>, ApiError> {
    let resource_type = query
        .resource_type
        .as_deref()
        .map(parse_resource_type)
        .transpose()?;
    let shares = state
        .services
        .list_shares
        .execute(ListSharesQuery {
            actor_id: user.user_id,
            resource_type,
            resource_id: query.resource_id,
        })
        .await?;
    Ok(Json(shares.into_iter().map(ShareDto::from_view).collect()))
}

#[utoipa::path(
    post,
    path = "/v1/shares",
    request_body = CreateShareRequest,
    responses((status = 200, body = ShareDto)),
    security(("bearer" = []))
)]
pub async fn create(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Json(body): Json<CreateShareRequest>,
) -> Result<Json<ShareDto>, ApiError> {
    let share = state
        .services
        .create_share
        .execute(CreateShareCommand {
            actor_id: user.user_id,
            resource_type: parse_resource_type(&body.resource_type)?,
            resource_id: body.resource_id,
            grantee_id: domain::UserId::from_uuid(body.grantee_id),
            permission: parse_permission(&body.permission)?,
        })
        .await?;
    Ok(Json(ShareDto::from_view(share)))
}

#[utoipa::path(
    delete,
    path = "/v1/shares/{id}",
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
        .delete_share
        .execute(user.user_id, ShareId::from_uuid(id))
        .await?;
    Ok(StatusCode::NO_CONTENT)
}
