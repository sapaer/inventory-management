-- Split user name into first / last, captured at sign up.
-- Existing `name` column is kept as a legacy/display field and is
-- backfilled from first + last name whenever those are set.
ALTER TABLE users ADD COLUMN first_name VARCHAR(50);
ALTER TABLE users ADD COLUMN last_name  VARCHAR(50);

-- Best-effort backfill: first token -> first_name, remainder -> last_name.
UPDATE users
SET first_name = split_part(name, ' ', 1),
    last_name  = NULLIF(regexp_replace(name, '^\S+\s*', ''), '')
WHERE name IS NOT NULL AND first_name IS NULL;
