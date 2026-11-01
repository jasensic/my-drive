use domain::DomainError;

#[derive(Debug, Clone)]
pub struct Settings {
    pub database_url: String,
    pub jwt_secret: String,
    pub api_host: String,
    pub api_port: u16,
    pub api_public_url: String,
    pub minio_endpoint: String,
    pub minio_bucket: String,
    pub minio_region: String,
    pub minio_access_key: String,
    pub minio_secret_key: String,
    pub mdns_enable: bool,
    pub mdns_service_name: String,
    pub memory_backend: bool,
    pub musicdl_url: String,
}

impl Settings {
    pub fn from_env() -> Result<Self, DomainError> {
        let memory_backend = std::env::var("MEMORY_BACKEND")
            .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
            .unwrap_or(false);
        Ok(Self {
            database_url: env_or("DATABASE_URL", "postgres://mydrive:mydrive@localhost:5432/mydrive"),
            jwt_secret: jwt_secret(std::env::var("JWT_SECRET").ok(), memory_backend)?,
            api_host: env_or("API_HOST", "0.0.0.0"),
            api_port: env_or("API_PORT", "8080")
                .parse()
                .map_err(|_| DomainError::infra("invalid API_PORT"))?,
            api_public_url: env_or("API_PUBLIC_URL", "http://localhost:8080"),
            minio_endpoint: env_or("MINIO_ENDPOINT", "http://localhost:9000"),
            minio_bucket: env_or("MINIO_BUCKET", "mydrive"),
            minio_region: env_or("MINIO_REGION", "us-east-1"),
            minio_access_key: env_or("MINIO_ROOT_USER", "minio"),
            minio_secret_key: env_or("MINIO_ROOT_PASSWORD", "minio12345"),
            mdns_enable: env_or("MDNS_ENABLE", "true") != "false",
            mdns_service_name: env_or("MDNS_SERVICE_NAME", "my-drive"),
            memory_backend,
            musicdl_url: env_or("MUSICDL_URL", "http://127.0.0.1:8090"),
        })
    }
}

fn env_or(key: &str, default: &str) -> String {
    std::env::var(key).unwrap_or_else(|_| default.to_string())
}

const MIN_JWT_SECRET_LEN: usize = 32;
/// Values shipped in `.env.example` (current and older). Anyone on the LAN could forge tokens with them.
const PLACEHOLDER_JWT_SECRETS: [&str; 2] = [
    "change-me-please-use-a-long-random-string",
    "change-me-in-production-please-use-a-long-random-string",
];

/// The NAS refuses to start with a missing, short, or placeholder secret.
/// The in-memory backend (tests, CI, quick local runs) keeps a fixed dev fallback.
fn jwt_secret(value: Option<String>, memory_backend: bool) -> Result<String, DomainError> {
    let value = value.map(|v| v.trim().to_string()).filter(|v| !v.is_empty());
    match value {
        None if memory_backend => Ok("dev-secret-change-me".into()),
        Some(secret) if memory_backend => Ok(secret),
        Some(secret)
            if secret.len() >= MIN_JWT_SECRET_LEN
                && !PLACEHOLDER_JWT_SECRETS.contains(&secret.as_str()) =>
        {
            Ok(secret)
        }
        _ => Err(DomainError::infra(format!(
            "JWT_SECRET must be a random string of at least {MIN_JWT_SECRET_LEN} characters \
             (for example `openssl rand -hex 32`); set it in .env"
        ))),
    }
}

#[cfg(test)]
mod tests {
    use super::jwt_secret;

    #[test]
    fn nas_requires_a_real_jwt_secret() {
        assert!(jwt_secret(None, false).is_err());
        assert!(jwt_secret(Some("short".into()), false).is_err());
        assert!(jwt_secret(Some("change-me-please-use-a-long-random-string".into()), false).is_err());
        let good = "f".repeat(64);
        assert_eq!(jwt_secret(Some(good.clone()), false).unwrap(), good);
    }

    #[test]
    fn memory_backend_keeps_a_dev_fallback() {
        assert_eq!(jwt_secret(None, true).unwrap(), "dev-secret-change-me");
        assert_eq!(jwt_secret(Some("ci-secret".into()), true).unwrap(), "ci-secret");
    }
}
