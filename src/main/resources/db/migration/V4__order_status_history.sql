CREATE TABLE order_status_history (
  id         SERIAL PRIMARY KEY,
  order_id   INT         NOT NULL REFERENCES orders(id),
  status     VARCHAR(20) NOT NULL,
  reason     VARCHAR(40),
  changed_at TIMESTAMP   NOT NULL
);

CREATE INDEX idx_order_status_history_order_id ON order_status_history (order_id);
