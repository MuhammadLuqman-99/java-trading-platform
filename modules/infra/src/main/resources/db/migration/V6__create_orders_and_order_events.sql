CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    instrument VARCHAR(32) NOT NULL,
    side VARCHAR(8) NOT NULL,
    type VARCHAR(16) NOT NULL,
    qty NUMERIC(36,18) NOT NULL,
    price NUMERIC(36,18) NULL,
    status VARCHAR(24) NOT NULL,
    filled_qty NUMERIC(36,18) NOT NULL DEFAULT 0,
    client_order_id VARCHAR(64) NOT NULL,
    exchange_order_id VARCHAR(64) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_orders_account_id
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_orders_side
        CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_orders_type
        CHECK (type IN ('MARKET', 'LIMIT')),
    CONSTRAINT ck_orders_qty_positive
        CHECK (qty > 0),
    CONSTRAINT ck_orders_price_for_type
        CHECK (
            (type = 'LIMIT' AND price IS NOT NULL AND price > 0)
            OR
            (type = 'MARKET' AND price IS NULL)
        ),
    CONSTRAINT ck_orders_status
        CHECK (status IN ('NEW', 'ACK', 'PARTIALLY_FILLED', 'FILLED', 'CANCELED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_orders_filled_qty_range
        CHECK (filled_qty >= 0 AND filled_qty <= qty)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_orders_account_client_order_id
    ON orders (account_id, client_order_id);

CREATE INDEX IF NOT EXISTS idx_orders_account_created_at_desc
    ON orders (account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_orders_exchange_order_id
    ON orders (exchange_order_id);

CREATE INDEX IF NOT EXISTS idx_orders_status_created_at
    ON orders (status, created_at);

CREATE TABLE IF NOT EXISTS order_events (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(24) NULL,
    to_status VARCHAR(24) NOT NULL,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_order_events_order_id
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_order_events_order_id_created_at
    ON order_events (order_id, created_at);

CREATE INDEX IF NOT EXISTS idx_order_events_event_type_created_at
    ON order_events (event_type, created_at);
