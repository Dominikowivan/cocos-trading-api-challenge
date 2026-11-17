CREATE TABLE order_idempotency (
  id                  SERIAL PRIMARY KEY,
  userid              INT          NOT NULL REFERENCES users(id),
  idempotency_key     VARCHAR(255) NOT NULL,
  request_fingerprint CHAR(64)     NOT NULL,
  order_id            INT          REFERENCES orders(id),
  created_at          TIMESTAMP    NOT NULL,
  UNIQUE (userid, idempotency_key)
);
