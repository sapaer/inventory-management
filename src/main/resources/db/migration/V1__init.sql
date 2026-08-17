-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- USERS
CREATE TABLE users (
    id                  UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    phone               VARCHAR(10)   UNIQUE NOT NULL,
    name                VARCHAR(100),
    shop_name           VARCHAR(100),
    email               VARCHAR(100),
    business_type       VARCHAR(20)   CHECK (business_type IN ('SHOP','SERVICE_CENTER','BOTH')),
    onboarding_status   VARCHAR(20)   NOT NULL DEFAULT 'REGISTERED'
                                      CHECK (onboarding_status IN ('REGISTERED','PROFILED','ACTIVE')),
    is_verified         BOOLEAN       NOT NULL DEFAULT false,
    created_at          TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- USER LOCATIONS
CREATE TABLE user_locations (
    id          UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    address     TEXT,
    area        VARCHAR(100),
    city        VARCHAR(100),
    state       VARCHAR(50)   DEFAULT 'Haryana',
    pincode     VARCHAR(6),
    geo_lat     DECIMAL(10,8),
    geo_lng     DECIMAL(11,8),
    is_primary  BOOLEAN       NOT NULL DEFAULT true,
    created_at  TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- USER VEHICLE CATEGORIES
CREATE TABLE user_vehicle_categories (
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category    VARCHAR(20) NOT NULL
                            CHECK (category IN ('TWO_WHEELER','FOUR_WHEELER',
                                                'THREE_WHEELER','COMMERCIAL','EV')),
    PRIMARY KEY (user_id, category)
);

-- INVENTORY ITEMS
CREATE TABLE inventory_items (
    id               UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID          NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    part_name        VARCHAR(200)  NOT NULL,
    local_name       VARCHAR(200),
    specification    TEXT,
    description      TEXT,
    vehicle_category VARCHAR(20)   CHECK (vehicle_category IN ('TWO_WHEELER','FOUR_WHEELER',
                                                               'THREE_WHEELER','COMMERCIAL','EV')),
    brand            VARCHAR(100),
    model            VARCHAR(100),
    quantity         INTEGER       NOT NULL DEFAULT 0,
    min_quantity     INTEGER       NOT NULL DEFAULT 2,
    selling_price    DECIMAL(10,2),
    cost_price       DECIMAL(10,2),
    images           JSON          NOT NULL DEFAULT '[]',
    is_active        BOOLEAN       NOT NULL DEFAULT true,
    search_vector    TSVECTOR      GENERATED ALWAYS AS (
        to_tsvector('english',
            part_name || ' ' ||
            COALESCE(local_name, '') || ' ' ||
            COALESCE(specification, '') || ' ' ||
            COALESCE(brand, '') || ' ' ||
            COALESCE(model, ''))
    ) STORED,
    created_at       TIMESTAMP     NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP     NOT NULL DEFAULT NOW()
);

-- INVENTORY HISTORY
CREATE TABLE inventory_history (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    item_id      UUID        NOT NULL REFERENCES inventory_items(id) ON DELETE CASCADE,
    user_id      UUID        NOT NULL REFERENCES users(id),
    change_type  VARCHAR(20) CHECK (change_type IN ('ADD','SOLD','RECEIVED',
                                                    'ADJUSTMENT','RETURNED')),
    qty_before   INTEGER     NOT NULL,
    qty_change   INTEGER     NOT NULL,
    qty_after    INTEGER     NOT NULL,
    note         TEXT,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- NOTIFICATIONS
CREATE TABLE notifications (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(20) CHECK (type IN ('LOW_STOCK','OTP_SENT')),
    title       VARCHAR(200),
    body        TEXT,
    data        JSON,
    channel     VARCHAR(20) CHECK (channel IN ('WHATSAPP','PUSH','IN_APP')),
    is_read     BOOLEAN     NOT NULL DEFAULT false,
    sent_at     TIMESTAMP,
    created_at  TIMESTAMP   NOT NULL DEFAULT NOW()
);

-- INDEXES
CREATE INDEX idx_inventory_user    ON inventory_items(user_id, is_active);
CREATE INDEX idx_inventory_search  ON inventory_items USING GIN(search_vector);
CREATE INDEX idx_inventory_low     ON inventory_items(user_id, quantity, min_quantity)
                                   WHERE is_active = true;
CREATE INDEX idx_history_item      ON inventory_history(item_id, created_at DESC);
CREATE INDEX idx_notif_user        ON notifications(user_id, is_read, created_at DESC);
CREATE INDEX idx_users_phone       ON users(phone);

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();

CREATE TRIGGER trg_inventory_updated_at
    BEFORE UPDATE ON inventory_items
    FOR EACH ROW EXECUTE FUNCTION update_updated_at();
