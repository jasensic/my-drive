use application::AppError;
use axum::http::StatusCode;
use axum::response::{IntoResponse, Response};
use axum::Json;
use domain::DomainError;
use serde::Serialize;
use utoipa::ToSchema;

#[derive(Serialize, ToSchema)]
pub struct ErrorBody {
    pub error: String,
}

pub struct ApiError(pub AppError);

impl From<AppError> for ApiError {
    fn from(value: AppError) -> Self {
        Self(value)
    }
}

impl IntoResponse for ApiError {
    fn into_response(self) -> Response {
        let (status, msg) = match &self.0 {
            AppError::Domain(DomainError::Conflict(m)) => (StatusCode::CONFLICT, m.clone()),
            AppError::Domain(DomainError::NotFound(m)) => (StatusCode::NOT_FOUND, m.clone()),
            AppError::Domain(DomainError::Validation(m)) => (StatusCode::BAD_REQUEST, m.clone()),
            AppError::Domain(DomainError::Unauthorized(m)) => (StatusCode::UNAUTHORIZED, m.clone()),
            AppError::Domain(DomainError::Forbidden(m)) => (StatusCode::FORBIDDEN, m.clone()),
            AppError::Domain(DomainError::Infrastructure(m)) => {
                tracing::error!("infra error: {m}");
                (StatusCode::INTERNAL_SERVER_ERROR, "internal error".into())
            }
        };
        (status, Json(ErrorBody { error: msg })).into_response()
    }
}
