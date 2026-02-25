ALTER TABLE IF EXISTS instruments
    ADD COLUMN IF NOT EXISTS tick_size NUMERIC(36,18),
    ADD COLUMN IF NOT EXISTS step_size NUMERIC(36,18),
    ADD COLUMN IF NOT EXISTS min_qty NUMERIC(36,18),
    ADD COLUMN IF NOT EXISTS max_qty NUMERIC(36,18),
    ADD COLUMN IF NOT EXISTS min_notional NUMERIC(36,18);

ALTER TABLE IF EXISTS instruments
    ADD CONSTRAINT ck_instruments_tick_size_positive
        CHECK (tick_size IS NULL OR tick_size > 0);

ALTER TABLE IF EXISTS instruments
    ADD CONSTRAINT ck_instruments_step_size_positive
        CHECK (step_size IS NULL OR step_size > 0);

ALTER TABLE IF EXISTS instruments
    ADD CONSTRAINT ck_instruments_min_qty_positive
        CHECK (min_qty IS NULL OR min_qty > 0);

ALTER TABLE IF EXISTS instruments
    ADD CONSTRAINT ck_instruments_max_qty_positive
        CHECK (max_qty IS NULL OR max_qty > 0);

ALTER TABLE IF EXISTS instruments
    ADD CONSTRAINT ck_instruments_min_notional_positive
        CHECK (min_notional IS NULL OR min_notional > 0);

ALTER TABLE IF EXISTS instruments
    ADD CONSTRAINT ck_instruments_qty_range_valid
        CHECK (min_qty IS NULL OR max_qty IS NULL OR max_qty >= min_qty);
