# ADR-0017: Layers, with a service that orchestrates and a model that never reaches persistence

- **Status:** Accepted
- **Date:** 2026-11-15
- **Constitution:** principle VIII as of 2.0.0, replacing "Ports and Adapters" from 1.1.0

## Context

The first implementation of the portfolio endpoint followed constitution 1.x, principle VIII
(Ports and Adapters). It worked and its tests were green, but the shape it produced was this:

```text
PortfolioController → PortfolioQueryService → PortfolioProjection ─┬→ AccountLedger → LedgerEntries (port) → JPA
                                                                    ├→ LatestPrices (port) → JPA
                                                                    └→ PortfolioDiagnostics (port) → log
```

The service checked that the user existed and delegated everything else. `PortfolioProjection`, a
model object, fetched prices and logged; `AccountLedger`, another model object, fetched the orders.
To follow one request a reader had to open four classes and two Spring `@Bean` definitions, and the
order of the reads was a consequence of which object called which, not something written down in
one place. The model was free of JPA imports, as the principle required, but it still **drove**
persistence: it decided when to read and what.

On review, the author could not defend that shape: the flow of a request was not visible anywhere.

## Decision

Four layers, with one direction of calls:

```text
controller → service → persistence (repositories + mappers)
                     → model (pure)
```

- **The controller** translates HTTP: path id in, one service call, response DTO out.
- **The service** orchestrates, in order and in one method: it reads through Spring Data repositories,
  maps entities and rows into model values with hand-written mappers, asks the model to compute,
  performs the side effects (today, logging the anomalies) and returns the model to the controller.
  It is also the transaction boundary.
- **The model** receives data and returns data. It holds no repository, no port, no service and is not a
  Spring bean. What it detects, it returns as a value: `Portfolio.shortPositions()` and
  `Portfolio.unpricedPositions()`, which the service logs at `ERROR` and `WARN`
  ([ADR-0012](0012-ledger-anomalies-are-reported-not-hidden.md),
  [ADR-0013](0013-unknown-money-is-absent-never-zero.md)).
- **Persistence** is Spring Data repositories over JPA entities. Entities leave the package only through a
  mapper ([ADR-0014](0014-data-access-with-spring-data-jpa.md)).

For the portfolio, the whole path is now `PortfolioService.portfolioOf`: check the user, read the
ledger, fold it, read the closes for what is held, value the portfolio, log its anomalies.

## Alternatives considered

- **Keep ports and adapters, but move the orchestration into the service.** The model would stop
  holding ports, and the service would call `LedgerEntries` and `LatestPrices` interfaces implemented
  in an adapter. Rejected: each port would have exactly one implementation and no second one in
  sight, so the interfaces would add a layer of indirection to read past without buying anything.
  Swapping the database is not a requirement of this exercise.
- **Keep the 1.x shape.** It met every functional requirement. Rejected for the reason in the
  context: a design its author cannot walk a reviewer through is a liability in an exercise whose
  evaluation is the reasoning.
- **JPA entities as the model.** The usual layered Spring layout, and the least code: no mappers.
  Rejected because the model would then carry JPA annotations and lazy proxies, and the
  constitution's requirement that the model is tested as plain Java (V) would rest on discipline
  rather than on structure. The mappers are the price, and it is small: three static classes.
- **Service interfaces with an `Impl`.** Rejected under constitution IX: one implementation and no
  boundary behind it.

## Consequences

- The path of a request reads top to bottom in one method, and the transaction boundary is on the
  same class that issues the queries.
- **Model tests need no test doubles.** They build a list of orders and a map of closes and
  assert on the returned value. The recording fakes for the ledger and the diagnostics were deleted.
  The suite kept its 78 tests and every figure.
- The anomaly log lines now come from `PortfolioService`, not from a dedicated adapter class. Their
  levels and wording are unchanged. If a second consumer of the anomalies appears (a metric, an
  alert), it reads the same `shortPositions()` and `unpricedPositions()`.
- The service depends on concrete repositories. Testing the service in isolation would mean mocking them,
  which constitution V forbids for persistence; the service is covered end to end by the
  Testcontainers functional tests instead, which is where its orchestration is observable.
- Order submission will follow the same shape: the service reads the ledger, feeds the same
  `AccountLedger`, asks the snapshot whether the order fits, and writes.
