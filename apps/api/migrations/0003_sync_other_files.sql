INSERT INTO sync_rules (id, profile_id, media_kind, max_age_days, max_size_bytes, include_all)
SELECT gen_random_uuid(), p.id, 'other', NULL, NULL, TRUE
FROM sync_profiles p
WHERE NOT EXISTS (
    SELECT 1 FROM sync_rules r WHERE r.profile_id = p.id AND r.media_kind = 'other'
);
