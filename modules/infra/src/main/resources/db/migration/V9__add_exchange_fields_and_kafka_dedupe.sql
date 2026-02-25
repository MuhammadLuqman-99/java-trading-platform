ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS exchange_name VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS exchange_client_order_id VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_orders_exchange_name_order_id
    ON orders (exchange_name, exchange_order_id);

CREATE INDEX IF NOT EXISTS idx_orders_exchange_name_client_order_id
    ON orders (exchange_name, exchange_client_order_id);

CREATE TABLE IF NOT EXISTS processed_kafka_events (
    event_id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    order_id UUID NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_processed_kafka_events_order_id
        FOREIGN KEY (order_id) REFERENCES orders (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_processed_kafka_events_topic_processed_at
    ON processed_kafka_events (topic, processed_at DESC);
