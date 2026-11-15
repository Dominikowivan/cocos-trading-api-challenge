# ADR-0002: `orders` is a ledger, the portfolio is a projection

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [01PrimeraLectura](../journal/01PrimeraLectura.md), *Modelado de datos*

## Context

The provided schema has four tables: `users`, `instruments`, `marketdata` and `orders`. It has no
positions table and no balance column. There is nowhere to write "this user holds 30 shares of BMA
and has 753,000 in cash".

Everything the endpoints are asked to return — total account value, cash available to trade,
quantity per position, market value, total return — is therefore not stored anywhere. Cash is not
a column either: it is an instrument of type `MONEDA`, and transfers (`CASH_IN` / `CASH_OUT`) are
rows in `orders` like any other order.

## Decision

Treat that as the model rather than as something missing. **`orders` is the ledger**, and the
portfolio is a **projection** computed when read, by folding the user's `FILLED` orders (plus what
`NEW` orders reserve, see [ADR-0004](0004-reserve-funds-on-new-orders.md)). No positions table and
no balance column are added.

One domain service owns that fold, and both the portfolio endpoint and order validation consume
it.

## Alternatives considered

- **Materialised positions and balance, updated on every order.** Constant-time reads and trivial
  queries. Discarded because it creates a second source of truth: any bug, or any crash between
  inserting the order and updating the position, leaves the balance diverging from the ledger.
  With money, that failure is silent and is discovered late. It also still requires transactions
  and locking, so it does not remove the hard problem — it only adds one.
- **Ledger plus a snapshot or materialised view.** Not discarded on merit: this is the answer once
  `orders` grows past what an aggregation can serve. It is deferred, not rejected.

## Consequences

- **Reading the portfolio costs an aggregation over the user's whole history**, and grows linearly
  with it. Mitigated for now with indexes on `(userid, status)` and `(userid, instrumentid)`; at
  real volume the answer is the snapshot-plus-delta alternative above.
- **Concurrency is not solved by the ledger.** Two simultaneous orders can both read the same
  available cash and spend it twice. That is handled by locking and by the reservation in
  ADR-0004, not by this decision.
- **The same fold must serve both readers.** If the portfolio endpoint computes available cash one
  way and order validation another, there are two truths again, through the back door. Hence the
  single domain service.
- In exchange: one source of truth, a balance that can always be recomputed and audited, and a
  model that fits the given schema instead of inventing state around it.
