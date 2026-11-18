# ADR-0044: The latest price is one index seek per instrument, not a scan of every price

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [10Refactors](../journal/10Refactors.md)
- **Refines:** [ADR-0014](0014-data-access-with-spring-data-jpa.md)

## Context

`findLatestMarketData` answers the most recent `close` and `previousClose` of a set of instruments.
It backs three reads: the portfolio, the instrument search and a `MARKET` submission.

It was written as `DISTINCT ON (instrumentid) … ORDER BY instrumentid, date DESC`, which asks the
database for *every* price row of those instruments and then keeps the first of each group. The
provided dataset has two dates per instrument, so the work discarded is invisible in it. Market data
is append-only and grows by one row per instrument per trading day, so what is discarded grows with
every day the system runs.

Measured on PostgreSQL 18.7 with the given schema and its indexes, loaded with 2,500 dates per
instrument (about ten years) for 66 instruments — 165,000 rows:

| Query | Reading 10 instruments | Reading all 66 |
|---|---|---|
| `DISTINCT ON`, as written | 138 ms, 25,000 rows read | **311 ms, 165,000 rows read** |
| `DISTINCT ON` plus an index on `(instrumentid, date DESC)` | 30 ms, 25,000 rows read | not measured |
| One seek per instrument (`LATERAL … LIMIT 1`) | 1.1 ms, 10 rows read | **3.1 ms, 66 rows read** |

The second row is the one that decides this: **adding an index does not fix it.** A descending index
removes the sort and still reads all 25,000 rows, because the query asks for them. The shape of the
query is the cost, not a missing index.

## Decision

**Read the latest row of each instrument with one index seek per instrument**, as a `LATERAL`
subquery ordered by `date DESC` and limited to one row:

```sql
SELECT i.id, latest.close, latest.previousclose
FROM instruments i
CROSS JOIN LATERAL (
    SELECT m.close, m.previousclose
    FROM marketdata m
    WHERE m.instrumentid = i.id
    ORDER BY m.date DESC
    LIMIT 1
) latest
WHERE i.id IN (:instrumentIds)
```

No migration and no new index: the unique index on `(instrumentid, date)` added in
`V2__portfolio_read_indexes.sql` already serves it, scanned backwards.

## Rationale

"The latest row per group" is answered by the index itself when the question is asked one group at a
time: each seek descends the btree to the end of that instrument's range and stops at the first row.
`DISTINCT ON` cannot do that — it is defined over a sorted stream, so the rows have to exist before
the first one can be taken.

This is still one query and one round trip, so [ADR-0014](0014-data-access-with-spring-data-jpa.md)
holds: the loop is the database's, inside one plan, not the application's.

Three properties the previous query had are preserved, and each is covered by a test in
`MarketDataRepositoryTest`:

- the latest row is resolved **per instrument**, not at one date shared by all of them;
- `previousClose` comes from that same row, never from a different one;
- an instrument whose latest row has a `NULL` close reports an unknown price, rather than falling
  back to an older row that has one — `LIMIT 1` takes the latest row, not the latest *priced* row.

An instrument with no price row at all is absent from the result, as before: `CROSS JOIN LATERAL`
drops the rows whose subquery is empty. The three unpriced instruments of the dataset (`PGR`, `IRCP`
and `ARS`) are exactly that case.

The rewrite rests on two constraints that already exist, and for the same reasons they were added:
`(instrumentid, date)` is unique (`V2__portfolio_read_indexes.sql`), so "the latest row" of an
instrument is a single row and `LIMIT 1` cannot pick arbitrarily; and `date` is `NOT NULL`
([ADR-0030](0030-the-schema-requires-columns-not-business-rules.md)), so a dateless row cannot take
the first place that `DESC` gives to a `NULL`. That ADR added the constraint to protect the
`DISTINCT ON` query from exactly this; the `LATERAL` ordering inherits the protection unchanged.
The constraints that make the read deterministic are the ones that make it fast.

## Alternatives considered

- **An index on `(instrumentid, date DESC)`.** Measured: 311 ms → 30 ms for ten instruments, still
  reading every row. It buys a constant factor and keeps the linear growth, and costs a migration
  and a second index on the largest table.
- **Denormalising the latest close onto `instruments`.** The fastest read, and the one that can be
  wrong: a second copy of a value with no rule keeping it in step — the same criticism this
  repository already makes of `previousclose`, which the dataset carries as a column instead of
  deriving it.
- **One query per instrument from the service.** The N+1 that
  [ADR-0014](0014-data-access-with-spring-data-jpa.md) forbids: the same seeks, each paying a round
  trip.
- **Leaving it as it was.** Defensible with the provided dataset, where it reads two rows per
  instrument. Not defensible as a statement about how the read behaves in service, which is what
  this repository documents.

## Consequences

- The portfolio read stops growing with the age of the market data. Measured at ten years of prices:
  311 ms → 3.1 ms, and 1,830 buffers → 265.
- The query names `instruments`, so the instrument ids it is given must exist there. They always do:
  every caller takes them from an instrument it has already read, and `marketdata.instrumentid` is a
  foreign key to that same table.
- The measurement was taken on synthetic volume, not on the provided database, which is too small to
  show it. The script that loads it is not part of the repository.
