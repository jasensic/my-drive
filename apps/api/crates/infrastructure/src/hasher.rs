use argon2::password_hash::{rand_core::OsRng, PasswordHash, PasswordHasher as _, PasswordVerifier, SaltString};
use argon2::Argon2;
use domain::ports::PasswordHasher;
use domain::DomainError;

pub struct Argon2Hasher {
    argon: Argon2<'static>,
}

impl Argon2Hasher {
    pub fn new() -> Self {
        Self {
            argon: Argon2::default(),
        }
    }
}

impl Default for Argon2Hasher {
    fn default() -> Self {
        Self::new()
    }
}

impl PasswordHasher for Argon2Hasher {
    fn hash(&self, password: &str) -> Result<String, DomainError> {
        let salt = SaltString::generate(&mut OsRng);
        self.argon
            .hash_password(password.as_bytes(), &salt)
            .map(|h| h.to_string())
            .map_err(|e| DomainError::infra(e.to_string()))
    }

    fn verify(&self, password: &str, hash: &str) -> Result<bool, DomainError> {
        let parsed = PasswordHash::new(hash).map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(self.argon.verify_password(password.as_bytes(), &parsed).is_ok())
    }
}
