use domain::DomainError;
use thiserror::Error;

#[derive(Debug, Error)]
pub enum AppError {
    #[error(transparent)]
    Domain(#[from] DomainError),
}

impl AppError {
    pub fn conflict(msg: impl Into<String>) -> Self {
        Self::Domain(DomainError::conflict(msg))
    }

    pub fn not_found(msg: impl Into<String>) -> Self {
        Self::Domain(DomainError::not_found(msg))
    }

    pub fn validation(msg: impl Into<String>) -> Self {
        Self::Domain(DomainError::validation(msg))
    }

    pub fn unauthorized(msg: impl Into<String>) -> Self {
        Self::Domain(DomainError::unauthorized(msg))
    }

    pub fn forbidden(msg: impl Into<String>) -> Self {
        Self::Domain(DomainError::forbidden(msg))
    }
}
