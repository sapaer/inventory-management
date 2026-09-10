-- Account deletion: DELETE /account marks the row PENDING_DELETION instead of
-- hard-deleting; a daily job purges rows whose grace period (30 days) has elapsed.
-- Logging back in before then restores the account.

ALTER TABLE users ADD COLUMN deletion_requested_at TIMESTAMP;

ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check
    CHECK (status IN ('ACTIVE', 'DEACTIVATED', 'PENDING_DELETION'));

-- Lets the purge job scan only the accounts it might act on.
CREATE INDEX IF NOT EXISTS idx_users_deletion_requested_at
    ON users (deletion_requested_at)
    WHERE deletion_requested_at IS NOT NULL;
