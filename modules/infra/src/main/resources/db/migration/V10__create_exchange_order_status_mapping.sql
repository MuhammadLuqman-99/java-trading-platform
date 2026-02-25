CREATE TABLE IF NOT EXISTS exchange_order_status_mapping (
    venue VARCHAR(32) NOT NULL,
    external_status VARCHAR(64) NOT NULL,
    domain_status VARCHAR(24) NOT NULL,
    is_terminal BOOLEAN NOT NULL DEFAULT FALSE,
    is_cancelable BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_exchange_order_status_mapping PRIMARY KEY (venue, external_status),
    CONSTRAINT ck_exchange_order_status_mapping_domain_status
        CHECK (domain_status IN ('NEW', 'ACK', 'PARTIALLY_FILLED', 'FILLED', 'CANCELED', 'REJECTED', 'EXPIRED'))
);

INSERT INTO exchange_order_status_mapping (
    venue,
    external_status,
    domain_status,
    is_terminal,
    is_cancelable
) VALUES
    ('BINANCE_SPOT', 'NEW', 'NEW', FALSE, TRUE),
    ('BINANCE_SPOT', 'PARTIALLY_FILLED', 'PARTIALLY_FILLED', FALSE, TRUE),
    ('BINANCE_SPOT', 'FILLED', 'FILLED', TRUE, FALSE),
    ('BINANCE_SPOT', 'CANCELED', 'CANCELED', TRUE, FALSE),
    ('BINANCE_SPOT', 'REJECTED', 'REJECTED', TRUE, FALSE),
    ('BINANCE_SPOT', 'EXPIRED', 'EXPIRED', TRUE, FALSE),
    ('BINANCE_SPOT', 'PENDING_CANCEL', 'ACK', FALSE, FALSE),
    ('BINANCE_SPOT', 'EXPIRED_IN_MATCH', 'EXPIRED', TRUE, FALSE)
ON CONFLICT (venue, external_status) DO NOTHING;
