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
}

impl Settings {
    pub fn from_env() -> Result<Self, DomainError> {
        let memory_backend = std::env::var("MEMORY_BACKEND")
            .map(|v| v == "1" || v.eq_ignore_ascii_case("true"))
            .unwrap_or(false);
        Ok(Self {
            database_url: env_or("DATABASE_URL", "postgres://mydrive:mydrive@localhost:5432/mydrive"),
            jwt_secret: env_or("JWT_SECRET", "dev-secret-change-me"),
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
        })
    }
}

fn env_or(key: &str, default: &str) -> String {
    std::env::var(key).unwrap_or_else(|_| default.to_string())
}
