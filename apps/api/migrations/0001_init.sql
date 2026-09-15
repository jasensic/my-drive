-- Add migration 001
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS albums (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS files (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    album_id UUID REFERENCES albums (id) ON DELETE SET NULL,
    name TEXT NOT NULL,
    size BIGINT NOT NULL,
    mime TEXT NOT NULL,
    checksum TEXT NOT NULL,
    object_key TEXT NOT NULL,
    thumbnail_key TEXT,
    media_kind TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS devices (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    last_sync_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_profiles (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL UNIQUE REFERENCES devices (id) ON DELETE CASCADE,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS sync_rules (
    id UUID PRIMARY KEY,
    profile_id UUID NOT NULL REFERENCES sync_profiles (id) ON DELETE CASCADE,
    media_kind TEXT NOT NULL,
    max_age_days INTEGER,
    max_size_bytes BIGINT,
    include_all BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS files_owner_idx ON files (owner_id);
CREATE INDEX IF NOT EXISTS devices_user_idx ON devices (user_id);
