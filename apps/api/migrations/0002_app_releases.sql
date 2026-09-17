CREATE TABLE IF NOT EXISTS app_releases (
    id UUID PRIMARY KEY,
    version_code INTEGER NOT NULL UNIQUE,
    version_name TEXT NOT NULL,
    changelog TEXT NOT NULL DEFAULT '',
    object_key TEXT NOT NULL,
    checksum TEXT NOT NULL,
    size BIGINT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS app_releases_version_idx ON app_releases (version_code DESC);
