use axum::Json;

use crate::dto::HealthResponse;

#[utoipa::path(
    get,
    path = "/health",
    responses((status = 200, body = HealthResponse))
)]
pub async fn health() -> Json<HealthResponse> {
    Json(HealthResponse {
        status: "ok".into(),
    })
}
