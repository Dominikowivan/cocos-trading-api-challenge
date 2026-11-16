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
| [0011](0011-ci-with-github-actions-and-jacoco.md) | CI on GitHub Actions, with coverage reported | Accepted; coverage enforcement superseded by [0020](0020-static-analysis-on-sonarqube-cloud.md) |
| [0012](0012-ledger-anomalies-are-reported-not-hidden.md) | A ledger anomaly is served as-is and logged as an error | Accepted |
| [0013](0013-unknown-money-is-absent-never-zero.md) | An unknown money figure is absent, never zero | Accepted |
| [0014](0014-data-access-with-spring-data-jpa.md) | Data access with Spring Data JPA, mapped by hand | Accepted |
| [0015](0015-the-account-snapshot-is-the-folds-contract.md) | The fold returns one account snapshot | Accepted |
| [0016](0016-decimal-scale-and-rounding-policy.md) | One decimal scale and rounding policy | Accepted |
| [0017](0017-layers-with-a-service-that-orchestrates.md) | Layers, with a service that orchestrates and a pure model | Accepted |
| [0018](0018-architecture-rules-as-archunit-tests.md) | The layering rules are enforced by ArchUnit tests | Accepted |
| [0019](0019-spring-java-format-checked-in-the-build.md) | Spring Java Format, checked in the build | Accepted |
| [0020](0020-static-analysis-on-sonarqube-cloud.md) | Static analysis on SonarQube Cloud, gating develop | Accepted; partly superseded by [0021](0021-the-quality-gate-blocks-pull-requests.md) |
| [0021](0021-the-quality-gate-blocks-pull-requests.md) | The quality gate blocks pull requests, not pushes to develop | Accepted |
| [0022](0022-sonar-way-comprehensive-profile.md) | Java is analysed with the Sonar way comprehensive profile | Accepted |
| [0023](0023-dependency-and-secret-scanning-with-trivy.md) | Dependencies and secrets are scanned with Trivy in CI | Accepted |
| [0024](0024-every-response-is-validated-against-the-contract.md) | One living API contract, and every response in the web tests is validated against it | Accepted |
| [0025](0025-the-api-runs-in-docker.md) | The API runs in Docker with one command | Accepted |
