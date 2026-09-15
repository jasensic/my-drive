use domain::DomainError;
use mdns_sd::{ServiceDaemon, ServiceInfo};
use tracing::info;

use crate::config::Settings;

pub fn advertise(settings: &Settings) -> Result<ServiceDaemon, DomainError> {
    let daemon = ServiceDaemon::new().map_err(|e| DomainError::infra(e.to_string()))?;
    let service_type = "_mydrive._tcp.local.";
    let instance = settings.mdns_service_name.clone();
    let host = hostname();
    let properties = [("path", "/v1"), ("api", "/v1")];
    let info = ServiceInfo::new(
        service_type,
        &instance,
        &format!("{host}.local."),
        (),
        settings.api_port,
        &properties[..],
    )
    .map_err(|e| DomainError::infra(e.to_string()))?
    .enable_addr_auto();
    daemon
        .register(info)
        .map_err(|e| DomainError::infra(e.to_string()))?;
    info!(service_type, instance, port = settings.api_port, "mDNS advertised");
    Ok(daemon)
}

fn hostname() -> String {
    std::env::var("HOSTNAME").unwrap_or_else(|_| "my-drive".into())
}
