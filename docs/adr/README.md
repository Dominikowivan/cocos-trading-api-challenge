# Architecture Decision Records

One file per decision that had alternatives worth weighing. Findings and observations are not
decisions and live in the [journal](../journal), which is where each of these was thought through
first.

An ADR is never edited once accepted. When a decision changes, the old record is marked
`Superseded by ADR-00XX` and a new one is written. **These files are the authority on what
currently holds; the journal is the authority on how it was reached.**

| # | Decision | Status |
|---|---|---|
| [0001](0001-java-21-and-spring-boot-4.md) | Java 21 and Spring Boot 4.1.2 | Accepted |
| [0002](0002-orders-as-ledger.md) | `orders` is a ledger, the portfolio is a projection | Accepted |
| [0003](0003-weighted-average-cost.md) | Weighted average cost for positions, not FIFO | Accepted |
| [0004](0004-reserve-funds-on-new-orders.md) | `NEW` limit orders reserve funds and holdings | Accepted |
| [0005](0005-order-status-history.md) | An append-only `order_status_history` table | Accepted |
| [0006](0006-dedicated-cancel-endpoint.md) | A dedicated endpoint for cancelling an order | Accepted |
| [0007](0007-out-of-scope-matching-engine-and-auth.md) | No matching engine and no authentication | Accepted |
| [0008](0008-spec-kit-tooling-versioned.md) | The agent tooling is versioned with the project | Accepted |
| [0009](0009-flyway-and-provided-database-as-staging.md) | Flyway, and the provided database treated as staging | Accepted |
| [0010](0010-testcontainers-with-postgresql-18.md) | Tests and local development run on the same PostgreSQL as staging | Accepted |
| [0011](0011-ci-with-github-actions-and-jacoco.md) | CI on GitHub Actions, with coverage reported | Accepted |
| [0012](0012-ledger-anomalies-are-reported-not-hidden.md) | A ledger anomaly is served as-is and logged as an error | Accepted |
| [0013](0013-unknown-money-is-absent-never-zero.md) | An unknown money figure is absent, never zero | Accepted |
| [0014](0014-data-access-with-spring-data-jpa.md) | Data access with Spring Data JPA, mapped by hand | Accepted |
| [0015](0015-the-account-snapshot-is-the-folds-contract.md) | The fold returns one account snapshot | Accepted |
| [0016](0016-decimal-scale-and-rounding-policy.md) | One decimal scale and rounding policy | Accepted |
| [0017](0017-layers-with-a-service-that-orchestrates.md) | Layers, with a service that orchestrates and a pure model | Accepted |
| [0018](0018-architecture-rules-as-archunit-tests.md) | The layering rules are enforced by ArchUnit tests | Accepted |
