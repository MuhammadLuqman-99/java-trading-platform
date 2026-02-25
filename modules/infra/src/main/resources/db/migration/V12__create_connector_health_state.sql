CREATE TABLE IF NOT EXISTS connector_health_state (
    connector_name VARCHAR(64) PRIMARY KEY,
    status VARCHAR(16) NOT NULL,
    last_success_at TIMESTAMPTZ NULL,
    last_poll_started_at TIMESTAMPTZ NULL,
    last_poll_completed_at TIMESTAMPTZ NULL,
    last_error_at TIMESTAMPTZ NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message TEXT NULL,
    open_orders_fetched INT NOT NULL DEFAULT 0,
    recent_trades_fetched INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_connector_health_state_status
        CHECK (status IN ('UP', 'DEGRADED', 'DOWN')),
    CONSTRAINT ck_connector_health_state_open_orders_non_negative
        CHECK (open_orders_fetched >= 0),
    CONSTRAINT ck_connector_health_state_recent_trades_non_negative
        CHECK (recent_trades_fetched >= 0)
);

INSERT INTO connector_health_state (
    connector_name,
    status,
    updated_at
) VALUES (
    'binance-spot',
    'DOWN',
    NOW()
)
ON CONFLICT (connector_name) DO NOTHING;
