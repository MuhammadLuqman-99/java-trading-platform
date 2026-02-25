CREATE TABLE IF NOT EXISTS connector_replay_requests (
    id UUID PRIMARY KEY,
    connector_name VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(32) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL,
    requested_by VARCHAR(128) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    error_code VARCHAR(64) NULL,
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_connector_replay_requests_trigger_type
        CHECK (trigger_type IN ('MANUAL', 'RECOVERY')),
    CONSTRAINT ck_connector_replay_requests_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_connector_replay_requests_connector_status_requested_at
    ON connector_replay_requests (connector_name, status, requested_at);

CREATE INDEX IF NOT EXISTS idx_connector_replay_requests_status_requested_at
    ON connector_replay_requests (status, requested_at);
