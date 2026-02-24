ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ADD COLUMN IF NOT EXISTS processing_started_at TIMESTAMPTZ NULL;

ALTER TABLE outbox_events
    DROP CONSTRAINT IF EXISTS ck_outbox_events_status;

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_status
        CHECK (status IN ('NEW', 'PROCESSING', 'PUBLISHED', 'FAILED', 'DEAD'));

ALTER TABLE outbox_events
    DROP CONSTRAINT IF EXISTS ck_outbox_events_attempt_count_non_negative;

ALTER TABLE outbox_events
    ADD CONSTRAINT ck_outbox_events_attempt_count_non_negative
        CHECK (attempt_count >= 0);

UPDATE outbox_events
SET next_attempt_at = COALESCE(next_attempt_at, created_at)
WHERE next_attempt_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_outbox_events_poll_eligible
    ON outbox_events (status, next_attempt_at, created_at)
    WHERE status IN ('NEW', 'FAILED');

CREATE INDEX IF NOT EXISTS idx_outbox_events_processing_started_at
    ON outbox_events (processing_started_at)
    WHERE status = 'PROCESSING';
