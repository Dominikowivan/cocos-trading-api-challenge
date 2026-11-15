# ADR-0007: No matching engine and no authentication

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [01PrimeraLectura](../journal/01PrimeraLectura.md), *Enunciado*

## Context

Two things a reviewer would expect from a trading API are absent here, and both absences are
instructed rather than overlooked. The statement says the market does not need to be simulated,
and it does not ask for authentication. Leaving either out silently would read as an omission, so
both are recorded as decisions with their boundary stated.

## Decision

**No matching engine.** A `LIMIT` order is created `NEW` and stays `NEW`. The only transition out
of that state is the user cancelling it. There is no order book, no counterparty, no price-time
priority and no partial fills. A `MARKET` order fills immediately at the last close, which is the
statement's replacement for the whole book.

**No authentication.** The user is a path parameter. Any request can read any portfolio and
submit orders on anyone's behalf.

## Alternatives considered

- **Implementing a minimal matching engine** so limit orders can execute against each other.
  Discarded because it contradicts the statement, and because the seeded data has no counterparty
  to match against: the other users hold no orders. Building it would mean inventing market
  behaviour the exercise explicitly excluded.
- **Adding authentication anyway**, as a demonstration. Discarded because a hand-rolled auth layer
  nobody asked for is more likely to be wrong than to be impressive, and it would obscure the code
  that is actually being evaluated.

## Consequences

- While this holds, a `LIMIT` order is terminal in practice. That is a property of the current
  scope, not a design assumption: adding a matching engine is the natural next step, and nothing
  in this solution is justified by limit orders staying in `NEW` forever.
- A useful corollary: a `LIMIT` order carries its own price, so it does not need `marketdata` at
  all. An instrument with no price rows can still receive a limit order; what it cannot receive is
  a `MARKET` order, which has no price to execute at.
- Without authentication, the user id is untrusted input. Every query is still scoped by it, so
  adding authentication later means replacing where the id comes from, not rewriting the queries.
- This is the one decision in the repository that would not survive production. It is stated
  plainly in the README rather than left for the reader to notice.
