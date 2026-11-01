use domain::model::User;
use domain::UserId;
use sha2::{Digest, Sha256};
use uuid::Uuid;

use crate::{AppError, Deps};

/// Long-lived secret a device trades for fresh access tokens. Two v4 UUIDs give
/// 244 random bits from the OS generator. Only its SHA-256 is stored.
pub(crate) fn new_device_token() -> String {
    format!("{}{}", Uuid::new_v4().simple(), Uuid::new_v4().simple())
}

pub(crate) fn hash_device_token(token: &str) -> String {
    format!("{:x}", Sha256::digest(token.as_bytes()))
}

pub(crate) async fn require_admin(deps: &Deps, actor_id: UserId) -> Result<User, AppError> {
    let user = deps
        .users
        .find_by_id(actor_id)
        .await?
        .ok_or_else(|| AppError::unauthorized("unknown user"))?;
    if !user.is_admin {
        return Err(AppError::forbidden("only an admin can do that"));
    }
    Ok(user)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tokens_are_long_unique_and_hash_stably() {
        let a = new_device_token();
        let b = new_device_token();
        assert_eq!(a.len(), 64);
        assert_ne!(a, b);
        assert_eq!(hash_device_token(&a), hash_device_token(&a));
        assert_ne!(hash_device_token(&a), hash_device_token(&b));
    }
}
