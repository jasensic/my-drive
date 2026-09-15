use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use domain::model::AuthSession;

use crate::error::ApiError;
use crate::state::AppState;

pub struct CurrentUser(pub AuthSession);

impl FromRequestParts<AppState> for CurrentUser {
    type Rejection = ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let header = parts
            .headers
            .get(axum::http::header::AUTHORIZATION)
            .and_then(|v| v.to_str().ok())
            .ok_or_else(|| ApiError(application::AppError::unauthorized("missing token")))?;
        let token = header
            .strip_prefix("Bearer ")
            .ok_or_else(|| ApiError(application::AppError::unauthorized("missing token")))?;
        let session = state
            .services
            .authenticate
            .execute(token)
            .await
            .map_err(ApiError)?;
        Ok(CurrentUser(session))
    }
}
