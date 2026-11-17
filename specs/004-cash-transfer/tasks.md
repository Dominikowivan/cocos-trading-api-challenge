---

description: "Task list for Cash Transfer"
---

# Tasks: Cash Transfer

**Input**: Design documents from `/specs/004-cash-transfer/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/cash-transfer.openapi.yaml](contracts/cash-transfer.openapi.yaml), [quickstart.md](quickstart.md)

**Tests**: REQUIRED for every user story (constitution V): every behaviour a story adds is covered at the level where it lives — plain Java for the transfer's rules and its canonical form, Testcontainers for the writes and the races, Testcontainers + MockMvc for HTTP with every response validated against `docs/api/openapi.yaml` (ADR-0024). Writing them before the implementation is allowed, not required; a story is not done without them.

**Organization**: tasks are grouped by user story so each story can be implemented, tested and demonstrated on its own, in the journal's order: the endpoint (US1), its lock (US2), its idempotency key (US3), then the extraction of what US2 and US3 duplicate from order submission (US4).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- **[Story]**: which user story the task belongs to (US1 … US4)
- Every task names its exact file path

## Path Conventions

Single-module Maven web service (plan.md, *Project Structure*): sources under `src/main/java/com/cocos/trading/`, tests under `src/test/java/com/cocos/trading/`, docs under `docs/`. **No migration** in this feature (R-313).

## Already done before this task list

- ADR-0038, ADR-0039 and ADR-0040 are written, with their rows in `docs/adr/README.md` (checked 2026-11-17).
- The constitution is amended to 2.4.0: cash transfer is in the endpoint list and principle VIII admits `service/userwrite`.

Nothing below repeats them. File names are proposals from plan.md; the contracts in data-model.md are what binds.

## Rules every task follows

- No comments, Javadoc included (constitution VII). Money is `BigDecimal`, compared with `compareTo` (II).
- The model (`model/transfer`) imports no Spring, Jakarta, JPA, web type or clock (VIII).
- Seeded figures (data-model.md, quickstart.md): user `1` settled `753,000.00`, available `627,500.00`; users `2`, `3`, `4` have no orders; `ARS` is the only `MONEDA` instrument (id `66`); `PAMP` (id `47`) closes at `925.85`. Tests use seeded users and need no fixture.
- `specs/004-cash-transfer/contracts/` is a design-time proposal and is never edited; the live contract is `docs/api/openapi.yaml`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: the live contract every HTTP test of this feature validates against, and the request collection

- [ ] T001 Add the `transferCash` operation (`POST /api/v1/users/{userId}/cash-transfers`, optional `Idempotency-Key` header, answers `201`, `400`, `404`, `409`, `503`) and its `CashTransfer` request schema (closed: `side` enum `CASH_IN`/`CASH_OUT`, `amount` integer-valued number, `additionalProperties: false`) and `RecordedCashTransfer` response schema (`orderId`, `userId`, `side`, `amount` at money scale, `status` `FILLED`/`REJECTED`, `rejectionReason` nullable, `transferredAt`) to docs/api/openapi.yaml, taken from specs/004-cash-transfer/contracts/cash-transfer.openapi.yaml; reuse the existing problem-detail schema for the error answers
- [ ] T002 [P] Add the quickstart's requests (deposit, withdrawal, rejected withdrawal, keyed deposit twice, keyed conflict) to docs/api/trading-api.http, following the format of its existing order submission requests

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the write path widened to cash rows, the lookup of the cash instrument, and an endpoint-neutral unreadable-body answer — what every story needs before it can record a transfer

**CRITICAL**: no user story work can begin until this phase is complete

- [ ] T003 Declare `String name()` in src/main/java/com/cocos/trading/model/order/LedgerSide.java (both `OrderSide` and `CashMovement` already implement it as enums), so the write path can store either kind of side by name (R-307)
- [ ] T004 Widen `side` in src/main/java/com/cocos/trading/model/order/NewOrder.java from `OrderSide` to `LedgerSide` and fix every caller to compile with no behaviour change: src/main/java/com/cocos/trading/model/submission/PricedSubmission.java (or wherever `placedBy` builds the `NewOrder`), src/main/java/com/cocos/trading/service/submission/SubmittedOrder.java, src/main/java/com/cocos/trading/controller/dto/OrderResponse.java (still writes `side().name()`), src/main/java/com/cocos/trading/persistence/mapper/OrderMapper.java `toNewOrder` / `toNewTransition`, and any test helper under src/test/java/com/cocos/trading/model/submission/ that reads `side()` as an `OrderSide` (R-307; depends on T003)
- [ ] T005 Make `OrderMapper.toPlacedOrder` in src/main/java/com/cocos/trading/persistence/mapper/OrderMapper.java read the side back through the existing private `toLedgerSide` instead of `OrderSide.valueOf`, so a stored `CASH_IN` / `CASH_OUT` row can be read back for a replay (depends on T004)
- [ ] T006 Testcontainers test in src/test/java/com/cocos/trading/persistence/CashOrderAppendTest.java (extends `PersistenceTestBase`): a `NewOrder` with side `CASH_IN` and one with `CASH_OUT`, `MARKET`, on instrument `66`, size `100000`, price `1.00`, one `FILLED` and one `REJECTED` for `INSUFFICIENT_FUNDS`, are appended with `OrderRepository.append` + `OrderStatusHistoryRepository.appendTransitionOf`; `findPlacedOrder` reads each back with its `CashMovement` side, and the history row carries the reason only for the rejected one (FR-006, FR-010; depends on T005)
- [ ] T007 [P] Add a read of the instruments of one type to src/main/java/com/cocos/trading/persistence/InstrumentRepository.java (a derived query such as `findByType(String type)` returning a `List<InstrumentEntity>`), used with `InstrumentRef.CASH` (R-303)
- [ ] T008 [P] Testcontainers test in src/test/java/com/cocos/trading/persistence/InstrumentRepositoryTest.java (add a test method) that the `MONEDA` read returns exactly one instrument, `ARS`, id `66` (depends on T007)
- [ ] T009 [P] Make the detail of `malformedBody` in src/main/java/com/cocos/trading/controller/ApiExceptionHandler.java endpoint-neutral (e.g. "The request body is not readable JSON of the expected shape") — it is shared by two endpoints now and no test or contract asserts its text (R-312)

**Checkpoint**: a cash row can be written and read back through the one write path; the cash instrument can be found by type. `./mvnw verify` is green with no existing test changed except the helpers T004 had to recompile.

---

## Phase 3: User Story 1 - Deposit and withdraw pesos (Priority: P1) 🎯 MVP

**Goal**: `POST /api/v1/users/{userId}/cash-transfers` records one `CASH_IN` or `CASH_OUT` order on `ARS` at price `1`; a deposit is always `FILLED`; a withdrawal is `FILLED` when available cash covers it and `REJECTED` with `INSUFFICIENT_FUNDS` otherwise; both answer `201` with the transfer's own shape; malformed requests answer `400` / `404` with nothing written.

**Independent Test**: for user `2`, deposit `100000` → portfolio reports `100000.00` available; withdraw `100001` → `REJECTED`; withdraw `100000` → `FILLED`, cash `0.00`. For user `1`, withdraw `700000` → `REJECTED` although settled cash is `753,000.00`.

### Tests for User Story 1 (REQUIRED - constitution V)

- [ ] T010 [P] [US1] Plain-Java test in src/test/java/com/cocos/trading/model/transfer/CashTransferAmountTest.java: `100` and `100.00` are both accepted as one hundred pesos; `100.50`, `0`, `-5`, `2147483648` and a missing amount are refused with a message naming the rule and the value; `1` and `2147483647` are accepted; nothing is rounded (FR-003, R-304)
- [ ] T011 [P] [US1] Plain-Java test in src/test/java/com/cocos/trading/model/transfer/CashTransferDecisionTest.java, building an `AccountSnapshot` from orders the way src/test/java/com/cocos/trading/model/ledger/Ledgers.java does: `CASH_IN` is `FILLED` whatever the available cash, negative included; `CASH_OUT` equal to available cash is `FILLED`, one peso more is `REJECTED` with `INSUFFICIENT_FUNDS`; with a negative available cash every withdrawal is `REJECTED`; in every case the order is `MARKET`, price `1.00`, size = amount, on the cash instrument passed in, for the user and instant passed in (FR-006 – FR-008)
- [ ] T012 [P] [US1] Create src/test/java/com/cocos/trading/controller/CashTransferWebTestBase.java extending `WebTestBase` (as src/test/java/com/cocos/trading/controller/SubmissionWebTestBase.java does): the path `/api/v1/users/{userId}/cash-transfers`, the seeded user constants (`SEEDED_USER = 1`, users `2`, `3`, `4` with no orders, `ARS = 66`, `PAMP = 47`), and helpers to post a transfer body, read a user's available and settled cash from the portfolio, and count a user's orders (to assert "nothing written")
- [ ] T013 [P] [US1] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CashTransferDepositWithdrawTest.java: quickstart scenario 1 — user `2` deposits `100000` → `201`, `CASH_IN`, `amount` `100000.00`, `FILLED`, `rejectionReason` `null`, settled and available `100000.00`; withdraws `100001` → `201`, `REJECTED`, `INSUFFICIENT_FUNDS`, cash unchanged; withdraws `100000` → `FILLED`, cash `0.00`. Scenario 4 — user `3` withdraws `1` → `201`, `REJECTED` (US1 scenarios 1, 2, 5, 8; depends on T012)
- [ ] T014 [P] [US1] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CashTransferFundsBoundaryTest.java: user `1` withdraws `627501` → `REJECTED`; `627500` → `FILLED` and available `0.00`; in a fresh class state `700000` → `REJECTED` while the portfolio still reports available `627500.00` — and the available cash the portfolio reported before each withdrawal is the one the decision used (US1 scenarios 3, 4; SC-002, SC-003, SC-007; depends on T012)
- [ ] T015 [P] [US1] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CashTransferSpendableDepositTest.java: user `4` deposits `500000`, then `POST /api/v1/users/4/orders` `{"instrumentId":47,"type":"MARKET","side":"BUY","size":100}` → `201`, `FILLED` at `925.85`, available `407415.00` (US1 scenario 6; SC-001; depends on T012)
- [ ] T016 [P] [US1] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CashTransferHistoryTest.java: a `FILLED` deposit and a `REJECTED` withdrawal each leave exactly one order and exactly one `order_status_history` row for it, the rejected one with reason `INSUFFICIENT_FUNDS` and the filled one with none; the order row is `MARKET`, instrument `66`, price `1.00`, size = amount (US1 scenario 7; FR-006, FR-010; SC-004; depends on T012)
- [ ] T017 [P] [US1] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CashTransferRefusalTest.java, every row of quickstart scenario 6 except the closed-body and key rows: `side` `BUY`, `SELL`, unknown and missing → `400`; amount missing, `0`, `-5`, `100.50`, `2147483648` → `400`; `100.00` → `201` recorded as `100.00`; user `999` with a valid body → `404`; user `999` with `{"side":"BUY"}` → `400` (body judged first, ADR-0033); an unreadable body → `400`. Every refusal leaves the user's order count unchanged (FR-002, FR-003, FR-005; SC-009; depends on T012)
- [ ] T018 [P] [US1] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CashTransferBodyFieldsTest.java: a valid body plus each of `instrumentId`, `type`, `price`, `size`, `status`, and one unlisted field (`currency`) → `400` naming the field, nothing written (FR-004, R-305; depends on T012)

### Implementation for User Story 1

- [ ] T019 [US1] Create src/main/java/com/cocos/trading/model/transfer/CashTransfer.java: valid by construction from a `CashMovement` and a `BigDecimal` amount (whole pesos once trailing zeros are stripped, `1` ≤ amount ≤ `2,147,483,647`, the bound stated once here as the ledger's size capacity; a violation throws `IllegalArgumentException` naming the rule and the value, never rounding); given the user's `AccountSnapshot`, the cash `InstrumentRef`, the `UserId` and the instant, it answers the `NewOrder` to record per data-model.md section 2's decision table, with the price of a peso (`Price` of `1`) named once in this class (FR-003, FR-006 – FR-008, R-304, R-306; makes T010, T011 pass)
- [ ] T020 [P] [US1] Create src/main/java/com/cocos/trading/service/transfer/InvalidTransferException.java mirroring src/main/java/com/cocos/trading/service/submission/InvalidSubmissionException.java (message constructor, cause constructor, a static `refusing(Supplier)` that turns the model's `IllegalArgumentException` into this exception)
- [ ] T021 [P] [US1] Create src/main/java/com/cocos/trading/service/transfer/TransferRequest.java: the raw request the controller read — `long userId`, `String side`, `BigDecimal amount`, the `Set<String>` names of any body fields outside `side` and `amount`, and a nullable `String idempotencyKey` (unused until US3)
- [ ] T022 [P] [US1] Create src/main/java/com/cocos/trading/service/transfer/RecordedTransfer.java: what the controller maps — order id, `UserId`, `CashMovement` side, amount, `OrderStatus`, `Optional<RejectionReason>`, instant — built from an order id and the `NewOrder` that was appended (a static `of(Integer, NewOrder)`, as `SubmittedOrder.of` does)
- [ ] T023 [US1] Create src/main/java/com/cocos/trading/service/transfer/RequestedTransfer.java: reads and validates a `TransferRequest` with no database — refuses any field outside the body's shape (naming it), parses `side` into a `CashMovement` (refusing `BUY`, `SELL`, unknown and missing with a message naming the accepted values), builds the `CashTransfer` through `InvalidTransferException.refusing`, and exposes the `UserId` (FR-002 – FR-004; depends on T019 – T021)
- [ ] T024 [US1] Create src/main/java/com/cocos/trading/service/transfer/CashTransferService.java: `@Service`, `@Transactional(isolation = Isolation.READ_COMMITTED)`, one public `transfer(TransferRequest)` returning `RecordedTransfer` that reads as the summary of its steps — read the request (T023), verify the user exists (non-locking `existsById`, else `UnknownUserException` → `404`), find the cash instrument (the T007 read; zero or several rows is `IllegalStateException`, a `500` and the deployment's fault), fold the user's contributing orders into an `AccountSnapshot` as `OrderSubmissionService.availabilityOf` does, ask the `CashTransfer` for the order, append it with `OrderRepository.append` and its transition with `OrderStatusHistoryRepository.appendTransitionOf`, return `RecordedTransfer.of`; the instant comes from the injected `Clock`, truncated to microseconds as submission's `now()` does (FR-001, FR-005, FR-009, FR-010; depends on T007, T022, T023)
- [ ] T025 [P] [US1] Create src/main/java/com/cocos/trading/controller/dto/CashTransferRequest.java: `side` and `amount` (`BigDecimal`), plus a `@JsonAnySetter` collecting the names of every other field so the use case can refuse them (R-305; not bean validation, ADR-0033)
- [ ] T026 [P] [US1] Create src/main/java/com/cocos/trading/controller/dto/CashTransferResponse.java: `orderId`, `userId`, `side`, `amount` through `DecimalPolicy.money`, `status`, `rejectionReason` (JSON `null` when absent, as `OrderResponse` writes it), `transferredAt`, with a static `from(RecordedTransfer)` (FR-011, FR-013; depends on T022)
- [ ] T027 [US1] Create src/main/java/com/cocos/trading/controller/CashTransferController.java: `POST /api/v1/users/{userId}/cash-transfers`, builds the `TransferRequest` from the path and the DTO, calls `CashTransferService.transfer` once, answers `201` with `CashTransferResponse.from` — HTTP translation only, following src/main/java/com/cocos/trading/controller/OrderSubmissionController.java (depends on T024 – T026)
- [ ] T028 [US1] Map `InvalidTransferException` to `400` in src/main/java/com/cocos/trading/controller/ApiExceptionHandler.java, as `InvalidSubmissionException` is (depends on T020)
- [ ] T029 [US1] Run `./mvnw verify` and make T010 – T018 pass with every response validated against docs/api/openapi.yaml; fix the contract (T001) only where it disagrees with the plan's rules, never the other way round

### Refactor for User Story 1

- [ ] T030 [US1] Refactor: with this story's tests green, review the code it added (model/transfer, service/transfer, the controller, its DTOs) against constitution IX (one thing per method, public method as the summary of its steps, steps below in call order, no performance-driven structure without a measurement) and clean it; the tests stay green and no behaviour changes

**Checkpoint**: deposits and withdrawals work end to end and their effect is visible in the portfolio — the MVP

---

## Phase 4: User Story 2 - A withdrawal and an order cannot spend the same pesos (Priority: P2)

**Goal**: every transfer takes the same per-user row lock order submission takes (`UserRepository.lockById`, ADR-0036) before reading the fold, so transfers and orders of one user share one serialisation and different users never wait on each other.

**Independent Test**: for user `1`, fire a withdrawal of `400000` and a `MARKET BUY` of 400 `PAMP` (`370,340.00`) at the same instant and check exactly one is `FILLED` and the other `REJECTED`.

### Tests for User Story 2 (REQUIRED - constitution V)

- [ ] T031 [P] [US2] Two-thread Testcontainers test in src/test/java/com/cocos/trading/controller/ConcurrentWithdrawalTest.java using src/test/java/com/cocos/trading/controller/Simultaneously.java, as src/test/java/com/cocos/trading/controller/ConcurrentBuySubmissionTest.java does: user `1`, two withdrawals of `400000` → exactly one `FILLED`, one `REJECTED` `INSUFFICIENT_FUNDS`; two withdrawals of `300000` → both `FILLED`, available `27500.00`; afterwards available cash is never below zero (US2 scenarios 1, 3, 5; FR-014)
- [ ] T032 [P] [US2] Two-thread Testcontainers test in src/test/java/com/cocos/trading/controller/ConcurrentWithdrawalAndOrderTest.java: user `1`, a withdrawal of `400000` and `POST /orders` `MARKET BUY` 400 `PAMP` at the same instant → exactly one accepted, the other `REJECTED` `INSUFFICIENT_FUNDS`, available cash not below zero (US2 scenarios 2, 5; SC-005)
- [ ] T033 [P] [US2] Testcontainers test in src/test/java/com/cocos/trading/controller/CashTransferUserIsolationTest.java: while user `2`'s `users` row is held `FOR UPDATE` from another connection (as src/test/java/com/cocos/trading/persistence/UserLockContentionTest.java holds one), user `1`'s withdrawal completes without waiting for it to be released (US2 scenario 4)
- [ ] T034 [P] [US2] Testcontainers test in src/test/java/com/cocos/trading/controller/CashTransferLockDeadlineTest.java, as src/test/java/com/cocos/trading/controller/SubmissionLockDeadlineTest.java does for orders: a transfer for a user whose row is held locked past the deadline answers `503` and writes nothing (R-312, ADR-0031)

### Implementation for User Story 2

- [ ] T035 [US2] In src/main/java/com/cocos/trading/service/transfer/CashTransferService.java, lock the user's row with `UserRepository.lockById` (absent → `UnknownUserException`) after the existence check and before the cash instrument and the fold are read — a deliberate copy of `OrderSubmissionService.lockUser`, which the slice rule forbids importing and US4 removes (plan, *Complexity Tracking*; R-309)
- [ ] T036 [US2] Run `./mvnw verify`: T031 – T034 pass, and every US1 test still passes unchanged

### Refactor for User Story 2

- [ ] T037 [US2] Refactor: with this story's tests green, review the code it added against constitution IX (one thing per method, public method as the summary of its steps, steps below in call order, no performance-driven structure without a measurement) and clean it; the tests stay green and no behaviour changes. The lock call's duplicate with submission stays until US4

**Checkpoint**: transfers and orders of one user are serialised with each other; US1 still works on its own

---

## Phase 5: User Story 3 - The same transfer sent twice moves money once (Priority: P3)

**Goal**: an optional `Idempotency-Key` header, claimed in `order_idempotency` before the lock exactly as ADR-0037 decided for orders; the transfer's canonical form starts with `operation=CASH_TRANSFER`, so a key already used for an order (or the other way round) is a `409`; order submission's form is untouched.

**Independent Test**: deposit `100000` for user `2` twice with key `dep-1` → both responses name the same order and cash is `100000.00`; then a withdrawal with `dep-1` → `409`.

### Tests for User Story 3 (REQUIRED - constitution V)

- [ ] T038 [P] [US3] Plain-Java test in src/test/java/com/cocos/trading/service/transfer/TransferFingerprintTest.java: the canonical form is `operation=CASH_TRANSFER;userId=<id>;side=<text as sent>;amount=<whole pesos, plain>`; `100` and `100.00` give the same form and the same fingerprint; a different side, amount or user gives a different one; the form begins with the operation tag, which no order form does (order forms begin with `userId=`, src/main/java/com/cocos/trading/service/submission/RequestFingerprint.java) (R-310)
- [ ] T039 [P] [US3] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/IdempotentCashTransferTest.java, quickstart scenario 8: user `2` deposits `100000` twice with `dep-1` → same `orderId` and status, cash `100000.00`; `dep-1` with `CASH_OUT` `100000` → `409`, nothing written; `dep-1` with `amount` `100000.00` → `201`, the same order; user `3` with `dep-1` → a new order; user `2` withdraws `200000` with `w-1` → `REJECTED`, deposits more, retries `w-1` → the stored `REJECTED` with `INSUFFICIENT_FUNDS`; no key → executed normally; a blank and a 256-character key → `400`, nothing written (US3 scenarios 1 – 3, 5, 7; FR-015, FR-017; SC-006)
- [ ] T040 [P] [US3] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CashTransferKeySharedWithOrdersTest.java: user `1` places an order with key `k-1`, then a transfer with `k-1` → `409` and never that order; user `1` sends a transfer with `t-1`, then an order with `t-1` → `409` (US3 scenario 6; FR-016)
- [ ] T041 [P] [US3] Two-thread Testcontainers test in src/test/java/com/cocos/trading/controller/ConcurrentIdempotentCashTransferTest.java, as src/test/java/com/cocos/trading/controller/ConcurrentIdempotentSubmissionTest.java does: two deposits with the same key at the same instant → one order, both answers name it, cash raised once (US3 scenario 4)

### Implementation for User Story 3

- [ ] T042 [P] [US3] Create src/main/java/com/cocos/trading/service/transfer/IdempotencyKey.java, a copy of src/main/java/com/cocos/trading/service/submission/IdempotencyKey.java (non-blank, at most 255 characters) — deliberate, removed by US4
- [ ] T043 [P] [US3] Create src/main/java/com/cocos/trading/service/transfer/TransferFingerprint.java: the transfer's canonical form (package-visible `canonicalFormOf`, the amount normalised to whole pesos with `stripTrailingZeros().toPlainString()`) hashed with SHA-256 to hex, with `matches(String storedHex)` — the hashing a copy of `RequestFingerprint`'s, removed by US4 (R-310; makes T038 pass)
- [ ] T044 [P] [US3] Create src/main/java/com/cocos/trading/service/transfer/TransferIdempotencyConflictException.java (the key in its message) — the transfer's copy of submission's conflict, removed by US4
- [ ] T045 [US3] Create src/main/java/com/cocos/trading/service/transfer/IdempotentTransfer.java: the key (built through `InvalidTransferException.refusing`, so a bad key is a `400`) and the fingerprint of a `TransferRequest`, with `keyValue()`, `fingerprintHex()`, `wasSentBefore(String)` — a copy of submission's `IdempotentRequest`; expose it from `RequestedTransfer` as `Optional<IdempotentTransfer> idempotent()` when a key was sent (depends on T042, T043)
- [ ] T046 [US3] In src/main/java/com/cocos/trading/service/transfer/CashTransferService.java add submission's claim flow, in ADR-0037's order: existence, then, when keyed, `OrderIdempotencyRepository.claim` before the lock; claimed → the US1/US2 work, then `attachOrderTo`; not claimed → read the stored fingerprint, different → `TransferIdempotencyConflictException`, same → read the order the key names with `OrderRepository.findPlacedOrder` and its reason with `OrderStatusHistoryRepository.reasonOf`, and answer it as a `RecordedTransfer`. A recorded order whose side is not a `CashMovement` cannot match a transfer's fingerprint; if one is ever reached it is an `IllegalStateException`, not a response (FR-015 – FR-017; depends on T044, T045)
- [ ] T047 [US3] Read the optional `Idempotency-Key` header in src/main/java/com/cocos/trading/controller/CashTransferController.java into `TransferRequest.idempotencyKey`, and map `TransferIdempotencyConflictException` to `409` in src/main/java/com/cocos/trading/controller/ApiExceptionHandler.java with the same problem shape as submission's conflict (depends on T044)
- [ ] T048 [US3] Run `./mvnw verify`: T038 – T041 pass, US1 and US2 tests pass unchanged, and src/test/java/com/cocos/trading/service/submission/RequestFingerprintTest.java passes untouched (submission's form unchanged, FR-019)

### Refactor for User Story 3

- [ ] T049 [US3] Refactor: with this story's tests green, review the code it added against constitution IX (one thing per method, public method as the summary of its steps, steps below in call order, no performance-driven structure without a measurement) and clean it; the tests stay green and no behaviour changes. The claim flow's duplicate with submission stays until US4

**Checkpoint**: a retried transfer moves money once; keys are one namespace per user across both endpoints

---

## Phase 6: User Story 4 - Serialisation and idempotency live in one place (Priority: P4)

**Goal**: the existence check, the claim (attach / replay / conflict) and the user lock are extracted from both use cases into `service/userwrite` (ADR-0040, constitution 2.4.0 VIII), which both services call; nothing a caller observes changes.

**Independent Test**: `./mvnw verify` passes with **no test of order submission or of cash transfer edited** (only src/test/java/com/cocos/trading/ArchitectureTest.java changes, plus new userwrite tests), and ArchUnit asserts that only `service/userwrite` locks and claims.

### Tests for User Story 4 (REQUIRED - constitution V)

- [ ] T050 [US4] In src/test/java/com/cocos/trading/ArchitectureTest.java: change `use_cases_do_not_depend_on_each_other` so `com.cocos.trading.service.userwrite` is exempt as a dependency **target** only (use cases may depend on it); add a rule that `service.userwrite` depends on no other `service` subpackage; add a rule that `service.userwrite` holds no `@Service` and no `@RestController`; add a rule that only classes in `service.userwrite` call `UserRepository.lockById` and `OrderIdempotencyRepository.claim` (R-311, SC-008). These rules fail until T053 – T057 land
- [ ] T051 [P] [US4] Testcontainers test in src/test/java/com/cocos/trading/service/userwrite/UserWriteTransactionTest.java: calling the shared protection outside a transaction is refused with an `IllegalStateException` and writes nothing (it runs only inside the caller's transaction, data-model.md section 4)

### Implementation for User Story 4

- [ ] T052 [P] [US4] Move src/main/java/com/cocos/trading/service/submission/IdempotencyKey.java to src/main/java/com/cocos/trading/service/userwrite/IdempotencyKey.java (public, same rules and messages) and delete the transfer's copy src/main/java/com/cocos/trading/service/transfer/IdempotencyKey.java
- [ ] T053 [P] [US4] Move src/main/java/com/cocos/trading/service/submission/IdempotencyConflictException.java to src/main/java/com/cocos/trading/service/userwrite/IdempotencyConflictException.java, delete src/main/java/com/cocos/trading/service/transfer/TransferIdempotencyConflictException.java, and leave src/main/java/com/cocos/trading/controller/ApiExceptionHandler.java with one `409` mapping for the moved exception
- [ ] T054 [P] [US4] Create src/main/java/com/cocos/trading/service/userwrite/Fingerprint.java: the SHA-256 hex of a canonical form and `matches(String storedHex)`, the hashing moved out of `RequestFingerprint` and `TransferFingerprint`; each of those keeps its own `canonicalFormOf` (same signature and visibility, so src/test/java/com/cocos/trading/service/submission/RequestFingerprintTest.java and src/test/java/com/cocos/trading/service/transfer/TransferFingerprintTest.java pass unedited) and delegates the hash. The stored fingerprints of orders must stay byte-identical (FR-019)
- [ ] T055 [US4] Create src/main/java/com/cocos/trading/service/userwrite/UserWrite.java, a `@Component` (not a `@Service`, no `@Transactional` — `only_services_open_transactions_*` forbid it): given the `UserId`, an optional keyed request (key + fingerprint), the work and the replay, it verifies the user exists (`UnknownUserException` before any claim), claims the key when sent, takes `lockById`, runs the work, attaches the order to the key; when the claim is lost it answers the replay for the same fingerprint or throws `IdempotencyConflictException`. It refuses to run without an active transaction (checked with `TransactionSynchronizationManager.isActualTransactionActive()`) and never opens one. Also a keyed-request type if one is needed (e.g. src/main/java/com/cocos/trading/service/userwrite/KeyedRequest.java) (ADR-0036, ADR-0037, ADR-0040; depends on T052 – T054; makes T051 pass)
- [ ] T056 [US4] Make src/main/java/com/cocos/trading/service/submission/OrderSubmissionService.java delegate existence, claim, lock, attach, replay-or-conflict to `UserWrite`, keeping only its own work (`placedOrderOf` without the lock), its canonical form and its replay (read the order and reason back into a `SubmittedOrder`); reduce or delete src/main/java/com/cocos/trading/service/submission/IdempotentRequest.java accordingly; an invalid key is still a `400` through `InvalidSubmissionException.refusing` (depends on T055)
- [ ] T057 [US4] Make src/main/java/com/cocos/trading/service/transfer/CashTransferService.java delegate the same way and delete what US2 and US3 copied (its `lockUser`, its claim flow; reduce or delete src/main/java/com/cocos/trading/service/transfer/IdempotentTransfer.java); an invalid key is still a `400` through `InvalidTransferException.refusing` (depends on T055)
- [ ] T058 [US4] Run `./mvnw verify` and confirm with `git diff --stat develop -- src/test` that, beyond the tests this feature added, only src/test/java/com/cocos/trading/ArchitectureTest.java changed and no submission or transfer test was edited for the refactor; the T050 rules pass (US4 scenarios 1 – 3; FR-018, FR-019; SC-008)

### Refactor for User Story 4

- [ ] T059 [US4] Refactor: with every test green, review `service/userwrite`, `OrderSubmissionService` and `CashTransferService` against constitution IX (one thing per method, public method as the summary of its steps, steps below in call order, no performance-driven structure without a measurement) and clean them; each service's `transfer` / `submit` still reads top to bottom as its use case; the tests stay green and no behaviour changes

**Checkpoint**: the lock and the claim each exist once; both endpoints behave exactly as before the extraction

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: what a reviewer reads, and the full-feature validation

- [ ] T060 [P] List the cash transfer endpoint in README.md next to the other endpoints, as an addition to the statement (not a departure: the statement says transfers *can* be modelled as `CASH_IN`/`CASH_OUT` orders), with a pointer to ADR-0038 and ADR-0039; state that its body is closed while order submission's is not
- [ ] T061 [P] Check that docs/api/openapi.yaml and docs/api/trading-api.http match what was built (the response field names, the `409` for a key used on the other endpoint, the closed request schema) and that specs/004-cash-transfer/contracts/cash-transfer.openapi.yaml was not edited (`git diff develop -- specs/004-cash-transfer/contracts` is empty)
- [ ] T062 Run quickstart.md scenarios 1 – 6 and 8 by hand against `docker compose up -d` + `./mvnw spring-boot:run`, resetting with `docker compose down -v` between scenarios, and confirm every figure; scenario 7 and 9 are the automated suite's
- [ ] T063 Run `./mvnw verify` once more on the final tree (formatting check, ArchUnit, Trivy-free local build) and confirm the JaCoCo report covers the new code above the Sonar gate's 80% on new code (ADR-0020); add a test where a branch of model/transfer, service/transfer or service/userwrite is uncovered

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T001 must land before any MockMvc test can pass (contract validation).
- **Foundational (Phase 2)**: depends on Setup; BLOCKS every story. T003 → T004 → T005 → T006; T007 → T008; T009 independent.
- **US1 (Phase 3)**: depends on Foundational.
- **US2 (Phase 4)**: depends on US1 (it adds the lock to US1's service).
- **US3 (Phase 5)**: depends on US1; independent of US2 in code, but done after it in the journal's order, and its claim must precede US2's lock (ADR-0037).
- **US4 (Phase 6)**: depends on US2 **and** US3 — it extracts what they duplicated, and is only safe once both endpoints are proven by their tests.
- **Polish (Phase 7)**: depends on US4.

### User Story Dependencies

```text
Setup ─► Foundational ─► US1 ─┬─► US2 ─┐
                              └─► US3 ─┴─► US4 ─► Polish
```

Unlike the template's default, these stories are **not** independent in code: the journal sequences them (endpoint, then lock, then key, then extraction), and US4 is a refactor of US2 + US3. Each story is still independently **testable**: its tests prove its behaviour without the later stories.

### Within Each User Story

- Tests exist and pass before the story is done; test-first is allowed, not required.
- Model before service, service before controller, controller before exception mapping.
- The refactor task runs last, with the story's tests green.

### Parallel Opportunities

- Phase 1: T002 alongside T001.
- Phase 2: T007/T008 and T009 alongside the T003 → T006 chain.
- US1: all tests T010 – T018 in parallel (T013 – T018 after T012); T020, T021, T022 in parallel; T025, T026 in parallel.
- US2: T031 – T034 in parallel.
- US3: T038 – T041 in parallel; T042, T043, T044 in parallel.
- US4: T051 – T054 in parallel after T050.
- Polish: T060, T061 in parallel.

---

## Parallel Example: User Story 1

```bash
# The plain-Java tests and the web tests, once CashTransferWebTestBase (T012) exists:
Task: "Plain-Java test in src/test/java/com/cocos/trading/model/transfer/CashTransferAmountTest.java"
Task: "Plain-Java test in src/test/java/com/cocos/trading/model/transfer/CashTransferDecisionTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/CashTransferDepositWithdrawTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/CashTransferRefusalTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/CashTransferBodyFieldsTest.java"

# The use case's own types:
Task: "Create src/main/java/com/cocos/trading/service/transfer/InvalidTransferException.java"
Task: "Create src/main/java/com/cocos/trading/service/transfer/TransferRequest.java"
Task: "Create src/main/java/com/cocos/trading/service/transfer/RecordedTransfer.java"
```

## Parallel Example: User Story 3

```bash
Task: "Plain-Java test in src/test/java/com/cocos/trading/service/transfer/TransferFingerprintTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/IdempotentCashTransferTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/CashTransferKeySharedWithOrdersTest.java"
Task: "Two-thread test in src/test/java/com/cocos/trading/controller/ConcurrentIdempotentCashTransferTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: the live contract.
2. Phase 2: the widened write path and the cash instrument lookup.
3. Phase 3: US1.
4. **STOP and VALIDATE**: quickstart scenarios 1 – 6 by hand; `./mvnw verify`.

The MVP is not mergeable on its own: constitution IV requires the lock (US2) before the feature is done, and the plan's Complexity Tracking requires US4 so the US2/US3 duplicates never reach `develop` alone. Merge the feature whole.

### Incremental Delivery

1. Setup + Foundational → a cash row can be written.
2. US1 → deposits and withdrawals work (MVP, demo).
3. US2 → race-free against orders.
4. US3 → retries are safe.
5. US4 → one implementation of the protections; nothing visible changes.
6. Polish → README, contract check, quickstart, coverage.

Commit after each task or logical group, one commit per story at least.

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks.
- [Story] label maps a task to its user story for traceability.
- Every behaviour a story adds is covered by a passing test at the level where it lives.
- Avoid: editing a submission test to make US4 pass (that is the failure SC-008 exists to catch), restating the plan's rules in code comments, hardcoding instrument id `66` in main code (R-303).
