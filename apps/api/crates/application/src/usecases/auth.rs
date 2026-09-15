use std::sync::Arc;

use async_trait::async_trait;
use domain::model::{AuthSession, User};
use domain::UserId;

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

#[async_trait]
pub trait Authenticate: Send + Sync {
    async fn execute(&self, token: &str) -> Result<AuthSession, AppError>;
}

#[async_trait]
pub trait GetCurrentUser: Send + Sync {
    async fn execute(&self, user_id: UserId) -> Result<User, AppError>;
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

#[async_trait]
impl SetupAdmin for SetupAdminService {
    async fn execute(&self, username: String, password: String) -> Result<SetupResult, AppError> {
        let username = username.trim().to_string();
        if username.is_empty() || password.len() < 8 {
            return Err(AppError::validation(
                "username is required and password must be at least 8 characters",
            ));
        }
        if self.deps.users.count().await? > 0 {
            return Err(AppError::conflict("setup has already been completed"));
        }
        let now = self.deps.clock.now();
        let user = User {
            id: UserId::new(),
            username,
            password_hash: self.deps.hasher.hash(&password)?,
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
