use async_trait::async_trait;
use chrono::{DateTime, Utc};
use domain::model::{Album, AppRelease, Device, FileRecord, SyncProfile, SyncRule, User};
use domain::ports::{
    AlbumRepository, AppReleaseRepository, DeviceRepository, FileRepository,
    SyncProfileRepository, UserRepository,
};
use domain::{
    AlbumId, AppReleaseId, DeviceId, DomainError, FileId, LibrarySilo, MediaKind, SyncProfileId, UserId,
};
use sqlx::{PgPool, Row};
use uuid::Uuid;

#[derive(Clone)]
pub struct PgRepos {
    pool: PgPool,
}

impl PgRepos {
    pub async fn connect(database_url: &str) -> Result<Self, DomainError> {
        let pool = PgPool::connect(database_url)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(Self { pool })
    }

    pub async fn migrate(&self) -> Result<(), DomainError> {
        sqlx::migrate!("../../migrations")
            .run(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))
    }
}

#[async_trait]
impl UserRepository for PgRepos {
    async fn count(&self) -> Result<i64, DomainError> {
        let row: (i64,) = sqlx::query_as("SELECT COUNT(*) FROM users")
            .fetch_one(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(row.0)
    }

    async fn insert(&self, user: &User) -> Result<(), DomainError> {
        sqlx::query(
            "INSERT INTO users (id, username, password_hash, created_at) VALUES ($1, $2, $3, $4)",
        )
        .bind(user.id.0)
        .bind(&user.username)
        .bind(&user.password_hash)
        .bind(user.created_at)
        .execute(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn find_by_username(&self, username: &str) -> Result<Option<User>, DomainError> {
        let row = sqlx::query(
            "SELECT id, username, password_hash, created_at FROM users WHERE username = $1",
        )
        .bind(username)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(row.map(|r| User {
            id: UserId::from_uuid(r.get("id")),
            username: r.get("username"),
            password_hash: r.get("password_hash"),
            created_at: r.get("created_at"),
        }))
    }

    async fn find_by_id(&self, id: UserId) -> Result<Option<User>, DomainError> {
        let row = sqlx::query("SELECT id, username, password_hash, created_at FROM users WHERE id = $1")
            .bind(id.0)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(row.map(|r| User {
            id: UserId::from_uuid(r.get("id")),
            username: r.get("username"),
            password_hash: r.get("password_hash"),
            created_at: r.get("created_at"),
        }))
    }
}

#[async_trait]
impl AlbumRepository for PgRepos {
    async fn insert(&self, album: &Album) -> Result<(), DomainError> {
        sqlx::query(
            "INSERT INTO albums (id, owner_id, name, silo, created_at) VALUES ($1, $2, $3, $4, $5)",
        )
        .bind(album.id.0)
        .bind(album.owner_id.0)
        .bind(&album.name)
        .bind(album.silo.as_str())
        .bind(album.created_at)
        .execute(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn list_by_owner(&self, owner_id: UserId) -> Result<Vec<Album>, DomainError> {
        let rows = sqlx::query(
            "SELECT id, owner_id, name, silo, created_at FROM albums WHERE owner_id = $1 ORDER BY created_at DESC",
        )
        .bind(owner_id.0)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(rows.into_iter().map(row_to_album).collect())
    }

    async fn find_by_id(&self, id: AlbumId) -> Result<Option<Album>, DomainError> {
        let row = sqlx::query("SELECT id, owner_id, name, silo, created_at FROM albums WHERE id = $1")
            .bind(id.0)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(row.map(row_to_album))
    }

    async fn update_name(&self, id: AlbumId, name: &str) -> Result<(), DomainError> {
        sqlx::query("UPDATE albums SET name = $2 WHERE id = $1")
            .bind(id.0)
            .bind(name)
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn delete(&self, id: AlbumId) -> Result<(), DomainError> {
        sqlx::query("DELETE FROM albums WHERE id = $1")
            .bind(id.0)
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }
}

#[async_trait]
impl FileRepository for PgRepos {
    async fn insert(&self, file: &FileRecord) -> Result<(), DomainError> {
        sqlx::query(
            "INSERT INTO files (id, owner_id, album_id, name, size, mime, checksum, object_key, thumbnail_key, media_kind, created_at, uploaded_at, deleted_at)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13)",
        )
        .bind(file.id.0)
        .bind(file.owner_id.0)
        .bind(file.album_id.map(|a| a.0))
        .bind(&file.name)
        .bind(file.size as i64)
        .bind(&file.mime)
        .bind(&file.checksum)
        .bind(&file.object_key)
        .bind(&file.thumbnail_key)
        .bind(file.media_kind.as_str())
        .bind(file.created_at)
        .bind(file.uploaded_at)
        .bind(file.deleted_at)
        .execute(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn list_by_owner(&self, owner_id: UserId) -> Result<Vec<FileRecord>, DomainError> {
        let rows = sqlx::query(
            "SELECT id, owner_id, album_id, name, size, mime, checksum, object_key, thumbnail_key, media_kind, created_at, uploaded_at, deleted_at
             FROM files WHERE owner_id = $1 AND deleted_at IS NULL ORDER BY created_at DESC",
        )
        .bind(owner_id.0)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        rows.into_iter().map(row_to_file).collect()
    }

    async fn list_trashed_by_owner(&self, owner_id: UserId) -> Result<Vec<FileRecord>, DomainError> {
        let rows = sqlx::query(
            "SELECT id, owner_id, album_id, name, size, mime, checksum, object_key, thumbnail_key, media_kind, created_at, uploaded_at, deleted_at
             FROM files WHERE owner_id = $1 AND deleted_at IS NOT NULL ORDER BY deleted_at DESC",
        )
        .bind(owner_id.0)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        rows.into_iter().map(row_to_file).collect()
    }

    async fn find_by_id(&self, id: FileId) -> Result<Option<FileRecord>, DomainError> {
        let row = sqlx::query(
            "SELECT id, owner_id, album_id, name, size, mime, checksum, object_key, thumbnail_key, media_kind, created_at, uploaded_at, deleted_at
             FROM files WHERE id = $1",
        )
        .bind(id.0)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        row.map(row_to_file).transpose()
    }

    async fn assign_album(&self, id: FileId, album_id: Option<AlbumId>) -> Result<(), DomainError> {
        sqlx::query("UPDATE files SET album_id = $2 WHERE id = $1")
            .bind(id.0)
            .bind(album_id.map(|a| a.0))
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn update_name(&self, id: FileId, name: &str) -> Result<(), DomainError> {
        sqlx::query("UPDATE files SET name = $2 WHERE id = $1")
            .bind(id.0)
            .bind(name)
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn set_thumbnail_key(&self, id: FileId, thumbnail_key: &str) -> Result<(), DomainError> {
        sqlx::query("UPDATE files SET thumbnail_key = $2 WHERE id = $1")
            .bind(id.0)
            .bind(thumbnail_key)
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn set_deleted_at(
        &self,
        id: FileId,
        deleted_at: Option<chrono::DateTime<chrono::Utc>>,
    ) -> Result<(), DomainError> {
        sqlx::query("UPDATE files SET deleted_at = $2 WHERE id = $1")
            .bind(id.0)
            .bind(deleted_at)
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn delete(&self, id: FileId) -> Result<(), DomainError> {
        sqlx::query("DELETE FROM files WHERE id = $1")
            .bind(id.0)
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }
}

fn row_to_album(r: sqlx::postgres::PgRow) -> Album {
    let silo: String = r.get("silo");
    Album {
        id: AlbumId::from_uuid(r.get("id")),
        owner_id: UserId::from_uuid(r.get("owner_id")),
        name: r.get("name"),
        silo: LibrarySilo::parse(&silo).unwrap_or(LibrarySilo::Photos),
        created_at: r.get("created_at"),
    }
}

fn row_to_file(r: sqlx::postgres::PgRow) -> Result<FileRecord, DomainError> {
    let kind: String = r.get("media_kind");
    Ok(FileRecord {
        id: FileId::from_uuid(r.get("id")),
        owner_id: UserId::from_uuid(r.get("owner_id")),
        album_id: r.get::<Option<Uuid>, _>("album_id").map(AlbumId::from_uuid),
        name: r.get("name"),
        size: r.get::<i64, _>("size") as u64,
        mime: r.get("mime"),
        checksum: r.get("checksum"),
        object_key: r.get("object_key"),
        thumbnail_key: r.get("thumbnail_key"),
        media_kind: MediaKind::parse(&kind).unwrap_or(MediaKind::Other),
        created_at: r.get("created_at"),
        uploaded_at: r.get("uploaded_at"),
        deleted_at: r.get("deleted_at"),
    })
}

#[async_trait]
impl DeviceRepository for PgRepos {
    async fn insert(&self, device: &Device) -> Result<(), DomainError> {
        sqlx::query(
            "INSERT INTO devices (id, user_id, name, last_sync_at, created_at) VALUES ($1,$2,$3,$4,$5)",
        )
        .bind(device.id.0)
        .bind(device.user_id.0)
        .bind(&device.name)
        .bind(device.last_sync_at)
        .bind(device.created_at)
        .execute(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn list_by_user(&self, user_id: UserId) -> Result<Vec<Device>, DomainError> {
        let rows = sqlx::query(
            "SELECT id, user_id, name, last_sync_at, created_at FROM devices WHERE user_id = $1 ORDER BY created_at",
        )
        .bind(user_id.0)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(rows
            .into_iter()
            .map(|r| Device {
                id: DeviceId::from_uuid(r.get("id")),
                user_id: UserId::from_uuid(r.get("user_id")),
                name: r.get("name"),
                last_sync_at: r.get("last_sync_at"),
                created_at: r.get("created_at"),
            })
            .collect())
    }

    async fn find_by_id(&self, id: DeviceId) -> Result<Option<Device>, DomainError> {
        let row = sqlx::query(
            "SELECT id, user_id, name, last_sync_at, created_at FROM devices WHERE id = $1",
        )
        .bind(id.0)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(row.map(|r| Device {
            id: DeviceId::from_uuid(r.get("id")),
            user_id: UserId::from_uuid(r.get("user_id")),
            name: r.get("name"),
            last_sync_at: r.get("last_sync_at"),
            created_at: r.get("created_at"),
        }))
    }

    async fn touch_sync(&self, id: DeviceId, at: DateTime<Utc>) -> Result<(), DomainError> {
        sqlx::query("UPDATE devices SET last_sync_at = $2 WHERE id = $1")
            .bind(id.0)
            .bind(at)
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }
}

#[async_trait]
impl SyncProfileRepository for PgRepos {
    async fn upsert(&self, profile: &SyncProfile) -> Result<(), DomainError> {
        let mut tx = self
            .pool
            .begin()
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        sqlx::query(
            "INSERT INTO sync_profiles (id, device_id, name) VALUES ($1,$2,$3)
             ON CONFLICT (device_id) DO UPDATE SET name = EXCLUDED.name",
        )
        .bind(profile.id.0)
        .bind(profile.device_id.0)
        .bind(&profile.name)
        .execute(&mut *tx)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;

        sqlx::query("DELETE FROM sync_rules WHERE profile_id = $1")
            .bind(profile.id.0)
            .execute(&mut *tx)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;

        for rule in &profile.rules {
            sqlx::query(
                "INSERT INTO sync_rules (id, profile_id, media_kind, max_age_days, max_size_bytes, include_all)
                 VALUES ($1,$2,$3,$4,$5,$6)",
            )
            .bind(Uuid::new_v4())
            .bind(profile.id.0)
            .bind(rule.media_kind.as_str())
            .bind(rule.max_age_days.map(|v| v as i32))
            .bind(rule.max_size_bytes.map(|v| v as i64))
            .bind(rule.include_all)
            .execute(&mut *tx)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        }
        tx.commit()
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn find_by_device(&self, device_id: DeviceId) -> Result<Option<SyncProfile>, DomainError> {
        let profile = sqlx::query("SELECT id, device_id, name FROM sync_profiles WHERE device_id = $1")
            .bind(device_id.0)
            .fetch_optional(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        let Some(p) = profile else {
            return Ok(None);
        };
        let profile_id: Uuid = p.get("id");
        let rows = sqlx::query(
            "SELECT media_kind, max_age_days, max_size_bytes, include_all FROM sync_rules WHERE profile_id = $1",
        )
        .bind(profile_id)
        .fetch_all(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        let rules = rows
            .into_iter()
            .map(|r| {
                let kind: String = r.get("media_kind");
                SyncRule {
                    media_kind: MediaKind::parse(&kind).unwrap_or(MediaKind::Other),
                    max_age_days: r.get::<Option<i32>, _>("max_age_days").map(|v| v as u32),
                    max_size_bytes: r.get::<Option<i64>, _>("max_size_bytes").map(|v| v as u64),
                    include_all: r.get("include_all"),
                }
            })
            .collect();
        Ok(Some(SyncProfile {
            id: SyncProfileId::from_uuid(profile_id),
            device_id: DeviceId::from_uuid(p.get("device_id")),
            name: p.get("name"),
            rules,
        }))
    }
}

#[async_trait]
impl AppReleaseRepository for PgRepos {
    async fn insert(&self, release: &AppRelease) -> Result<(), DomainError> {
        sqlx::query(
            "INSERT INTO app_releases (id, version_code, version_name, changelog, object_key, checksum, size, published_at)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8)",
        )
        .bind(release.id.0)
        .bind(release.version_code)
        .bind(&release.version_name)
        .bind(&release.changelog)
        .bind(&release.object_key)
        .bind(&release.checksum)
        .bind(release.size as i64)
        .bind(release.published_at)
        .execute(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        Ok(())
    }

    async fn latest(&self) -> Result<Option<AppRelease>, DomainError> {
        let row = sqlx::query(
            "SELECT id, version_code, version_name, changelog, object_key, checksum, size, published_at
             FROM app_releases ORDER BY version_code DESC LIMIT 1",
        )
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        row.map(row_to_release).transpose()
    }

    async fn list(&self) -> Result<Vec<AppRelease>, DomainError> {
        let rows = sqlx::query(
            "SELECT id, version_code, version_name, changelog, object_key, checksum, size, published_at
             FROM app_releases ORDER BY version_code DESC",
        )
        .fetch_all(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        rows.into_iter().map(row_to_release).collect()
    }

    async fn find_by_id(&self, id: AppReleaseId) -> Result<Option<AppRelease>, DomainError> {
        let row = sqlx::query(
            "SELECT id, version_code, version_name, changelog, object_key, checksum, size, published_at
             FROM app_releases WHERE id = $1",
        )
        .bind(id.0)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        row.map(row_to_release).transpose()
    }

    async fn find_by_version_code(
        &self,
        version_code: i32,
    ) -> Result<Option<AppRelease>, DomainError> {
        let row = sqlx::query(
            "SELECT id, version_code, version_name, changelog, object_key, checksum, size, published_at
             FROM app_releases WHERE version_code = $1",
        )
        .bind(version_code)
        .fetch_optional(&self.pool)
        .await
        .map_err(|e| DomainError::infra(e.to_string()))?;
        row.map(row_to_release).transpose()
    }

    async fn update(&self, release: &AppRelease) -> Result<(), DomainError> {
        let result = sqlx::query(
            "UPDATE app_releases
             SET version_code = $2, version_name = $3, changelog = $4, object_key = $5, checksum = $6, size = $7
             WHERE id = $1",
        )
        .bind(release.id.0)
        .bind(release.version_code)
        .bind(&release.version_name)
        .bind(&release.changelog)
        .bind(&release.object_key)
        .bind(&release.checksum)
        .bind(release.size as i64)
        .execute(&self.pool)
        .await
        .map_err(|e| {
            if e.to_string().contains("unique") || e.to_string().contains("duplicate") {
                DomainError::conflict("version_code already published")
            } else {
                DomainError::infra(e.to_string())
            }
        })?;
        if result.rows_affected() == 0 {
            return Err(DomainError::not_found("app release not found"));
        }
        Ok(())
    }

    async fn delete(&self, id: AppReleaseId) -> Result<(), DomainError> {
        let result = sqlx::query("DELETE FROM app_releases WHERE id = $1")
            .bind(id.0)
            .execute(&self.pool)
            .await
            .map_err(|e| DomainError::infra(e.to_string()))?;
        if result.rows_affected() == 0 {
            return Err(DomainError::not_found("app release not found"));
        }
        Ok(())
    }
}

fn row_to_release(r: sqlx::postgres::PgRow) -> Result<AppRelease, DomainError> {
    Ok(AppRelease {
        id: AppReleaseId::from_uuid(r.get("id")),
        version_code: r.get("version_code"),
        version_name: r.get("version_name"),
        changelog: r.get("changelog"),
        object_key: r.get("object_key"),
        checksum: r.get("checksum"),
        size: r.get::<i64, _>("size") as u64,
        published_at: r.get("published_at"),
    })
}
