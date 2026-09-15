use aws_config::BehaviorVersion;
use aws_sdk_s3::config::{Credentials, Region};
use aws_sdk_s3::primitives::ByteStream;
use aws_sdk_s3::types::CreateBucketConfiguration;
use aws_sdk_s3::Client;
use bytes::Bytes;
use domain::ports::ObjectStore;
use domain::DomainError;

use crate::config::Settings;

pub struct S3Store {
    client: Client,
    bucket: String,
}

impl S3Store {
    pub async fn connect(settings: &Settings) -> Result<Self, DomainError> {
        let creds = Credentials::new(
            &settings.minio_access_key,
            &settings.minio_secret_key,
            None,
            None,
            "mydrive",
        );
        let cfg = aws_config::defaults(BehaviorVersion::latest())
            .region(Region::new(settings.minio_region.clone()))
            .endpoint_url(&settings.minio_endpoint)
            .credentials_provider(creds)
            .load()
            .await;
        let s3_config = aws_sdk_s3::config::Builder::from(&cfg)
            .force_path_style(true)
            .build();
        let client = Client::from_conf(s3_config);
        let store = Self {
            client,
            bucket: settings.minio_bucket.clone(),
        };
        store.ensure_bucket().await?;
        Ok(store)
    }

    async fn ensure_bucket(&self) -> Result<(), DomainError> {
        let exists = self
            .client
            .head_bucket()
            .bucket(&self.bucket)
            .send()
            .await
            .is_ok();
        if exists {
            return Ok(());
        }
        let mut req = self.client.create_bucket().bucket(&self.bucket);
        if self.client.config().region().map(|r| r.as_ref()) != Some("us-east-1") {
            if let Some(region) = self.client.config().region() {
                req = req.create_bucket_configuration(
                    CreateBucketConfiguration::builder()
                        .location_constraint(
                            aws_sdk_s3::types::BucketLocationConstraint::from(region.as_ref()),
                        )
                        .build(),
                );
            }
        }
        req.send()
            .await
            .map_err(|e| DomainError::infra(format!("create bucket: {e}")))?;
        Ok(())
    }
}

#[async_trait::async_trait]
impl ObjectStore for S3Store {
    async fn put(&self, key: &str, bytes: Bytes, content_type: &str) -> Result<(), DomainError> {
        self.client
            .put_object()
            .bucket(&self.bucket)
            .key(key)
            .content_type(content_type)
            .body(ByteStream::from(bytes.to_vec()))
            .send()
            .await
            .map_err(|e| DomainError::infra(format!("put object: {e}")))?;
        Ok(())
    }

    async fn get(&self, key: &str) -> Result<Bytes, DomainError> {
        let out = self
            .client
            .get_object()
            .bucket(&self.bucket)
            .key(key)
            .send()
            .await
            .map_err(|e| DomainError::not_found(format!("object: {e}")))?;
        let data = out
            .body
            .collect()
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(Bytes::from(data.into_bytes().to_vec()))
    }

    async fn get_range(
        &self,
        key: &str,
        start: u64,
        end: Option<u64>,
    ) -> Result<(Bytes, u64), DomainError> {
        let range = match end {
            Some(end) => format!("bytes={start}-{end}"),
            None => format!("bytes={start}-"),
        };
        let out = self
            .client
            .get_object()
            .bucket(&self.bucket)
            .key(key)
            .range(range)
            .send()
            .await
            .map_err(|e| DomainError::not_found(format!("object: {e}")))?;
        let total = out.content_range().and_then(|cr| {
            cr.rsplit('/').next()?.parse::<u64>().ok()
        }).unwrap_or_else(|| out.content_length().unwrap_or(0) as u64);
        let data = out
            .body
            .collect()
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok((Bytes::from(data.into_bytes().to_vec()), total))
    }
}
