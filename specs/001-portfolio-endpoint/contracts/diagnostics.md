# Contract: Portfolio Diagnostics

**Feature**: `001-portfolio-endpoint` | **Decision**: research [R-011](../research.md#r-011-diagnostics-are-a-domain-port-not-a-logger-call)

The two diagnostics are functional requirements (FR-019, FR-022) with an acceptance criterion
(SC-009), not logging taste: they are the only way either anomaly is ever noticed. So they have a
contract, like the HTTP response.

**Amended 2026-11-15** (constitution 2.0.0, [ADR-0017](../../../docs/adr/0017-layers-with-a-service-that-orchestrates.md)):
the diagnostics were first a domain port the projection called mid-loop. The model now **returns**
the anomalies as values and `PortfolioService` logs them. The levels, the fields and the guarantees
below did not change.

```text
Portfolio
    shortPositions()     -> List<Position>   the positions with a negative quantity
    unpricedPositions()  -> List<Position>   the positions with no known close
```

| Anomaly | Level | Carries | Means | Record |
|---|---|---|---|---|
| short position | **ERROR** | user id, ticker, quantity | an invariant of ours is broken — the ledger holds a short the API forbids. *Fix our data.* | [ADR-0012](../../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md) |
| missing market data | **WARN** | user id, ticker | the world has not told us a price yet. A legitimate state. *Watch the feed.* | [ADR-0013](../../../docs/adr/0013-unknown-money-is-absent-never-zero.md) |

The level difference is load-bearing and is the substance of the ADR pair: using one level for both
would make both ignorable. Alerting can be built on the distinction — error means act, warning
means observe.

## Guarantees

- **Neither event changes the HTTP status.** Both requests answer `200`. The alarm is the record,
  not a rejection (spec Assumptions, confirmed by the author on 2026-11-15).
- **One record per affected position per request.** SC-009 requires *exactly one* error entry for
  user `1` on the provided dataset, naming `BMA` and `-10`. Reading the portfolio twice produces
  two — the valuation is per request and nothing is deduplicated.
- **Detected by the model, logged by the service, never by the controller**, so the same anomaly is
  reported whoever calls the service.
- **The user id is always present**, so a record can be traced to the account without correlating
  against the request log.
- No diagnostic for a zero quantity (FR-025) or for a user with no orders (FR-014): neither is an
  anomaly.

## Logging

SLF4J, logger named after `PortfolioService`, messages with the three fields in a fixed order so
they are greppable:

```text
ERROR  short position served as-is: userId=1 ticker=BMA quantity=-10
WARN   no market data for held instrument: userId=3 ticker=PGR
```

Nothing asserts on these strings. Tests assert on `shortPositions()` and `unpricedPositions()`,
which are plain values, so no fake is needed; the wording above may change without touching a test.

Whether the service also increments a Micrometer counter per event is open for the author
([research Q-002](../research.md#q-002-a-micrometer-counter-alongside-each-diagnostic)). It would
be an addition, not a substitute: a counter answers *how often*, these records answer *to whom*.

## The provided dataset is permanently noisy

Every portfolio read for user `1` logs one error, because the seeded ledger really is broken
(`SELL FILLED` 30 BMA against `BUY FILLED` 20). That is accepted and correct behaviour, stated here
so nobody treats it as a bug in the endpoint.
