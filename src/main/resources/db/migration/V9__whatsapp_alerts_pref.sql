-- Per-account switch for WhatsApp low-stock alerts. Defaults ON so existing
-- accounts keep getting alerts; users can turn it off from My account > Preferences.
ALTER TABLE users ADD COLUMN whatsapp_alerts_enabled BOOLEAN NOT NULL DEFAULT TRUE;
