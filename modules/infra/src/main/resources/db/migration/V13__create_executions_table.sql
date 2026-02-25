CREATE TABLE IF NOT EXISTS executions (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    account_id UUID NOT NULL,
    instrument VARCHAR(32) NOT NULL,
    trade_id VARCHAR(64) NOT NULL,
    exchange_name VARCHAR(32) NOT NULL,
    exchange_order_id VARCHAR(64) NOT NULL,
    side VARCHAR(8) NOT NULL,
    qty NUMERIC(36,18) NOT NULL,
    price NUMERIC(36,18) NOT NULL,
    fee_asset VARCHAR(20) NULL,
    fee_amount NUMERIC(36,18) NULL,
    executed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_executions_order_id
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_executions_account_id
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_executions_side
        CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT ck_executions_qty_positive
        CHECK (qty > 0),
    CONSTRAINT ck_executions_price_positive
        CHECK (price > 0),
    CONSTRAINT ck_executions_fee_amount_non_negative
        CHECK (fee_amount IS NULL OR fee_amount >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_executions_exchange_trade
    ON executions (exchange_name, instrument, trade_id);

CREATE INDEX IF NOT EXISTS idx_executions_account_executed_at_desc
    ON executions (account_id, executed_at DESC);

CREATE INDEX IF NOT EXISTS idx_executions_account_order_executed_at_desc
    ON executions (account_id, order_id, executed_at DESC);

CREATE INDEX IF NOT EXISTS idx_executions_account_instrument_executed_at_desc
    ON executions (account_id, instrument, executed_at DESC);
