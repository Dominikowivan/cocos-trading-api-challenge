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

ADR-0002 is the one the others hang from: once the balance is derived rather than stored, the
reservation in 0004, the history in 0005 and the cancellation in 0006 all follow from it.
