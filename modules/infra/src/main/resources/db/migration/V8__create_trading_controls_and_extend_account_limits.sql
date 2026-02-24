CREATE TABLE IF NOT EXISTS trading_controls (
    id INT PRIMARY KEY,
    trading_frozen BOOLEAN NOT NULL DEFAULT FALSE,
    freeze_reason VARCHAR(256) NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_trading_controls_singleton CHECK (id = 1)
);

INSERT INTO trading_controls (id, trading_frozen, freeze_reason, updated_by, updated_at)
VALUES (1, FALSE, NULL, 'system', NOW())
ON CONFLICT (id) DO NOTHING;

ALTER TABLE account_limits
    ADD COLUMN IF NOT EXISTS updated_by VARCHAR(128) NOT NULL DEFAULT 'system';
