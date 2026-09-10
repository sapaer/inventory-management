-- Extra profile fields surfaced on the My account screen. All optional.
-- photo_url / shop_photo_url are public URLs to uploaded images (same bucket as part photos).
ALTER TABLE users ADD COLUMN photo_url       TEXT;
ALTER TABLE users ADD COLUMN shop_photo_url  TEXT;
ALTER TABLE users ADD COLUMN gstin           VARCHAR(20);
ALTER TABLE users ADD COLUMN alt_phone       VARCHAR(15);
