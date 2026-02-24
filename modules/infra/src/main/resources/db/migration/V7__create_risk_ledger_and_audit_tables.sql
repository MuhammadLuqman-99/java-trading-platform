CREATE TABLE IF NOT EXISTS instruments (
    id UUID PRIMARY KEY,
    symbol VARCHAR(32) NOT NULL,
    status VARCHAR(20) NOT NULL,
    reference_price NUMERIC(36,18) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_instruments_symbol UNIQUE (symbol),
    CONSTRAINT ck_instruments_status
        CHECK (status IN ('ACTIVE', 'HALTED', 'DISABLED')),
    CONSTRAINT ck_instruments_reference_price_positive
        CHECK (reference_price > 0)
);

CREATE INDEX IF NOT EXISTS idx_instruments_status_symbol
    ON instruments (status, symbol);

CREATE TABLE IF NOT EXISTS account_limits (
    account_id UUID PRIMARY KEY,
    max_order_notional NUMERIC(36,18) NOT NULL,
    price_band_bps INT NOT NULL,
    updated_by VARCHAR(128) NOT NULL DEFAULT 'system',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_account_limits_account_id
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_account_limits_max_order_notional_positive
        CHECK (max_order_notional > 0),
    CONSTRAINT ck_account_limits_price_band_bps_non_negative
        CHECK (price_band_bps >= 0)
);

CREATE INDEX IF NOT EXISTS idx_account_limits_updated_at
    ON account_limits (updated_at);

CREATE TABLE IF NOT EXISTS trading_controls (
    id INT PRIMARY KEY,
    trading_frozen BOOLEAN NOT NULL DEFAULT FALSE,
    freeze_reason VARCHAR(256) NULL,
    updated_by VARCHAR(128) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_trading_controls_singleton
        CHECK (id = 1)
);

INSERT INTO trading_controls (id, trading_frozen, freeze_reason, updated_by, updated_at)
VALUES (1, FALSE, NULL, 'system', NOW())
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS ledger_transactions (
    id UUID PRIMARY KEY,
    correlation_id VARCHAR(128) NOT NULL,
    type VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ledger_transactions_correlation_created_at
    ON ledger_transactions (correlation_id, created_at);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY,
    tx_id UUID NOT NULL,
    account_id UUID NOT NULL,
    asset VARCHAR(20) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    amount NUMERIC(36,18) NOT NULL,
    ref_type VARCHAR(50) NOT NULL,
    ref_id VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_ledger_entries_tx_id
        FOREIGN KEY (tx_id) REFERENCES ledger_transactions (id) ON DELETE CASCADE,
    CONSTRAINT fk_ledger_entries_account_id
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_ledger_entries_direction
        CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entries_amount_positive
        CHECK (amount > 0)
);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_tx_id
    ON ledger_entries (tx_id);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_asset_created_at
    ON ledger_entries (account_id, asset, created_at);

CREATE INDEX IF NOT EXISTS idx_ledger_entries_ref_type_ref_id
    ON ledger_entries (ref_type, ref_id);

CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY,
    actor_user_id VARCHAR(128) NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(128) NOT NULL,
    before_json JSONB NULL,
    after_json JSONB NULL,
    result VARCHAR(20) NOT NULL,
    error_code VARCHAR(100) NULL,
    error_message TEXT NULL,
    metadata_json JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_audit_log_result
        CHECK (result IN ('SUCCESS', 'REJECTED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_audit_log_entity_created_at
    ON audit_log (entity_type, entity_id, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_log_action_created_at
    ON audit_log (action, created_at);

CREATE INDEX IF NOT EXISTS idx_audit_log_actor_created_at
    ON audit_log (actor_user_id, created_at);
