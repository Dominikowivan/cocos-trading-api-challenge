# ADR-0013: An unknown money figure is absent, never zero, and it suppresses the account total

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [03portfolio](../journal/03portfolio.md), *Primera clarificacion*

## Context

A position's market value is quantity times the instrument's latest `close`. An instrument can be
held and have no `marketdata` row at all — it has not traded yet, or the feed has not delivered
it.

Measured on `db/seed/V1000__given_dataset.sql`: three of the 66 instruments have no market data —
`PGR`, `IRCP`, and `ARS`, the `MONEDA` instrument. **`ARS` is held, by every user who has ever
received a transfer**: user `1` has `CASH_IN` and `CASH_OUT` rows in it, and settled cash of
`753,000.00`. It is excluded from the positions list because
it is cash, not a position, which is a rule that exists for its own reasons and is not about
prices. That exclusion has to happen before this decision applies, or the most ordinary account in
the dataset would have an unpriced "position" and no account total at all.

With `ARS` set aside, **no user in the seed holds `PGR` or `IRCP`**, and the API cannot put them
there: a `MARKET` order needs a `close` to execute against and there is none, and a `LIMIT` order
never executes ([ADR-0007](0007-out-of-scope-matching-engine-and-auth.md)). So the case is reached
only by data written outside the API — a backfill, a migration, a manual fix — or by a feed that
stops covering an instrument already held. It is genuinely defensive, and saying so precisely
matters more than claiming a user path that does not exist.

Unlike [ADR-0012](0012-ledger-anomalies-are-reported-not-hidden.md), **nothing is broken here**.
No invariant of ours is violated. We simply do not know a price, and the question is how an API
about money says "I do not know" — and what the total account value is when one of its terms is
unknown.

## Decision

Two rules.

**An unknown money figure is reported as absent, never as `0`.** The position is still listed with
its quantity, which we do know; its market value and total return are absent. `0` is a valid
amount and would assert that the holding is worthless, which is a different and false claim.

**If any listed position has an unknown market value, the total account value is absent too.** Not
a partial sum over the positions we could price.

The condition emits a **warning**, naming the user and the instrument — not an error, because an
unpriced instrument is a legitimate state, and not silence, because a suppressed account total
needs an explanation somewhere.

## Alternatives considered

- **Market value `0`, return `-100%`.** Nothing is null, every field keeps its type, and clients
  need no special handling. Discarded because it is a lie with a number attached: it states the
  holding is worth nothing and that the user lost everything on it. A wrong number is worse than a
  missing one, because it looks usable.
- **Exclude the position from the list.** Makes the arithmetic complete again. Discarded for the
  same reason as in ADR-0012: the user stops seeing shares they own, and the response is clean
  precisely by omitting what is inconvenient.
- **Report a partial account total over the priced positions.** The tempting middle ground.
  Discarded because the result is a plausible number that is wrong, with
  no marker saying so. A user comparing it against their holdings finds a gap they cannot explain,
  and nothing in the response admits a term is missing. Absent is recoverable; quietly wrong is
  not.
- **Fall back to the last known price from another source, or to the order price.** Produces a
  defensible estimate. Discarded as out of scope and dishonest by framing: the field means "latest
  close", and silently serving something else makes every consumer's interpretation wrong. If
  valuation fallbacks are ever wanted, they are a feature with their own ADR, not a default.
- **Treat it as an error, like the short position.** Discarded: it is not a broken rule, and
  levelling the two together devalues the one that is.

## Consequences

- **One unpriced holding suppresses the whole account total.** This is the real cost of the
  decision and is accepted deliberately: the alternative is a wrong figure about money. It also
  gives the behaviour a sharp edge that gets noticed and fixed, instead of a quiet inaccuracy.
- Clients must handle an absent market value, return and account total. The API's contract says so
  explicitly rather than making them discover it.
- **Absent-not-zero is now a general rule for this API**, not a rule about missing prices. It is
  why a negative position reports no total return rather than `0` (ADR-0012), and it applies to
  any money figure that is unknown rather than nil. A genuinely zero amount — cash for a user who
  has never traded — is still `0`, because that value is known.
- The path needs a **constructed fixture**: once `ARS` is excluded as cash, the seed holds no
  unpriced instrument, so a test cannot rely on it. Noted in the spec so the case is not declared
  covered by the dataset.
- **`ARS` makes the ordering of the two rules load-bearing.** Excluding cash from the positions
  list comes first; suppressing the total for an unpriced position comes second. Reversed, user
  `1` gets a `null` account total on the happy path. A test asserts user `1`'s total is
  `889,756.00` precisely to pin that order down.
- Warning versus error is a contract between this codebase and whoever watches it: error means fix
  our data, warning means watch the feed. Alerting can be built on that distinction.
