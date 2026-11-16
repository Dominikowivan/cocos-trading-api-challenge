CREATE INDEX        idx_orders_userid_status        ON orders     (userid, status);
CREATE UNIQUE INDEX uq_marketdata_instrumentid_date ON marketdata (instrumentid, date);
