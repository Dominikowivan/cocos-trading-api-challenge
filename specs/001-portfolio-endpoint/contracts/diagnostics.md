# Contract: Portfolio Diagnostics

**Feature**: `001-portfolio-endpoint` | **Decision**: research [R-011](../research.md#r-011-diagnostics-are-a-domain-port-not-a-logger-call)

The two diagnostics are functional requirements (FR-019, FR-022) with an acceptance criterion
(SC-009), not logging taste: they are the only way either anomaly is ever noticed. So they have a
contract, like the HTTP response.

The domain declares the port; the adapter decides level, wording and destination.

```text
PortfolioDiagnostics
    shortPositionDetected(UserId userId, String ticker, BigDecimal quantity)
    marketDataMissing(UserId userId, String ticker)
```

| Event | Level | Carries | Means | Record |
|---|---|---|---|---|
| `shortPositionDetected` | **ERROR** | user id, ticker, quantity | an invariant of ours is broken — the ledger holds a short the API forbids. *Fix our data.* | [ADR-0012](../../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md) |
| `marketDataMissing` | **WARN** | user id, ticker | the world has not told us a price yet. A legitimate state. *Watch the feed.* | [ADR-0013](../../../docs/adr/0013-unknown-money-is-absent-never-zero.md) |

The level difference is load-bearing and is the substance of the ADR pair: using one level for both
would make both ignorable. Alerting can be built on the distinction — error means act, warning
means observe.

## Guarantees

- **Neither event changes the HTTP status.** Both requests answer `200`. The alarm is the record,
  not a rejection (spec Assumptions, confirmed by the author on 2026-11-15).
- **One record per affected position per request.** SC-009 requires *exactly one* error entry for
  user `1` on the provided dataset, naming `BMA` and `-10`. Reading the portfolio twice produces
  two — the projection is per request and nothing is deduplicated.
- **Emitted by the projection, not by the web adapter**, so the same anomaly is reported whoever
  calls the projection.
- **The user id is always present**, so a record can be traced to the account without correlating
  against the request log.
- No diagnostic for a zero quantity (FR-025) or for a user with no orders (FR-014): neither is an
  anomaly.

## Logging adapter

SLF4J, logger named after the adapter class, messages with the three fields in a fixed order so
they are greppable:

```text
ERROR  short position served as-is: userId=1 ticker=BMA quantity=-10
WARN   no market data for held instrument: userId=3 ticker=PGR
```

Nothing asserts on these strings. Tests assert on the port through a recording fake, which is why
the port exists; the wording above is the adapter's and may change without touching a test.

Whether this adapter also increments a Micrometer counter per event is open for the author
([research Q-002](../research.md#q-002-a-micrometer-counter-alongside-each-diagnostic)). It would
be an addition, not a substitute: a counter answers *how often*, these records answer *to whom*.

## The provided dataset is permanently noisy

Every portfolio read for user `1` logs one error, because the seeded ledger really is broken
(`SELL FILLED` 30 BMA against `BUY FILLED` 20). That is accepted and correct behaviour, stated here
so nobody treats it as a bug in the endpoint.
