ALTER TABLE orders
  ALTER COLUMN instrumentid SET NOT NULL,
  ALTER COLUMN userid       SET NOT NULL,
  ALTER COLUMN size         SET NOT NULL,
  ALTER COLUMN price        SET NOT NULL,
  ALTER COLUMN type         SET NOT NULL,
  ALTER COLUMN side         SET NOT NULL,
  ALTER COLUMN status       SET NOT NULL,
  ALTER COLUMN datetime     SET NOT NULL;

ALTER TABLE marketdata
  ALTER COLUMN instrumentid SET NOT NULL,
  ALTER COLUMN date         SET NOT NULL;
