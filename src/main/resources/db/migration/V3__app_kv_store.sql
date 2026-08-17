CREATE TABLE IF NOT EXISTS app_kv_store (
    key         TEXT PRIMARY KEY,
    value       TEXT NOT NULL,
    expires_at  TIMESTAMPTZ NULL
);

CREATE INDEX IF NOT EXISTS idx_app_kv_store_expires_at ON app_kv_store (expires_at);
