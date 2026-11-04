use aws_config::BehaviorVersion;
use aws_sdk_s3::config::{Credentials, Region};
use aws_sdk_s3::primitives::ByteStream;
use aws_sdk_s3::types::{CompletedMultipartUpload, CompletedPart, CreateBucketConfiguration};
use aws_sdk_s3::Client;
use bytes::{Bytes, BytesMut};
use futures_util::StreamExt;
use domain::ports::ObjectStore;
use domain::{ByteRange, ByteStream as Chunks, ObjectBody};
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

/// Objects up to one part go in a single request; larger ones are sent as a multipart upload.
/// S3 needs every part but the last to be at least 5 MiB.
const PART_SIZE: usize = 8 * 1024 * 1024;

impl S3Store {
    async fn upload_parts(
        &self,
        key: &str,
        content_type: &str,
        stream: &mut Chunks,
        upload_id: &mut Option<String>,
    ) -> Result<(), DomainError> {
        let mut buf = BytesMut::with_capacity(PART_SIZE);
        let mut parts: Vec<CompletedPart> = Vec::new();
        let mut finished = false;
        while !finished {
            while buf.len() < PART_SIZE {
                match stream.next().await {
                    Some(chunk) => buf.extend_from_slice(&chunk?),
                    None => {
                        finished = true;
                        break;
                    }
                }
            }
            if finished && upload_id.is_none() {
                return self.put(key, buf.freeze(), content_type).await;
            }
            if buf.is_empty() {
                break;
            }
            let id = match upload_id {
                Some(id) => id.clone(),
                None => {
                    let created = self
                        .client
                        .create_multipart_upload()
                        .bucket(&self.bucket)
                        .key(key)
                        .content_type(content_type)
                        .send()
                        .await
                        .map_err(|e| DomainError::infra(format!("start multipart upload: {e}")))?;
                    let id = created
                        .upload_id()
                        .ok_or_else(|| DomainError::infra("multipart upload has no id"))?
                        .to_string();
                    *upload_id = Some(id.clone());
                    id
                }
            };
            let number = parts.len() as i32 + 1;
            let uploaded = self
                .client
                .upload_part()
                .bucket(&self.bucket)
                .key(key)
                .upload_id(&id)
                .part_number(number)
                .body(ByteStream::from(buf.split().freeze()))
                .send()
                .await
                .map_err(|e| DomainError::infra(format!("upload part {number}: {e}")))?;
            parts.push(
                CompletedPart::builder()
                    .set_e_tag(uploaded.e_tag().map(str::to_string))
                    .part_number(number)
                    .build(),
            );
        }
        let id = upload_id.as_deref().expect("a multipart upload has started");
        self.client
            .complete_multipart_upload()
            .bucket(&self.bucket)
            .key(key)
            .upload_id(id)
            .multipart_upload(CompletedMultipartUpload::builder().set_parts(Some(parts)).build())
            .send()
            .await
            .map_err(|e| DomainError::infra(format!("complete multipart upload: {e}")))?;
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

    async fn put_stream(
        &self,
        key: &str,
        content_type: &str,
        mut stream: Chunks,
    ) -> Result<(), DomainError> {
        let mut upload_id = None;
        let result = self.upload_parts(key, content_type, &mut stream, &mut upload_id).await;
        if result.is_err() {
            // Parts of an unfinished upload keep costing space until it is aborted.
            if let Some(id) = upload_id {
                let _ = self
                    .client
                    .abort_multipart_upload()
                    .bucket(&self.bucket)
                    .key(key)
                    .upload_id(id)
                    .send()
                    .await;
            }
        }
        result
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
