ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_channel_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_channel_check
    CHECK (channel IN ('WHATSAPP', 'SMS', 'PUSH', 'IN_APP'));
