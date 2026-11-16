# ADR-0015: The fold returns one account snapshot, and that snapshot is its contract

- **Status:** Accepted
- **Date:** 2026-11-15
- **Research:** [R-004](../../specs/001-portfolio-endpoint/research.md), feature `001-portfolio-endpoint`

## Context

[ADR-0002](0002-orders-as-ledger.md) decided that `orders` is the ledger and that exactly one
place folds it. It did not decide **what that fold hands back**, and the
constitution's requirement that the portfolio and order validation cannot disagree about available
cash is a property of the returned shape, not of the file layout: two callers of a service with
four separate query methods can still fold with different filters and reach different numbers.

The portfolio needs quantities, cost, settled cash and available cash. Order validation — a later
feature — needs available cash for a `BUY` and available *holdings* for a `SELL`
([constitution I](../../.specify/memory/constitution.md),
[ADR-0004](0004-reserve-funds-on-new-orders.md)). Those overlap but are not identical, and the
overlap is where a second source of truth for money would appear.

## Decision

One entry point, `new AccountLedger(orders).snapshot()`, computing an `AccountSnapshot` from the
user's contributing orders, **read once** from the database. The service reads those orders and hands them
in; the fold itself never reads
([ADR-0017](0017-layers-with-a-service-that-orchestrates.md)). The snapshot carries everything
either reader needs:

| Member | Returns |
|---|---|
| `settledCash()` | the pesos effect of every `FILLED` order, reservations included |
| `availableCash()` | `settledCash` minus the pesos reserved by `BUY LIMIT` orders still in `NEW` |
| `holdings()` | one `Holding` per instrument with a non-zero settled quantity, with its quantity and its weighted-average cost |
| `availableQuantityOf(instrumentId)` | the holding minus the shares reserved by `SELL LIMIT` orders still in `NEW` |

Its members are **questions, not accumulators**
([constitution IX](../../.specify/memory/constitution.md)): a caller asks the snapshot for a figure
rather than fetching raw sums and doing arithmetic of its own. The reservation subtraction exists
once, inside the snapshot.

**Reserved shares are computed even though the portfolio never reports them.** That is the point of
the decision rather than an oversight: computing them in a second place, later, when order
validation needs them, is exactly how the two truths come apart.

## Alternatives considered

- **A method per figure** (`availableCash(userId)`, `quantityOf(userId, instrumentId)`). Reads
  beautifully at the call site. Rejected: each call re-folds the ledger, so one portfolio becomes
  one query per figure, and nothing stops two call sites from folding with different status
  filters — the divergence this decision exists to prevent.
- **Return a `Map<instrumentId, BigDecimal>` and a cash `BigDecimal`.** Fewer types. Rejected
  under constitution IX: the caller owns the arithmetic, so the "subtract what is reserved" rule
  would be duplicated in the validator, which is the one rule that must not be.
- **Let order validation own the fold and have the portfolio consume the validator.** Rejected: it
  points the dependency the wrong way, making a read-only projection depend on the write path.
- **An event-sourced projection kept in memory.** Rejected as the snapshot-plus-delta design
  ADR-0002 deliberately deferred; it is still deferred, and this shape is what a later snapshot
  would have to reproduce.

## Consequences

- **A future validator cannot disagree with the portfolio**, because it reads the same value
  computed by the same pass. That is the whole decision; everything else is a cost of it.
- The snapshot is wider than any single caller needs, and `availableQuantityOf` currently has no
  production caller at all. It is tested here anyway. The alternative — add it when the write
  feature lands — is the divergence above.
- The fold holds **no `switch` over sides or statuses**: `Order` answers whether it settled,
  what it does to cash, and what it reserves, so the signs live in one place and each figure reads
  as arithmetic.
- **One small method per figure**, each its own pass over the orders already in memory:
  `snapshot()` reads as the list of what a snapshot is made of, and each rule can be read without
  the other three. Only the cost-basis walk depends on the `(occurredAt, orderId)` order
  ([ADR-0003](0003-weighted-average-cost.md)), and it is still one pass in that order; the three
  sums are indifferent to order, so separate passes cannot disagree. A single interleaved loop was
  the first implementation, and it was replaced for readability: four iterations over one user's
  history in memory cost nothing measurable next to the query that loaded it.
- The read cost stays what ADR-0002 accepted: proportional to the user's order history. The
  snapshot does not change that, and the indexes in `V2__portfolio_read_indexes.sql` remain the
  mitigation.
