ALTER TABLE order_idempotency ADD CONSTRAINT uq_order_idempotency_order_id UNIQUE (order_id);
