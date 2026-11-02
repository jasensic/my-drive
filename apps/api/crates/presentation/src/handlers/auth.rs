use axum::extract::State;
use axum::Json;

use domain::DeviceId;

use crate::dto::{AuthResponse, CredentialsRequest, RefreshRequest, StatusResponse, UserDto};
use crate::error::ApiError;
use crate::extract::CurrentUser;
use crate::state::AppState;

#[utoipa::path(get, path = "/v1/status", responses((status = 200, body = StatusResponse)))]
pub async fn status(State(state): State<AppState>) -> Result<Json<StatusResponse>, ApiError> {
    let setup_required = state.services.check_setup.execute().await?;
    Ok(Json(StatusResponse { setup_required }))
}

#[utoipa::path(post, path = "/v1/setup", request_body = CredentialsRequest, responses((status = 200, body = AuthResponse)))]
pub async fn setup(
    State(state): State<AppState>,
    Json(body): Json<CredentialsRequest>,
) -> Result<Json<AuthResponse>, ApiError> {
    let result = state
        .services
        .setup_admin
        .execute(body.username, body.password)
        .await?;
    Ok(Json(AuthResponse {
        token: result.token,
        user: result.user.into(),
    }))
}

#[utoipa::path(post, path = "/v1/login", request_body = CredentialsRequest, responses((status = 200, body = AuthResponse)))]
pub async fn login(
    State(state): State<AppState>,
    Json(body): Json<CredentialsRequest>,
) -> Result<Json<AuthResponse>, ApiError> {
    let result = state
        .services
        .login
        .execute(body.username, body.password)
        .await?;
    Ok(Json(AuthResponse {
        token: result.token,
        user: result.user.into(),
    }))
}

#[utoipa::path(get, path = "/v1/me", responses((status = 200, body = UserDto)), security(("bearer" = [])))]
pub async fn me(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
) -> Result<Json<UserDto>, ApiError> {
    let user = state.services.current_user.execute(user.user_id).await?;
    Ok(Json(user.into()))
}

/// Admin only. The new user signs in with these credentials; no token is returned.
#[utoipa::path(post, path = "/v1/users", request_body = CredentialsRequest, responses((status = 200, body = UserDto), (status = 403)), security(("bearer" = [])))]
pub async fn create_user(
    State(state): State<AppState>,
    CurrentUser(user): CurrentUser,
    Json(body): Json<CredentialsRequest>,
) -> Result<Json<UserDto>, ApiError> {
    let created = state
        .services
        .create_user
        .execute(user.user_id, body.username, body.password)
        .await?;
    Ok(Json(created.into()))
}

/// Trade a device refresh token (from `POST /v1/devices`) for a new access token.
#[utoipa::path(post, path = "/v1/auth/refresh", request_body = RefreshRequest, responses((status = 200, body = AuthResponse), (status = 401)))]
pub async fn refresh(
    State(state): State<AppState>,
    Json(body): Json<RefreshRequest>,
) -> Result<Json<AuthResponse>, ApiError> {
    let result = state
        .services
        .refresh_session
        .execute(DeviceId::from_uuid(body.device_id), &body.device_token)
        .await?;
    Ok(Json(AuthResponse {
        token: result.token,
        user: result.user.into(),
    }))
}

#[utoipa::path(get, path = "/v1/users", responses((status = 200, body = [UserDto])), security(("bearer" = [])))]
pub async fn list_users(
    State(state): State<AppState>,
    CurrentUser(_user): CurrentUser,
) -> Result<Json<Vec<UserDto>>, ApiError> {
    let users = state.services.list_users.execute().await?;
    Ok(Json(users.into_iter().map(Into::into).collect()))
}
