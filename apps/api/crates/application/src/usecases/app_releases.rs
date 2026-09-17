use std::sync::Arc;

use async_trait::async_trait;
use bytes::Bytes;
use domain::model::AppRelease;
use domain::{AppReleaseId, UserId};
use sha2::{Digest, Sha256};

use crate::{AppError, Deps};

pub struct PublishAppReleaseCommand {
    pub actor_id: UserId,
    pub version_code: i32,
    pub version_name: String,
    pub changelog: String,
    pub file_name: String,
    pub bytes: Bytes,
}

pub struct AppReleaseApk {
    pub mime: String,
    pub file_name: String,
    pub data: Bytes,
}

#[async_trait]
pub trait PublishAppRelease: Send + Sync {
    async fn execute(&self, cmd: PublishAppReleaseCommand) -> Result<AppRelease, AppError>;
}

#[async_trait]
pub trait ListAppReleases: Send + Sync {
    async fn execute(&self, actor_id: UserId) -> Result<Vec<AppRelease>, AppError>;
}

#[async_trait]
pub trait GetLatestAppRelease: Send + Sync {
    async fn execute(&self, actor_id: UserId) -> Result<AppRelease, AppError>;
}

#[async_trait]
pub trait GetAppRelease: Send + Sync {
    async fn execute(&self, actor_id: UserId, id: AppReleaseId) -> Result<AppRelease, AppError>;
}

#[async_trait]
pub trait GetAppReleaseApk: Send + Sync {
    async fn execute(&self, actor_id: UserId, id: AppReleaseId) -> Result<AppReleaseApk, AppError>;
}

pub struct PublishAppReleaseService {
    deps: Arc<Deps>,
}

impl PublishAppReleaseService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl PublishAppRelease for PublishAppReleaseService {
    async fn execute(&self, cmd: PublishAppReleaseCommand) -> Result<AppRelease, AppError> {
        let _ = self
            .deps
            .users
            .find_by_id(cmd.actor_id)
            .await?
            .ok_or_else(|| AppError::unauthorized("unknown user"))?;
        if cmd.version_code <= 0 {
            return Err(AppError::validation("version_code must be greater than 0"));
        }
        let version_name = cmd.version_name.trim().to_string();
        if version_name.is_empty() {
            return Err(AppError::validation("version_name is required"));
        }
        if cmd.bytes.is_empty() {
            return Err(AppError::validation("apk is empty"));
        }
        if !looks_like_apk(&cmd.file_name) {
            return Err(AppError::validation("file must be an Android APK"));
        }
        if self
            .deps
            .app_releases
            .find_by_version_code(cmd.version_code)
            .await?
            .is_some()
        {
            return Err(AppError::conflict("version_code already published"));
        }

        let id = AppReleaseId::new();
        let checksum = format!("sha256:{:x}", Sha256::digest(&cmd.bytes));
        let object_key = format!("app-releases/{id}.apk");
        self.deps
            .objects
            .put(
                &object_key,
                cmd.bytes.clone(),
                "application/vnd.android.package-archive",
            )
            .await?;
        let release = AppRelease {
            id,
            version_code: cmd.version_code,
            version_name,
            changelog: cmd.changelog,
            object_key,
            checksum,
            size: cmd.bytes.len() as u64,
            published_at: self.deps.clock.now(),
        };
        self.deps.app_releases.insert(&release).await?;
        Ok(release)
    }
}

pub struct ListAppReleasesService {
    deps: Arc<Deps>,
}

impl ListAppReleasesService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl ListAppReleases for ListAppReleasesService {
    async fn execute(&self, actor_id: UserId) -> Result<Vec<AppRelease>, AppError> {
        require_user(&self.deps, actor_id).await?;
        Ok(self.deps.app_releases.list().await?)
    }
}

pub struct GetLatestAppReleaseService {
    deps: Arc<Deps>,
}

impl GetLatestAppReleaseService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl GetLatestAppRelease for GetLatestAppReleaseService {
    async fn execute(&self, actor_id: UserId) -> Result<AppRelease, AppError> {
        require_user(&self.deps, actor_id).await?;
        self.deps
            .app_releases
            .latest()
            .await?
            .ok_or_else(|| AppError::not_found("no android app release published"))
    }
}

pub struct GetAppReleaseService {
    deps: Arc<Deps>,
}

impl GetAppReleaseService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl GetAppRelease for GetAppReleaseService {
    async fn execute(&self, actor_id: UserId, id: AppReleaseId) -> Result<AppRelease, AppError> {
        require_user(&self.deps, actor_id).await?;
        self.deps
            .app_releases
            .find_by_id(id)
            .await?
            .ok_or_else(|| AppError::not_found("app release not found"))
    }
}

pub struct GetAppReleaseApkService {
    deps: Arc<Deps>,
}

impl GetAppReleaseApkService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl GetAppReleaseApk for GetAppReleaseApkService {
    async fn execute(&self, actor_id: UserId, id: AppReleaseId) -> Result<AppReleaseApk, AppError> {
        require_user(&self.deps, actor_id).await?;
        let release = self
            .deps
            .app_releases
            .find_by_id(id)
            .await?
            .ok_or_else(|| AppError::not_found("app release not found"))?;
        let data = self.deps.objects.get(&release.object_key).await?;
        Ok(AppReleaseApk {
            mime: "application/vnd.android.package-archive".into(),
            file_name: format!("my-drive-{}.apk", release.version_name),
            data,
        })
    }
}

async fn require_user(deps: &Deps, actor_id: UserId) -> Result<(), AppError> {
    deps.users
        .find_by_id(actor_id)
        .await?
        .ok_or_else(|| AppError::unauthorized("unknown user"))?;
    Ok(())
}

fn looks_like_apk(file_name: &str) -> bool {
    file_name.to_ascii_lowercase().ends_with(".apk")
}

#[cfg(test)]
mod tests {
    use super::looks_like_apk;

    #[test]
    fn accepts_apk_file_names() {
        assert!(looks_like_apk("my-drive-1.2.0.apk"));
        assert!(looks_like_apk("APP.APK"));
        assert!(!looks_like_apk("notes.txt"));
        assert!(!looks_like_apk(""));
    }
}
