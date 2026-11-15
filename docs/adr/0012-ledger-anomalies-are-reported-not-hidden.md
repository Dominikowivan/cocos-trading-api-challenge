# ADR-0012: A ledger anomaly is served as-is and logged as an error, not hidden and not fatal

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [03portfolio](../journal/03portfolio.md), *Primera clarificacion*

## Context

The API forbids a user from going short: the statement requires rejecting a `SELL` when the user
does not hold enough shares, and [ADR-0004](0004-reserve-funds-on-new-orders.md) extends that to
shares already committed by a pending `SELL`. Yet the provided dataset folds to a negative
position — user `1` holds BMA `-10`, measured on `db/seed/V1000__given_dataset.sql`, from a
`SELL FILLED` of 30 against a `BUY FILLED` of 20.

So the portfolio has to answer a question the validation rules say cannot happen: what does a read
do when the ledger already contradicts an invariant the writer enforces? The violation is in the
past and is not repairable by the reader. Three things are in tension — not breaking the user's
portfolio, not telling the user something false about their own account, and not letting a
violated money invariant sit unnoticed.

This generalises past BMA. Any invariant enforced on write can be found broken on read, by a bug,
a manual fix in the database, or data that predates the rule.

## Decision

The anomaly is **reported as-is and logged as an error**. The portfolio lists the position with
quantity `-10` and market value `-15,028.00`, that negative value counts toward the total account
value, and the request succeeds. In the same pass, an **error-level** record carrying the user id,
the instrument and the quantity is emitted.

The user gets the truth about their account; the technical team gets a signal it cannot miss. The
read never repairs the ledger and never rejects on account of it.

## Alternatives considered

- **Reject the request.** Loudest possible signal, and it does make the breakage impossible to
  ignore. Discarded because the cost lands on the wrong party: one bad row would deny the user
  access to their entire portfolio — cash, every other position — over a holding they cannot fix.
  A read-only endpoint refusing to describe state that demonstrably exists is also the one answer
  that is certainly wrong.
- **Filter the position out of the list.** Keeps the response clean and the arithmetic tidy.
  Discarded as the worst option: it hides money. The total would become `904,784.00` while the
  user owns something that moves it, and the user cannot see why the two disagree. Silence about a
  broken invariant is how it survives to production.
- **Report it, log it, and also mark it in the response** with a field saying this position is
  anomalous. The closest alternative to what was chosen, and it loses on contract cost rather than
  on honesty: it adds a field to every position response that exists only because some data is
  bad, which every client must then understand and handle forever. The log already reaches the
  party who can act on it; the user is served by seeing the real quantity, not by being told our
  invariant broke.
- **Clamp the quantity to zero.** Discarded immediately: it fabricates a holding that does not
  match the ledger, which is exactly the second source of truth ADR-0002 exists to prevent.

## Consequences

- **The portfolio read needs an anomaly check it would not otherwise have**, and it is an
  observability requirement rather than a presentational one: the error log *is* the detection
  mechanism, so it is specified in the feature, not left to taste.
- The error level is load-bearing. It separates this case from the merely unknown
  ([ADR-0013](0013-unknown-money-is-absent-never-zero.md), a warning): error means an invariant of
  ours is broken, warning means the world has not told us something yet. Using one level for both
  would make both ignorable.
- The provided dataset is permanently noisy: any portfolio read for user `1` logs an error. That
  is accepted, and is the correct behaviour — the data really is broken.
- Total return is not reported for such a position. A negative quantity yields a negative cost
  basis, and `(market value − cost) / cost` stops meaning "up or down"; reporting it anyway would
  publish a number nobody can interpret. That follows the absent-not-zero rule in ADR-0013.
- The same treatment is now the expected shape for any other invariant found broken on read, which
  is the point of recording it as a decision rather than as a special case for BMA.
- The statement does not consider this case at all, so this is not a departure from it and
  principle VII's README obligation does not apply. This record is where the behaviour lives.
