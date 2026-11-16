# ADR-0016: One decimal scale and rounding policy, stated in one place

- **Status:** Accepted
- **Date:** 2026-11-15
- **Research:** [R-007](../../specs/001-portfolio-endpoint/research.md), feature `001-portfolio-endpoint`

## Context

[Constitution II](../../.specify/memory/constitution.md) requires every division and every rescale
to state its scale and `RoundingMode`. It does not say which, and the portfolio's acceptance
criteria are figures computed by hand: `-0.45` for PAMP, `-8.20` for METR. Two reviewers
reproducing those under different policies would disagree and both be right, so the policy has to
be a decision rather than whatever each call site passes.

There are only two genuine divisions in the feature: the total-return ratio, and the weighted
average inside the cost-basis walk ([ADR-0003](0003-weighted-average-cost.md)). Everything else is
multiplication and addition over `NUMERIC(10,2)` values and whole share counts, which is exact.

## Decision

| Figure | Scale | Rounding |
|---|---|---|
| Money on the wire — market value, settled cash, available cash, total account value | 2 | `HALF_UP` |
| Total return, in percent units | 2 | `HALF_UP` |
| Internal average cost per share | 10 | `HALF_UP` |
| Quantity | 0 | exact; `size` is an `INT` |

The policy lives in **one class**, `DecimalPolicy`, in the domain. Nothing else names a scale or a
`RoundingMode`: a caller asks for `money(...)`, `percent(part, whole)` or `averageCost(cost,
shares)`. Money arithmetic is exact, so `setScale(2, HALF_UP)` on an amount is a normalisation of
the output, not a loss of information.

## Alternatives considered

- **`HALF_EVEN` (banker's rounding) everywhere.** The accounting convention, and the better choice
  when rounding is applied repeatedly to money that is then summed. Rejected because nothing here
  rounds money: only the two derived figures round, and each rounds once, so `HALF_EVEN`'s
  advantage does not apply while its disadvantage does — a reviewer's hand calculation disagreeing
  at a `.005` edge. **Worth revisiting the first time a feature rounds intermediate money.**
- **`MathContext.DECIMAL64` for the internal average.** Fewer decisions to make. Rejected:
  significant digits are not a scale, so the guarantee is harder to state, and it rounds
  `HALF_EVEN` silently, contradicting the table above.
- **Return as a ratio (`-0.0045`) rather than percent units (`-0.45`).** Cleaner arithmetic.
  Rejected: the statement asks for `rendimiento total (%)` and the acceptance figures are percent
  units. The field name carries the unit, which is the cheapest defence against a client dividing
  by 100 twice.
- **Scale 4 on the return, for basis-point precision.** Rejected: it publishes precision the
  inputs do not have, and it would make the acceptance figure `-0.45` an approximation of the
  contract rather than the contract.
- **Let each call site state its own scale, as the constitution literally requires.** Rejected as
  the thing that produces two defensible answers to one question about money; stating it once
  satisfies the same principle more strongly.

## Consequences

- **The acceptance numbers are reproducible on paper**, which is what an exercise judged on
  hand-computed figures needs. `37,034 − 37,200` over `37,200` settles at `-0.45` under this policy
  and under a spreadsheet's default, and the test asserts the same digits.
- Scale 10 for the internal average is wide enough that the second decimal of the return is never
  in doubt, and it is *stated* rather than inherited from a `MathContext` default.
- Every comparison in code and in tests uses `compareTo`, never `equals`, so a value scaled to 2
  and the same value scaled to 10 are equal where it matters. `DecimalPolicy` makes that cheap; it
  does not make `equals` safe, and nothing uses it.
- Changing a scale or a rounding mode is a one-file change with a visible blast radius — and a new
  ADR, because the published figures would move.
