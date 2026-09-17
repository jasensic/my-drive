use std::sync::Arc;

use async_trait::async_trait;
use bytes::Bytes;
use domain::apk::ApkIdentity;
use domain::model::AppRelease;
use domain::{AppReleaseId, UserId};
use sha2::{Digest, Sha256};

use crate::{AppError, Deps};

pub struct PublishAppReleaseCommand {
    pub actor_id: UserId,
    pub changelog: String,
    pub file_name: String,
    pub bytes: Bytes,
}

pub struct InspectApkCommand {
    pub actor_id: UserId,
    pub file_name: String,
    pub bytes: Bytes,
}

pub struct UpdateAppReleaseCommand {
    pub actor_id: UserId,
    pub id: AppReleaseId,
    pub changelog: Option<String>,
    pub file_name: Option<String>,
    pub bytes: Option<Bytes>,
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
pub trait InspectApk: Send + Sync {
    async fn execute(&self, cmd: InspectApkCommand) -> Result<ApkIdentity, AppError>;
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

#[async_trait]
pub trait UpdateAppRelease: Send + Sync {
    async fn execute(&self, cmd: UpdateAppReleaseCommand) -> Result<AppRelease, AppError>;
}

#[async_trait]
pub trait DeleteAppRelease: Send + Sync {
    async fn execute(&self, actor_id: UserId, id: AppReleaseId) -> Result<(), AppError>;
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
        require_user(&self.deps, cmd.actor_id).await?;
        let identity = identity_from_apk(&self.deps, &cmd.file_name, &cmd.bytes)?;
        if self
            .deps
            .app_releases
            .find_by_version_code(identity.version_code)
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
            version_code: identity.version_code,
            version_name: identity.version_name,
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

pub struct InspectApkService {
    deps: Arc<Deps>,
}

impl InspectApkService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl InspectApk for InspectApkService {
    async fn execute(&self, cmd: InspectApkCommand) -> Result<ApkIdentity, AppError> {
        require_user(&self.deps, cmd.actor_id).await?;
        identity_from_apk(&self.deps, &cmd.file_name, &cmd.bytes)
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

pub struct UpdateAppReleaseService {
    deps: Arc<Deps>,
}

impl UpdateAppReleaseService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl UpdateAppRelease for UpdateAppReleaseService {
    async fn execute(&self, cmd: UpdateAppReleaseCommand) -> Result<AppRelease, AppError> {
        require_user(&self.deps, cmd.actor_id).await?;
        if cmd.changelog.is_none() && cmd.bytes.is_none() {
            return Err(AppError::validation("changelog or apk is required"));
        }
        let mut release = self
            .deps
            .app_releases
            .find_by_id(cmd.id)
            .await?
            .ok_or_else(|| AppError::not_found("app release not found"))?;
        if let Some(changelog) = cmd.changelog {
            release.changelog = changelog;
        }
        if let Some(bytes) = cmd.bytes {
            let file_name = cmd.file_name.unwrap_or_else(|| "my-drive.apk".into());
            let identity = identity_from_apk(&self.deps, &file_name, &bytes)?;
            if let Some(existing) = self
                .deps
                .app_releases
                .find_by_version_code(identity.version_code)
                .await?
            {
                if existing.id != release.id {
                    return Err(AppError::conflict("version_code already published"));
                }
            }
            self.deps
                .objects
                .put(
                    &release.object_key,
                    bytes.clone(),
                    "application/vnd.android.package-archive",
                )
                .await?;
            release.version_code = identity.version_code;
            release.version_name = identity.version_name;
            release.checksum = format!("sha256:{:x}", Sha256::digest(&bytes));
            release.size = bytes.len() as u64;
        }
        self.deps.app_releases.update(&release).await?;
        Ok(release)
    }
}

pub struct DeleteAppReleaseService {
    deps: Arc<Deps>,
}

impl DeleteAppReleaseService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl DeleteAppRelease for DeleteAppReleaseService {
    async fn execute(&self, actor_id: UserId, id: AppReleaseId) -> Result<(), AppError> {
        require_user(&self.deps, actor_id).await?;
        let release = self
            .deps
            .app_releases
            .find_by_id(id)
            .await?
            .ok_or_else(|| AppError::not_found("app release not found"))?;
        self.deps.objects.delete(&release.object_key).await?;
        self.deps.app_releases.delete(id).await?;
        Ok(())
    }
}

async fn require_user(deps: &Deps, actor_id: UserId) -> Result<(), AppError> {
    deps.users
        .find_by_id(actor_id)
        .await?
        .ok_or_else(|| AppError::unauthorized("unknown user"))?;
    Ok(())
}

fn identity_from_apk(deps: &Deps, file_name: &str, bytes: &Bytes) -> Result<ApkIdentity, AppError> {
    if bytes.is_empty() {
        return Err(AppError::validation("apk is empty"));
    }
    if !looks_like_apk(file_name) {
        return Err(AppError::validation("file must be an Android APK"));
    }
    Ok(deps.apk_inspector.inspect(bytes)?)
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
