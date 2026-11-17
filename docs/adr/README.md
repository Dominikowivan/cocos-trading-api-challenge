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
| [0006](0006-dedicated-cancel-endpoint.md) | A dedicated endpoint for cancelling an order | Superseded by [0041](0041-the-order-cancellation-endpoint-shape.md) |
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
| [0026](0026-actuator-exposes-health-and-prometheus.md) | Actuator exposes health and Prometheus metrics, and nothing collects them here | Accepted |
| [0027](0027-instrument-search-is-one-short-jpql-query.md) | Instrument search is one short JPQL query | Accepted |
| [0028](0028-search-results-are-paginated.md) | Search results are paginated, with a total count | Accepted; partly superseded by [0032](0032-paging-is-spring-datas-pageable.md) |
| [0029](0029-the-provided-dataset-is-an-opt-in-repeatable-seed.md) | The provided dataset is an opt-in repeatable seed, not a versioned migration | Accepted |
| [0030](0030-the-schema-requires-columns-not-business-rules.md) | The schema requires the columns the code cannot read without, and nothing more | Accepted |
| [0031](0031-database-access-has-a-deadline-and-a-bounded-pool.md) | Database access has a deadline and a bounded pool, and running out of either is a 503 | Accepted |
| [0032](0032-paging-is-spring-datas-pageable.md) | Paging is Spring Data's `Pageable`, and out-of-range values are adjusted, not rejected | Accepted |
| [0033](0033-the-submission-endpoint-shape.md) | One submission endpoint under the user, where every recorded outcome is a `201` | Accepted |
| [0034](0034-an-unpriced-instrument-refuses-the-submission.md) | An instrument with no price refuses the submission; it is not a recorded rejection — **a departure from the statement** | Accepted |
| [0035](0035-a-price-is-a-type-and-only-a-priced-submission-decides.md) | A price is a type, and only a priced submission can decide | Accepted |
| [0036](0036-submissions-are-serialised-per-user.md) | Submissions are serialised per user by a pessimistic lock on their `users` row | Accepted |
| [0037](0037-idempotent-submission-with-a-caller-key.md) | An idempotent submission is a caller key, a fingerprint and `INSERT … ON CONFLICT DO NOTHING` | Accepted |
| [0038](0038-the-cash-transfer-endpoint-shape.md) | One cash transfer endpoint under the user, with its own response and a closed body | Accepted |
| [0039](0039-a-transfer-is-a-market-order-on-the-cash-instrument.md) | A transfer is a `MARKET` order on the `MONEDA` instrument at price `1`, in whole pesos | Accepted |
| [0040](0040-per-user-write-protection-lives-once.md) | The per-user write protection lives once, in `service/userwrite` | Accepted |
| [0041](0041-the-order-cancellation-endpoint-shape.md) | Cancellation is `POST …/users/{userId}/orders/{orderId}/cancel`, answering the order | Accepted |
| [0042](0042-the-history-reason-says-why-the-order-moved.md) | The history's `reason` says why the order moved, not only why it was rejected | Accepted |
| [0043](0043-a-replay-answers-the-outcome-as-first-recorded.md) | An idempotent replay answers the outcome as first recorded | Accepted |
| [0044](0044-the-latest-price-is-one-index-seek-per-instrument.md) | The latest price is one index seek per instrument, not a scan of every price | Accepted |
| [0045](0045-a-transitions-reason-is-a-sealed-type-not-two-optionals.md) | A transition's reason is a sealed type, not two `Optional`s | Accepted |
