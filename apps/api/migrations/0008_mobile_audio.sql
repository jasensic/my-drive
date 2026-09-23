ALTER TABLE files
    ADD COLUMN IF NOT EXISTS mobile_object_key TEXT,
    ADD COLUMN IF NOT EXISTS mobile_checksum TEXT,
    ADD COLUMN IF NOT EXISTS mobile_size BIGINT,
    ADD COLUMN IF NOT EXISTS mobile_mime TEXT;
