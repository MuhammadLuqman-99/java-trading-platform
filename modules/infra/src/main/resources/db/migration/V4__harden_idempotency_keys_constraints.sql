ALTER TABLE idempotency_keys
    DROP CONSTRAINT IF EXISTS ck_idempotency_keys_expires_at_valid;

ALTER TABLE idempotency_keys
    ADD CONSTRAINT ck_idempotency_keys_expires_at_valid
        CHECK (expires_at >= created_at);

ALTER TABLE idempotency_keys
    DROP CONSTRAINT IF EXISTS ck_idempotency_keys_status_shape;

ALTER TABLE idempotency_keys
    ADD CONSTRAINT ck_idempotency_keys_status_shape
        CHECK (
            (status = 'IN_PROGRESS' AND response_code IS NULL AND response_body IS NULL AND error_code IS NULL)
            OR
            (status = 'COMPLETED' AND response_code IS NOT NULL AND error_code IS NULL)
            OR
            (status = 'FAILED' AND error_code IS NOT NULL)
        );

CREATE INDEX IF NOT EXISTS idx_idempotency_keys_scope_key_status
    ON idempotency_keys (scope, idempotency_key, status);
