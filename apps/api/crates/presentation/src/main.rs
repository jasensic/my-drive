use std::net::SocketAddr;
use std::sync::Arc;

use application::Services;
use infrastructure::config::Settings;
use tracing_subscriber::EnvFilter;

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    let _ = dotenvy::dotenv();
    tracing_subscriber::fmt()
        .with_env_filter(EnvFilter::from_default_env().add_directive("mydrive_api=info".parse()?))
        .init();

    let settings = Settings::from_env()?;
    let deps = infrastructure::build_deps(&settings).await?;
    let services = Arc::new(Services::new(deps));
    let app = mydrive_api::router(mydrive_api::state_from_services(
        settings.api_public_url.clone(),
        services,
    ));

    if settings.mdns_enable {
        match infrastructure::mdns::advertise(&settings) {
            Ok(_daemon) => {
                // Keep the daemon alive for the process lifetime.
                std::mem::forget(_daemon);
            }
            Err(err) => tracing::warn!("mDNS advertisement skipped: {err}"),
        }
    }

    let addr = SocketAddr::from((
        settings.api_host.parse::<std::net::IpAddr>()?,
        settings.api_port,
    ));
    tracing::info!("listening on {addr}");
    let listener = tokio::net::TcpListener::bind(addr).await?;
    axum::serve(listener, app).await?;
    Ok(())
}
