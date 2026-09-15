use axum::extract::State;
use axum::Json;

use crate::dto::{AuthResponse, CredentialsRequest, StatusResponse, UserDto};
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
