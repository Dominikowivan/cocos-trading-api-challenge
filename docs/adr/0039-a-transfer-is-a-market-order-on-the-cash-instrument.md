# ADR-0039: A transfer is a `MARKET` order on the `MONEDA` instrument at price `1`, in whole pesos

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [08CashTransfer](../journal/08CashTransfer.md)

## Context

`orders` is the ledger (ADR-0002) and cash is the fold over it (ADR-0015): there is no balance
column, so a transfer has to be a row in `orders` that the fold already counts. The columns are the
given schema's: `size INT`, `price NUMERIC(10, 2)`, `type`, `side`, `status`, all required since `V3`.

The provided dataset already records two transfers, measured on 2026-11-17:

```
(66, 1, 1000000, 1, 'CASH_IN',  'FILLED', 'MARKET', '2023-07-12 12:11:20')
(66, 1,  100000, 1, 'CASH_OUT', 'FILLED', 'MARKET', '2023-07-13 12:31:20')
```

Instrument `66` is `ARS` / `PESOS`, the only instrument of type `MONEDA`. The fold computes a row's
cash effect as `sign × size × price` and excludes the cash instrument from positions.

## Decision

**A transfer is one row in `orders`: the `MONEDA` instrument, side `CASH_IN` or `CASH_OUT`, type
`MARKET`, size equal to the amount in whole pesos, price `1.00`, status `FILLED` or `REJECTED`, and
its one row in `order_status_history`.**

- **The amount is whole pesos**, from `1` to `2,147,483,647` (the largest `INT`). `100` and `100.00`
  are the same amount; `100.50` is refused with `400`, never rounded. The bound is the column's
  capacity, stated once in the model, not configuration.
- **The instrument is found by its type**, `MONEDA`, not by the literal id `66`. Exactly one is
  expected; none or several is a fault in the deployment's data, answered `500` and logged, never
  blamed on the caller.
- **A deposit is `FILLED` unconditionally. A withdrawal is `FILLED` when available cash is at least
  the amount** — settled cash minus what `NEW BUY` orders reserve (ADR-0004), from the same snapshot
  the portfolio reports (ADR-0015) — **and `REJECTED` with `INSUFFICIENT_FUNDS` otherwise.** The
  reason already exists, so the constitution's list of rejection reasons does not grow.
- The write model `NewOrder` takes a `LedgerSide` instead of an `OrderSide`, as the read model
  `Order` already does, so both endpoints write and read back through one path.

## Rationale

It is the shape the given data uses, so every rule that already reads the ledger reads transfers
correctly with no change: `size × price` is the amount, the portfolio's cash moves by it, the cash
instrument never appears as a position. A transfer that needed a second shape would need every
reader to know two.

Whole pesos was the author's call (2026-11-17), and it follows from the shape: with the amount in
`size`, centavos have no column to go in. Looking the instrument up by type keeps the domain's
definition of cash — `InstrumentRef.CASH`, already what `isCash()` and the search's exclusion use —
the only one, instead of adding a dataset's id as a second.

## Alternatives considered

- **`size = 1`, `price = amount`** — allows centavos, but caps a transfer at `99,999,999.99`, makes
  `size` a meaningless constant, and departs from the seed.
- **Size in centavos at price `0.01`** — caps a transfer at `21,474,836.47` and makes `size` mean
  shares for one instrument and centavos for another.
- **Rounding a fractional amount** — moves money the caller did not ask to move.
- **Hardcoding instrument `66`** — true of one dataset; wrong on any other.
- **The instrument in the request** — the author confirmed it is implicit while there is one
  currency; a second currency would add it.
- **A separate write path for transfers** (`NewTransfer`, its own mapper) — duplicates the rule that
  every row gets its history row.

## Consequences

- No migration: every column needed exists and is wide enough (`CASH_OUT` is 8 of `side`'s 10).
- A deposit can make settled cash exceed what a single order's amount can carry
  (`99,999,999.99`); that bound is on orders, not on a balance, and nothing needs to change.
- Supporting centavos later means a new row shape, readers that know it, and a new ADR.
