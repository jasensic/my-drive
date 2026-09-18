ALTER TABLE files
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS files_owner_active_idx ON files (owner_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS files_owner_trash_idx ON files (owner_id)
    WHERE deleted_at IS NOT NULL;
