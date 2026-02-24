CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_users_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DISABLED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_email_lower
    ON users (LOWER(email));

CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    kyc_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_accounts_user_id
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_accounts_status
        CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    CONSTRAINT ck_accounts_kyc_status
        CHECK (kyc_status IN ('PENDING', 'VERIFIED', 'REJECTED'))
);

CREATE INDEX IF NOT EXISTS idx_accounts_user_id
    ON accounts (user_id);

CREATE INDEX IF NOT EXISTS idx_accounts_status
    ON accounts (status);

CREATE TABLE IF NOT EXISTS wallet_balances (
    account_id UUID NOT NULL,
    asset VARCHAR(20) NOT NULL,
    available NUMERIC(36,18) NOT NULL DEFAULT 0,
    reserved NUMERIC(36,18) NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (account_id, asset),
    CONSTRAINT fk_wallet_balances_account_id
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_wallet_balances_available_non_negative
        CHECK (available >= 0),
    CONSTRAINT ck_wallet_balances_reserved_non_negative
        CHECK (reserved >= 0)
);

CREATE INDEX IF NOT EXISTS idx_wallet_balances_account_asset
    ON wallet_balances (account_id, asset);

CREATE TABLE IF NOT EXISTS wallet_reservations (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    asset VARCHAR(20) NOT NULL,
    amount NUMERIC(36,18) NOT NULL,
    order_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at TIMESTAMPTZ NULL,
    CONSTRAINT fk_wallet_reservations_account_id
        FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_wallet_reservations_amount_positive
        CHECK (amount > 0),
    CONSTRAINT ck_wallet_reservations_status
        CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED', 'CANCELLED'))
);

CREATE INDEX IF NOT EXISTS idx_wallet_reservations_account_status_created_at
    ON wallet_reservations (account_id, status, created_at);

CREATE INDEX IF NOT EXISTS idx_wallet_reservations_order_id
    ON wallet_reservations (order_id);
