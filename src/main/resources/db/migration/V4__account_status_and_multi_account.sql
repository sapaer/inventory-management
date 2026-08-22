-- Allow multiple shop accounts to share one phone number (login is by phone,
-- but each account row is a distinct shop/business profile).
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_phone_key;

-- Account deactivate/reactivate + hard delete support.
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    CHECK (status IN ('ACTIVE', 'DEACTIVATED'));
ALTER TABLE users ADD COLUMN deactivated_at TIMESTAMP;
