# ADR-0004: `NEW` limit orders reserve funds and holdings

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [01PrimeraLectura](../journal/01PrimeraLectura.md), *Anomalías y estados de las órdenes*

## Context

The statement requires rejecting an order whose amount exceeds what the user has available, and it
makes no distinction between `MARKET` and `LIMIT` when it says so. But a `LIMIT` order does not
execute: it is born `NEW` and stays there, so nothing moves the balance.

That gap is exploitable. With 1,000 in cash, a user can submit ten `LIMIT` orders of 1,000 each.
Every one of them passes validation, because each is checked against a balance that never
changes, and the account ends up with 10,000 committed against 1,000 that exist.

The statement also asks for "cash available to trade", which is buying power, not an accounting
balance. Those are different numbers whenever an order is pending.

## Decision

A `NEW` limit order reserves what it commits: cash for a `BUY`, shares for a `SELL`. Available
cash is the balance minus everything reserved by open orders. Validation is performed against
available cash, never against the raw balance.

## Alternatives considered

- **Validate without reserving.** What the statement literally says, and the cheapest to build.
  Discarded because validating without reserving protects nothing: the second order sees the same
  balance as the first.
- **Reserve only at execution time.** Meaningless here, since under
  [ADR-0007](0007-out-of-scope-matching-engine-and-auth.md) a limit order never executes.

## Consequences

- **Releasing a reservation becomes a functional requirement.** Holding funds without a way to
  give them back is not an acceptable end state, so the API exposes cancellation
  ([ADR-0006](0006-dedicated-cancel-endpoint.md)) even though the statement does not ask for it.
- Available cash and account balance are two distinct values, and the API must not conflate them.
  The portfolio reports buying power, as asked.
- A `SELL LIMIT` reserves shares for the same reason: so the same lot is not sold twice by two
  pending orders.
- Reservation makes the concurrent case explicit rather than hiding it: two simultaneous orders
  must not both pass validation, which requires a lock or a serialisable transaction around read
  and insert.
- It is a deliberate departure from the literal statement, taken because the literal reading
  leaves a hole in a financial API. The README says so in those terms.
