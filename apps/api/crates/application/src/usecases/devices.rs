use std::sync::Arc;

use async_trait::async_trait;
use domain::model::{Device, SyncProfile, SyncRule};
use domain::{DeviceId, MediaKind, SyncProfileId, UserId};

use crate::{AppError, Deps};

#[async_trait]
pub trait RegisterDevice: Send + Sync {
    async fn execute(&self, user_id: UserId, name: String) -> Result<Device, AppError>;
}

#[async_trait]
pub trait ListDevices: Send + Sync {
    async fn execute(&self, user_id: UserId) -> Result<Vec<Device>, AppError>;
}

#[async_trait]
pub trait UpsertSyncProfile: Send + Sync {
    async fn execute(
        &self,
        user_id: UserId,
        device_id: DeviceId,
        name: String,
        rules: Vec<SyncRule>,
    ) -> Result<SyncProfile, AppError>;
}

#[async_trait]
pub trait GetSyncProfile: Send + Sync {
    async fn execute(&self, user_id: UserId, device_id: DeviceId) -> Result<SyncProfile, AppError>;
}

pub struct RegisterDeviceService {
    deps: Arc<Deps>,
}

impl RegisterDeviceService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl RegisterDevice for RegisterDeviceService {
    async fn execute(&self, user_id: UserId, name: String) -> Result<Device, AppError> {
        let name = name.trim().to_string();
        if name.is_empty() {
            return Err(AppError::validation("device name is required"));
        }
        let now = self.deps.clock.now();
        let device = Device {
            id: DeviceId::new(),
            user_id,
            name,
            last_sync_at: None,
            created_at: now,
        };
        self.deps.devices.insert(&device).await?;
        let profile = default_profile(device.id);
        self.deps.profiles.upsert(&profile).await?;
        Ok(device)
    }
}

pub struct ListDevicesService {
    deps: Arc<Deps>,
}

impl ListDevicesService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl ListDevices for ListDevicesService {
    async fn execute(&self, user_id: UserId) -> Result<Vec<Device>, AppError> {
        Ok(self.deps.devices.list_by_user(user_id).await?)
    }
}

pub struct UpsertSyncProfileService {
    deps: Arc<Deps>,
}

impl UpsertSyncProfileService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl UpsertSyncProfile for UpsertSyncProfileService {
    async fn execute(
        &self,
        user_id: UserId,
        device_id: DeviceId,
        name: String,
        rules: Vec<SyncRule>,
    ) -> Result<SyncProfile, AppError> {
        let device = owned_device(&self.deps, user_id, device_id).await?;
        let existing = self.deps.profiles.find_by_device(device.id).await?;
        let profile = SyncProfile {
            id: existing
                .map(|p| p.id)
                .unwrap_or_else(SyncProfileId::new),
            device_id: device.id,
            name: if name.trim().is_empty() {
                "default".into()
            } else {
                name
            },
            rules,
        };
        self.deps.profiles.upsert(&profile).await?;
        Ok(profile)
    }
}

pub struct GetSyncProfileService {
    deps: Arc<Deps>,
}

impl GetSyncProfileService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl GetSyncProfile for GetSyncProfileService {
    async fn execute(&self, user_id: UserId, device_id: DeviceId) -> Result<SyncProfile, AppError> {
        let device = owned_device(&self.deps, user_id, device_id).await?;
        self.deps
            .profiles
            .find_by_device(device.id)
            .await?
            .ok_or_else(|| AppError::not_found("sync profile not found"))
    }
}

pub(crate) async fn owned_device(
    deps: &Deps,
    user_id: UserId,
    device_id: DeviceId,
) -> Result<Device, AppError> {
    let device = deps
        .devices
        .find_by_id(device_id)
        .await?
        .ok_or_else(|| AppError::not_found("device not found"))?;
    if device.user_id != user_id {
        return Err(AppError::not_found("device not found"));
    }
    Ok(device)
}

pub fn default_profile(device_id: DeviceId) -> SyncProfile {
    SyncProfile {
        id: SyncProfileId::new(),
        device_id,
        name: "default".into(),
        rules: vec![
            SyncRule {
                media_kind: MediaKind::Photo,
                max_age_days: Some(365),
                max_size_bytes: None,
                include_all: false,
            },
            SyncRule {
                media_kind: MediaKind::Video,
                max_age_days: None,
                max_size_bytes: Some(10 * 1024 * 1024),
                include_all: false,
            },
            SyncRule {
                media_kind: MediaKind::Audio,
                max_age_days: None,
                max_size_bytes: None,
                include_all: true,
            },
            SyncRule {
                media_kind: MediaKind::Other,
                max_age_days: None,
                max_size_bytes: None,
                include_all: true,
            },
        ],
    }
}
