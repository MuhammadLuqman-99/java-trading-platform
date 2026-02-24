CREATE TABLE IF NOT EXISTS idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    scope VARCHAR(128) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    response_code INT NULL,
    response_body JSONB NULL,
    error_code VARCHAR(64) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_idempotency_keys_status
        CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_keys_scope_key
    ON idempotency_keys (scope, idempotency_key);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_status_created_at
    ON idempotency_keys (status, created_at);

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_expires_at
    ON idempotency_keys (expires_at);
