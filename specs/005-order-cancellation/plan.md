# Implementation Plan: Order Cancellation

**Branch**: `feature/cancelOrder` (feature directory `005-order-cancellation`) | **Date**: 2026-11-17 |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-order-cancellation/spec.md`, scoped by
`docs/journal/09CancelOrder.md`

## Summary

One endpoint, `POST /api/v1/users/{userId}/orders/{orderId}/cancel`, with no body and an optional
`Idempotency-Key`. Under the user's lock it reads the order **scoped by user and id**, asks the model
whether its status allows cancelling — only `NEW` does — and, if so, sets `orders.status` to
`CANCELLED` with a **guarded update** (only while it is still `NEW`) and appends one history row with
the reason `USER_REQUESTED`, in one transaction. It answers **`200` with the `Order` schema**
submission already uses; a missing or foreign order is the same `404`, and an order that is not `NEW`
is a `409` ([R-402](research.md#r-402-a-cancellation-answers-200-with-the-order-author),
[R-411](research.md#r-411-the-error-map-200-400-404-409-503)). The release of what the order reserved
needs no code: a `CANCELLED` order is not part of the fold
([R-409](research.md#r-409-the-release-is-the-fold-unchanged)).

Delivered in the spec's order:

1. **US1** — the endpoint, the cancellation rule (`model`), the use case (`service/cancellation`),
   the scoped read and guarded transition (`persistence`), and the history's reason read by status
   ([R-406](research.md#r-406-the-historys-reason-holds-why-the-order-moved-not-only-rejections)).
2. **US2** — the serialisation. It comes from `UserWrite` the moment US1 calls it; this story is the
   concurrency tests that prove it, and the guarded update.
3. **US3** — the idempotency: the cancellation's canonical form, and **the replay of every write
   endpoint answering the outcome as first recorded**
   ([R-407](research.md#r-407-a-replay-answers-the-outcome-as-first-recorded-author)), which this
   feature is the first to make different from the current status.

**No migration, no new dependency, no new configuration, no constitution amendment.** Three ADRs
(0041–0043) written with this plan; ADR-0006 marked superseded.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.2 (`webmvc`, `data-jpa`, `flyway`), PostgreSQL JDBC driver.
Nothing added.

**Storage**: PostgreSQL 18.7. Read: `users`, `orders` (+ `instruments`), `order_idempotency`,
`order_status_history`. Written: `orders.status`, `order_status_history`, `order_idempotency` — all
existing. `reason VARCHAR(40)` holds `USER_REQUESTED` (measured,
[research.md](research.md#measured-facts-this-phase-relied-on)).

**Migrations**: none.

**Configuration**: none.

**Transaction**: one per cancellation, `READ COMMITTED`, not read-only — the other write endpoints'
(ADR-0036). Order: read the request, existence, key claim, user lock, scoped order read, decide,
guarded update + history row, attach key.

**Testing**: JUnit 5. The cancellation rule over the four statuses as plain Java; the scoped read, the
guarded transition, the history row and the earliest-transition read on Testcontainers
(`postgres:18.7`); MockMvc for the endpoint with every response validated against
`docs/api/openapi.yaml` (ADR-0024); two-thread Testcontainers tests for US2 and for concurrent
same-key requests; the existing ArchUnit rules cover the new use case. `./mvnw verify`.

**Target Platform**: Linux server / JVM 21; Docker Compose locally (PostgreSQL on 5433), API runnable
in Docker (ADR-0025).

**Project Type**: single-module Maven web service in layers (constitution VIII).

**Performance Goals**: none set. Cost of a cancellation: one existence read, at most one claim, one
lock, one order read, one update, one or two inserts.

**Constraints**: no comments (VII); model free of framework types and of any clock (VIII); every
query scoped by the untrusted user id; no money arithmetic is added (II applies to nothing new).

**Scale/Scope**: one endpoint, three stories, one change to the two existing replays, no migration,
three ADRs.

No item is left NEEDS CLARIFICATION: the three open choices (the replay of a cancelled order, the
status for not cancellable, the response) were answered by the author on 2026-11-17.

## Constitution Check

*Evaluated against constitution 2.4.0 before Phase 0, and re-evaluated after Phase 1 design.*

| Principle | How this feature complies | Verdict |
|---|---|---|
| **I. Ledger is the single source of truth** | Cancelling changes a ledger row's status; no balance or reservation is stored. The release is the existing fold leaving a `CANCELLED` order out ([R-409](research.md#r-409-the-release-is-the-fold-unchanged)); nothing computes it a second way. | PASS |
| **II. Money is exact** | No money is computed or compared by the cancellation. The response's price comes from the row at its stored scale, as submission's replay does. | PASS |
| **III. Logic in Java** | Which statuses may be cancelled is decided in `model` ([R-408](research.md#r-408-the-model-decides-from-the-status-alone)). The update's `WHERE status = …` is a compare-and-set on the status the model approved, not the rule ([R-405](research.md#r-405-the-transition-is-a-guarded-update-and-one-history-row)). No trigger, no `CHECK`. | PASS |
| **IV. Auditable and race-free** | The status update and the history row are in one transaction (FR-006). Only `NEW` is cancellable; a reservation cannot be released twice because the second cancellation reads `CANCELLED` under the lock, and the guarded update covers a writer outside it (FR-011). The rejection-reason list does not grow: `USER_REQUESTED` is a separate type ([R-406](research.md#r-406-the-historys-reason-holds-why-the-order-moved-not-only-rejections)). | PASS |
| **V. Tests on the real engine** | Plain Java for the rule; Testcontainers for persistence, HTTP and concurrency; every refusal — invalid ids, unknown user, missing and foreign order, each non-`NEW` status, key conflict — has a test (SC-008). | PASS |
| **VI. Migrations only** | No schema change. Seed untouched; every scenario uses seeded orders or orders submitted in the test. | PASS |
| **VII. No comments; reasoning in documents** | ADR-0041 (endpoint; supersedes ADR-0006, marked), ADR-0042 (the reason column), ADR-0043 (replay), index updated in the same change. No departure from the statement, which defines `CANCELLED` and that only `NEW` is cancellable; the endpoint is an addition the README already lists, and its row is updated to cite ADR-0041. Journal untouched. | PASS |
| **VIII. Layers** | Controller translates HTTP only; `service/cancellation` holds the use case and its own request, result and exceptions; the rule is plain Java in `model`. The lock and the claim come from `service/userwrite` only; the use case depends on no other use case. `OrderResponse` gains a mapping from the cancellation's result — a DTO depending on service results, as it already does. | PASS |
| **IX. Design for change** | The cancellable statuses are stated once. Replays answer through the history in each use case; whether the two existing copies of "the order as first recorded" become one persistence read is the refactor step's call. `AppendedOrder` is renamed so its name states its contract for a caller that appends nothing ([R-404](research.md#r-404-the-protections-are-userwrites-unchanged)). No extension point invented. | PASS |
| **Scope: the endpoint list** | Already lists order cancellation; spec exists; the alternatives are recorded in ADR-0041. | PASS |

**Gate verdict**: **PASS**, before and after Phase 1. No amendment, no violation to track.

## Project Structure

### Documentation (this feature)

```text
specs/005-order-cancellation/
├── spec.md                                  # Input
├── plan.md                                  # This file
├── research.md                              # Phase 0: R-401 to R-412, measured facts, three author answers
├── data-model.md                            # Phase 1: tables, model rule, service contracts, requirement map, ADRs
├── quickstart.md                            # Phase 1: eight validation scenarios from the seed
├── contracts/
│   └── order-cancellation.openapi.yaml      # Phase 1 design-time proposal; live contract is docs/api/openapi.yaml
├── checklists/
│   └── requirements.md                      # EXISTS — from /speckit-specify
└── tasks.md                                 # Phase 2 (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
src/main/java/com/cocos/trading/
├── controller/
│   ├── OrderCancellationController.java         # POST /api/v1/users/{userId}/orders/{orderId}/cancel; 200
│   ├── ApiExceptionHandler.java                 # EXISTS — gains 400 / 404 / 409 for the cancellation's exceptions
│   └── dto/OrderResponse.java                   # EXISTS — gains a mapping from the cancelled order
├── service/cancellation/                        # the use case and its own types
│   ├── OrderCancellationService.java            # @Transactional(READ_COMMITTED); calls UserWrite
│   ├── CancellationRequest.java                 # raw request: user id, order id, optional key
│   ├── CancelledOrder.java                      # what the controller maps; names its order for UserWrite
│   ├── CancellationFingerprint.java             # operation=ORDER_CANCELLATION;userId=…;orderId=…
│   ├── InvalidCancellationException.java        # -> 400
│   ├── UnknownOrderException.java               # -> 404
│   └── OrderNotCancellableException.java        # -> 409
├── service/submission/OrderSubmissionService.java   # EXISTS — replay reads the earliest transition
├── service/transfer/CashTransferService.java        # EXISTS — replay reads the earliest transition
├── service/userwrite/AppendedOrder.java             # EXISTS — renamed (proposed WrittenOrder), contract unchanged
├── model/order/
│   ├── CancellationReason.java                  # USER_REQUESTED
│   └── (the cancellation rule)                  # from a status: the transition, or not cancellable
└── persistence/
    ├── OrderRepository.java                     # EXISTS — gains the scoped read and the guarded transition
    ├── OrderStatusHistoryRepository.java        # EXISTS — cancellation row; earliest / CANCELLED transition; reasonOf by status
    └── mapper/OrderMapper.java                  # EXISTS — reason column read by the row's status; order id column

docs/api/openapi.yaml, docs/api/trading-api.http # EXISTS — gain the operation; Order.status gains CANCELLED (ADR-0024)
docs/adr/0041-…, 0042-…, 0043-…, README.md      # WRITTEN 2026-11-17 with their index rows; 0006 marked superseded
README.md                                        # EXISTS — the endpoint's row cites ADR-0041; status codes

src/test/java/com/cocos/trading/
├── model/order/                                 # the rule over NEW, FILLED, REJECTED, CANCELLED
├── service/cancellation/                        # canonical form
├── persistence/                                 # scoped read; guarded transition; history rows; reasonOf over a CANCELLED row
├── controller/                                  # quickstart 1–7, contract-validated; concurrency of scenario 8
└── ArchitectureTest.java                        # EXISTS — unchanged; covers service/cancellation
```

File names are proposals for `/speckit-tasks`; the contracts in [data-model.md](data-model.md) are
what binds.

**Structure Decision**: the single module and four layers of features 001–004. A new use case
`service/cancellation`; the rule joins `model/order`, where order statuses already live. No new
shared package: the protection is `service/userwrite`'s.

## Phase 0 and Phase 1 outputs

| Artifact | Contents |
|---|---|
| [research.md](research.md) | 12 decisions (R-401 to R-412) with alternatives; facts measured on 2026-11-17 |
| [data-model.md](data-model.md) | Tables read and written, the cancellation rule, the use case's contract and step order, the replay change, a requirement-to-owner map, the ADRs |
| [contracts/order-cancellation.openapi.yaml](contracts/order-cancellation.openapi.yaml) | The operation, its five answers with examples, the widened `Order.status` |
| [quickstart.md](quickstart.md) | Eight scenarios with expected figures from the seed, and what each test level covers |
| `docs/adr/0041`–`0043` | The three decisions above, indexed; ADR-0006 superseded |

## Requirement coverage

| Requirement | Where it is satisfied |
|---|---|
| FR-001 | `OrderCancellationController` + `OrderCancellationService` |
| FR-002 | the use case's reading of the request ([R-403](research.md#r-403-ids-are-validated-before-any-lookup)) |
| FR-003 | `UserWrite`'s existence read |
| FR-004, SC-004 | the order read scoped by user and id; one detail |
| FR-005, FR-007, SC-003 | the model's cancellation rule → `409` |
| FR-006 | guarded transition + history row, one transaction |
| FR-008, SC-001, SC-002, SC-007 | the unchanged fold |
| FR-009 | `OrderResponse`, `200` |
| FR-010, FR-011, SC-005 | `UserWrite` → `UserLock`; the guarded update |
| FR-012 – FR-015, SC-006 | `UserWrite` → `IdempotentWrite`; the `ORDER_CANCELLATION` canonical form |
| FR-016 | the existing ArchUnit rules: only `service/userwrite` locks and claims |
| SC-008 | every refusal path is a test |

## Complexity Tracking

No constitution violation. Two choices that add something beyond the minimum are recorded here so
`/speckit-tasks` does not drop them:

| Decision | Why needed | Simpler alternative rejected because |
|---|---|---|
| **The two existing replays change** | ADR-0037's "first outcome" stops being the current status the moment an order can be cancelled; without the change a keyed submission replay of a cancelled order answers `CANCELLED`, and today it would answer `500` ([R-407](research.md#r-407-a-replay-answers-the-outcome-as-first-recorded-author)). | Leaving them: the author chose the first outcome, and the `500` is a defect this feature would introduce. |
| **A guarded update although the lock already serialises** | FR-011 must hold for a writer that does not take the user's lock, which the constitution says features must not assume away ([R-405](research.md#r-405-the-transition-is-a-guarded-update-and-one-history-row)). | A plain update is correct only while every writer of `orders.status` takes the user's lock. |

## Answered by the author

| # | Question | Answer | What this plan does |
|---|---|---|---|
| Q-401 | A keyed submission replayed after its order was cancelled: first outcome or current status? | **First outcome** | Every replay reads the transition its request recorded; ADR-0043 ([R-407](research.md#r-407-a-replay-answers-the-outcome-as-first-recorded-author)) |
| Q-402 | An order that exists but is not `NEW`? | **`409 Conflict`** | ADR-0041 ([R-411](research.md#r-411-the-error-map-200-400-404-409-503)) |
| Q-403 | What does a successful cancellation return? | **`200` + `OrderResponse`** | `Order.status` admits `CANCELLED`; ADR-0041 ([R-402](research.md#r-402-a-cancellation-answers-200-with-the-order-author)) |

Earlier answers the journal already records: the user and the order in the path, the path itself, the
reason `USER_REQUESTED`, only `NEW` cancellable, a lock per user and idempotency.

## Deferred, by decision

- Validating `userId ≥ 1` with `400` on the other write endpoints and the portfolio, which answer `404`
  for `0` today ([R-403](research.md#r-403-ids-are-validated-before-any-lookup)).
- Reading an order or its history through the API.
- Cancelling any status but `NEW`, partial cancellation, caller-supplied reasons, bulk cancellation.
- Expiring idempotency records (unchanged from ADR-0037).
