---

description: "Task list for Order Cancellation"
---

# Tasks: Order Cancellation

**Input**: Design documents from `/specs/005-order-cancellation/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/order-cancellation.openapi.yaml](contracts/order-cancellation.openapi.yaml), [quickstart.md](quickstart.md)

**Tests**: REQUIRED for every user story (constitution V): every behaviour a story adds is covered at the level where it lives — plain Java for the cancellation rule and its canonical form, Testcontainers for the scoped read, the guarded transition, the history rows and the races, Testcontainers + MockMvc for HTTP with every response validated against `docs/api/openapi.yaml` (ADR-0024). Writing them before the implementation is allowed, not required; a story is not done without them.

**Organization**: tasks are grouped by user story, in the spec's order: the cancellation (US1), its serialisation with the user's other writes (US2), its idempotency and the replay of every write endpoint as first recorded (US3).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- **[Story]**: which user story the task belongs to (US1 … US3)
- Every task names its exact file path

## Path Conventions

Single-module Maven web service (plan.md, *Project Structure*): sources under `src/main/java/com/cocos/trading/`, tests under `src/test/java/com/cocos/trading/`, docs under `docs/`. **No migration, no new dependency, no new configuration** in this feature.

## Already done before this task list

- ADR-0041, ADR-0042 and ADR-0043 are written, with their rows in `docs/adr/README.md`; ADR-0006 is marked superseded by ADR-0041 (checked 2026-11-17).
- README.md already lists `POST /api/v1/users/{userId}/orders/{orderId}/cancel` in its endpoint table (line 157); it does not yet cite ADR-0041 (T043).

Nothing below repeats them. File names are proposals from plan.md; the contracts in data-model.md are what binds.

## Rules every task follows

- No comments, Javadoc included (constitution VII). No money arithmetic is added (II applies to nothing new).
- The model (`model/order`) imports no Spring, Jakarta, JPA, web type or clock (VIII); the instant is an input.
- Every query is scoped by the untrusted user id: the order is read **by id and user id in one lookup**, never found first and its owner compared after (FR-004).
- The lock, the existence check and the claim are `service/userwrite`'s (`UserWrite.write`); `service/cancellation` never calls `UserRepository.lockById` or `OrderIdempotencyRepository.claim` (FR-016; the existing ArchUnit rules enforce it).
- Seeded figures (spec.md, quickstart.md): user `1` owns all eleven orders; `5` (`BUY LIMIT 50 LOMA @ 710`, reserves `35,500.00`) and `7` (`BUY LIMIT 60 BMA @ 1500`, reserves `90,000.00`) are `NEW`; `3` is `CANCELLED`, `6` `REJECTED`, `1`, `2`, `4`, `8`–`11` `FILLED` (`1` is `CASH_IN`, `8` `CASH_OUT`). User `1`: settled cash `753,000.00`, available `627,500.00`, `40` `PAMP` (id `47`, last close `925.85`). Users `2`–`4` have no orders. **No seeded order has a history row**; the first order written through the API in a fresh class is `12`.
- `specs/005-order-cancellation/contracts/` is a design-time proposal and is never edited; the live contract is `docs/api/openapi.yaml`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: the live contract every HTTP test of this feature validates against, and the request collection

- [X] T001 Add the `cancelOrder` operation to docs/api/openapi.yaml, taken from specs/005-order-cancellation/contracts/order-cancellation.openapi.yaml: `POST /api/v1/users/{userId}/orders/{orderId}/cancel`, path parameters `userId` and `orderId` (`integer`, `minimum: 1`), no request body, optional `Idempotency-Key` header (the same parameter definition submission and transfer use), answers `200` with the existing `Order` schema, and `400`, `404`, `409`, `503` with the existing problem-detail schema. In the `Order` schema, add `CANCELLED` to the `status` enum and replace "`CANCELLED` never originates here (ADR-0006)" with where it does originate (the cancellation, ADR-0041)
- [X] T002 [P] Add the quickstart's requests (scenario 1 cancel of order `5` and the portfolio read; scenario 3 a non-`NEW` order; scenario 4 a foreign order; scenario 6 a keyed cancellation twice and the conflicting reuse) to docs/api/trading-api.http, following the format of its order submission and cash transfer requests, and drop the line saying cancellation "gets its own as that feature lands"

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the history's `reason` column read by the row's status, so a `CANCELLED` / `USER_REQUESTED` row can exist without breaking any read that exists today (R-406, ADR-0042)

**CRITICAL**: no user story work can begin until this phase is complete — without it, the first cancellation makes a keyed submission replay of that order answer `500` (`RejectionReason.valueOf("USER_REQUESTED")`)

- [X] T003 [P] Create src/main/java/com/cocos/trading/model/order/CancellationReason.java: an enum with the single value `USER_REQUESTED`, separate from `RejectionReason`, which does not change (constitution IV's list does not grow)
- [X] T004 Make `reasonOf` in src/main/java/com/cocos/trading/persistence/OrderStatusHistoryRepository.java read only the reason of a `REJECTED` row (filter by the row's status before mapping), and add to src/main/java/com/cocos/trading/persistence/mapper/OrderMapper.java the mapping of a history row's `reason` column by the row's status — `REJECTED` → `RejectionReason`, `CANCELLED` → `CancellationReason`, any other status → none — which `reasonOf` uses; `toNewTransition` and `toReasonColumn` keep writing a `NewOrder`'s rejection reason unchanged (depends on T003)
- [X] T005 Testcontainers test in src/test/java/com/cocos/trading/persistence/OrderStatusHistoryReasonTest.java (extends `PersistenceTestBase`): for an order appended with `OrderRepository.append` + `appendTransitionOf`, a further history row `CANCELLED` / `USER_REQUESTED` inserted with JDBC does not make `reasonOf` throw and `reasonOf` answers empty for a `NEW` order so cancelled; a `REJECTED` / `INSUFFICIENT_FUNDS` order still answers `INSUFFICIENT_FUNDS` (R-406; depends on T004)

**Checkpoint**: a `CANCELLED` history row can exist and every existing read still works. `./mvnw verify` is green with no existing test changed.

---

## Phase 3: User Story 1 - Cancel a pending order and get its reservation back (Priority: P1) 🎯 MVP

**Goal**: `POST /api/v1/users/{userId}/orders/{orderId}/cancel` reads the user's order under the user's lock, asks the model whether its status allows cancelling (only `NEW`), sets `orders.status` to `CANCELLED` with a guarded update and appends one `CANCELLED` / `USER_REQUESTED` history row in one transaction, and answers `200` with the `Order` schema. A missing or foreign order is the same `404`; a non-`NEW` order is `409`; a non-positive id is `400`. The release is the unchanged fold.

**Independent Test**: for user `1`, cancel order `5` → available cash goes from `627,500.00` to `663,000.00`, settled stays `753,000.00`; cancel it again → `409`; cancel order `2` → `409` and unchanged.

### Tests for User Story 1 (REQUIRED - constitution V)

- [X] T006 [P] [US1] Plain-Java test in src/test/java/com/cocos/trading/model/order/CancellationRuleTest.java: for `NEW` the rule answers the transition to `CANCELLED` with reason `USER_REQUESTED` at the instant passed in; for `FILLED`, `REJECTED` and `CANCELLED` it answers not cancellable, naming the order id and its status; the answer does not depend on type or side (a `NEW` `SELL LIMIT` and a `NEW` `BUY LIMIT` are both cancellable) (FR-005, FR-007, R-408)
- [X] T007 [P] [US1] Testcontainers test in src/test/java/com/cocos/trading/persistence/OrderCancellationPersistenceTest.java (extends `PersistenceTestBase`): the scoped read finds order `5` for user `1` with its instrument (`LOMA`, id `45`), and answers empty for order `5` with user `2`, for order `999` with user `1`, and for an order id above `Integer.MAX_VALUE`; the guarded transition of order `5` from `NEW` to `CANCELLED` changes 1 row and leaves size, price, instrument, side, type and datetime untouched; the same transition of order `2` (`FILLED`) or of order `5` once cancelled changes 0 rows; the cancellation's history row is `CANCELLED`, `USER_REQUESTED`, at the instant passed in (FR-004, FR-006, R-405)
- [X] T008 [P] [US1] Create src/test/java/com/cocos/trading/controller/CancellationWebTestBase.java extending src/test/java/com/cocos/trading/controller/CashTransferWebTestBase.java (so it inherits `submit`, `withdrawn`, `availableCashOf`, `settledCashOf`, `ordersOf`, `jdbc`): the path `/api/v1/users/{userId}/orders/{orderId}/cancel`, the seeded order constants (`PENDING_BUY = 5`, `OTHER_PENDING_BUY = 7`, `ALREADY_CANCELLED = 3`, `REJECTED_ORDER = 6`, `FILLED_ORDER = 2`, `CASH_IN_ORDER = 1`, `UNKNOWN_ORDER = 999`), helpers `cancel(userId, orderId)` and `cancel(userId, orderId, idempotencyKey)` returning the `ResultActions`, `cancelled(userId, orderId)` expecting `200`, `statusOf(orderId)` read with JDBC, and `historyOf(orderId)` returning the order's `order_status_history` rows (status, reason) in id order
- [X] T009 [P] [US1] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/OrderCancellationTest.java: quickstart scenario 1 — cancel order `5` as user `1` → `200`, `orderId` `5`, `userId` `1`, `ticker` `LOMA`, `type` `LIMIT`, `side` `BUY`, `size` `50`, `price` `710.00`, `status` `CANCELLED`, `rejectionReason` `null`; available cash `663000.00`, settled `753000.00`; `historyOf(5)` is exactly one row `CANCELLED` / `USER_REQUESTED`; order `5`'s other columns unchanged. Scenario 2 — cancel `7` as well → available equals settled, `753000.00` (US1 scenarios 1 – 3; FR-006, FR-008, FR-009; SC-001, SC-002; depends on T008)
- [X] T010 [P] [US1] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CancellationRefusalTest.java, quickstart scenarios 3 and 4: orders `3`, `6`, `2` and `1` as user `1` → `409` each, detail naming the order and its status, status and history unchanged; order `5` cancelled then cancelled again → `409`, available cash still `663000.00`, still one history row; user `2` cancels `5` → `404` with detail `Order 5 does not exist for user 2` and order `5` stays `NEW`; user `2` cancels `999` → `404`, `Order 999 does not exist for user 2` (the same shape); user `999` cancels `5` → `404`, `User 999 does not exist`; order `0`, order `-5`, user `0` and order `abc` → `400`; order `2147483648` → `404`; `…/orders/5/cancel/` → `404`. Every refusal leaves `ordersOf(1)`, the statuses and the history row count unchanged (US1 scenarios 5 – 9; FR-002 – FR-005; SC-003, SC-004, SC-008; depends on T008)
- [X] T011 [P] [US1] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CancelledSellReleasesSharesTest.java, quickstart scenario 5: user `1` submits `SELL LIMIT 40 PAMP @ 1000` → `201`, `NEW`; `SELL MARKET 40 PAMP` → `REJECTED`, `INSUFFICIENT_HOLDINGS`; cancel the `LIMIT` → `200`, `CANCELLED`; `SELL MARKET 40 PAMP` again → `FILLED` at `925.85`, settled cash up by `37034.00`; the `LIMIT`'s history is `NEW`, then `CANCELLED` / `USER_REQUESTED` (US1 scenarios 4, 10; FR-008; SC-007; depends on T008)

### Implementation for User Story 1

- [X] T012 [US1] Create the cancellation rule in src/main/java/com/cocos/trading/model/order/ (proposed: `CancellationRule.java` answering a sealed `CancellationDecision` — the transition to record, or not cancellable naming the order and its status): plain Java, decided by the status alone through `OrderStatus.isOpen()` rather than restating `== NEW`, the transition carrying `OrderStatus.CANCELLED`, `CancellationReason.USER_REQUESTED` and the instant passed in; which statuses are cancellable is stated only here (FR-005, FR-007, R-408; depends on T003; makes T006 pass)
- [X] T013 [P] [US1] Create src/main/java/com/cocos/trading/service/cancellation/CancellationRequest.java (the raw request the controller read: `long userId`, `long orderId`, nullable `String idempotencyKey`, unused until US3), src/main/java/com/cocos/trading/service/cancellation/InvalidCancellationException.java (`400`, message naming the id), src/main/java/com/cocos/trading/service/cancellation/UnknownOrderException.java (`404`, message `Order <orderId> does not exist for user <userId>`, the same for a missing and a foreign order) and src/main/java/com/cocos/trading/service/cancellation/OrderNotCancellableException.java (`409`, message naming the order and its status)
- [X] T014 [P] [US1] Create src/main/java/com/cocos/trading/service/cancellation/CancelledOrder.java implementing `AppendedOrder`: order id, `UserId`, `InstrumentRef`, `LedgerSide`, `OrderKind`, size, price, the instant the order was placed, status `CANCELLED` — built from the order id and the `NewOrder` the scoped read returned plus the new status (a static `of`, as `SubmittedOrder.of` does), never from a re-read after the update (data-model.md sections 2 and 3)
- [X] T015 [US1] Persistence in src/main/java/com/cocos/trading/persistence/: add to `mapper/OrderMapper.java` an order-id-to-column mapping (out of `INT` range → absent, as `UserMapper.toIdColumn` maps a user id) and the history row of a cancellation (`OrderStatusHistoryEntity.newTransition(orderId, "CANCELLED", "USER_REQUESTED", instant)`); add to `OrderRepository.java` the read of one order of one user (`join fetch` the instrument, `where o.id = :orderId and o.userId = :userId`, mapped through `toPlacedOrder`, absent when either does not match) and the guarded transition (`@Modifying` `update OrderEntity o set o.status = :to where o.id = :orderId and o.status = :from`, returning the rows changed); add to `OrderStatusHistoryRepository.java` the append of a cancellation's transition (R-405; depends on T004, T012; makes T007 pass)
- [X] T016 [US1] Create src/main/java/com/cocos/trading/service/cancellation/OrderCancellationService.java: `@Service`, `@Transactional(isolation = Isolation.READ_COMMITTED)`, one public `cancel(CancellationRequest)` returning `CancelledOrder` that reads as the summary of its steps — read the request (both ids `≥ 1`, else `InvalidCancellationException`, before any lookup; R-403), call `UserWrite.write(userId, Optional.empty(), work, replay)` whose work, under the lock, reads the user's order (absent → `UnknownOrderException`), asks the rule (not cancellable → `OrderNotCancellableException`), applies the guarded transition (0 rows → `OrderNotCancellableException`, the transaction rolls back) and appends the history row, and answers `CancelledOrder.of`; the instant comes from the injected `Clock` truncated to microseconds as the other services' `now()` does. The replay may throw `IllegalStateException` until US3 gives it a body (FR-001 – FR-006, FR-010; depends on T012 – T015)
- [X] T017 [US1] Add `OrderResponse.of(CancelledOrder)` to src/main/java/com/cocos/trading/controller/dto/OrderResponse.java (`rejectionReason` `null`, `submittedAt` the order's placement instant), and create src/main/java/com/cocos/trading/controller/OrderCancellationController.java: `POST /api/v1/users/{userId}/orders/{orderId}/cancel`, no body, builds the `CancellationRequest` from the path, calls `OrderCancellationService.cancel` once, answers `200` with `OrderResponse.of` — HTTP translation only, following src/main/java/com/cocos/trading/controller/CashTransferController.java (FR-009, R-402; depends on T014, T016)
- [X] T018 [US1] Map in src/main/java/com/cocos/trading/controller/ApiExceptionHandler.java `InvalidCancellationException` → `400`, `UnknownOrderException` → `404`, `OrderNotCancellableException` → `409`, with the same problem shape as the existing mappings; `UnknownUserException`, `MethodArgumentTypeMismatchException` and the `503` mappings are reused unchanged (R-411; depends on T013)
- [X] T019 [US1] Run `./mvnw verify` and make T006 – T011 pass with every response validated against docs/api/openapi.yaml; fix the contract (T001) only where it disagrees with the plan's rules, never the other way round. If `…/cancel/` does not answer `404`, record what the framework does and settle it per R-401 (a test, not an assumption)

### Refactor for User Story 1

- [X] T020 [US1] Refactor: with this story's tests green, review the code it added (the rule in model/order, service/cancellation, the persistence methods, the controller and the DTO mapping) against constitution IX (one thing per method, public method as the summary of its steps, steps below in call order, no performance-driven structure without a measurement) and clean it; the tests stay green and no behaviour changes

**Checkpoint**: an order can be cancelled end to end and its release is visible in the portfolio — the MVP

---

## Phase 4: User Story 2 - A cancellation and the user's other writes are serialised (Priority: P2)

**Goal**: prove that the serialisation US1 got from `UserWrite` holds — two cancellations of one order, a cancellation and a withdrawal, different users, the deadline — and that the guarded update keeps FR-011 true for a writer that does not take the user's lock.

**Independent Test**: for user `1`, fire two cancellations of order `5` at the same instant → exactly one `200`, one `409`, one `CANCELLED` history row.

### Tests for User Story 2 (REQUIRED - constitution V)

- [X] T021 [P] [US2] Two-thread Testcontainers test in src/test/java/com/cocos/trading/controller/ConcurrentCancellationTest.java using src/test/java/com/cocos/trading/controller/Simultaneously.java, as src/test/java/com/cocos/trading/controller/ConcurrentWithdrawalTest.java does: two cancellations of order `5` by user `1` at the same instant → exactly one `200` `CANCELLED`, one `409`; `historyOf(5)` holds exactly one `CANCELLED` row; available cash `663000.00` (US2 scenario 1; FR-011; SC-005; extends `CancellationWebTestBase`)
- [X] T022 [P] [US2] Two-thread Testcontainers test in src/test/java/com/cocos/trading/controller/ConcurrentCancellationAndWithdrawalTest.java: user `1` cancels order `5` and withdraws `663000` at the same instant → the cancellation is `200`, and either the withdrawal is `FILLED` and available ends `0.00`, or it is `REJECTED` `INSUFFICIENT_FUNDS` and available ends `663000.00`; available cash is never below zero (US2 scenario 2; FR-010)
- [X] T023 [P] [US2] Testcontainers test in src/test/java/com/cocos/trading/controller/CancellationUserIsolationTest.java, as src/test/java/com/cocos/trading/controller/CashTransferUserIsolationTest.java does: while user `2`'s `users` row is held `FOR UPDATE` from another connection (`lockedUser`), user `1`'s cancellation of order `5` completes without waiting for it to be released (US2 scenario 3)
- [X] T024 [P] [US2] Testcontainers test in src/test/java/com/cocos/trading/controller/CancellationLockDeadlineTest.java, as src/test/java/com/cocos/trading/controller/CashTransferLockDeadlineTest.java does: a cancellation of order `5` while user `1`'s row is held locked past the deadline answers `503`, order `5` stays `NEW` and no history row is written (US2 scenario 4; ADR-0031)
- [X] T025 [P] [US2] Two-thread Testcontainers test in src/test/java/com/cocos/trading/controller/CancellationOutsideWriterTest.java, a writer that does not take the user's lock: a JDBC connection opens a transaction, updates order `5` to `FILLED` and holds it uncommitted; the cancellation of order `5` (which reads `NEW` under `READ COMMITTED`) blocks on the guarded update; the connection commits; the cancellation answers `409`, order `5` is `FILLED` and has no `CANCELLED` history row (FR-011, R-405)

### Implementation for User Story 2

- [X] T026 [US2] Run `./mvnw verify`: T021 – T025 pass with no change to main code — the lock comes from `UserWrite` and the guarded update from T015/T016. If T025 fails, fix the 0-rows path in src/main/java/com/cocos/trading/service/cancellation/OrderCancellationService.java (it must throw `OrderNotCancellableException` and roll the history row back); every US1 test still passes unchanged

### Refactor for User Story 2

- [X] T027 [US2] Refactor: with this story's tests green, review the tests and any code it added against constitution IX (one thing per method, public method as the summary of its steps, steps below in call order, no performance-driven structure without a measurement) and clean them; the tests stay green and no behaviour changes

**Checkpoint**: a cancellation is serialised with every other write of its user and never releases a reservation twice

---

## Phase 5: User Story 3 - The same cancellation sent twice is answered once (Priority: P3)

**Goal**: an optional `Idempotency-Key` on the cancellation, through `UserWrite`'s claim, with the canonical form `operation=ORDER_CANCELLATION;userId=<id>;orderId=<id>`; and every replay — submission, transfer, cancellation — answering the outcome its first request recorded, read from the history, not `orders.status` (R-407, ADR-0043).

**Independent Test**: for user `1`, cancel order `5` twice with key `cancel-5` → both `200` naming order `5` as `CANCELLED`, one history row, available `663000.00`; cancel order `7` with `cancel-5` → `409`, order `7` stays `NEW`.

### Tests for User Story 3 (REQUIRED - constitution V)

- [X] T028 [P] [US3] Plain-Java test in src/test/java/com/cocos/trading/service/cancellation/CancellationFingerprintTest.java: the canonical form is exactly `operation=ORDER_CANCELLATION;userId=<id>;orderId=<id>`; a different user or order gives a different form and fingerprint; the form differs from any submission form (which begins `userId=`, src/main/java/com/cocos/trading/service/submission/RequestFingerprint.java) and from the transfer's (`operation=CASH_TRANSFER`) (R-410)
- [X] T029 [P] [US3] Testcontainers test in src/test/java/com/cocos/trading/persistence/FirstTransitionReadTest.java (extends `PersistenceTestBase`): for an order appended `NEW` and then given a `CANCELLED` / `USER_REQUESTED` row, the earliest-transition read answers `NEW` with no reason and the cancelled-transition read answers `CANCELLED` / `USER_REQUESTED`; for an order appended `REJECTED` / `INSUFFICIENT_FUNDS` the earliest-transition read answers that status and reason (R-407)
- [X] T030 [P] [US3] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/IdempotentCancellationTest.java, quickstart scenario 6: cancel `5` with `cancel-5` → `200`; the same again → `200`, the same body, one history row, available `663000.00`; cancel `7` with `cancel-5` → `409`, order `7` stays `NEW`; cancel `999` with `k-404` → `404`, then cancel `7` with `k-404` → `200` (a refused request leaves no key); user `2` cancels `999` with `cancel-5` → `404`, not `409`; a blank and a 256-character key → `400`, nothing written; no key → executed as in US1 (US3 scenarios 1, 2, 4, 6, 7; FR-012 – FR-015; SC-006; extends `CancellationWebTestBase`)
- [X] T031 [P] [US3] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/CancellationKeySharedWithOtherWritesTest.java: user `1` submits an order with `k-1`, then cancels order `5` with `k-1` → `409` and never that order; user `2` deposits with `t-1`, then cancels `999` with `t-1` → `409`; user `1` cancels order `5` with `c-1`, then submits an order with `c-1` → `409` (US3 scenario 3; FR-013)
- [X] T032 [P] [US3] Two-thread Testcontainers test in src/test/java/com/cocos/trading/controller/ConcurrentIdempotentCancellationTest.java, as src/test/java/com/cocos/trading/controller/ConcurrentIdempotentCashTransferTest.java does: two cancellations of order `5` with the same key at the same instant → both `200` naming order `5` as `CANCELLED`, one history row (US3 scenario 5)
- [X] T033 [P] [US3] MockMvc + Testcontainers test in src/test/java/com/cocos/trading/controller/ReplayAfterCancellationTest.java, quickstart scenario 7: user `1` submits `BUY LIMIT 10 PAMP @ 900` with `buy-1` → `201`, `NEW`; cancel it with `buy-1` → `409`; cancel it with no key → `200`; repeat the submission with `buy-1` exactly → `201`, the same `orderId`, status **`NEW`**, `rejectionReason` `null` — not `CANCELLED` and not `500`. A keyed `REJECTED` submission and a keyed `REJECTED` withdrawal still replay `REJECTED` with their reason (R-407; ADR-0043)

### Implementation for User Story 3

- [X] T034 [P] [US3] Create src/main/java/com/cocos/trading/service/cancellation/CancellationFingerprint.java: the canonical form (package-visible `canonicalFormOf`) hashed through the shared src/main/java/com/cocos/trading/service/userwrite/Fingerprint.java, as src/main/java/com/cocos/trading/service/transfer/TransferFingerprint.java does (R-410; makes T028 pass)
- [X] T035 [P] [US3] Add to src/main/java/com/cocos/trading/persistence/OrderStatusHistoryRepository.java a read of an order's earliest transition (lowest id: status and reason, the reason mapped by the row's status through T004's mapping) and of its `CANCELLED` transition (R-407; depends on T004; makes T029 pass)
- [X] T036 [US3] In src/main/java/com/cocos/trading/service/cancellation/OrderCancellationService.java pass `KeyedRequest.of(request.idempotencyKey(), () -> CancellationFingerprint.of(request))` to `UserWrite.write`, and give the replay its body: read the order the key names with `OrderRepository.findPlacedOrder` and its `CANCELLED` transition (T035), and answer it as a `CancelledOrder`; an order named by a key with no `CANCELLED` row is an `IllegalStateException`, not a response. An invalid key stays a `400` through the existing `InvalidIdempotencyKeyException` mapping (FR-012 – FR-015; depends on T034, T035)
- [X] T037 [US3] Change the replays of src/main/java/com/cocos/trading/service/submission/OrderSubmissionService.java (`recordedOrder`) and src/main/java/com/cocos/trading/service/transfer/CashTransferService.java (`recordedTransfer`) to report the order with the status and reason of its **earliest** history row (T035) instead of `orders.status` + `reasonOf`; no other behaviour of either endpoint changes, and their existing tests pass unedited (R-407; depends on T035; makes T033 pass)
- [X] T038 [US3] Read the optional `Idempotency-Key` header in src/main/java/com/cocos/trading/controller/OrderCancellationController.java into `CancellationRequest.idempotencyKey` (depends on T036)
- [X] T039 [P] [US3] In docs/api/openapi.yaml, update the `Idempotency-Key` descriptions of order submission and cash transfer: a replay answers the **first** outcome even if the order changed status since, and a cancellation shares the per-user key namespace (data-model.md section 6)
- [X] T040 [US3] Run `./mvnw verify`: T028 – T033 pass; US1 and US2 tests pass unchanged; src/test/java/com/cocos/trading/controller/IdempotentSubmissionTest.java, src/test/java/com/cocos/trading/controller/IdempotentCashTransferTest.java and the fingerprint tests of submission and transfer pass unedited

### Refactor for User Story 3

- [X] T041 [US3] Rename src/main/java/com/cocos/trading/service/userwrite/AppendedOrder.java to `WrittenOrder` (the order a write is about, to attach to its key; contract unchanged) and update its implementers `SubmittedOrder`, `RecordedTransfer`, `CancelledOrder` and `UserWrite`'s bound; no method, rule or ArchUnit test changes (R-404)
- [X] T042 [US3] Refactor: with this story's tests green, review the code it added against constitution IX (one thing per method, public method as the summary of its steps, steps below in call order, no performance-driven structure without a measurement) and clean it; decide whether the two "order as first recorded" reads of submission and transfer become one persistence method both call (plan, *Constitution Check* IX) — not a new shared service; the tests stay green and no behaviour changes

**Checkpoint**: a retried cancellation is answered once; every replay answers what its first request answered

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: what a reviewer reads, and the full-feature validation

- [X] T043 [P] In README.md, make the cancellation row of the endpoint table (line 157) cite ADR-0041, and add `409` for a non-cancellable order to the status codes it documents; state that it is an addition the statement implies (it defines `CANCELLED` and that only `NEW` can be cancelled), not a departure
- [X] T044 [P] Check that docs/api/openapi.yaml and docs/api/trading-api.http match what was built (field names, `CANCELLED` in `Order.status`, the `404` and `409` details, the `Idempotency-Key` descriptions), and that `git diff develop -- specs/005-order-cancellation/contracts` is empty
- [X] T045 Run quickstart.md scenarios 1 – 7 by hand against `docker compose up -d` + `./mvnw spring-boot:run`, resetting with `docker compose down -v` between scenarios, and confirm every figure; scenario 8 is the automated suite's
- [X] T046 Run `./mvnw verify` once more on the final tree (formatting check, ArchUnit with src/test/java/com/cocos/trading/ArchitectureTest.java unchanged) and confirm the JaCoCo report covers the new code above the Sonar gate's 80% on new code (ADR-0020); add a test where a branch of the cancellation rule, service/cancellation or the changed replays is uncovered

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies. T001 must land before any MockMvc test of this feature can pass (contract validation).
- **Foundational (Phase 2)**: depends on Setup; BLOCKS every story. T003 → T004 → T005.
- **US1 (Phase 3)**: depends on Foundational.
- **US2 (Phase 4)**: depends on US1 (it proves US1's serialisation and guarded update; it adds no main code unless T025 finds a gap).
- **US3 (Phase 5)**: depends on US1; independent of US2 in code, done after it in the spec's order. T037 (the replays) depends only on T035 and Foundational.
- **Polish (Phase 6)**: depends on US3.

### User Story Dependencies

```text
Setup ─► Foundational ─► US1 ─┬─► US2 ─┐
                              └─► US3 ─┴─► Polish
```

The stories are sequenced by the spec, and US2 and US3 build on US1's endpoint; each is still independently **testable**: its tests prove its behaviour without the later stories.

### Within Each User Story

- Tests exist and pass before the story is done; test-first is allowed, not required.
- Model before persistence, persistence before service, service before controller, controller before exception mapping.
- The refactor task runs last, with the story's tests green.

### Parallel Opportunities

- Phase 1: T002 alongside T001.
- Phase 2: T003 alongside Phase 1.
- US1: tests T006 – T011 in parallel (T009 – T011 after T008); T013 and T014 in parallel with T012.
- US2: T021 – T025 in parallel.
- US3: tests T028 – T033 in parallel; T034, T035 and T039 in parallel.
- Polish: T043, T044 in parallel.

---

## Parallel Example: User Story 1

```bash
# The plain-Java and persistence tests, and the web tests once CancellationWebTestBase (T008) exists:
Task: "Plain-Java test in src/test/java/com/cocos/trading/model/order/CancellationRuleTest.java"
Task: "Testcontainers test in src/test/java/com/cocos/trading/persistence/OrderCancellationPersistenceTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/OrderCancellationTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/CancellationRefusalTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/CancelledSellReleasesSharesTest.java"

# The use case's own types, alongside the model rule:
Task: "Create CancellationRequest and the three exceptions in src/main/java/com/cocos/trading/service/cancellation/"
Task: "Create src/main/java/com/cocos/trading/service/cancellation/CancelledOrder.java"
```

## Parallel Example: User Story 3

```bash
Task: "Plain-Java test in src/test/java/com/cocos/trading/service/cancellation/CancellationFingerprintTest.java"
Task: "Testcontainers test in src/test/java/com/cocos/trading/persistence/FirstTransitionReadTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/IdempotentCancellationTest.java"
Task: "MockMvc test in src/test/java/com/cocos/trading/controller/ReplayAfterCancellationTest.java"
Task: "Two-thread test in src/test/java/com/cocos/trading/controller/ConcurrentIdempotentCancellationTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1: the live contract.
2. Phase 2: the history's reason read by status.
3. Phase 3: US1.
4. **STOP and VALIDATE**: quickstart scenarios 1 – 5 by hand; `./mvnw verify`.

The MVP is not mergeable on its own: until US3's T037 lands, a keyed submission replayed after its order was cancelled answers `CANCELLED` instead of its first outcome, which the author ruled out (Q-401). Merge the feature whole.

### Incremental Delivery

1. Setup + Foundational → a `CANCELLED` history row can exist safely.
2. US1 → orders can be cancelled (MVP, demo).
3. US2 → the races are proven.
4. US3 → retries are safe; replays answer the first outcome.
5. Polish → README, contract check, quickstart, coverage.

Commit after each task or logical group, one commit per story at least.

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks.
- [Story] label maps a task to its user story for traceability.
- Every behaviour a story adds is covered by a passing test at the level where it lives.
- Avoid: building the cancelled order's response from a re-read after the guarded update (the bulk update leaves a loaded entity stale, data-model.md section 3); checking `type` or `side` in the cancellation rule; a lock or claim outside `service/userwrite`; editing a submission or transfer test to make T037 pass.
