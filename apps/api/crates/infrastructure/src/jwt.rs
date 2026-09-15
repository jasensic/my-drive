use chrono::{Duration, Utc};
use domain::model::{AuthSession, User};
use domain::ports::TokenService;
use domain::{DomainError, UserId};
use jsonwebtoken::{decode, encode, DecodingKey, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use uuid::Uuid;

pub struct JwtTokenService {
    encoding: EncodingKey,
    decoding: DecodingKey,
}

impl JwtTokenService {
    pub fn new(secret: &str) -> Self {
        Self {
            encoding: EncodingKey::from_secret(secret.as_bytes()),
            decoding: DecodingKey::from_secret(secret.as_bytes()),
        }
    }
}

#[derive(Debug, Serialize, Deserialize)]
struct Claims {
    sub: Uuid,
    name: String,
    exp: i64,
}

impl TokenService for JwtTokenService {
    fn issue(&self, user: &User) -> Result<String, DomainError> {
        let claims = Claims {
            sub: user.id.0,
            name: user.username.clone(),
            exp: (Utc::now() + Duration::days(7)).timestamp(),
        };
        encode(&Header::default(), &claims, &self.encoding)
            .map_err(|e| DomainError::infra(e.to_string()))
    }

    fn verify(&self, token: &str) -> Result<AuthSession, DomainError> {
        let data = decode::<Claims>(token, &self.decoding, &Validation::default())
            .map_err(|_| DomainError::unauthorized("invalid token"))?;
        Ok(AuthSession {
            user_id: UserId::from_uuid(data.claims.sub),
            username: data.claims.name,
        })
    }
}
