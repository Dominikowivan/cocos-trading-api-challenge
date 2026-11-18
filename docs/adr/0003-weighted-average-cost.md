# ADR-0003: Weighted average cost for positions, not FIFO

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [01PrimeraLectura](../journal/01PrimeraLectura.md), *Endpoints del enunciado*

## Context

The statement asks each position for its total return, and defines nothing else:

```text
total return (%) = (market value − cost) / cost × 100
```

`market value` is unambiguous — quantity times the last close. `cost` is not, as soon as the user
bought more than once at different prices and sold part of the holding in between.

Worked example from the journal: buy 10 at 10, buy 10 more at 20, then sell 10. Ten shares remain
and the last close is 20. What those ten shares cost depends entirely on which ten were sold.

| Method | Remaining cost | Total return |
|---|---|---|
| Weighted average | 150 | 33% |
| FIFO | 200 | 0% |

Both are legitimate accounting methods. They produce different numbers from the same data, so the
choice has to be made explicitly and stated.

## Decision

Weighted average cost.

## Alternatives considered

- **FIFO (first in, first out).** Discarded for two reasons. It answers a different question —
  *which of the purchases are still unsold* — and none of the three values the statement asks for
  needs that. And it costs more state: a queue of lots to maintain and walk, against two
  accumulators (total cost and total quantity).
- **LIFO.** Same structural cost as FIFO, without even FIFO's argument.

## Consequences

- The fold stays cheap: two accumulators per instrument, computed in one pass over the user's
  `FILLED` orders.
- A closed and reopened position resets its cost basis, which is the correct behaviour under this
  method and is covered by a test.
- Tax reporting, where applicable, typically requires lot tracking. This API does not produce tax
  figures, so the question does not arise here — but it is the first thing that would force a
  revisit, and it would be a new ADR superseding this one.
- The method is stated in the README, because two reviewers computing the expected number by hand
  with different methods would disagree and both be right.
