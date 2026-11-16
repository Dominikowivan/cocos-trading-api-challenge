---

description: "Task list for feature 001-portfolio-endpoint"
---

# Tasks: Portfolio Endpoint

**Input**: Design documents from `/specs/001-portfolio-endpoint/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Included, and not optional here — constitution V requires three levels (plain-Java domain, Testcontainers persistence, Testcontainers + MockMvc functional) and the plan's Testing section names them. Tests are written before the implementation they cover within each story.

**Amended 2026-11-15**: Phases 1-8 were executed as written, against constitution 1.2.0 (ports and
adapters), and are kept as the record of that run — their paths under `domain/`, `application/` and
`adapter/` no longer exist. Phase 9 restructures the result into layers under constitution 2.0.0
([ADR-0017](../../docs/adr/0017-layers-with-a-service-that-orchestrates.md)) without changing
behaviour; the current layout is in [plan.md](plan.md#source-code-repository-root).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1-US5)
- Every task carries its exact file path

## Path Conventions

Single Maven module (plan § Project Structure):

- Production: `src/main/java/com/cocos/trading/{domain,application,adapter}/…`
- Tests: `src/test/java/com/cocos/trading/…`
- Resources: `src/main/resources/`

All PostgreSQL identifiers are **lowercase** (`instrumentid`, `previousclose`) — `V1__given_schema.sql` writes them unquoted (data-model § 1).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: the skeleton already builds (`TradingApiApplication`, `pom.xml`, `V1__given_schema.sql`, `V1000__given_dataset.sql`, `TestcontainersConfiguration`). This phase adds only what the feature needs on top.

- [X] T001 Create the feature's package directories under `src/main/java/com/cocos/trading/`: `domain/ledger/`, `domain/portfolio/`, `domain/port/`, `application/`, `adapter/config/`, `adapter/persistence/`, `adapter/observability/`, `adapter/web/`, and the mirrored test packages under `src/test/java/com/cocos/trading/`
- [X] T002 [P] Create `src/main/resources/db/migration/V2__portfolio_read_indexes.sql` with `idx_orders_userid_status`, `idx_orders_userid_instrumentid` and `UNIQUE INDEX uq_marketdata_instrumentid_date ON marketdata (instrumentid, date)` exactly as in data-model § 1 (research R-018); `V1__given_schema.sql` stays untouched (constitution VI)
- [X] T003 [P] Add `spring.mvc.problemdetails.enabled: true` and the Jackson `WRITE_BIGDECIMAL_AS_PLAIN` generator feature to `src/main/resources/application.yaml` (research R-013, R-015), leaving `spring.jpa.hibernate.ddl-auto: validate` in place
- [X] T004 Run `./mvnw verify` and confirm the baseline is green with V2 applied — `src/test/java/com/cocos/trading/TradingApiApplicationTests.java` boots the context against Testcontainers, so a bad migration or a failed `validate` fails here, before any domain code exists

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the value types, ports, persistence adapter and HTTP shell every story needs regardless of which figure it reports. No story work can begin until this phase is complete.

**⚠️ CRITICAL**: `LedgerEntry` is created here with `isSettled()` and `signedQuantity()` only; `cashEffect()`, `reservesCash()` and `reservesShares()` arrive in US4, which is the story that needs them.

### Domain value types

- [X] T005 [P] Create `UserId` (wrapping `long`) in `src/main/java/com/cocos/trading/domain/ledger/UserId.java` — it crosses every port signature and both diagnostics (research R-008)
- [X] T006 [P] Create `OrderSide` enum (`BUY`, `SELL`, `CASH_IN`, `CASH_OUT`) with `cashSign()` and `positionSign()` in `src/main/java/com/cocos/trading/domain/ledger/OrderSide.java`; `CASH_IN`/`CASH_OUT` have no `positionSign()` (data-model § 3)
- [X] T007 [P] Create `OrderStatus` enum (`NEW`, `FILLED`, `REJECTED`, `CANCELLED`) with `isSettled()`, `isOpen()` and the static `contributing()` returning `{FILLED, NEW}` in `src/main/java/com/cocos/trading/domain/ledger/OrderStatus.java` (research R-024)
- [X] T008 [P] Create `OrderKind` enum (`MARKET`, `LIMIT`) in `src/main/java/com/cocos/trading/domain/ledger/OrderKind.java`
- [X] T009 [P] Create `InstrumentRef` (`instrumentId`, `ticker`, `name`, `type`) with `isCash()` returning `type == MONEDA` in `src/main/java/com/cocos/trading/domain/ledger/InstrumentRef.java` (FR-005, FR-023, research R-009)
- [X] T010 Create `LedgerEntry` (`entryId`, `InstrumentRef`, `OrderSide`, `OrderStatus`, `OrderKind`, `quantity` as `BigDecimal` scale 0, `price`, `occurredAt` as `LocalDateTime`) with `isSettled()` and `signedQuantity()` in `src/main/java/com/cocos/trading/domain/ledger/LedgerEntry.java` (FR-003, FR-004, FR-005; depends on T006-T009)
- [X] T011 [P] Create `DecimalPolicy` in `src/main/java/com/cocos/trading/domain/portfolio/DecimalPolicy.java` as the one place scales and rounding live: money scale 2 `HALF_UP`, return scale 2 `HALF_UP` percent units, internal average scale 10 `HALF_UP`, quantity scale 0 (FR-015, research R-007)

### Ports

- [X] T012 [P] Create `LedgerEntries` port — `of(UserId, Set<OrderStatus>) -> List<LedgerEntry>` in `(occurredAt, entryId)` order — in `src/main/java/com/cocos/trading/domain/port/LedgerEntries.java` (research R-006, R-024)
- [X] T013 [P] Create `Users` port — `exists(UserId) -> boolean` — in `src/main/java/com/cocos/trading/domain/port/Users.java` (FR-024, research R-014)
- [X] T014 [P] Create `PortfolioDiagnostics` port with `shortPositionDetected(UserId, String ticker, BigDecimal quantity)` and `marketDataMissing(UserId, String ticker)` in `src/main/java/com/cocos/trading/domain/port/PortfolioDiagnostics.java` exactly as [contracts/diagnostics.md](contracts/diagnostics.md) states (FR-019, FR-022, research R-011)
- [X] T015 [P] Create `UnknownUserException` in `src/main/java/com/cocos/trading/domain/port/UnknownUserException.java`, a plain domain exception carrying the requested id (FR-024)

### Persistence adapter

- [X] T016 [P] Create `UserEntity` (`id` only) in `src/main/java/com/cocos/trading/adapter/persistence/UserEntity.java` — `email` and `accountnumber` are not read
- [X] T017 [P] Create `InstrumentEntity` (`id`, `ticker`, `name`, `type`) in `src/main/java/com/cocos/trading/adapter/persistence/InstrumentEntity.java`
- [X] T018 Create `OrderEntity` (`id`, `userId`, `size` as `int`, `price`, `side`, `status`, `type`, `datetime`) with `@ManyToOne(LAZY)` to `InstrumentEntity` in `src/main/java/com/cocos/trading/adapter/persistence/OrderEntity.java`, `@Column` names lowercase (depends on T017)
- [X] T019 [P] Create `OrderJpaRepository` in `src/main/java/com/cocos/trading/adapter/persistence/OrderJpaRepository.java` with the ledger query `select o from OrderEntity o join fetch o.instrument where o.userId = :userId and o.status in :statuses order by o.datetime asc, o.id asc` — the status set is a bound parameter, never a literal (research R-024)
- [X] T020 [P] Create `UserJpaRepository` in `src/main/java/com/cocos/trading/adapter/persistence/UserJpaRepository.java` using `existsById`
- [X] T021 Create `JpaLedgerEntries` in `src/main/java/com/cocos/trading/adapter/persistence/JpaLedgerEntries.java` implementing `LedgerEntries` with hand-written mapping to `LedgerEntry`; an unrecognised `side`, `status` or `type` fails loudly rather than being guessed (data-model § 1; depends on T010, T018, T019)
- [X] T022 Create `JpaUsers` in `src/main/java/com/cocos/trading/adapter/persistence/JpaUsers.java` implementing `Users` (depends on T013, T020)

### Observability, wiring and HTTP shell

- [X] T023 Create `LoggingPortfolioDiagnostics` in `src/main/java/com/cocos/trading/adapter/observability/LoggingPortfolioDiagnostics.java` — SLF4J, ERROR for the short position, WARN for the missing price, fields in the fixed order of [contracts/diagnostics.md](contracts/diagnostics.md) (depends on T014)
- [X] T024 [P] Create the recording diagnostics fake in `src/test/java/com/cocos/trading/domain/portfolio/RecordingDiagnostics.java` so domain tests assert on the port, never on log strings (contracts/diagnostics.md § Logging adapter)
- [X] T025 Create `PortfolioQueryService` in `src/main/java/com/cocos/trading/application/PortfolioQueryService.java`, annotated `@Transactional(isolation = REPEATABLE_READ, readOnly = true)`, checking `Users.exists` and throwing `UnknownUserException` before delegating to the projection (research R-012, R-014; plan § Complexity Tracking)
- [X] T026 Create `DomainConfiguration` in `src/main/java/com/cocos/trading/adapter/config/DomainConfiguration.java` with `@Bean` wiring for the plain domain objects, keeping Spring out of `domain/` (constitution VIII)
- [X] T027 Create `ApiExceptionHandler` in `src/main/java/com/cocos/trading/adapter/web/ApiExceptionHandler.java`, a `@RestControllerAdvice` mapping `UnknownUserException` to a 404 `ProblemDetail` and a malformed path id to a 400 `ProblemDetail` naming the offending id (FR-024, research R-013)
- [X] T028 Create `PortfolioController` for `GET /api/v1/users/{userId}/portfolio` in `src/main/java/com/cocos/trading/adapter/web/PortfolioController.java`, plus the `PortfolioResponse` and `PositionResponse` records in the same package, with the field names and nullability of [contracts/portfolio.openapi.yaml](contracts/portfolio.openapi.yaml) (`userId`, `totalAccountValue`, `availableCash`, `settledCash`, `positions[].{instrumentId, ticker, name, quantity, marketValue, totalReturnPercent}`); fields added per story as they become real, keys always present, absent serialising as `null` (research R-010, R-021)
- [X] T029 Create the Testcontainers persistence test base in `src/test/java/com/cocos/trading/adapter/persistence/PersistenceTestBase.java`, reusing `src/test/java/com/cocos/trading/TestcontainersConfiguration.java` so every persistence test runs on `postgres:18.7-alpine` built from the same migrations and seed (constitution V, ADR-0010)

### Decisions recorded in code's company

- [X] T030 [P] Write `docs/adr/0014-data-access-with-spring-data-jpa.md` from research R-001 and add its row to `docs/adr/README.md` in the same change (constitution VII)
- [X] T031 [P] Write `docs/adr/0016-decimal-scale-and-rounding-policy.md` from research R-007 and add its row to `docs/adr/README.md` in the same change

**Checkpoint**: the endpoint answers and the ledger can be read; no figure is computed yet. User story work can begin.

---

## Phase 3: User Story 1 - Quantity held per instrument (Priority: P1) 🎯 MVP

**Goal**: one entry per instrument held, with its signed quantity from `FILLED` orders only, pesos excluded, zero quantities omitted, and an error-level diagnostic when a quantity is negative.

**Independent Test**: `GET /api/v1/users/1/portfolio` returns positions BMA `-10`, METR `500`, PAMP `40` (ticker ascending), no `ARS` entry, and exactly one ERROR line naming `userId=1 ticker=BMA quantity=-10`; no prices and no cash figures are needed to judge it.

### Tests for User Story 1

> Write these first and watch them fail.

- [X] T032 [P] [US1] Plain-Java fold test in `src/test/java/com/cocos/trading/domain/ledger/AccountLedgerQuantityTest.java`: `BUY` adds and `SELL` subtracts over `FILLED` rows (FR-003); `NEW`, `CANCELLED` and `REJECTED` rows change nothing (FR-004); a `MONEDA` instrument never becomes a holding (FR-005); a net-zero instrument is not listed (FR-025); assertions use `compareTo`, never `equals` (constitution II)
- [X] T033 [P] [US1] Plain-Java projection test in `src/test/java/com/cocos/trading/domain/portfolio/ProjectionShortPositionTest.java`: a negative holding is listed with its negative quantity, the call does not throw, and exactly one `shortPositionDetected` is recorded on the fake per request (FR-017, FR-019, SC-009)
- [X] T034 [P] [US1] Persistence test in `src/test/java/com/cocos/trading/adapter/persistence/JpaLedgerEntriesTest.java`: user `1`'s `CANCELLED` and `REJECTED` rows are never returned, rows come back in `(datetime, id)` order, the instrument is fetch-joined (no N+1), and another user's rows never appear (FR-013, research R-024)
- [X] T035 [P] [US1] Functional test in `src/test/java/com/cocos/trading/adapter/web/PortfolioQuantitiesTest.java` (Testcontainers + MockMvc): quickstart Scenario 1's quantities and ticker ordering, Scenario 2's empty list for user `2`, and Scenario 3's `404` for user `999` and `400` for `abc`, both `application/problem+json` (SC-001, SC-005, SC-011)

### Implementation for User Story 1

- [X] T036 [P] [US1] Create `Holding` with `instrument()`, `quantity()` and `isShort()` in `src/main/java/com/cocos/trading/domain/ledger/Holding.java` (`totalCost()` arrives in US3)
- [X] T037 [US1] Create `AccountSnapshot` in `src/main/java/com/cocos/trading/domain/ledger/AccountSnapshot.java` exposing `holdings()` — non-zero quantities only (FR-025); cash members arrive in US4 (depends on T036)
- [X] T038 [US1] Create `AccountLedger` with `snapshotOf(UserId) -> AccountSnapshot` in `src/main/java/com/cocos/trading/domain/ledger/AccountLedger.java`: one pass over `LedgerEntries.of(userId, OrderStatus.contributing())` accumulating signed quantity per instrument, with no `switch` over sides or statuses — `LedgerEntry` answers those (FR-002, FR-003, FR-016, research R-004)
- [X] T039 [P] [US1] Create `Position` with `instrument()` and `quantity()` in `src/main/java/com/cocos/trading/domain/portfolio/Position.java` (`marketValue()` in US2, `totalReturnPercent()` in US3)
- [X] T040 [US1] Create `Portfolio` with `positions()` ordered by ticker ascending in `src/main/java/com/cocos/trading/domain/portfolio/Portfolio.java` (research R-017; depends on T039)
- [X] T041 [US1] Create `PortfolioProjection` with `portfolioOf(UserId) -> Portfolio` in `src/main/java/com/cocos/trading/domain/portfolio/PortfolioProjection.java`: map each holding to a `Position` and call `diagnostics.shortPositionDetected(...)` for each short one (FR-019; depends on T038, T040)
- [X] T042 [US1] Add `userId`, `positions[].instrumentId`, `ticker`, `name` and `quantity` to `PortfolioResponse`/`PositionResponse` and return the projection's result from `PortfolioController`, wiring `AccountLedger` and `PortfolioProjection` beans in `src/main/java/com/cocos/trading/adapter/config/DomainConfiguration.java`
- [X] T043 [US1] Write `docs/adr/0015-the-account-snapshot-is-the-folds-contract.md` from research R-004 and add its row to `docs/adr/README.md` in the same change

**Checkpoint**: quantities are correct and verifiable end to end; the endpoint is already useful.

---

## Phase 4: User Story 2 - Market value per position (Priority: P2)

**Goal**: each position reports quantity × the latest `close` of that instrument, resolved per instrument; an unpriced holding reports `null` rather than `0` and raises a warning.

**Independent Test**: user `1` reports PAMP `37034.00`, METR `114750.00`, BMA `-15028.00`; the Scenario 4 fixture reports `PGR` with `quantity: 25` and `marketValue: null` plus one WARN line.

### Story setup for User Story 2

- [X] T044 [P] [US2] Create `LatestPrices` port — `closeFor(Collection<Long>) -> Map<Long, BigDecimal>`, a missing key meaning no known price — in `src/main/java/com/cocos/trading/domain/port/LatestPrices.java` (FR-006, FR-020)
- [X] T045 [P] [US2] Create `MarketDataEntity` (`id`, `instrumentId`, `close` nullable, `date` as `LocalDate`) in `src/main/java/com/cocos/trading/adapter/persistence/MarketDataEntity.java`; `previousclose`, `open`, `high` and `low` are deliberately not mapped (FR-009)

### Tests for User Story 2

- [X] T046 [P] [US2] Plain-Java projection test in `src/test/java/com/cocos/trading/domain/portfolio/ProjectionMarketValueTest.java`: market value is quantity × close at scale 2 `HALF_UP`, a negative quantity yields a negative value (FR-017), and an absent price yields `marketValue` absent with exactly one `marketDataMissing` recorded and no error (FR-020, FR-022, SC-010)
- [X] T047 [P] [US2] Persistence test in `src/test/java/com/cocos/trading/adapter/persistence/JpaLatestPricesTest.java`: the latest row is picked **per instrument** (the seed carries two days each), a row with a NULL `close` counts as no known price (research R-019), an instrument with no row is absent from the map, and `ARS`/`PGR`/`IRCP` are the three unpriced instruments in the seed
- [X] T048 [P] [US2] Functional test in `src/test/java/com/cocos/trading/adapter/web/PortfolioMarketValueTest.java`: Scenario 1's three market values (SC-002), and Scenario 4's unpriced holding via an `@Sql` fixture inserting the `CASH_IN` and `BUY PGR` rows for user `3` (research R-020), asserting `marketValue: null` and `totalReturnPercent: null` with `settledCash` and `availableCash` still `477500.00`

### Implementation for User Story 2

- [X] T049 [US2] Create `MarketDataJpaRepository` in `src/main/java/com/cocos/trading/adapter/persistence/MarketDataJpaRepository.java` with the native `SELECT DISTINCT ON (instrumentid) instrumentid, close … ORDER BY instrumentid, date DESC` query, carrying no tiebreak because `uq_marketdata_instrumentid_date` makes the latest row unique (research R-002, R-018)
- [X] T050 [US2] Create `JpaLatestPrices` in `src/main/java/com/cocos/trading/adapter/persistence/JpaLatestPrices.java` implementing `LatestPrices`, omitting instruments whose latest `close` is NULL (depends on T044, T049)
- [X] T051 [US2] Add `marketValue()` as `Optional<BigDecimal>` (scale 2) to `src/main/java/com/cocos/trading/domain/portfolio/Position.java`
- [X] T052 [US2] Extend `src/main/java/com/cocos/trading/domain/portfolio/PortfolioProjection.java`: look prices up once for all held instrument ids, compute market value through `DecimalPolicy`, and call `diagnostics.marketDataMissing(...)` when a price is absent — cash never reaches this loop because `isCash()` excluded it during the fold, which is FR-023 made structural (FR-006, FR-020, FR-022)
- [X] T053 [US2] Add `marketValue` to `PositionResponse` in `src/main/java/com/cocos/trading/adapter/web/PositionResponse.java`, serialising absent as `null` and never as `0` (ADR-0013, research R-010)

**Checkpoint**: US1 and US2 both hold; positions are worth something and unknown prices are honest.

---

## Phase 5: User Story 3 - Total return per position (Priority: P3)

**Goal**: each position reports `(market value − cost) / cost` as a percentage, with cost built by a weighted-average walk that resets on a return to zero and re-bases on a crossing through zero; absent when the holding is short or the cost is zero.

**Independent Test**: user `1` reports PAMP `-0.45`, METR `-8.20` and BMA `null`; after the Scenario 5 fixture, BMA is `+20` at basis `1,502.80` with `totalReturnPercent: 0.00`.

### Tests for User Story 3

- [X] T054 [P] [US3] Plain-Java walk test in `src/test/java/com/cocos/trading/domain/ledger/CostBasisWalkTest.java` covering every row of data-model § "The cost-basis walk": flat start, same-sign extension (weighted average, not the oldest lot), partial opposite sale leaving the average unchanged, exact close resetting to zero, and reopening after a reset carrying nothing forward (FR-007, spec US3 AS2-AS3)
- [X] T055 [P] [US3] Plain-Java crossing test in `src/test/java/com/cocos/trading/domain/ledger/CostBasisCrossingTest.java`: `+20` at `100` then `SELL` 50 at `120` leaves `-30` based at `120` (spec US3 AS5), and user `1`'s BMA `0 → +20 → -10` plus a `BUY` 30 at `1,502.80` leaves `+20` based at `1,502.80`, with a basis carried over from `1,540` explicitly asserted against (FR-008, SC-013)
- [X] T056 [P] [US3] Plain-Java projection test in `src/test/java/com/cocos/trading/domain/portfolio/ProjectionTotalReturnTest.java`: return is absent for a short holding (FR-018 — never `0`, never sign-flipped, never a division by zero), absent when the market value is absent, absent when `totalCost()` is zero, and otherwise scale 2 `HALF_UP` in percent units; `previousClose` is never an input (FR-009)
- [X] T057 [P] [US3] Functional test in `src/test/java/com/cocos/trading/adapter/web/PortfolioTotalReturnTest.java`: Scenario 1's `-0.45` and `-8.20` with BMA `null` (SC-003), and Scenario 5's crossing via an `@Sql` fixture asserting `quantity: 20`, `marketValue: 30056.00`, `totalReturnPercent: 0.00` and that the short-position ERROR is no longer raised (SC-013)

### Implementation for User Story 3

- [X] T058 [US3] Create `CostBasisWalk` in `src/main/java/com/cocos/trading/domain/ledger/CostBasisWalk.java` carrying signed `quantity` and `averageCost` (scale 10 `HALF_UP` via `DecimalPolicy`), implementing the five-case table of data-model § "The cost-basis walk" with the weighted average as its only division (FR-007, FR-008, ADR-0003, research R-005)
- [X] T059 [US3] Add `totalCost()` (`quantity × averageCost`) to `src/main/java/com/cocos/trading/domain/ledger/Holding.java`
- [X] T060 [US3] Feed each instrument's entries through a `CostBasisWalk` inside the single pass in `src/main/java/com/cocos/trading/domain/ledger/AccountLedger.java`, keeping the fold one pass in `(occurredAt, entryId)` order (research R-006)
- [X] T061 [US3] Add `totalReturnPercent()` as `Optional<BigDecimal>` (scale 2, percent units) to `src/main/java/com/cocos/trading/domain/portfolio/Position.java`
- [X] T062 [US3] Apply the three absence rules and the ratio in `src/main/java/com/cocos/trading/domain/portfolio/PortfolioProjection.java` (FR-007, FR-018, FR-020)
- [X] T063 [US3] Add `totalReturnPercent` to `PositionResponse` in `src/main/java/com/cocos/trading/adapter/web/PositionResponse.java`, absent as `null`

**Checkpoint**: all three position attributes are correct and independently verified.

---

## Phase 6: User Story 4 - Available cash (Priority: P4)

**Goal**: settled cash is the pesos effect of **every** `FILLED` order, and available cash is that minus the pesos reserved by `BUY LIMIT` orders still in `NEW`.

**Independent Test**: user `1` reports `settledCash: 753000.00` and `availableCash: 627500.00`; a user with no `NEW` orders reports the two equal; a user whose only pending order is a `SELL LIMIT` reports them equal too.

### Tests for User Story 4

- [X] T064 [P] [US4] Plain-Java cash test in `src/test/java/com/cocos/trading/domain/ledger/AccountLedgerCashTest.java`: settled cash folds `CASH_IN` and `SELL` positive, `CASH_OUT` and `BUY` negative over every `FILLED` order — with the explicit guard that folding only the pesos instrument's `size` would give `900,000.00` instead of `753,000.00` (FR-010) — and that a user with no orders folds to `0`, not absent (FR-014)
- [X] T065 [P] [US4] Plain-Java reservation test in `src/test/java/com/cocos/trading/domain/ledger/AccountLedgerReservationTest.java`: a `BUY LIMIT` in `NEW` reserves `size × price` and reduces available cash, a `SELL LIMIT` in `NEW` reserves shares and leaves cash untouched (FR-011, ADR-0004), `NEW MARKET` rows reserve nothing, and `availableQuantityOf` subtracts reserved shares from the holding (FR-016, research R-004)
- [X] T066 [P] [US4] Functional test in `src/test/java/com/cocos/trading/adapter/web/PortfolioCashTest.java`: user `1`'s `753000.00`/`627500.00` pair with the `125,500.00` gap traced to the two `BUY LIMIT NEW` rows (50 LOMA at `710`, 60 BMA at `1,500`), and user `2`'s `0.00`/`0.00` as numbers rather than `null` (SC-004, SC-005)

### Implementation for User Story 4

- [X] T067 [US4] Add `cashEffect()`, `reservesCash()` and `reservesShares()` to `src/main/java/com/cocos/trading/domain/ledger/LedgerEntry.java` per data-model § 3's question table (FR-010, FR-011, research R-009)
- [X] T068 [US4] Add `settledCash()`, `availableCash()` (settled − reserved cash) and `availableQuantityOf(instrumentId)` (held − reserved shares) to `src/main/java/com/cocos/trading/domain/ledger/AccountSnapshot.java` (FR-011, FR-016)
- [X] T069 [US4] Accumulate settled cash, reserved cash and reserved shares in the same single pass in `src/main/java/com/cocos/trading/domain/ledger/AccountLedger.java`, with no second iteration over the entries
- [X] T070 [US4] Carry `availableCash()` and `settledCash()` through `src/main/java/com/cocos/trading/domain/portfolio/Portfolio.java` from the snapshot (research R-016)
- [X] T071 [US4] Add `availableCash` and `settledCash` to `PortfolioResponse` in `src/main/java/com/cocos/trading/adapter/web/PortfolioResponse.java`, scale 2 `HALF_UP`

**Checkpoint**: buying power is reported and reconciles with the reservations; nothing validates an order here — order submission stays out of scope (spec Assumptions).

---

## Phase 7: User Story 5 - Total account value (Priority: P5)

**Goal**: one figure for the account — full settled cash, reservations included, plus the market value of every position — absent whenever any listed position lacks a price.

**Independent Test**: user `1` reports `totalAccountValue: 889756.00`, which equals `settledCash` plus the position values in the same response; the Scenario 4 fixture reports `null`; user `2` reports `0.00`.

### Tests for User Story 5

- [X] T072 [P] [US5] Plain-Java total test in `src/test/java/com/cocos/trading/domain/portfolio/PortfolioTotalValueTest.java`: the total is settled cash + Σ market value including a negative position's contribution (FR-012, FR-017), uses settled rather than available cash so it exceeds `availableCash + Σ marketValue` whenever something is reserved (spec US5 AS2), is `0` for a user with no orders (FR-014), and is absent as soon as one position is unpriced rather than a partial sum (FR-021)
- [X] T073 [P] [US5] Plain-Java ordering test in `src/test/java/com/cocos/trading/domain/portfolio/CashExclusionOrderingTest.java`: a user holding only the `MONEDA` instrument plus priced instruments reports a **non-null** total, pinning that FR-005 runs before FR-020/FR-021 (FR-023, SC-012)
- [X] T074 [P] [US5] Functional test in `src/test/java/com/cocos/trading/adapter/web/PortfolioTotalValueTest.java`: user `1`'s `889756.00` and the self-reconciliation `settledCash + Σ marketValue == totalAccountValue` asserted from the response alone for every user in the seed (SC-007, SC-012), user `2`'s `0.00`, and the Scenario 4 fixture's `null`
- [X] T075 [P] [US5] Functional test in `src/test/java/com/cocos/trading/adapter/web/PortfolioRepeatedReadTest.java`: two consecutive requests for user `1` with no intervening order return byte-identical bodies, position order included (SC-006, quickstart Scenario 6)

### Implementation for User Story 5

- [X] T076 [US5] Add `totalAccountValue()` as `Optional<BigDecimal>` to `src/main/java/com/cocos/trading/domain/portfolio/Portfolio.java` — settled cash + Σ market value, scale 2 `HALF_UP` (FR-012)
- [X] T077 [US5] Suppress the total in `src/main/java/com/cocos/trading/domain/portfolio/PortfolioProjection.java` when any listed position has no market value, and only there — cash absence is not a case, since cash is always known (FR-021, FR-023)
- [X] T078 [US5] Add `totalAccountValue` to `PortfolioResponse` in `src/main/java/com/cocos/trading/adapter/web/PortfolioResponse.java`, absent as `null`

**Checkpoint**: all five stories functional; every number in [quickstart.md](quickstart.md) reproducible.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [X] T079 Check the controller against [contracts/portfolio.openapi.yaml](contracts/portfolio.openapi.yaml) field by field — names, nullability, the three worked examples — and fix whichever side is wrong, the contract included (research R-022)
- [X] T080 [P] Update `docs/api/trading-api.http` so its portfolio requests and expected figures match what ships, including the `404`/`400` pair (research R-023)
- [X] T081 [P] Update the `DELETE BEFORE DELIVERY` block in `README.md`, which claims no endpoint and no index migration exist, and add `docs/api/trading-api.http` to its "Where things are" table (plan § Deferred, by decision)
- [X] T082 [P] Verify no production file, DTO or test uses `double`, `float` or `BigDecimal.equals` across `src/main/java/com/cocos/trading/` and `src/test/java/com/cocos/trading/` (constitution II)
- [X] T083 [P] Verify no comment was added anywhere under `src/main/java/com/cocos/trading/` (constitution VII) and that `src/main/java/com/cocos/trading/domain/` imports nothing outside the JDK (constitution VIII)
- [X] T084 Run `./mvnw verify` and review the JaCoCo report under `target/site/jacoco/` — reported, not gated (ADR-0011)
- [X] T085 Walk all six scenarios of [quickstart.md](quickstart.md) by hand against `./mvnw spring-boot:run` with `docker compose up -d`, confirming the ERROR and WARN lines appear in the application log exactly as [contracts/diagnostics.md](contracts/diagnostics.md) states, then reset with `docker compose down -v`
- [X] T086 Put research's two open questions to the author and act on the answers — Q-001 `springdoc-openapi` in the running app, Q-002 a Micrometer counter per diagnostic; both are additions, neither blocks anything above ([research.md](research.md#open-questions-for-the-author))

---

## Phase 9: Restructure into layers (constitution 2.0.0)

**Purpose**: the author rejected the ports-and-adapters result because the flow of a request could
not be followed: `PortfolioProjection` and `AccountLedger` fetched their own data through ports.
Same behaviour, same 78 tests, new shape: controller → service → model and persistence.

- [X] T087 Amend the constitution to 2.0.0 — principle VIII redefined as layers, I/III/V/IX
  reworded — and write `docs/adr/0017-layers-with-a-service-that-orchestrates.md` with its row in
  `docs/adr/README.md`
- [X] T088 Move the model to `model/ledger` and `model/portfolio`. `AccountLedger` takes
  `List<LedgerEntry>` and exposes `snapshot()`; `AccountSnapshot` gains `heldInstrumentIds()`;
  `PortfolioProjection` is replaced by `Portfolio.valued(snapshot, closes)`, which returns
  `shortPositions()` and `unpricedPositions()` instead of calling a diagnostics port
- [X] T089 Replace the ports and their `Jpa*` implementations with `persistence/`: `UserRepository`,
  `OrderRepository`, `MarketDataRepository` (the empty-list guard as a default method), entities in
  `persistence/entity`, and `UserMapper`, `OrderMapper`, `MarketDataMapper` in `persistence/mapper`
- [X] T090 Replace `PortfolioQueryService`, `DomainConfiguration` and `LoggingPortfolioDiagnostics`
  with `service/PortfolioService`: the transaction boundary, the orchestration of repositories, mappers and
  model, and the ERROR/WARN log lines of [contracts/diagnostics.md](contracts/diagnostics.md)
- [X] T091 Move HTTP to `controller/` and the response records to `controller/dto/`
- [X] T092 Rewrite the model tests without doubles (`RecordingLedgerEntries` and
  `RecordingDiagnostics` deleted; `Projection*Test` become `Valuation*Test`), and the persistence
  tests as `OrderRepositoryTest` and `MarketDataRepositoryTest` over repository + mapper
- [X] T093 Update plan.md, data-model.md, research.md (amendment notes), contracts/diagnostics.md
  and ADR-0014/0015, which were not yet committed
- [X] T094 Run `./mvnw verify`: 78 tests green, the same count and figures as before the
  restructure
- [X] T095 Rename the persistence `XxxDAO` interfaces to `XxxRepository` (`OrderRepository`,
  `UserRepository`, `MarketDataRepository`, and their tests), the convention chosen by the author
- [X] T096 Rename the model's `LedgerEntry` to `Order` (`entryId` → `orderId`,
  `OrderMapper.toOrders`, the test helper `Ledgers.orders`), so the model speaks the challenge's
  vocabulary; `AccountLedger` keeps the ledger concept of ADR-0002 at the level of the whole set
- [X] T097 Clean-code pass on `AccountLedger`: `snapshot()` composes four private methods, one per
  figure (`settledCash`, `reservedCash`, `holdings`, `reservedShares`); the parallel
  `instruments`/`walks` maps become one `Map<InstrumentRef, CostBasisWalk>`; `Order.movesPosition()`
  replaces the `signum() != 0` check; the flat-holding filter moves from `AccountSnapshot`'s
  constructor to `holdings()` via `Holding.isFlat()`. ADR-0015's single-pass consequence restated
- [X] T098 `holdings()` and `reservedShares()` as streams: `reservedShares` with `groupingBy` +
  `reducing`; `holdings` groups the position-moving orders by instrument with `groupingBy` (encounter
  order kept within each group on a sequential stream) and walks each group in `holdingOf`. A custom
  `Collector` was rejected: a cost-basis walk has no meaningful combiner
- [X] T099 Amend the constitution to 2.1.0 (method-level readability and measured performance in
  IX; design documents specify contracts, not code shape; a refactor task closes every story), add
  the refactor task to `.specify/templates/overrides/tasks-template.md`, and restate data-model's
  fold section as figures and rules instead of private methods
- [X] T100 Make tests required, not optional: constitution V (still 2.1.0, uncommitted) requires
  every behaviour a feature adds to be tested at the level where it lives, test-first allowed but
  not required; the tasks template override and the `speckit-tasks` skill drop Spec Kit's "tests
  are OPTIONAL unless requested" default
- [X] T101 Split `model/ledger` by concept: `model/order` (`Order`, `OrderSide`, `OrderStatus`,
  `OrderKind`), `model/instrument` (`InstrumentRef`), `model/user` (`UserId`), and `DecimalPolicy`
  to the root of `model` — which also removes the `ledger ↔ portfolio` package cycle it caused
  from `portfolio/`. Dependencies now run `portfolio → ledger → order → instrument`
- [X] T102 Review pass, behaviour unchanged, 78 tests green after each step: (1) the latest-close
  query returns a typed `LatestClose` projection instead of `List<Object[]>`; (5) `PortfolioService`
  at one level of abstraction — `requireExisting`, `ledgerOf`, `latestClosesOf` — with the project's
  import order; (2) `CostBasisWalk.accept` dispatches to named cases `open`, `extend`, `reduce`,
  `close`, `crossZero`; (6) `DecimalPolicy.scaleAverageCost` vs `averageCost(cost, shares)`, the
  unused `UnknownUserException.userId()` removed, and `idx_orders_userid_instrumentid` dropped from
  `V2` (no query reads it; R-018 note); (4) `Closes` as a first-class collection, which removes the
  `Optional` parameter from `Portfolio.positionOf`; `totalReturnPercentOf` likewise takes the
  market value itself and is reached through `flatMap`, so no method takes an `Optional`. `OrderSide` as a table was declined: it trades
  explicit constant bodies for a `0` sentinel

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: needs Setup — **blocks every story**
- **US1 (Phase 3)**: needs Foundational
- **US2 (Phase 4)**: needs US1 — market value multiplies the quantity US1 produces
- **US3 (Phase 5)**: needs US2 — the return compares against the market value
- **US4 (Phase 6)**: needs Foundational only; independent of US1-US3 and can run in parallel with them
- **US5 (Phase 7)**: needs US2 (position values) and US4 (settled cash)
- **Polish (Phase 8)**: needs every story that is shipping

The story chain is real, not conventional: each later position attribute consumes the previous one, which is the priority order the spec argues for. US4 is the one story that can be built at any point after Phase 2.

### Within Each Story

- Tests are written before the implementation they cover and must fail first (constitution V)
- Value types before the fold, the fold before the projection, the projection before the response mapping
- Each story ends at a checkpoint where the endpoint is coherent and demonstrable

### Parallel Opportunities

- T002 and T003 in Setup
- T005-T009, T011-T015, T016-T017, T019-T020, T024, T030-T031 in Foundational — the independent value types, ports, entities, repository interfaces and the two ADRs
- All four test tasks within each story (T032-T035, T046-T048, T054-T057, T064-T066, T072-T075) — different files, no shared state
- T036 with T039; T044 with T045
- US4 (T064-T071) alongside US1-US3 for a second developer
- T080-T083 in Polish

---

## Parallel Example: User Story 1

```bash
# All four US1 test files at once (they must fail before T036 starts):
Task: "Plain-Java fold test in src/test/java/com/cocos/trading/domain/ledger/AccountLedgerQuantityTest.java"
Task: "Plain-Java projection test in src/test/java/com/cocos/trading/domain/portfolio/ProjectionShortPositionTest.java"
Task: "Persistence test in src/test/java/com/cocos/trading/adapter/persistence/JpaLedgerEntriesTest.java"
Task: "Functional test in src/test/java/com/cocos/trading/adapter/web/PortfolioQuantitiesTest.java"

# The two independent domain types:
Task: "Create Holding in src/main/java/com/cocos/trading/domain/ledger/Holding.java"
Task: "Create Position in src/main/java/com/cocos/trading/domain/portfolio/Position.java"
```

---

## Implementation Strategy

### MVP first (US1 only)

1. Phase 1 Setup → 2. Phase 2 Foundational (blocks everything) → 3. Phase 3 US1
4. **Stop and validate**: user `1` reports BMA `-10`, METR `500`, PAMP `40`, no `ARS`, one ERROR line, `404` for an unknown id
5. Demo-able: a portfolio that reports only quantities is already useful (spec US1)

### Incremental delivery

1. Setup + Foundational → the endpoint answers
2. + US1 → quantities (MVP, SC-001, SC-009, SC-011)
3. + US2 → market values and honest absence (SC-002, SC-010)
4. + US3 → total return, crossing included (SC-003, SC-013)
5. + US4 → buying power (SC-004)
6. + US5 → the account total (SC-007, SC-012)
7. + Polish → contract, docs, README, quickstart sweep

Each step adds a field and breaks none of the earlier ones, so every checkpoint is a demo.

### Parallel team strategy

Two developers after Phase 2: one takes the position chain US1 → US2 → US3, the other takes US4 immediately, then they meet at US5. The seam is `AccountSnapshot` (T037/T068) and `AccountLedger`'s single pass (T038/T060/T069) — three files both touch, so they are worth agreeing on before splitting.

---

## Notes

- **Out of scope, deliberately**: order submission, cancellation and validation. `availableCash()` and `availableQuantityOf()` are computed and tested here because FR-016 requires the two readers to share one fold, but nothing in this feature validates an order.
- `previousclose` is never mapped on any entity — FR-009 enforced by absence rather than by a rule.
- Every acceptance figure in these tasks was measured on `src/main/resources/db/seed/V1000__given_dataset.sql`, not estimated (plan § Summary).
- 86 tasks; commit after each or after a logical group, and stop at any checkpoint to validate a story on its own.
