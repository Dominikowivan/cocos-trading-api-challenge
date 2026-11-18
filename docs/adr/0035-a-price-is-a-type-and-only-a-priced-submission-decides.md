# ADR-0035: A price is a type, and only a priced submission can decide

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [07OrderPlacement](../journal/07OrderPlacement.md)

## Context

A submission is answered against a price: a LIMIT order's own, a MARKET order's latest close. Until
this change those two prices arrived through different doors and only one of them was guarded.

`Submission` refused a LIMIT price outside `0.01 .. 99999999.99`. The MARKET path read `close`
straight out of `marketdata`, where `V1__given_schema.sql` declares it `NUMERIC(10, 2)` with no
`CHECK`, and `MarketDataMapper` dropped only the `null`s (ADR-0034). So a published close of
`0.00` or a negative one reached the decision untouched, and the decision was stated in pesos:

- `BUY` by size compared `cash >= size x 0`, which is `0 >= 0`. **Filled with no money.**
- `SELL` by size compared `shares x 0 >= size x 0`, which is `0 >= 0`. **Filled without the shares**,
  against the statement's own rule that a sale is validated against the holdings.
- `BUY` by amount divided the amount by the close. **`ArithmeticException`, answered `500`.**

Multiplying both sides of the sufficiency test by the price is a no-op in arithmetic, and the units
said so, but nothing in the types did: everything was `BigDecimal`. When the price went to zero the
multiplication erased the invariant along with the units.

The same shape allowed a second, quieter problem. `Submission.decide(snapshot, price)` accepted any
`BigDecimal` as the price. The service passed the one `priceGovernedBy` returned, by discipline
only; the object could not tell a governing price from any other number.

Separately, the bounds of the ledger's money columns were written twice — in `Submission` for a
price and in `TradeQuantity` for a total amount — as the same two literals.

## Decision

**`Price` is a type.** Every price the system acts on is built by `Price.usable`, which answers
with nothing unless the amount is positive and inside the ledger's money bounds, at its scale.
`Closes` keeps only usable prices, so an instrument quoting `0.00` answers exactly like `PGR`,
which never quoted at all: `400`, nothing written, the message of ADR-0034.

**Only a `PricedSubmission` decides.** `Submission` validates the shape of the request and cannot
answer anything; its one way out is `priceGovernedBy`, which returns a `PricedSubmission` or
nothing. The price inside is therefore always the one the order's *kind* dictated, and there is no
signature left through which a caller could decide a MARKET order at a price of its own choosing.

**The money bounds are stated once**, in `DecimalPolicy.fitsTheMoneyColumn`, which a price and a
total amount both ask.

## Rationale

The rule "a price is positive" existed; it was attached to the request parser instead of to the
concept, so the path that did not go through the parser never met it. A value object is the only
place a rule like that cannot be routed around: `Price.wholeSharesIn` cannot divide by zero because
zero never becomes a `Price`.

Folding an unusable close into "does not quote" rather than inventing a fourth refusal keeps
ADR-0034's table at exactly three recorded rejection reasons, all of them about the user's money,
and gives a caller one case to handle instead of two that differ only in why the market is useless.

## Alternatives considered

- **A `CHECK (close > 0)` on `marketdata`.** Worth having, and not a substitute: the market data is
  loaded from outside the application, and the rule belongs where the decision is made.
- **Guarding the close in `OrderSubmissionService`.** Rejected: it puts a business invariant in the
  orchestration, and the next reader of a price would have to remember it again.
- **`Money` and `Shares` types throughout, so the sufficiency test cannot compare pesos to shares
  at all.** The honest end state, and larger than this change. `Price` closes the hole that was
  reachable; the rest is recorded in the journal as the next step.
- **Keeping `decide(snapshot, price)` and documenting the order of calls.** Rejected: a comment is
  not a constraint.

## Consequences

- A MARKET order for an instrument whose latest close is `0.00`, negative, or beyond the money
  column now answers `400` with "has no market price", where before it filled or returned `500`.
  No seeded instrument is in that state, so no existing behaviour changes on the provided dataset.
- **A position valued at an unusable close is now an unpriced position** in the portfolio, since
  `Closes` is shared. That is the honest reading and it is already a case the portfolio handles:
  the position reports no market value and the total account value is absent.
- `Order`, the read model for rows already in the ledger, still carries a `BigDecimal`. `Price`
  guards what enters the ledger; history is reported as it was written.
