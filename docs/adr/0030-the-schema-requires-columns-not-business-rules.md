# ADR-0030: The schema requires the columns the code cannot read without, and nothing more

- **Status:** Accepted
- **Date:** 2026-11-16
- **Journal:** [06Refactors](../journal/06Refactors.md)

## Context

The given schema (`V1`) declares no `NOT NULL` at all. The code, meanwhile, cannot read a row
without certain columns:

- `OrderMapper` calls `OrderSide.valueOf`, `OrderStatus.valueOf` and `OrderType.valueOf` on the raw
  columns; a `NULL` is a `NullPointerException`.
- `OrderEntity.size` is a primitive `int`; Hibernate cannot load a `NULL` into it.
- `Order.cashEffect()` multiplies by `price`; a `NULL` price fails there.
- `findLatestMarketData` picks the latest row with `ORDER BY instrumentid, date DESC`. In a
  descending order PostgreSQL puts `NULL` **first**, and the unique index on
  `(instrumentid, date)` admits any number of `NULL` dates. A row with no date would silently become
  "the latest price".

Each of these fails at read time, and the first three turn one bad row into a `500` for the whole
portfolio of its owner. Order submission is the next feature, and it is the first that writes to
`orders`.

## Decision

**`V3__required_columns.sql` makes the columns the code cannot do without `NOT NULL`, and adds no
other constraint.**

| Table | Columns made `NOT NULL` |
|---|---|
| `orders` | `instrumentid, userid, size, price, type, side, status, datetime` |
| `marketdata` | `instrumentid, date` |

`price` is required for market orders too: the statement says a market order is executed at the
latest `close`, and that is the price recorded. Cash movements carry `1.00`.

`SchemaConstraintsTest` inserts one valid order (the control, so the rejections cannot pass on a
broken template) and then one row per required column with that column `NULL`, asserting each is
refused. The class is `@Transactional`, so nothing it writes survives the test, even if a constraint
went missing.

### Business rules stay out of the database

Author's decision, 2026-11-16: the schema says what a row needs in order to exist, and the
application says what values make business sense. Adding a side, a kind of order or a status, or
changing what a valid quantity is, should be a change to the code and not a migration as well.
Three things were in the first draft of `V3`, or proposed for it, and were removed on that ground:

- **`CHECK ... IN (...)` on `side`, `type` and `status`.** The vocabulary lives in the Java enums,
  in one place. An unknown value still fails, in `OrderMapper`'s `valueOf`.
- **`CHECK (size > 0)` and `CHECK (price > 0)`.** That the sign of a movement comes from `side`
  and never from `size` is a modelling decision (ADR-0002, ADR-0003), not something the column
  demands.
- **The status a `MARKET` order may have** (`FILLED` or `REJECTED`, never `NEW`). It would fit a
  single-row `CHECK`, and it is the clearest business rule of the three.

All of them become the job of order submission, still to be built, which is the only writer that
could produce such a row and has to validate before writing.

### Also not constrained

- **`marketdata.close` stays nullable.** An unknown close is a modelled state, not a defect
  (ADR-0013), and a fixture tests it.
- **"`CASH_IN`/`CASH_OUT` only on a `MONEDA` instrument"** crosses tables, so it needs a trigger,
  and it is a business rule. The anomaly report (ADR-0012) already covers it.
- **`UNIQUE` on `instruments.ticker` and `users.accountnumber`.** No code depends on them today.
  They are left for when a feature does.

## Measured

On the provided dataset (local database, 2026-11-16): 0 nulls in every column above. `V3` applied
inside a transaction and rolled back cleanly before the suite ran.

**The staging database has not been measured.** `V3` validates every existing row, so a single
`NULL` there makes the deploy fail (loudly, with a rollback: Flyway runs it in one transaction).
Before the first deploy with `V3`, run these against staging and expect `0`:

```sql
SELECT count(*) FROM orders
WHERE instrumentid IS NULL OR userid IS NULL OR size IS NULL OR price IS NULL
   OR type IS NULL OR side IS NULL OR status IS NULL OR datetime IS NULL;

SELECT count(*) FROM marketdata WHERE instrumentid IS NULL OR date IS NULL;
```

## Alternatives considered

- **Validating in the mapper instead** (skip or report rows with missing columns). It moves the
  failure from a `500` to a silently wrong portfolio, and it does nothing for the writer that order
  submission will add.
- **`CHECK` constraints and Postgres enum types** for the values. Removed, for the reason given
  above.
- **`NOT VALID` constraints**, validated later. Not available for `NOT NULL` before PostgreSQL 18,
  and the point is that the code assumes *every* row is complete, which a constraint that skips the
  existing rows does not guarantee. A one-off cleanup of staging would come first.
