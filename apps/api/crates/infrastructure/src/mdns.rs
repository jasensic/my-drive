use domain::sync::public_base_url_for_discovery;
use domain::DomainError;
use mdns_sd::{ServiceDaemon, ServiceInfo};
use tracing::info;

use crate::config::Settings;

pub fn advertise(settings: &Settings) -> Result<ServiceDaemon, DomainError> {
    let daemon = ServiceDaemon::new().map_err(|e| DomainError::infra(e.to_string()))?;
    let service_type = "_mydrive._tcp.local.";
    let instance = settings.mdns_service_name.clone();
    let host = hostname();
    let host_name = format!("{host}.local.");
    // The first mDNS packet has to carry a real LAN address. `enable_addr_auto` fills
    // addresses a moment later, and Android often caches that empty first response.
    let lan_ip = primary_lan_ipv4();
    let lan_origin = lan_ip.map(|ip| format!("http://{ip}:{}", settings.api_port));
    let advertised = public_base_url_for_discovery(&settings.api_public_url).or(lan_origin);
    let path = "/v1";
    let mut properties = vec![("path", path), ("api", path)];
    let logged_url = advertised.clone().unwrap_or_default();
    if let Some(url) = advertised.as_deref() {
        properties.push(("url", url));
    }
    let info = match lan_ip {
        Some(ip) => ServiceInfo::new(
            service_type,
            &instance,
            &host_name,
            std::net::IpAddr::V4(ip),
            settings.api_port,
            &properties[..],
        ),
        None => ServiceInfo::new(
            service_type,
            &instance,
            &host_name,
            (),
            settings.api_port,
            &properties[..],
        ),
    }
    .map_err(|e| DomainError::infra(e.to_string()))?
    .enable_addr_auto();
    daemon
        .register(info)
        .map_err(|e| DomainError::infra(e.to_string()))?;
    info!(
        service_type,
        instance,
        port = settings.api_port,
        lan_ip = lan_ip.map(|ip| ip.to_string()).unwrap_or_default(),
        url = logged_url,
        "mDNS advertised"
    );
    Ok(daemon)
}

fn hostname() -> String {
    std::env::var("HOSTNAME").unwrap_or_else(|_| "my-drive".into())
}

/// Source address the kernel would use to reach the internet: the LAN address phones can route to.
fn primary_lan_ipv4() -> Option<std::net::Ipv4Addr> {
    let socket = std::net::UdpSocket::bind("0.0.0.0:0").ok()?;
    socket.connect("8.8.8.8:9").ok()?;
    match socket.local_addr().ok()?.ip() {
        std::net::IpAddr::V4(ip) if is_advertisable_lan_v4(ip) => Some(ip),
        _ => None,
    }
}

fn is_advertisable_lan_v4(ip: std::net::Ipv4Addr) -> bool {
    let [a, b, _, _] = ip.octets();
    if a == 0 || a == 127 || a >= 224 {
        return false;
    }
    if a == 169 && b == 254 {
        return false;
    }
    // Default Docker bridges are not reachable from a phone on the LAN.
    if a == 172 && (b == 17 || b == 18) {
        return false;
    }
    true
}

#[cfg(test)]
mod tests {
    use super::is_advertisable_lan_v4;
    use std::net::Ipv4Addr;

    #[test]
    fn lan_address_skips_loopback_link_local_and_docker() {
        assert!(!is_advertisable_lan_v4(Ipv4Addr::new(127, 0, 0, 1)));
        assert!(!is_advertisable_lan_v4(Ipv4Addr::new(169, 254, 1, 1)));
        assert!(!is_advertisable_lan_v4(Ipv4Addr::new(172, 17, 0, 1)));
        assert!(is_advertisable_lan_v4(Ipv4Addr::new(192, 168, 1, 20)));
        assert!(is_advertisable_lan_v4(Ipv4Addr::new(10, 0, 0, 8)));
    }
}
