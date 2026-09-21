ALTER TABLE albums ADD COLUMN IF NOT EXISTS silo TEXT NOT NULL DEFAULT 'photos';

UPDATE albums a
SET silo = CASE
    WHEN EXISTS (
        SELECT 1 FROM files f
        WHERE f.album_id = a.id AND f.media_kind = 'audio'
    ) THEN 'music'
    WHEN EXISTS (
        SELECT 1 FROM files f
        WHERE f.album_id = a.id AND f.media_kind = 'other'
    ) THEN 'files'
    ELSE 'photos'
END;

CREATE INDEX IF NOT EXISTS albums_owner_silo_idx ON albums (owner_id, silo);
