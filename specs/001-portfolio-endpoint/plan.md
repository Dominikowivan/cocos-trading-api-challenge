# Implementation Plan: Portfolio Endpoint

**Branch**: `feature/portfolio` (feature directory `001-portfolio-endpoint`) | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-portfolio-endpoint/spec.md`

## Summary

One read-only endpoint, `GET /api/v1/users/{userId}/portfolio`, answering with the total account
value, the pesos available to trade, and one entry per instrument held with quantity, market value
and total return (FR-001).

Technically it is one service method behind one transaction, orchestrating persistence and a pure
model (constitution VIII, ADR-0017). The service reads the user's `orders` through a repository and maps
them into model `Order`s. A **fold** (`AccountLedger`) turns those orders into
an `AccountSnapshot` carrying settled cash, available cash, the holdings with their
weighted-average cost, and the shares still available after `SELL` reservations. The service then
reads the latest `close` per held instrument, and **`Portfolio.valued`** applies them, reporting
absent instead of zero where a figure is unknown. The portfolio returns its short and unpriced
positions as values, and the service logs them as the two required diagnostics. The snapshot is
the piece order validation will consume later (FR-016), which is why it computes reserved shares
this feature never reports — and the reason the fold takes no prices: a validator has no use for
them.

Three queries (user existence, ledger, latest closes) inside one `REPEATABLE_READ` read-only
transaction, so the cash, the quantities and the prices in a response are all as of one instant —
PostgreSQL's default `READ COMMITTED` would give each statement its own snapshot (research R-012).
No stored positions, no balance column (ADR-0002). Order submission, cancellation and validation
are **out of scope**: this feature designs the value they will read and builds none of them.

Every acceptance number was reproduced by hand from `V1000__given_dataset.sql` before planning —
see [research.md](research.md#measured-facts-this-phase-relied-on).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.2 (`webmvc`, `data-jpa`, `validation`, `flyway`),
PostgreSQL JDBC driver, SLF4J (via Boot). Versions from the Boot BOM; nothing added by this
feature — `pom.xml` already carries everything. Whether `springdoc-openapi` is added so the running
app serves live API docs is left open for the author (research Q-001).

**Storage**: PostgreSQL 18.7, schema as provided. Tables read: `users`, `instruments`, `orders`,
`marketdata`. One new migration, `V2__portfolio_read_indexes.sql` — two indexes on
`orders` plus `UNIQUE (instrumentid, date)` on `marketdata`, which is at once the constraint the
given schema lacks and the index the latest-close query reads (research R-018). No table or column
changes.

**Schema facts that drive the mapping** (all identifiers are lowercase in PostgreSQL, since
`V1__given_schema.sql` writes them unquoted):

| Column | Type | Maps to |
|---|---|---|
| `orders.instrumentid`, `orders.userid` | `INT` | `long` |
| `orders.datetime` | `timestamp without time zone` | `LocalDateTime`, no zone conversion |
| `orders.size` | `INT` | `BigDecimal` scale 0 in the domain (research R-008) |
| `orders.price` | `NUMERIC(10,2)` | `BigDecimal` |
| `marketdata.date` | `DATE` | `LocalDate` — day resolution; `UNIQUE (instrumentid, date)` makes the latest row per instrument unique, so no tiebreak column is needed |
| `marketdata.close` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent (research R-019) |
| `marketdata.previousclose` | `NUMERIC(10,2)` | **never read** (FR-009) |
| `users.accountnumber` | `VARCHAR(20)` | not read by this feature |

**Testing**: JUnit 5. Domain rules as plain Java with no Spring and no database; persistence and
functional tests on Testcontainers `postgres:18.7-alpine` built from the same migrations and seed
as local development (constitution V, ADR-0010). MockMvc for the HTTP layer. `./mvnw verify` with
JaCoCo reported, not gated.

**Target Platform**: Linux server / JVM 21; local development on Docker Compose (PostgreSQL on port
5433).

**Project Type**: Single-module Maven web service in layers: controller → service → model and
persistence (constitution VIII).

**Performance Goals**: none set by the statement. Accepted characteristic (ADR-0002): the read costs
one pass over the user's order history and grows with it, mitigated by the indexes in R-018. The
snapshot-plus-delta design is explicitly deferred, not forgotten.

**Constraints**: `BigDecimal` on every money path, `compareTo` never `equals`, every division and
rescale stating scale and `RoundingMode` (constitution II; the policy is research R-007). No
comments in source (VII). Model free of Spring, Jakarta, JPA and web types, and of any reference
to persistence (VIII). Read-only: the
feature never writes to the ledger.

**Scale/Scope**: one endpoint, 66 instruments and 126 price rows in the seed; a user's order history
is the only input that grows. Roughly 30 production types across controller, service, model and
persistence.

**Amended 2026-11-15** after the first implementation: the plan originally laid this out as ports
and adapters, with the fold and the projection reaching persistence through ports. The author
rejected that shape because the flow of a request could not be followed; constitution 2.0.0 and
ADR-0017 replace it with layers. The behaviour, the tests' expectations and every figure are
unchanged.

## Constitution Check

*GATE: passed before Phase 0; re-evaluated after Phase 1 design — see the second verdict column.*

| Principle | How this feature complies | Pre-Phase 0 | Post-Phase 1 |
|---|---|---|---|
| **I. The ledger is the single source of truth** | Everything is computed on read from `orders` + `marketdata`; no positions table, no balance column. **One** model object owns the fold (`AccountLedger.snapshot`, fed by the service), and its snapshot carries available cash *and* available holdings so order validation reads the same value (FR-016, research R-004). | PASS | PASS |
| **II. Money is exact** | `BigDecimal` everywhere, quantities included (R-008); `compareTo` in code and in assertions; one stated scale/rounding policy (R-007) — money scale 2 `HALF_UP`, return scale 2 `HALF_UP` percent units, internal average scale 10 `HALF_UP`. No `double` or `float` in production code, DTOs or tests. | PASS | PASS |
| **III. Business logic in Java, not in the database** | The fold, the cost-basis walk, the reservation subtraction and the absent-value rules are Java. SQL does two things only: fetch the user's orders in the statuses the domain names (`OrderStatus.contributing()`, bound as a parameter — R-024), and pick the latest priced row per instrument. Both are row selection and reading, which this principle explicitly allows; what each status *means* stays in `Order`. No trigger, procedure or function. | PASS | PASS |
| **IV. Order state is auditable and race-free** | Not applicable: nothing here transitions an order or inserts history. The read runs `REPEATABLE_READ` and `readOnly` for intra-request coherence (R-012) and takes no locks, because it writes nothing — on PostgreSQL a read-only snapshot blocks no writer and cannot fail to serialise. The future write path is unobstructed, and this principle's locking requirement lands there; the fold it will call is a pure function of the rows it is given. | N/A | N/A |
| **V. Tests run on the real engine** | Three levels: plain-Java domain tests (no Spring, no database), Testcontainers persistence tests, Testcontainers + MockMvc functional tests. No H2, no mocked repository standing in for PostgreSQL, no test pointed at staging. Rejection paths (404, 400, unpriced holding, short position) ship with the feature. | PASS | PASS |
| **VI. Schema changes only through migrations** | `V1__given_schema.sql` untouched. The indexes and the `UNIQUE (instrumentid, date)` constraint arrive as `V2__portfolio_read_indexes.sql` — a constraint that protects integrity, which this principle's own wording allows (R-018). The seed stays the provided dataset: the FR-020..022 fixture is inserted by the test via `@Sql` (R-020). Staging reached only through the `staging` profile; no credential committed. | PASS | PASS |
| **VII. No comments; reasoning in documents** | No comments in the code this feature adds. Three decisions that had real alternatives *and* bind work beyond this feature are marked as ADR candidates in [research.md](research.md) (0014 data access, 0015 the fold's contract, 0016 scale and rounding); each is written at implementation time with `docs/adr/README.md` updated in the same change. Two others — diagnostics as a port (R-011) and `ProblemDetail` (R-013) — are recorded in research.md and owed no ADR: both are internal to this feature, and the cross-cutting part of the first (which level means what) is already ADR-0012's and ADR-0013's. Behaviour already decided is cited (0002, 0003, 0004, 0007, 0012, 0013), not restated. No departure from the challenge statement is introduced here, so no new README note is owed — the reservation departure is ADR-0004's and already stated. | PASS | PASS |
| **VIII. Layers** | `PortfolioController` translates HTTP and calls `PortfolioService` once. The service orchestrates: `UserRepository`, `OrderRepository` and `MarketDataRepository` read, hand-written mappers turn entities and rows into model values, `AccountLedger` and `Portfolio` compute, and the service logs what the portfolio reports as anomalous. The model imports nothing but the JDK and holds no repository, port or service; it is not a Spring bean. Entities stay in `persistence`; DTOs stay in `controller`. | PASS | PASS |
| **IX. Design for change** | `AccountSnapshot`, `Holding` and `Position` answer questions instead of exposing accumulators; `Order` owns the meaning of its side and status so the fold holds no `switch`. Each rule in one place: the signs, the reservation rule and the absent-value rule each exist once. Value types exist where they guard a boundary — `UserId` crosses every layer signature, quantity crosses none, which is why there is no `Quantity` (R-008, rationale corrected after review: the earlier appeal to "no second use" would have condemned `UserId` too). | PASS | PASS |

**Gate verdict**: PASS, before and after design. One structural judgement call is recorded in
[Complexity Tracking](#complexity-tracking) rather than left implicit.

## Project Structure

### Documentation (this feature)

```text
specs/001-portfolio-endpoint/
├── spec.md                           # Input
├── plan.md                           # This file
├── research.md                       # Phase 0: 24 decisions, 3 ADR candidates, 2 open questions
├── data-model.md                     # Phase 1: schema, adapter, domain model
├── quickstart.md                     # Phase 1: six runnable validation scenarios
├── contracts/
│   ├── portfolio.openapi.yaml        # Phase 1: HTTP contract, hand-written
│   └── diagnostics.md                # Phase 1: the two required diagnostics
├── checklists/
│   └── requirements.md               # From /speckit-specify
└── tasks.md                          # Phase 2 (/speckit-tasks — not created here)
```

### Source Code (repository root)

```text
src/main/java/com/cocos/trading/
├── TradingApiApplication.java
├── controller/
│   ├── PortfolioController.java          # HTTP only: path id in, one service call, DTO out
│   ├── ApiExceptionHandler.java          # ProblemDetail: 404 unknown user, 400 malformed id
│   └── dto/
│       ├── PortfolioResponse.java        # record; absent -> JSON null
│       └── PositionResponse.java
├── service/
│   ├── PortfolioService.java             # @Transactional(REPEATABLE_READ, readOnly); orchestrates repositories,
│   │                                     # mappers and model; logs the anomalies the model returns
│   └── UnknownUserException.java
├── model/
│   ├── DecimalPolicy.java                # the one place scales and RoundingModes live (R-007)
│   ├── order/
│   │   ├── Order.java                    # movesPosition, cashEffect, signedQuantity, reservesCash, reservesShares
│   │   ├── OrderSide.java                # BUY, SELL, CASH_IN, CASH_OUT
│   │   ├── OrderStatus.java              # NEW, FILLED, REJECTED, CANCELLED
│   │   └── OrderKind.java                # MARKET, LIMIT
│   ├── instrument/
│   │   └── InstrumentRef.java            # isCash() -> type == MONEDA (FR-005, FR-023)
│   ├── user/
│   │   └── UserId.java
│   ├── ledger/
│   │   ├── AccountLedger.java            # the fold, FR-016 — built from orders, one method per figure
│   │   ├── AccountSnapshot.java          # settledCash, availableCash, holdings, availableQuantityOf
│   │   ├── Holding.java                  # quantity, totalCost, isShort, isFlat
│   │   └── CostBasisWalk.java            # weighted average + through-zero crossing (FR-007, FR-008)
│   └── portfolio/
│       ├── Portfolio.java                # valued(snapshot, closes); shortPositions, unpricedPositions
│       ├── Position.java                 # isShort, isPriced
│       └── Closes.java                   # latest close per instrument; of(instrument) -> Optional
└── persistence/
    ├── UserRepository.java
    ├── OrderRepository.java              # ledger query, fetch join, (datetime, id) order
    ├── MarketDataRepository.java         # native DISTINCT ON latest close; empty input reads nothing
    ├── LatestClose.java                  # typed projection of that query (quoted aliases)
    ├── entity/
    │   ├── UserEntity.java
    │   ├── InstrumentEntity.java
    │   ├── OrderEntity.java
    │   └── MarketDataEntity.java
    └── mapper/
        ├── UserMapper.java               # UserId -> INT column, absent when out of range
        ├── OrderMapper.java              # OrderEntity -> Order, OrderStatus -> column value
        └── MarketDataMapper.java         # LatestClose rows -> Closes, NULL dropped

src/main/resources/
├── application.yaml                      # + spring.mvc.problemdetails.enabled: true
└── db/migration/V2__portfolio_read_indexes.sql   # 2 indexes + UNIQUE (instrumentid, date)

docs/api/
└── trading-api.http                      # example requests, the statement's optional collection

src/test/java/com/cocos/trading/
├── model/ledger/                         # fold and walk, plain Java, no doubles (helper Ledgers builds orders)
├── model/portfolio/                      # valuation and anomalies from data, plain Java, no doubles
├── persistence/                          # Testcontainers: repository + mapper, ordering, latest close
└── controller/                           # Testcontainers + MockMvc: quickstart scenarios 1-6
```

**Structure Decision**: single Maven module, four top-level packages — `controller` (HTTP and
DTOs), `service` (the transaction boundary and the orchestration), `model` (plain Java, JDK-only
imports, no reference to persistence) and `persistence` (Spring Data repositories, JPA entities and the
hand-written mappers). This is constitution VIII made concrete: the path of a request reads top to
bottom in `PortfolioService.portfolioOf`. Inside `model`, one package per concept — `order`,
`instrument`, `user`, `ledger`, `portfolio` — with dependencies in one direction and no cycle:
`portfolio → ledger → order → instrument`, and the shared `DecimalPolicy` at the root of `model`.
The layout absorbs the next three features without moving anything — instrument search grows
`model/instrument` and adds a repository query and a service method; order submission grows
`model/order` (lifecycle, validation against the same `AccountLedger` snapshot) and adds a service.

## Phase 0 and Phase 1 outputs

| Artifact | Contents |
|---|---|
| [research.md](research.md) | 24 decisions with alternatives and rationale; 3 marked as ADR candidates (0014-0016); the measured dataset facts the plan relies on; **two questions left open for the author** (Q-001 `springdoc-openapi`, Q-002 Micrometer counters) |
| [data-model.md](data-model.md) | The given schema as read (lowercase identifiers, type mapping, nullable `close`), the persistence layer (repositories and mappers), the model with each member's behaviour, the fold's and walk's rules, and an entity-to-requirement map |
| [contracts/portfolio.openapi.yaml](contracts/portfolio.openapi.yaml) | OpenAPI 3.1: the endpoint, the 200/400/404 responses, three worked examples (seeded user, never traded, unpriced holding), and per-field semantics including when each is `null` |
| [contracts/diagnostics.md](contracts/diagnostics.md) | The two diagnostics as a contract: what the model reports, levels, fields, guarantees (one record per affected position per request, status unaffected) |
| [quickstart.md](quickstart.md) | Six validation scenarios with expected figures, including the two that need constructed data, plus what each test level proves |
| [`docs/api/trading-api.http`](../../docs/api/trading-api.http) | The statement's optional collection (R-023): the portfolio requests with their expected figures, runnable from IntelliJ's HTTP Client or VS Code's REST Client. Lives outside `specs/` because it ships with the API, and gains a section per endpoint as the later features land |

## Requirement coverage

| Requirement | Where it is satisfied |
|---|---|
| FR-001, FR-013 | `PortfolioController` + `PortfolioService`, scoped by the path id |
| FR-002 | no stored state; three read queries per request |
| FR-003, FR-004, FR-025 | `Order.signedQuantity()` and `AccountSnapshot.holdings()` (non-zero only); `CANCELLED` and `REJECTED` rows are never fetched, the set of statuses coming from `OrderStatus.contributing()` (R-024) |
| FR-005, FR-023 | `InstrumentRef.isCash()`, applied while folding — before any price lookup |
| FR-006 | `MarketDataRepository`, `DISTINCT ON (instrumentid)` per instrument, mapped by `MarketDataMapper` |
| FR-007, FR-008 | `CostBasisWalk` |
| FR-009 | `previousclose` is not mapped on any entity |
| FR-010 | `Order.cashEffect()` over every settled order |
| FR-011 | `AccountSnapshot.availableCash()` |
| FR-012, FR-021 | `Portfolio.totalAccountValue()` as `Optional`; `settledCash` published alongside it so SC-007 is checkable from one response (R-016) |
| FR-014 | empty snapshot folds to zeros; existence check passes |
| FR-015 | `DecimalPolicy` (R-007) |
| FR-016 | `AccountSnapshot`, with available cash *and* available holdings (R-004) |
| FR-017, FR-018 | `Holding.isShort()`; `Position.totalReturnPercent()` absent |
| FR-019, FR-022 | `Portfolio.shortPositions()` and `unpricedPositions()`, logged by `PortfolioService` at ERROR and WARN |
| FR-020 | absent market value and return when the price is unknown (including a NULL `close`, R-019) |
| FR-024 | `UserRepository.existsById` in the service → `UnknownUserException` → 404 |

## Complexity Tracking

One judgement call, recorded rather than hidden. There is no constitution violation.

| Decision | Why needed | Simpler alternative rejected because |
|---|---|---|
| `service/PortfolioService` declares `@Transactional(isolation = REPEATABLE_READ, readOnly = true)` | The feature issues three queries and the figures in one response must describe one instant. The service is where the queries are orchestrated, so it is where the boundary belongs; the model may not import Spring (VIII). | `@Transactional` on the controller makes the web adapter own the persistence boundary and the use case callable only over HTTP. Leaving the default `READ COMMITTED`, or using no transaction at all, gives each statement its own snapshot, so an order filling mid-request yields a response whose parts disagree — intermittently and only under load. Full reasoning, including why SC-006 is *not* what justifies this, in [research R-012](research.md#r-012-the-read-only-transaction-boundary-sits-in-a-thin-application-service). |

## Open for the author

Two additions are **not decided**; both are reasonable, neither blocks implementation, and each
changes what ships. Full arguments on both sides in [research.md](research.md#open-questions-for-the-author):

| # | Question | If yes |
|---|---|---|
| Q-001 | Add `springdoc-openapi` so the running app serves generated OpenAPI and a Swagger UI? | a dependency the BOM does not manage, annotations on the controller, and a version to track — against discoverability for a reviewer. The hand-written contract in `contracts/` stays either way, since a generated document cannot catch a regression (R-022) |
| Q-002 | Add a Micrometer counter next to each diagnostic? | `spring-boot-starter-actuator` and two lines next to the log calls in `PortfolioService`. The counter needs no user id or ticker tag — the log already carries the identity, the counter answers *how often*. Cheap to defer, because the portfolio already names the two anomalies |

## Deferred, by decision

- **Order submission, cancellation and validation** — separate features. This plan designs the
  snapshot they consume (FR-016) and builds none of them.
- **A snapshot or materialised view of the ledger** — deferred in ADR-0002, not rejected; the read
  grows with order history and the indexes in R-018 are the current mitigation.
- **Money as JSON strings** (R-015) and **`reservedCash` as its own field** (R-016, it is
  `settledCash − availableCash`) — considered, declined with their reasons, compatible to add later.
- **README housekeeping**: the `DELETE BEFORE DELIVERY` block in `README.md` claims no endpoint and
  no index migration exist. Both become false when this feature lands, so updating it is part of
  the implementation, not of the delivery sweep. The same change adds `docs/api/trading-api.http`
  to the README's "Where things are" table.
