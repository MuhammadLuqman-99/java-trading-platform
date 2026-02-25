ALTER TABLE connector_health_state
    ADD COLUMN IF NOT EXISTS ws_connection_state VARCHAR(16) NOT NULL DEFAULT 'DOWN',
    ADD COLUMN IF NOT EXISTS last_ws_connected_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS last_ws_disconnected_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS last_ws_error_at TIMESTAMPTZ NULL,
    ADD COLUMN IF NOT EXISTS last_ws_error_code VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS last_ws_error_message TEXT NULL,
    ADD COLUMN IF NOT EXISTS ws_reconnect_attempts BIGINT NOT NULL DEFAULT 0;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_connector_health_state_ws_connection_state'
    ) THEN
        ALTER TABLE connector_health_state
            ADD CONSTRAINT ck_connector_health_state_ws_connection_state
                CHECK (ws_connection_state IN ('CONNECTING', 'UP', 'DEGRADED', 'DOWN'));
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_connector_health_state_ws_reconnect_attempts_non_negative'
    ) THEN
        ALTER TABLE connector_health_state
            ADD CONSTRAINT ck_connector_health_state_ws_reconnect_attempts_non_negative
                CHECK (ws_reconnect_attempts >= 0);
    END IF;
END
$$;
