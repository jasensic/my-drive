use aws_config::BehaviorVersion;
use aws_sdk_s3::config::{Credentials, Region};
use aws_sdk_s3::primitives::ByteStream;
use aws_sdk_s3::types::CreateBucketConfiguration;
use aws_sdk_s3::Client;
use bytes::Bytes;
use domain::ports::ObjectStore;
use domain::{ByteRange, ObjectBody};
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

    async fn open(&self, key: &str, range: Option<ByteRange>) -> Result<ObjectBody, DomainError> {
        let mut req = self.client.get_object().bucket(&self.bucket).key(key);
        if let Some(range) = range {
            req = req.range(match range.end {
                Some(end) => format!("bytes={}-{}", range.start, end),
                None => format!("bytes={}-", range.start),
            });
        }
        let out = req.send().await.map_err(|err| match err.as_service_error() {
            Some(service) if service.is_no_such_key() => DomainError::not_found("object not found"),
            _ => DomainError::infra(format!("get object: {err}")),
        })?;
        let len = out.content_length().unwrap_or(0).max(0) as u64;
        let total = match range {
            // "bytes START-END/TOTAL"
            Some(_) => out
                .content_range()
                .and_then(|value| value.rsplit('/').next()?.parse::<u64>().ok())
                .unwrap_or(len),
            None => len,
        };
        let start = range.map_or(0, |range| range.start);
        // A failed read ends the stream after reporting the error; the client sees a short body.
        let stream = futures_util::stream::unfold(Some(out.body), |body| async move {
            let mut body = body?;
            match body.try_next().await {
                Ok(Some(chunk)) => Some((Ok(chunk), Some(body))),
                Ok(None) => None,
                Err(err) => Some((Err(DomainError::infra(format!("read object: {err}"))), None)),
            }
        });
        Ok(ObjectBody {
            total,
            start,
            len,
            stream: Box::pin(stream),
        })
    }

    async fn delete(&self, key: &str) -> Result<(), DomainError> {
        self.client
            .delete_object()
            .bucket(&self.bucket)
            .key(key)
            .send()
            .await
            .map_err(|e| DomainError::infra(format!("delete object: {e}")))?;
        Ok(())
    }
}
