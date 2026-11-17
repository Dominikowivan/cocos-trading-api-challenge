# ADR-0034: An instrument with no price refuses the submission; it is not a recorded rejection

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [07OrderPlacement](../journal/07OrderPlacement.md)

**This is a departure from the challenge statement, for one case. The README says so.**

## Context

A `MARKET` order is executed at the instrument's latest `close`, and that close is the price the
ledger row records. Three instruments in the provided dataset have no `marketdata` row at all:
`PGR` (id 3), `IRCP` (id 10) and `ARS` (id 66, which is cash and refused anyway).

`V3__required_columns.sql` made `orders.price` `NOT NULL` the day before this feature started
(ADR-0030), on the ground that the code cannot read a row without it. So for this one rejection
there is no price, and the row has to carry one.

The statement says an order the user cannot execute is stored as `REJECTED`. The spec's first draft
followed it literally for this case too (FR-010), which left the question of what goes in the
column.

## Decision

**A `MARKET` submission for an instrument with no market price is refused with `400` and nothing is
written.**

The other three rejections — insufficient funds, insufficient holdings, a derived size below one
share — are still recorded as `REJECTED` orders, exactly as the statement asks. The machine-readable
reasons are therefore exactly three, and `NO_PRICE_AVAILABLE` is not among them.

A `LIMIT` order for `PGR` or `IRCP` is unaffected and still accepted: its price came in the request,
and a limit order never reads market data.

## Rationale

An instrument that does not quote is a property of the **request**, like an instrument id that does
not exist or a `MONEDA` instrument: it is knowable before any of the user's money is considered, and
the answer does not depend on their account at all. Refusing it keeps three invariants that each
alternative gives up something to break:

- **Every row in `orders` has a price.** `V3`'s guard stands unchanged, no migration relaxes it, and
  no reader of the ledger ever meets a priced-at-nothing order. The provided dataset already has
  this property: its own seeded `REJECTED` row (`PAMP SELL` 100 at `950.00`) carries a price.
- **No money figure is ever written as `0` when it is unknown** — ADR-0013, enforced from inside the
  database rather than only on the wire.
- **The `reason` column stays about the user's money.** Every recorded `REJECTED` row is a statement
  about funds, holdings or size: things the account decides, not things the market failed to publish.

## Alternatives considered

- **Make `price` nullable and record the row with an absent price.** The honest rendering of "there
  was no price", and ADR-0013's own logic. Rejected: it gives back a `NOT NULL` guard added the day
  before, and it introduces a second rule — "a price is absent only on a `REJECTED` row" — that only
  Java enforces, on the one table the schema protects most.
- **Record `0.00`.** Rejected: it writes a money figure that is false into the ledger, which is
  exactly what ADR-0013 forbids. A later reader cannot tell it from an order that really traded at
  zero.
- **Reject with a derived size of zero instead**, reusing `SIZE_BELOW_ONE_SHARE`. Rejected: the size
  is not the problem, and the reason would misdescribe the cause.

## Consequences

- It **departs from the challenge statement's "store the rejection"** for the no-price case only.
  Constitution VII requires a departure to be recorded here and stated in the README; both are done
  in this change.
- The spec was amended in the same change and the amendment is dated and marked: FR-010, user story
  1's scenario 8, the "instrument with no price" edge case and SC-009 now describe the refusal.
- **Constitution IV's parenthetical ("no funds, no holdings, no price") is stale**, as is the
  `reason` example in ADR-0005. Neither is violated — a no-price submission is no longer a rejection,
  so no rejection lacks a reason — but the list names an outcome that can no longer occur. Resolving
  it is a `PATCH` amendment through `/speckit-constitution`, flagged in
  `specs/003-order-placement/plan.md` and deliberately not done silently. ADR-0005 is accepted and
  is not edited.
- A client that wants to trade an instrument with no published close has to send a `LIMIT` order,
  which is the honest request for it: naming your own price is the only way to buy something the
  market has not priced.
