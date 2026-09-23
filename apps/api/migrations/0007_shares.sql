CREATE TABLE IF NOT EXISTS shares (
    id UUID PRIMARY KEY,
    resource_type TEXT NOT NULL,
    resource_id UUID NOT NULL,
    owner_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    grantee_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (resource_type, resource_id, grantee_id),
    CHECK (resource_type IN ('file', 'album')),
    CHECK (permission IN ('read', 'write')),
    CHECK (owner_id <> grantee_id)
);

CREATE INDEX IF NOT EXISTS shares_grantee_idx ON shares (grantee_id);
CREATE INDEX IF NOT EXISTS shares_owner_idx ON shares (owner_id);
CREATE INDEX IF NOT EXISTS shares_resource_idx ON shares (resource_type, resource_id);
