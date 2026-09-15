use std::sync::Arc;

use application::Services;

#[derive(Clone)]
pub struct AppState {
    pub public_url: String,
    pub services: Arc<Services>,
}
