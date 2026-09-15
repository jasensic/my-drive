mod dto;
mod error;
mod extract;
mod handlers;
mod openapi;
mod state;

use std::sync::Arc;

use application::Services;
use axum::extract::DefaultBodyLimit;
use axum::http::{header, Method};
use axum::routing::{get, post};
use axum::Router;
use tower_http::cors::{Any, CorsLayer};
use tower_http::trace::TraceLayer;
use utoipa::OpenApi;
use utoipa_swagger_ui::SwaggerUi;

use crate::handlers::{albums, auth, devices, files, health, sync};
use crate::openapi::ApiDoc;
use crate::state::AppState;

pub fn router(state: AppState) -> Router {
    let cors = CorsLayer::new()
        .allow_origin(Any)
        .allow_methods([
            Method::GET,
            Method::POST,
            Method::PUT,
            Method::PATCH,
            Method::DELETE,
            Method::OPTIONS,
        ])
        .allow_headers([header::AUTHORIZATION, header::CONTENT_TYPE, header::RANGE]);

    Router::new()
        .merge(SwaggerUi::new("/api-docs").url("/api-docs/openapi.json", ApiDoc::openapi()))
        .route("/health", get(health::health))
        .route("/v1/status", get(auth::status))
        .route("/v1/setup", post(auth::setup))
        .route("/v1/login", post(auth::login))
        .route("/v1/me", get(auth::me))
        .route("/v1/albums", get(albums::list).post(albums::create))
        .route("/v1/files", get(files::list).post(files::upload))
        .route("/v1/files/{id}", get(files::get).patch(files::assign))
        .route("/v1/files/{id}/content", get(files::content))
        .route("/v1/files/{id}/thumbnail", get(files::thumbnail))
        .route("/v1/devices", get(devices::list).post(devices::register))
        .route(
            "/v1/devices/{id}/sync-profile",
            get(devices::get_profile).put(devices::put_profile),
        )
        .route("/v1/sync/manifest", post(sync::manifest))
        .layer(DefaultBodyLimit::max(1024 * 1024 * 512))
        .layer(cors)
        .layer(TraceLayer::new_for_http())
        .with_state(state)
}

pub fn state_from_services(public_url: String, services: Arc<Services>) -> AppState {
    AppState {
        public_url,
        services,
    }
}
