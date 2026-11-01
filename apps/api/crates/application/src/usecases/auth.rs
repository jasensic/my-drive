use std::sync::Arc;

use async_trait::async_trait;
use domain::model::{AuthSession, User, UserSummary};
use domain::{DeviceId, UserId};

use crate::device_tokens::{hash_device_token, require_admin};
use crate::{AppError, Deps};

#[derive(Debug, Clone)]
pub struct SetupResult {
    pub user: User,
    pub token: String,
}

#[async_trait]
pub trait CheckSetup: Send + Sync {
    async fn execute(&self) -> Result<bool, AppError>;
}

#[async_trait]
pub trait SetupAdmin: Send + Sync {
    async fn execute(&self, username: String, password: String) -> Result<SetupResult, AppError>;
}

#[async_trait]
pub trait Login: Send + Sync {
    async fn execute(&self, username: String, password: String) -> Result<SetupResult, AppError>;
}

/// Only an admin adds people to the NAS. There is no self sign-up.
#[async_trait]
pub trait CreateUser: Send + Sync {
    async fn execute(
        &self,
        actor_id: UserId,
        username: String,
        password: String,
    ) -> Result<UserSummary, AppError>;
}

#[async_trait]
pub trait ListUsers: Send + Sync {
    async fn execute(&self) -> Result<Vec<UserSummary>, AppError>;
}

#[async_trait]
pub trait Authenticate: Send + Sync {
    async fn execute(&self, token: &str) -> Result<AuthSession, AppError>;
}

#[async_trait]
pub trait GetCurrentUser: Send + Sync {
    async fn execute(&self, user_id: UserId) -> Result<User, AppError>;
}

/// Trade a device refresh token for a fresh access token, so phones stay signed in.
#[async_trait]
pub trait RefreshSession: Send + Sync {
    async fn execute(&self, device_id: DeviceId, device_token: &str) -> Result<SetupResult, AppError>;
}

pub struct CheckSetupService {
    deps: Arc<Deps>,
}

impl CheckSetupService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl CheckSetup for CheckSetupService {
    async fn execute(&self) -> Result<bool, AppError> {
        Ok(self.deps.users.count().await? == 0)
    }
}

pub struct SetupAdminService {
    deps: Arc<Deps>,
}

impl SetupAdminService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

fn validate_credentials(username: &str, password: &str) -> Result<(), AppError> {
    if username.is_empty() || password.len() < 8 {
        return Err(AppError::validation(
            "username is required and password must be at least 8 characters",
        ));
    }
    Ok(())
}

#[async_trait]
impl SetupAdmin for SetupAdminService {
    async fn execute(&self, username: String, password: String) -> Result<SetupResult, AppError> {
        let username = username.trim().to_string();
        validate_credentials(&username, &password)?;
        if self.deps.users.count().await? > 0 {
            return Err(AppError::conflict("setup has already been completed"));
        }
        let now = self.deps.clock.now();
        let user = User {
            id: UserId::new(),
            username,
            password_hash: self.deps.hasher.hash(&password)?,
            is_admin: true,
            created_at: now,
        };
        self.deps.users.insert(&user).await?;
        let token = self.deps.tokens.issue(&user)?;
        Ok(SetupResult { user, token })
    }
}

pub struct LoginService {
    deps: Arc<Deps>,
}

impl LoginService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl Login for LoginService {
    async fn execute(&self, username: String, password: String) -> Result<SetupResult, AppError> {
        let Some(user) = self.deps.users.find_by_username(username.trim()).await? else {
            return Err(AppError::unauthorized("invalid credentials"));
        };
        if !self.deps.hasher.verify(&password, &user.password_hash)? {
            return Err(AppError::unauthorized("invalid credentials"));
        }
        let token = self.deps.tokens.issue(&user)?;
        Ok(SetupResult { user, token })
    }
}

pub struct AuthenticateService {
    deps: Arc<Deps>,
}

impl AuthenticateService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl Authenticate for AuthenticateService {
    async fn execute(&self, token: &str) -> Result<AuthSession, AppError> {
        Ok(self.deps.tokens.verify(token)?)
    }
}

pub struct GetCurrentUserService {
    deps: Arc<Deps>,
}

impl GetCurrentUserService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl GetCurrentUser for GetCurrentUserService {
    async fn execute(&self, user_id: UserId) -> Result<User, AppError> {
        self.deps
            .users
            .find_by_id(user_id)
            .await?
            .ok_or_else(|| AppError::not_found("user not found"))
    }
}

pub struct CreateUserService {
    deps: Arc<Deps>,
}

impl CreateUserService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl CreateUser for CreateUserService {
    async fn execute(
        &self,
        actor_id: UserId,
        username: String,
        password: String,
    ) -> Result<UserSummary, AppError> {
        require_admin(&self.deps, actor_id).await?;
        let username = username.trim().to_string();
        validate_credentials(&username, &password)?;
        if self.deps.users.find_by_username(&username).await?.is_some() {
            return Err(AppError::conflict("username already exists"));
        }
        let user = User {
            id: UserId::new(),
            username,
            password_hash: self.deps.hasher.hash(&password)?,
            is_admin: false,
            created_at: self.deps.clock.now(),
        };
        self.deps.users.insert(&user).await?;
        Ok(UserSummary {
            id: user.id,
            username: user.username,
            is_admin: user.is_admin,
        })
    }
}

pub struct ListUsersService {
    deps: Arc<Deps>,
}

impl ListUsersService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl ListUsers for ListUsersService {
    async fn execute(&self) -> Result<Vec<UserSummary>, AppError> {
        Ok(self.deps.users.list_summaries().await?)
    }
}

pub struct RefreshSessionService {
    deps: Arc<Deps>,
}

impl RefreshSessionService {
    pub fn new(deps: Arc<Deps>) -> Self {
        Self { deps }
    }
}

#[async_trait]
impl RefreshSession for RefreshSessionService {
    async fn execute(&self, device_id: DeviceId, device_token: &str) -> Result<SetupResult, AppError> {
        let rejected = || AppError::unauthorized("device is not signed in");
        if device_token.trim().is_empty() {
            return Err(rejected());
        }
        let device = self.deps.devices.find_by_id(device_id).await?.ok_or_else(rejected)?;
        let stored = self.deps.devices.token_hash(device.id).await?.ok_or_else(rejected)?;
        if stored != hash_device_token(device_token.trim()) {
            return Err(rejected());
        }
        let user = self
            .deps
            .users
            .find_by_id(device.user_id)
            .await?
            .ok_or_else(rejected)?;
        let token = self.deps.tokens.issue(&user)?;
        Ok(SetupResult { user, token })
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::test_support::{deps_from, TestMem};
    use crate::usecases::devices::{IssueDeviceToken, IssueDeviceTokenService, RegisterDevice, RegisterDeviceService};

    #[tokio::test]
    async fn setup_creates_an_admin_who_alone_can_create_users() {
        let mem = TestMem::new();
        let deps = deps_from(mem.clone());
        let admin = SetupAdminService::new(deps.clone())
            .execute("admin".into(), "password1".into())
            .await
            .unwrap();
        assert!(admin.user.is_admin);

        let created = CreateUserService::new(deps.clone())
            .execute(admin.user.id, "friend".into(), "password2".into())
            .await
            .unwrap();
        assert!(!created.is_admin);

        let err = CreateUserService::new(deps)
            .execute(created.id, "stranger".into(), "password3".into())
            .await
            .unwrap_err();
        assert!(err.to_string().contains("only an admin"));
    }

    #[tokio::test]
    async fn refresh_accepts_the_current_device_token_only() {
        let mem = TestMem::new();
        let user = mem.add_user("owner").await;
        let deps = deps_from(mem);
        let registered = RegisterDeviceService::new(deps.clone())
            .execute(user, "Phone".into())
            .await
            .unwrap();
        let refresh = RefreshSessionService::new(deps.clone());

        let session = refresh.execute(registered.device.id, &registered.token).await.unwrap();
        assert_eq!(session.user.id, user);
        assert!(refresh.execute(registered.device.id, "wrong").await.is_err());

        let rotated = IssueDeviceTokenService::new(deps)
            .execute(user, registered.device.id)
            .await
            .unwrap();
        assert!(refresh.execute(registered.device.id, &registered.token).await.is_err());
        assert!(refresh.execute(registered.device.id, &rotated).await.is_ok());
    }
}
