-- Devices still on the old default (photos from the last year, videos under 10 MB)
-- become full NAS mirrors. Profiles someone edited to other values stay as they are.
WITH old_default AS (
    SELECT p.id
    FROM sync_profiles p
    WHERE EXISTS (
        SELECT 1 FROM sync_rules r
        WHERE r.profile_id = p.id AND r.media_kind = 'photo'
          AND r.max_age_days = 365 AND r.max_size_bytes IS NULL AND NOT r.include_all
    )
      AND EXISTS (
        SELECT 1 FROM sync_rules r
        WHERE r.profile_id = p.id AND r.media_kind = 'video'
          AND r.max_age_days IS NULL AND r.max_size_bytes = 10485760 AND NOT r.include_all
    )
      AND (
        SELECT COUNT(*) FROM sync_rules r
        WHERE r.profile_id = p.id AND r.media_kind IN ('photo', 'video')
    ) = 2
)
UPDATE sync_rules
SET max_age_days = NULL, max_size_bytes = NULL, include_all = TRUE
WHERE profile_id IN (SELECT id FROM old_default)
  AND media_kind IN ('photo', 'video');
