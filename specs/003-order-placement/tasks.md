---

description: "Task list for Order Placement"
---

# Tasks: Order Placement

**Input**: Design documents from `/specs/003-order-placement/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/order-submission.openapi.yaml](contracts/order-submission.openapi.yaml), [quickstart.md](quickstart.md)

**Tests**: REQUIRED for every user story (constitution V): every behaviour a story adds is covered at the level where it lives — plain Java for the rules, Testcontainers for the writes and the races, Testcontainers + MockMvc for HTTP. Writing them before the implementation is allowed, not required; a story is not done without them.

**Organization**: tasks are grouped by user story so each story can be implemented, tested and demonstrated on its own.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: can run in parallel (different files, no dependencies)
- **[Story]**: which user story the task belongs to (US1 … US6)
- Every task names its exact file path

## Path Conventions

Single-module Maven web service (plan.md, *Project Structure*): sources under `src/main/java/com/cocos/trading/`, tests under `src/test/java/com/cocos/trading/`, migrations under `src/main/resources/db/migration/`, docs under `docs/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: the decisions and the live contract that this feature's every HTTP test validates against (constitution VII, ADR-0024)

- [X] T001 Write ADR-0033 in docs/adr/0033-the-submission-endpoint-shape.md (under the user, `201` for every recorded outcome, `400` for a request that writes nothing; alternatives from R-201, R-202, R-212) and add its row to docs/adr/README.md
- [X] T002 Write ADR-0034 in docs/adr/0034-an-unpriced-instrument-refuses-the-submission.md (R-203; the one departure from the challenge statement), add its row to docs/adr/README.md, and state the departure in README.md
- [X] T003 Add the `submitOrder` operation and its `OrderSubmission` and `Order` schemas to docs/api/openapi.yaml, taken from specs/003-order-placement/contracts/order-submission.openapi.yaml, so the MockMvc contract validation has something to validate against (ADR-0024)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the schema, the shared model moves and the persistence plumbing that no single story owns

**CRITICAL**: no user story work can begin until this phase is complete

- [X] T004 Create src/main/resources/db/migration/V4__order_status_history.sql with `id`, `order_id` (FK to `orders(id)`), `status`, `reason`, `changed_at` and an index on `order_id`, per data-model.md section *Change to the schema* (R-208); `V1`, `V2` and `V3` stay untouched
- [X] T005 Testcontainers test in src/test/java/com/cocos/trading/persistence/OrderStatusHistorySchemaTest.java that `V4` applies on top of the given schema and that the foreign key, the `NOT NULL`s and the index exist
- [X] T006 [P] Move `Closes` from src/main/java/com/cocos/trading/model/portfolio/Closes.java to src/main/java/com/cocos/trading/model/instrument/Closes.java and update its callers (`MarketDataMapper`, `Portfolio`, and the tests under src/test/java/com/cocos/trading/model/portfolio/) — a market fact, not a portfolio concept (constitution VIII)
- [X] T007 [P] Add the "does this side move cash rather than an instrument" answer to src/main/java/com/cocos/trading/model/order/OrderSide.java so `CASH_IN` and `CASH_OUT` can be refused by name (FR-002)
- [X] T008 [P] Plain-Java test for that answer in src/test/java/com/cocos/trading/model/order/OrderSideCashMovementTest.java
- [X] T009 Create src/main/java/com/cocos/trading/persistence/mapper/InstrumentMapper.java with the one `toInstrumentRef(entity)` mapping and make src/main/java/com/cocos/trading/persistence/mapper/InstrumentSearchMapper.java and src/main/java/com/cocos/trading/persistence/mapper/OrderMapper.java call it instead of keeping their own copies (R-214)
- [X] T010 [P] Add `lockById` to src/main/java/com/cocos/trading/persistence/UserRepository.java as `@Lock(PESSIMISTIC_WRITE)` — existence and the serialisation lock in one statement (R-209), returning absent for an unknown user
- [X] T011 [P] Make src/main/java/com/cocos/trading/persistence/entity/OrderEntity.java insertable with an `IDENTITY`-generated id and a way to build a new row, leaving its read mapping untouched (R-215)
- [X] T012 [P] Create src/main/java/com/cocos/trading/persistence/entity/OrderStatusHistoryEntity.java and src/main/java/com/cocos/trading/persistence/OrderStatusHistoryRepository.java
- [X] T013 Testcontainers test in src/test/java/com/cocos/trading/persistence/UserLockTest.java that `lockById` returns the row for a known user and nothing for an unknown one (FR-007)
- [X] T014 Extend src/test/java/com/cocos/trading/ArchitectureTest.java for the new packages: `service/submission` depends on no other use case, `model/submission` holds no Spring, Jakarta, JPA, web or clock type, entities do not leave `persistence` (constitution VIII)

**Checkpoint**: the ledger can be written to and the user's row can be locked; story work can begin

---

## Phase 3: User Story 1 - Buy or sell an exact number of shares at market (Priority: P1) 🎯 MVP

**Goal**: `POST /api/v1/users/{userId}/orders` accepts a `MARKET` order for an exact share count, prices it at the instrument's latest `close`, persists it `FILLED` when the user can pay or holds the shares and `REJECTED` with a reason when they cannot, writes the status-history row in the same transaction, and answers `201`.

**Independent Test**: for user `1`, buy 100 `PAMP` → `FILLED` at `925.85`; buy 1,000 → `REJECTED` insufficient funds; sell 40 → `FILLED`, sell 41 → `REJECTED` insufficient holdings. The portfolio before and after is the only other thing needed (quickstart scenarios 1-4).

### Tests for User Story 1 (REQUIRED - constitution V)

> These may be written before or after the implementation, but the story is not done until they exist and pass

- [X] T015 [P] [US1] Plain-Java test of the governing price of a `MARKET` submission in src/test/java/com/cocos/trading/model/submission/SubmissionMarketPriceTest.java: the instrument's latest `close` governs, and the price is absent when the instrument has no close (FR-009, R-205)
- [X] T016 [P] [US1] Plain-Java test of the `BUY` availability comparison in src/test/java/com/cocos/trading/model/submission/ExactSizeBuyDecisionTest.java: available cash against `size x price`, the 677-versus-678 `PAMP` boundary to the cent, `INSUFFICIENT_FUNDS`, and a user with no orders at all (FR-012, FR-014, SC-002)
- [X] T017 [P] [US1] Plain-Java test of the `SELL` availability comparison in src/test/java/com/cocos/trading/model/submission/ExactSizeSellDecisionTest.java: 40 against 41 `PAMP`, a holding at or below zero rejecting every `SELL` (`BMA` at -10), `INSUFFICIENT_HOLDINGS` (FR-015, SC-003)
- [X] T018 [P] [US1] Plain-Java test of `TradeQuantity`'s exact-size construction in src/test/java/com/cocos/trading/model/submission/TradeQuantityExactSizeTest.java: whole and greater than zero, refused above `2,147,483,647`, and what it needs at a price (FR-004, R-207)
- [X] T019 [P] [US1] Plain-Java test of `Submission`'s construction refusals in src/test/java/com/cocos/trading/model/submission/SubmissionValidityTest.java: a `CASH_IN`/`CASH_OUT` side, a `MONEDA` instrument, and a `MARKET` request carrying a price — refused, never ignored (FR-002, FR-005, FR-008)
- [X] T020 [P] [US1] Testcontainers test in src/test/java/com/cocos/trading/persistence/OrderAppendTest.java that one submission appends exactly one `orders` row and exactly one `order_status_history` row in the same transaction, and that a rollback leaves neither (FR-021, FR-022)
- [X] T021 [P] [US1] MockMvc test of quickstart scenarios 1-4 in src/test/java/com/cocos/trading/controller/MarketOrderExactSizeTest.java: the `FILLED` buy at `925.85`, the funds boundary, the sells, `BMA`'s negative position and a user with no orders — each response `201` and validated against docs/api/openapi.yaml (SC-001 to SC-003)
- [X] T022 [P] [US1] MockMvc test of the refusals in src/test/java/com/cocos/trading/controller/SubmissionRefusalTest.java: an unknown user → `404`; an unknown instrument, a `MONEDA` instrument, a `MARKET` order for `PGR` (quickstart scenario 8, first half), a `MARKET` order carrying a price, a size of `0`, a negative size, a fractional size and a malformed body → `400` with nothing written (FR-007, FR-010, R-212, SC-009)

### Implementation for User Story 1

- [X] T023 [P] [US1] Create src/main/java/com/cocos/trading/model/submission/RejectionReason.java with exactly `INSUFFICIENT_FUNDS`, `INSUFFICIENT_HOLDINGS` and `SIZE_BELOW_ONE_SHARE` (FR-019, R-203)
- [X] T024 [P] [US1] Create src/main/java/com/cocos/trading/model/submission/OrderDecision.java carrying the status, the size, the price and the reason when rejected — returned, never written (R-206)
- [X] T025 [US1] Create src/main/java/com/cocos/trading/model/submission/TradeQuantity.java with the exact-size case: bounds at construction, "what do you need at this price" and "what size does this make" (FR-004, FR-014, FR-015)
- [X] T026 [US1] Create src/main/java/com/cocos/trading/model/submission/Submission.java: valid by construction, answering which price governs given the latest closes and what the outcome is given an `AccountSnapshot` and that price, using `availableCash()` and `availableQuantityOf()` unchanged (FR-002 to FR-005, FR-008, FR-009, FR-012 to FR-015, FR-020, ADR-0015)
- [X] T027 [P] [US1] Create src/main/java/com/cocos/trading/service/submission/SubmissionRequest.java — the raw request the controller read, validated by nobody yet (constitution VIII)
- [X] T028 [P] [US1] Create src/main/java/com/cocos/trading/service/submission/SubmittedOrder.java — the order's id, user, instrument, side, type, status, size, price, reason and instant (FR-023)
- [X] T029 [P] [US1] Create src/main/java/com/cocos/trading/service/submission/InvalidSubmissionException.java naming the rule broken and the allowed values (R-212)
- [X] T030 [P] [US1] Create src/main/java/com/cocos/trading/service/submission/UnknownSubmissionUserException.java — its own type, because a use case may not depend on another's (constitution VIII)
- [X] T031 [US1] Extend src/main/java/com/cocos/trading/persistence/mapper/OrderMapper.java with decision → new `orders` row plus its `order_status_history` row, and saved row → `SubmittedOrder`
- [X] T032 [US1] Create src/main/java/com/cocos/trading/service/submission/OrderSubmissionService.java: one transaction — lock the user, fold the ledger, read the latest close for a `MARKET` order, let the model decide, append the order and its history row with the server's instant, answer (R-206, R-213)
- [X] T033 [P] [US1] Create src/main/java/com/cocos/trading/controller/dto/OrderSubmissionRequest.java with `instrumentId`, `type`, `side`, `size`, `amount`, `price` — `type` and `side` as text so the use case names what was allowed; no status and no cancel flag (FR-006)
- [X] T034 [P] [US1] Create src/main/java/com/cocos/trading/controller/dto/OrderResponse.java, an absent reason serialising as JSON `null` (FR-023)
- [X] T035 [US1] Create src/main/java/com/cocos/trading/controller/OrderSubmissionController.java for `POST /api/v1/users/{userId}/orders`: path variable and body → one `SubmissionRequest` → one service call → one DTO, `201` (R-201, R-202)
- [X] T036 [US1] Extend src/main/java/com/cocos/trading/controller/ApiExceptionHandler.java: `InvalidSubmissionException` and a malformed body → `400`, `UnknownSubmissionUserException` → `404`, the `503` mapping unchanged (R-212, ADR-0031)

### Refactor for User Story 1

- [X] T037 [US1] Refactor: with this story's tests green, review the code it added under src/main/java/com/cocos/trading/model/submission/, src/main/java/com/cocos/trading/service/submission/ and src/main/java/com/cocos/trading/controller/ against constitution IX (one thing per method, public method as the summary of its steps, steps below in call order, no performance-driven structure without a measurement) and clean it; the tests stay green and no behaviour changes

**Checkpoint**: a `MARKET` order for an exact size is fully functional and demonstrable on its own — the MVP

---

## Phase 4: User Story 2 - Spend a total amount at market, not a share count (Priority: P2)

**Goal**: the same `MARKET` path accepts a total amount in pesos instead of a size, derives `floor(amount / price)` whole shares, rejects a derived size of zero, and never trades a fraction of a share.

**Independent Test**: for user `1`, buy `100,000.00` of `PAMP` → 108 shares for `99,991.80`; buy `700,000.00` → `REJECTED`; sell `30,000.00` → 32 shares; sell `40,000.00` → `REJECTED` (quickstart scenarios 5-7).

### Tests for User Story 2 (REQUIRED - constitution V)

- [X] T038 [P] [US2] Plain-Java test of the new `DecimalPolicy` question in src/test/java/com/cocos/trading/model/DecimalPolicyWholeSharesTest.java: scale `0`, `RoundingMode.FLOOR`, and `100,000.00 / 925.85` giving 108 (FR-017, FR-024, R-207)
- [X] T039 [P] [US2] Plain-Java test of `TradeQuantity`'s amount case in src/test/java/com/cocos/trading/model/submission/TradeQuantityAmountTest.java: an amount greater than zero and at most `99,999,999.99`, both a size and an amount refused, neither refused, and a derived size checked against the same size ceiling (FR-003, FR-004, R-207)
- [X] T040 [P] [US2] Plain-Java test of the amount-path `BUY` decision in src/test/java/com/cocos/trading/model/submission/AmountBuyDecisionTest.java: available cash against the amount, the whole-cash case deriving 677 shares, the `500.00` case deriving `0` → `SIZE_BELOW_ONE_SHARE`, and the unspent `8.20` neither spent nor an error (FR-016, FR-017)
- [X] T041 [P] [US2] Plain-Java test of the amount-path `SELL` decision in src/test/java/com/cocos/trading/model/submission/AmountSellDecisionTest.java: `available holding x price` against the amount, 32 shares from `30,000.00`, the whole position from `37,034.00`, `40,000.00` rejected, `900.00` deriving `0` (FR-016, FR-017)
- [X] T042 [P] [US2] MockMvc test of quickstart scenario 5 in src/test/java/com/cocos/trading/controller/MarketOrderAmountTest.java: the 108-share buy for `99,991.80` and the 32-share sell for `29,627.20`, with the derived size reported on the order (SC-004, FR-023)
- [X] T043 [P] [US2] MockMvc test of quickstart scenarios 6 and 7 in src/test/java/com/cocos/trading/controller/SubmissionAmountRefusalTest.java: the amount path's `REJECTED` outcomes at `201`, against the request-shape `400`s — both a size and an amount, neither, an amount of `0` and a negative amount (FR-003, FR-019, SC-008)

### Implementation for User Story 2

- [X] T044 [US2] Add the named question "how many whole shares fit in this amount at this price" (scale `0`, `FLOOR`) to src/main/java/com/cocos/trading/model/DecimalPolicy.java — the floor lives in one place, not at a call site (FR-017, FR-024, R-207)
- [X] T045 [US2] Extend src/main/java/com/cocos/trading/model/submission/TradeQuantity.java with the total-amount case, mutually exclusive with the size at construction, answering the same two questions (FR-003, FR-016, FR-017)
- [X] T046 [US2] Extend src/main/java/com/cocos/trading/model/submission/Submission.java so the one availability comparison serves the amount path too — reject when available is less than the amount, otherwise `floor(amount / price)`, reject a derived size of `0` (FR-016 to FR-018)
- [X] T047 [US2] Wire the amount through src/main/java/com/cocos/trading/service/submission/SubmissionRequest.java and src/main/java/com/cocos/trading/service/submission/OrderSubmissionService.java, mapping the request-shape refusals to `InvalidSubmissionException` with the field named (FR-003, FR-004)

### Refactor for User Story 2

- [X] T048 [US2] Refactor: with this story's tests green, review src/main/java/com/cocos/trading/model/submission/Submission.java, TradeQuantity.java and src/main/java/com/cocos/trading/model/DecimalPolicy.java against constitution IX — in particular that the availability comparison is written **once** for both quantity kinds — and clean it; the tests stay green and no behaviour changes

**Checkpoint**: both `MARKET` paths work independently

---

## Phase 5: User Story 3 - Place a limit order for an exact number of shares (Priority: P3)

**Goal**: a `LIMIT` order for an exact size is validated at the price in the request, never against the market, and persisted `NEW`, reserving pesos for a purchase and shares for a sale so a second order cannot commit the same money twice.

**Independent Test**: for user `1`, a `LIMIT BUY` of 100 `PAMP` at `900.00` → `NEW`, available cash `537,500.00`, settled cash unchanged; a `LIMIT SELL` of 40 `PAMP` at `1,000.00` → available `PAMP` `0` with cash untouched; then a `MARKET SELL` of 1 `PAMP` → `REJECTED` (quickstart scenario 9).

### Tests for User Story 3 (REQUIRED - constitution V)

- [X] T049 [P] [US3] Plain-Java test of the `LIMIT` governing price in src/test/java/com/cocos/trading/model/submission/SubmissionLimitPriceTest.java: the requested price governs and the closes are never consulted, so an instrument with no market data is priced normally (FR-011, R-205)
- [X] T050 [P] [US3] Plain-Java test of the `LIMIT` exact-size decision in src/test/java/com/cocos/trading/model/submission/LimitExactSizeDecisionTest.java: `NEW` on acceptance, 700 `PAMP` at `900.00` rejected at the requested price and not at `925.85`, 100 at `2,000.00` accepted because a price nobody will trade at is not a rejection reason, and a `SELL` of 41 of a 40-share position rejected (FR-011, FR-014, FR-015, FR-020)
- [X] T051 [P] [US3] Plain-Java test in src/test/java/com/cocos/trading/model/submission/SubmissionPricePresenceTest.java that a `LIMIT` request with no price, or with a price at or below zero, is refused, and that the `MARKET`-with-a-price refusal still holds (FR-005)
- [X] T052 [P] [US3] MockMvc test of quickstart scenario 9 in src/test/java/com/cocos/trading/controller/LimitOrderExactSizeTest.java: the `NEW` order at `900.00`, available cash falling to `537,500.00` with settled cash at `753,000.00`, the whole-position `SELL` reservation, and the following `MARKET SELL` of 1 share rejected (SC-006, SC-007)
- [X] T053 [P] [US3] MockMvc test of quickstart scenario 8's second half in src/test/java/com/cocos/trading/controller/LimitOrderUnpricedInstrumentTest.java: a `LIMIT` order for `PGR` and `IRCP` is accepted while a `MARKET` order for them is refused (SC-009, FR-011)

### Implementation for User Story 3

- [X] T054 [US3] Extend src/main/java/com/cocos/trading/model/submission/Submission.java: the price answer returns the requested price for a `LIMIT` order and the decision's status is `NEW`, with no fill (FR-011, FR-020, ADR-0007)
- [X] T055 [US3] In src/main/java/com/cocos/trading/service/submission/OrderSubmissionService.java, read the latest close only for a `MARKET` submission, so a `LIMIT` order never touches `marketdata` and the model is never asked with an absent price (FR-011, constitution IX — no `Optional` parameter)

### Refactor for User Story 3

- [X] T056 [US3] Refactor: with this story's tests green, review src/main/java/com/cocos/trading/model/submission/Submission.java and src/main/java/com/cocos/trading/service/submission/OrderSubmissionService.java against constitution IX — the governing price answered in exactly one place — and clean it; the tests stay green and no behaviour changes

**Checkpoint**: both order types work for an exact size, and reservations are observable

---

## Phase 6: User Story 4 - Place a limit order for a total amount (Priority: P4)

**Goal**: the last of the four submission paths — a total amount at the caller's own price, with that price substituted on **both** sides of the comparison, so the user is bounded by their own price rather than by the market's.

**Independent Test**: for user `1`, a `LIMIT BUY` of `100,000.00` at `900.00` → 111 shares `NEW` reserving `99,900.00`; a `LIMIT SELL` of `30,000.00` at `1,000.00` → 30 shares, not the 32 the same amount yields at the close (quickstart scenario 10).

### Tests for User Story 4 (REQUIRED - constitution V)

- [X] T057 [P] [US4] Plain-Java test of the price substitution in src/test/java/com/cocos/trading/model/submission/LimitAmountSubstitutionTest.java: `100,000.00` at `900.00` derives 111 shares and not 108, and `30,000.00` of a 40-share position at `1,000.00` derives 30 and not 32 — the proof that the price substitutes on both sides (FR-018, SC-005)
- [X] T058 [P] [US4] Plain-Java test of the `LIMIT` amount rejections in src/test/java/com/cocos/trading/model/submission/LimitAmountDecisionTest.java: `700,000.00` rejected on the amount alone, `800.00` at `900.00` deriving `0`, `50,000.00` at `1,000.00` rejected because the position is worth `40,000.00`, and `30,000.00` at `500.00` rejected where `1,000.00` would have been accepted (FR-016 to FR-018)
- [X] T059 [P] [US4] MockMvc test of quickstart scenario 10 in src/test/java/com/cocos/trading/controller/LimitOrderAmountTest.java: the 111-share `NEW` buy reserving `99,900.00`, the 30-share reservation, and the same request refused at `500.00` (SC-005)

### Implementation for User Story 4

- [X] T060 [US4] Complete the `LIMIT`-plus-amount combination in src/main/java/com/cocos/trading/model/submission/Submission.java and src/main/java/com/cocos/trading/model/submission/TradeQuantity.java: no new rule, only the governing price of US3 feeding the derivation of US2 on both sides of the comparison (FR-018)

### Refactor for User Story 4

- [X] T061 [US4] Refactor: with this story's tests green, confirm in src/main/java/com/cocos/trading/model/submission/Submission.java and TradeQuantity.java, against constitution IX, that the four submission paths share one comparison and one derivation with no branch per path, and clean what does not; the tests stay green and no behaviour changes

**Checkpoint**: all four business paths are complete and independently testable

---

## Phase 7: User Story 5 - Two simultaneous orders cannot spend the same pesos (Priority: P5)

**Goal**: submissions for one user are serialised by a pessimistic lock on their `users` row, taken as the transaction's first statement, so two concurrent requests cannot both commit against the same availability — and no user ever waits on another's order.

**Independent Test**: fire two `MARKET BUY` orders of 400 `PAMP` for user `1` at the same instant (`740,680.00` together against `627,500.00` available) and check exactly one is `FILLED` and one `REJECTED`, in every run; then fire one order for each of two different users and check neither waits on the other (quickstart scenario 11).

### Tests for User Story 5 (REQUIRED - constitution V)

- [ ] T062 [P] [US5] Testcontainers two-thread test of quickstart scenario 11 in src/test/java/com/cocos/trading/controller/ConcurrentBuySubmissionTest.java: two `MARKET BUY` orders of 400 `PAMP` for user `1` end with exactly one `FILLED` and one `REJECTED` for insufficient funds, and the final available cash is the one the single fill implies (SC-010, FR-026)
- [ ] T063 [P] [US5] Testcontainers two-thread test in src/test/java/com/cocos/trading/controller/ConcurrentSellSubmissionTest.java: two `MARKET SELL` orders of 40 `PAMP` end with exactly one `FILLED`, the position never driven negative by these writes; two submissions that both fit are both `FILLED`; and concurrent submissions by two different users both complete with neither rejected on account of the other (FR-026, FR-027)
- [ ] T064 [P] [US5] Testcontainers test in src/test/java/com/cocos/trading/persistence/UserLockContentionTest.java that a second transaction's `lockById` blocks until the first commits and then reads what it committed, which is the `READ COMMITTED` behaviour the design rests on (R-209, R-211)
- [ ] T065 [P] [US5] Test in src/test/java/com/cocos/trading/controller/SubmissionLockDeadlineTest.java that a submission which waits past the transaction deadline answers `503` with nothing written, through the unchanged handler (R-209 *Consequence*, ADR-0031)

### Implementation for User Story 5

- [ ] T066 [US5] Write ADR-0035 in docs/adr/0035-submissions-are-serialised-per-user.md (the pessimistic lock on the `users` row at `READ COMMITTED`, with the `SERIALIZABLE`, advisory-lock, `version`-column, lock-the-orders and table-lock alternatives from R-209 and R-211) and add its row to docs/adr/README.md
- [ ] T067 [US5] Annotate the submission method in src/main/java/com/cocos/trading/service/submission/OrderSubmissionService.java `@Transactional(isolation = READ_COMMITTED)`, not read-only, and make `UserRepository.lockById` its **first** statement so the fold, the decision and both inserts run under that lock (FR-026, FR-027, R-209, R-211)

### Refactor for User Story 5

- [ ] T068 [US5] Refactor: with this story's tests green, review src/main/java/com/cocos/trading/service/submission/OrderSubmissionService.java against constitution IX — the public method reading as the summary of its steps in call order, the step order itself being the rule data-model.md section 4 states — and clean it; the tests stay green and no behaviour changes

**Checkpoint**: every validation in US1-US4 is now enforced rather than advisory

---

## Phase 8: User Story 6 - The same order sent twice is executed once (Priority: P6)

**Goal**: an optional `Idempotency-Key` header makes a retry of the same request return the first outcome instead of trading again, through a unique `(userid, idempotency_key)` and `INSERT … ON CONFLICT DO NOTHING`; the same key with a different body is a `409`.

**Independent Test**: send the same `MARKET BUY` of 100 `PAMP` for user `1` twice with one key and check exactly one order exists and both responses name it; then send a different order with that key and check it is refused as a conflict (quickstart scenario 12).

### Tests for User Story 6 (REQUIRED - constitution V)

- [ ] T069 [P] [US6] Testcontainers test in src/test/java/com/cocos/trading/persistence/OrderIdempotencySchemaTest.java that `V5` applies on top of the given schema and that `UNIQUE (userid, idempotency_key)`, both foreign keys and the nullable `order_id` are in place
- [ ] T070 [P] [US6] Plain-Java test of the fingerprint's canonical form in src/test/java/com/cocos/trading/service/submission/RequestFingerprintTest.java: the same request hashes identically regardless of JSON key order or whitespace, money normalised to scale 2 (`900` and `900.00` agree), 64 hex characters, and the key itself is not part of the hash (R-210)
- [ ] T071 [P] [US6] Testcontainers test in src/test/java/com/cocos/trading/persistence/OrderIdempotencyClaimTest.java: `claim` writes one row and reports `1` for the first caller and `0` for the second, `findByUser` reads the committed row back with its `order_id` set, and the same key for two users is two independent rows (FR-028, FR-030)
- [ ] T072 [P] [US6] MockMvc test of quickstart scenario 12 in src/test/java/com/cocos/trading/controller/IdempotentSubmissionTest.java: the identical retry returns the same order id and status with exactly one order in the ledger; the same key with a different body → `409` and nothing written; the same key for user `2` is processed as a new request; a `REJECTED` outcome is replayed and not re-decided; and a submission with no key is processed normally (SC-011, FR-028 to FR-031)
- [ ] T073 [P] [US6] Testcontainers two-thread test in src/test/java/com/cocos/trading/controller/ConcurrentIdempotentSubmissionTest.java that two requests with the same key submitted at the same instant yield exactly one order, the loser blocking on the unique index and then returning the committed outcome (FR-028, R-210, R-211)

### Implementation for User Story 6

- [ ] T074 [US6] Write ADR-0036 in docs/adr/0036-idempotent-submission-with-a-caller-key.md (the header, the fingerprint and `ON CONFLICT DO NOTHING`, with the body-field, stored-body, always-`409`, fingerprint-only and expiring-key alternatives from R-210, and the kept-forever retention stated) and add its row to docs/adr/README.md
- [ ] T075 [US6] Create src/main/resources/db/migration/V5__order_idempotency.sql with `id`, `userid`, `idempotency_key`, `request_fingerprint`, `order_id`, `created_at` and `UNIQUE (userid, idempotency_key)`, per data-model.md section *Change to the schema*
- [ ] T076 [P] [US6] Create src/main/java/com/cocos/trading/persistence/entity/OrderIdempotencyEntity.java
- [ ] T077 [US6] Create src/main/java/com/cocos/trading/persistence/OrderIdempotencyRepository.java with `claim` as a native `INSERT … ON CONFLICT (userid, idempotency_key) DO NOTHING` returning the rows written, `findByUser(userId, key)`, and attaching the order id to the claimed row (R-210)
- [ ] T078 [P] [US6] Create src/main/java/com/cocos/trading/service/submission/RequestFingerprint.java: the canonical form of a request and its SHA-256 as 64 hex characters, from `java.security.MessageDigest` with no new dependency (R-210)
- [ ] T079 [P] [US6] Create src/main/java/com/cocos/trading/service/submission/IdempotencyConflictException.java (FR-029)
- [ ] T080 [US6] Extend src/main/java/com/cocos/trading/service/submission/OrderSubmissionService.java: claim the key as the transaction's first work when one is present, replay the stored order when the claim wrote nothing and the fingerprints agree, raise the conflict when they differ, and attach the new order's id to the claimed row in the same transaction (FR-028 to FR-031, R-210)
- [ ] T081 [US6] Read the optional `Idempotency-Key` header in src/main/java/com/cocos/trading/controller/OrderSubmissionController.java and carry it on src/main/java/com/cocos/trading/service/submission/SubmissionRequest.java — absent means "process normally" (FR-031)
- [ ] T082 [US6] Map `IdempotencyConflictException` to `409` in src/main/java/com/cocos/trading/controller/ApiExceptionHandler.java and declare the header and the `409` answer on the operation in docs/api/openapi.yaml (FR-029, R-212, ADR-0024)

### Refactor for User Story 6

- [ ] T083 [US6] Refactor: with this story's tests green, review src/main/java/com/cocos/trading/service/submission/RequestFingerprint.java and OrderSubmissionService.java against constitution IX — the fingerprint having exactly one canonical form so a retry cannot hash differently from the first request, and the claim-before-the-work step order staying legible — and clean it; the tests stay green and no behaviour changes

**Checkpoint**: all six user stories are independently functional

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: the cross-story checks, the documentation and the gate

- [ ] T084 [P] Add the submission section to docs/api/trading-api.http: the four accepted paths, a refusal, a rejection and a keyed retry
- [ ] T085 [P] Testcontainers test of quickstart scenario 13 in src/test/java/com/cocos/trading/persistence/LedgerAuditAgreementTest.java: every order this feature wrote has exactly one history row with the same status and, when rejected, the same reason; no row has a `NULL` price; `size` is `0` only on a rejection that never derived one (FR-022, R-203)
- [ ] T086 [P] Test in src/test/java/com/cocos/trading/controller/SubmissionPortfolioAgreementTest.java that the available cash a submission validated against equals, to the cent, what the portfolio endpoint reports for the same user at the same moment, and that an order's effect is visible in the next portfolio read (FR-025, SC-012, SC-013)
- [ ] T087 [P] Update README.md: the endpoint in the API summary, the ADR-0034 departure already stated in T002, and that the challenge statement's functional test is the e2e suite
- [ ] T088 Run every quickstart.md scenario 1-13 against the running stack and reconcile each figure with the document, correcting whichever is wrong
- [ ] T089 Run `./mvnw verify` and clear the whole gate: the architecture rules of T014, the `spring-javaformat` check (ADR-0019), the contract validation of every response (ADR-0024), and the Sonar quality gate (ADR-0021, ADR-0022)
- [ ] T090 Review the refusal and rejection coverage against spec.md *Edge Cases* and confirm every path there has a test, not only the happy ones (SC-014, constitution V)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — T001 → T002 are sequential because both edit docs/adr/README.md
- **Foundational (Phase 2)**: depends on Setup; **blocks every user story**
- **User Stories (Phases 3-8)**: all depend on Foundational
- **Polish (Phase 9)**: depends on every story being complete

### User Story Dependencies

Unlike a greenfield feature, these six stories are **four layers on one endpoint plus two protections around it**, so they are sequential by construction rather than by choice — the spec's own ordering, and the author's (`docs/journal/07OrderPlacement.md`):

- **US1 (P1)**: after Foundational. Builds the endpoint, the service, the controller and the exact-size comparison. No dependency on another story
- **US2 (P2)**: depends on US1 — it adds the derivation to US1's comparison. Independently testable: the amount paths have their own tests and their own quickstart scenarios
- **US3 (P3)**: depends on US1 for the endpoint; independent of US2
- **US4 (P4)**: depends on US2's derivation and US3's governing price — the only story that needs both
- **US5 (P5)**: depends on US1 (something must be submitted to race). Deliberately after the business rules
- **US6 (P6)**: depends on US1; independent of US5, and built last

### Within Each User Story

- Tests MUST exist and pass before the story is done; test-first is allowed, not required
- Model before service, service before controller
- The story's refactor task runs last, with its tests green, before the story is called done
- The story is complete before the next priority starts

### Parallel Opportunities

- **Phase 2**: T006, T007, T008, T010, T011 and T012 touch different files and can run together; T009 and T013 follow
- **Every story's test tasks are all `[P]`** — different test files, no shared state
- **US1**: T023 and T024 together; then T027 to T030 together; then T033 and T034 together
- **US5 and US6 can be built in parallel** by two people once US1 is done: they share only `OrderSubmissionService`'s step order (T067 and T080), which is the one file to sequence between them
- **Phase 9**: T084 to T087 together; T088, T089 and T090 last

---

## Parallel Example: User Story 1

```bash
# All of User Story 1's tests, together:
Task: "Plain-Java test of the MARKET governing price in model/submission/SubmissionMarketPriceTest.java"
Task: "Plain-Java test of the BUY availability comparison in model/submission/ExactSizeBuyDecisionTest.java"
Task: "Plain-Java test of the SELL availability comparison in model/submission/ExactSizeSellDecisionTest.java"
Task: "Plain-Java test of TradeQuantity's exact-size construction in model/submission/TradeQuantityExactSizeTest.java"
Task: "Plain-Java test of Submission's construction refusals in model/submission/SubmissionValidityTest.java"
Task: "Testcontainers test of the order and its history row in persistence/OrderAppendTest.java"
Task: "MockMvc test of quickstart 1-4 in controller/MarketOrderExactSizeTest.java"
Task: "MockMvc test of the refusals in controller/SubmissionRefusalTest.java"

# The two value objects, together:
Task: "Create model/submission/RejectionReason.java"
Task: "Create model/submission/OrderDecision.java"

# The service's own types, together:
Task: "Create service/submission/SubmissionRequest.java"
Task: "Create service/submission/SubmittedOrder.java"
Task: "Create service/submission/InvalidSubmissionException.java"
Task: "Create service/submission/UnknownSubmissionUserException.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup — the two ADRs and the live contract
2. Phase 2: Foundational — `V4`, the `Closes` move, the shared instrument mapping, the lock, the writable entity
3. Phase 3: User Story 1
4. **STOP and VALIDATE**: quickstart scenarios 1-4 and 8's first half, by hand and in the suite
5. A `MARKET` order for an exact share count is already a usable trading API

### Incremental Delivery

1. Setup + Foundational → the ledger is writable
2. US1 → **MVP**: `MARKET` by size, filled or rejected, audited
3. US2 → `MARKET` by amount
4. US3 → `LIMIT` by size, with reservations observable
5. US4 → `LIMIT` by amount; all four paths done
6. US5 → the validations become enforced rather than advisory
7. US6 → a retry stops being a second order
8. Phase 9 → the gate

Each step adds value without breaking the ones before it, and each has its own quickstart scenarios to demonstrate it.

### Parallel Team Strategy

1. Everyone finishes Setup + Foundational
2. One developer takes US1 — nothing else can start before the endpoint exists
3. Once US1 is green: developer A takes US2 → US4, developer B takes US5, developer C takes US6. B and C coordinate on `OrderSubmissionService`'s step order (T067, T080); nothing else overlaps

---

## Notes

- `[P]` means different files and no dependency on unfinished work
- `[Story]` maps a task to a user story for traceability back to spec.md
- Every rejection path and every refusal path is a test, not only the happy ones (SC-014, constitution V)
- Figures in the tasks are the measured ones from research.md, recomputed on 2026-11-17 against the provided dataset; if a run disagrees, the figure is what gets re-measured, not the assertion that gets loosened
- No comment goes into the source; the reasoning lives in the ADRs and in these documents (constitution VII)
- Commit after each task or logical group; stop at any checkpoint to validate a story on its own
