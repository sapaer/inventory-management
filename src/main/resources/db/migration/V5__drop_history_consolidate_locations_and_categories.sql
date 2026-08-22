-- Stock-change audit trail feature removed entirely.
DROP TABLE IF EXISTS inventory_history;

-- Vehicle categories: one row per user instead of one row per (user, category).
ALTER TABLE users ADD COLUMN vehicle_categories JSON NOT NULL DEFAULT '[]';

UPDATE users u
SET vehicle_categories = COALESCE(
    (SELECT json_agg(uvc.category) FROM user_vehicle_categories uvc WHERE uvc.user_id = u.id),
    '[]'::json
);

DROP TABLE IF EXISTS user_vehicle_categories;

-- User locations: exactly one row per user, so is_primary is no longer meaningful.
-- Keep the row that was marked primary (or, failing that, the most recent one) per user.
DELETE FROM user_locations ul
WHERE ul.id NOT IN (
    SELECT DISTINCT ON (user_id) id
    FROM user_locations
    ORDER BY user_id, is_primary DESC, created_at DESC
);

ALTER TABLE user_locations DROP COLUMN is_primary;
ALTER TABLE user_locations ADD CONSTRAINT user_locations_user_id_key UNIQUE (user_id);

-- Support efficient notification retention cleanup.
CREATE INDEX IF NOT EXISTS idx_notif_created ON notifications(created_at);
