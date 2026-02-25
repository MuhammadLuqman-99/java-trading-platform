ALTER TABLE IF EXISTS instruments
    ADD COLUMN IF NOT EXISTS base_asset VARCHAR(20),
    ADD COLUMN IF NOT EXISTS quote_asset VARCHAR(20);

ALTER TABLE IF EXISTS instruments
    ADD CONSTRAINT ck_instruments_base_asset_not_blank
        CHECK (base_asset IS NULL OR LENGTH(TRIM(base_asset)) > 0);

ALTER TABLE IF EXISTS instruments
    ADD CONSTRAINT ck_instruments_quote_asset_not_blank
        CHECK (quote_asset IS NULL OR LENGTH(TRIM(quote_asset)) > 0);
