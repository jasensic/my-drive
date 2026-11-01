-- Admins create users and publish Android releases. The first account (from setup) is the admin.
ALTER TABLE users ADD COLUMN IF NOT EXISTS is_admin BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE users SET is_admin = TRUE
WHERE id = (SELECT id FROM users ORDER BY created_at, id LIMIT 1)
  AND NOT EXISTS (SELECT 1 FROM users WHERE is_admin);

-- SHA-256 of the device refresh token. NULL until the device asks for one.
ALTER TABLE devices ADD COLUMN IF NOT EXISTS token_hash TEXT;
