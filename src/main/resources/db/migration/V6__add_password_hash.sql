-- Optional password auth alongside OTP. NULL means the account has never set one
-- (OTP-only), so it can't be used to log in with a password.
ALTER TABLE users ADD COLUMN password_hash VARCHAR(100);
