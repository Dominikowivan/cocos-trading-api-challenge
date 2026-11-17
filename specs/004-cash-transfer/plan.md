# Implementation Plan: Cash Transfer

**Branch**: `feature/cashTransfer` (feature directory `004-cash-transfer`) | **Date**: 2026-11-17 |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-cash-transfer/spec.md`, scoped by
`docs/journal/08CashTransfer.md`

## Summary

One endpoint, `POST /api/v1/users/{userId}/cash-transfers`, with a body of exactly `side`
(`CASH_IN` / `CASH_OUT`) and `amount` in whole pesos. Each request appends **one order to the
ledger** in the shape the provided dataset already uses for transfers — a `MARKET` order on the
`MONEDA` instrument with the amount as its size and a price of `1` — so the portfolio's fold counts it
with no change ([R-303](research.md#r-303-a-transfer-is-the-seeds-row-a-market-order-on-the-moneda-instrument-at-price-1)).
A deposit is always `FILLED`. A withdrawal is `FILLED` when the user's **available** cash — the same
`AccountSnapshot.availableCash()` the portfolio reports — is at least the amount, and `REJECTED` with
`INSUFFICIENT_FUNDS` otherwise; both answer `201` with the transfer's own response shape (ADR-0033's
rule, [R-301](research.md#r-301-the-endpoint-is-post-apiv1usersuseridcash-transfers-author),
[R-302](research.md#r-302-the-response-is-the-transfers-own-shape-not-orderresponse-author)).

Delivered in the journal's order:

1. **US1** — the endpoint, its model (`model/transfer`) and its use case (`service/transfer`).
2. **US2** — the per-user lock, **the same row lock order submission takes** (ADR-0036), so a
   withdrawal and an order of one user queue on one serialisation.
3. **US3** — the `Idempotency-Key`, **the same table and claim** (ADR-0037). One key namespace per
   user across both endpoints, kept apart by a fingerprint that names the operation; submission's
   fingerprint is untouched ([R-310](research.md#r-310-idempotency-the-same-header-the-same-table-a-fingerprint-that-names-the-operation)).
4. **US4** — the lock and the claim, then duplicated, are **extracted into `service/userwrite`**, a
   shared subpackage that is not a use case; both services use it, nothing a caller sees changes
   ([R-311](research.md#r-311-the-shared-protection-lives-in-serviceuserwrite-author)). This needs a
   MINOR constitution amendment and an adjusted ArchUnit rule, flagged below.

**No migration, no new dependency, no new configuration.** One model change widens the write path:
`NewOrder.side` becomes a `LedgerSide`, as the read model `Order` already is
([R-307](research.md#r-307-the-write-model-takes-a-ledgerside-as-the-read-model-already-does)).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.2 (`webmvc`, `data-jpa`, `flyway`), PostgreSQL JDBC driver.
Nothing added.

**Storage**: PostgreSQL 18.7. Read: `users`, `instruments`, `orders`, `order_idempotency`,
`order_status_history`. Written: `orders`, `order_status_history`, `order_idempotency` — all existing.
Column capacities measured in [research.md](research.md#measured-facts-this-phase-relied-on):
`size INT` bounds the amount at `2,147,483,647`; `side VARCHAR(10)` holds `CASH_OUT`.

**Migrations**: none ([R-313](research.md#r-313-no-migration)).

**Configuration**: none. The amount's bounds are the column's capacity, stated once in the model.

**Transaction**: one per transfer, `READ COMMITTED`, not read-only — submission's, for submission's
reason (ADR-0036). Order: existence read, key claim, user lock, cash instrument, fold, decide, append
order + history, attach key.

**Testing**: JUnit 5. The amount's validity and the decision table as plain Java; the append and
read-back of a cash row on Testcontainers (`postgres:18.7`); MockMvc for the endpoint with every
response validated against `docs/api/openapi.yaml` (ADR-0024); two-thread Testcontainers tests for
US2 (including a withdrawal racing a `MARKET BUY`) and for concurrent same-key requests; ArchUnit for
US4. `./mvnw verify`.

**Target Platform**: Linux server / JVM 21; Docker Compose locally (PostgreSQL on 5433), API runnable
in Docker (ADR-0025).

**Project Type**: single-module Maven web service in layers (constitution VIII).

**Performance Goals**: none set. Cost of a transfer: one existence read, at most one claim, one lock,
one instrument read, one fold read, two or three writes — submission's profile.

**Constraints**: `BigDecimal` and `compareTo` on every money path (II); no comments (VII); model free
of framework types and of any clock (VIII); every query scoped by the untrusted user id.

**Scale/Scope**: one endpoint, four stories, no migration, three ADRs, one constitution amendment.

No item is left NEEDS CLARIFICATION: the three open choices (path, response shape, where the shared
protection lives) were answered by the author on 2026-11-17.

## Constitution Check

*Evaluated against constitution 2.3.1 before Phase 0, and re-evaluated after Phase 1 design.*

| Principle | How this feature complies | Verdict |
|---|---|---|
| **I. Ledger is the single source of truth** | A transfer is a row in `orders`; no balance column. The withdrawal check reads `AccountSnapshot.availableCash()` from the unchanged fold, the same object the portfolio uses (FR-009, SC-007, [R-308](research.md#r-308-available-cash-is-the-fold-the-portfolio-reports-unchanged)). | PASS |
| **II. Money is exact** | The amount is a `BigDecimal` from the DTO onward; whole pesos checked by scale, never rounded; the comparison is `compareTo(...) >= 0`; the reported amount goes through `DecimalPolicy.money`. No `double`. | PASS |
| **III. Logic in Java** | The decision table, the amount's rules and the price of a peso live in `model/transfer`. No trigger, no `CHECK` on `side`. | PASS |
| **IV. Auditable and race-free** | Every transfer appends its history row in the same transaction (FR-010). A rejection records `INSUFFICIENT_FUNDS`, already in the list. Reading available cash and appending the withdrawal happen under the user-row lock submission uses, so transfers and orders share one serialisation (FR-014, [R-309](research.md#r-309-serialisation-is-submissions-lock-on-the-same-row-us2)). | PASS |
| **V. Tests on the real engine** | Plain Java for the model; Testcontainers for persistence, HTTP, both concurrency pairs and the same-key race; every rejection and refusal path has a test (SC-009). | PASS |
| **VI. Migrations only** | No schema change. Seed untouched; every scenario uses seeded users with no fixture. | PASS |
| **VII. No comments; reasoning in documents** | Three ADRs (0038 endpoint, 0039 row shape, 0040 shared protection) with `docs/adr/README.md` updated in the same change. No departure from the statement: it says transfers *can* be modelled as `CASH_IN`/`CASH_OUT` orders, which is what this does; the extra endpoint is an addition, stated in the README as one. Journal untouched. | PASS |
| **VIII. Layers** | Controller translates HTTP only; `service/transfer` holds the use case and its own request, response and exception; `model/transfer` is plain Java. **US1–US3 pass as is. US4 conflicts with the rule as written**: `service/userwrite` is neither a use case nor at the root, and both use cases depend on it, which the slice rule forbids. | **PASS for US1–US3; US4 gated on the amendment below** |
| **IX. Design for change** | One write model for every ledger row ([R-307](research.md#r-307-the-write-model-takes-a-ledgerside-as-the-read-model-already-does)); one canonical form per request; the lock and the claim end in one place, enforced by ArchUnit. **Between US3 and US4 the claim flow exists twice** — on purpose, per the journal — and US4 is what removes it (Complexity Tracking). No extension point invented: `service/userwrite` has two real users the day it is created. | PASS, with a tracked temporary duplicate |
| **Scope: the endpoint list** | The constitution lists portfolio, search, submission and cancellation. A new endpoint requires a spec (this one) and, having had alternatives, an ADR (0038). The list itself must be amended. | **Amendment required** |

**Gate verdict**: **PASS for US1–US3.** US4 and the merge were gated on one constitution amendment,
**applied on 2026-11-17 (constitution 2.4.0)** at the author's request; re-evaluated against 2.4.0,
VIII and the scope row **PASS**.

### Constitution amendment (applied: 2.3.1 → 2.4.0)

One **MINOR** amendment, materially expanded guidance:

1. **Technology and Scope Constraints** — the endpoint list adds cash transfer.
2. **VIII. Layers** — `service` may hold, besides one subpackage per use case, a subpackage of
   shared write protections that is **not** a use case (no `@Service`, no endpoint): use cases may
   depend on it, it depends on none of them, and it runs only inside a use case's transaction. Origin:
   ADR-0040 and `docs/journal/08CashTransfer.md`.

`ArchitectureTest` changes in the US4 commit to match: the slice rule exempts `service.userwrite` as
a dependency target only, and a new rule asserts that `lockById` and `claim` are called only from it.
Governance requires the amendment in the same change as the code it permits.

## Project Structure

### Documentation (this feature)

```text
specs/004-cash-transfer/
├── spec.md                              # Input
├── plan.md                              # This file
├── research.md                          # Phase 0: R-301 to R-314, measured facts, three author answers
├── data-model.md                        # Phase 1: tables, model, service contracts, requirement map, ADRs
├── quickstart.md                        # Phase 1: nine validation scenarios from the seed
├── contracts/
│   └── cash-transfer.openapi.yaml       # Phase 1 design-time proposal; live contract is docs/api/openapi.yaml
├── checklists/
│   └── requirements.md                  # EXISTS — from /speckit-specify
└── tasks.md                             # Phase 2 (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
src/main/java/com/cocos/trading/
├── controller/
│   ├── CashTransferController.java          # POST /api/v1/users/{userId}/cash-transfers; 201
│   ├── ApiExceptionHandler.java             # EXISTS — gains the transfer's 400; unreadable-body detail made neutral
│   └── dto/
│       ├── CashTransferRequest.java         # side, amount, and any other field for the use case to refuse
│       └── CashTransferResponse.java        # orderId, userId, side, amount, status, rejectionReason, transferredAt
├── service/transfer/                        # US1–US3: the use case and its own types
│   ├── CashTransferService.java             # @Transactional(READ_COMMITTED)
│   ├── TransferRequest.java                 # raw request from the controller, optional key
│   ├── RecordedTransfer.java                # what the controller maps
│   ├── InvalidTransferException.java        # -> 400
│   └── (US3 copies of the key and claim flow, removed by US4)
├── service/userwrite/                       # US4: shared, not a use case
│   ├── UserWrite.java                       # @Component; existence, claim, lock, attach / replay / conflict
│   ├── IdempotencyKey.java                  # MOVED from service/submission
│   ├── IdempotencyConflictException.java    # MOVED from service/submission -> 409
│   └── (hashing of a canonical form)        # MOVED from RequestFingerprint; each use case keeps its form
├── service/submission/                      # EXISTS — US4 delegates its lock and claim; behaviour unchanged
├── model/transfer/                          # plain Java
│   └── CashTransfer.java                    # whole-peso amount, direction; answers the NewOrder to record
├── model/order/
│   ├── LedgerSide.java                      # EXISTS — declares name()
│   └── NewOrder.java                        # EXISTS — side widens to LedgerSide
└── persistence/
    ├── InstrumentRepository.java            # EXISTS — gains instruments of a type
    └── mapper/OrderMapper.java              # EXISTS — toPlacedOrder reads a LedgerSide

docs/api/openapi.yaml, docs/api/trading-api.http   # EXISTS — gain the operation (ADR-0024)
docs/adr/0038-…, 0039-…, 0040-…, README.md         # WRITTEN 2026-11-17 with their index rows (VII)
README.md                                          # EXISTS — lists the endpoint
.specify/memory/constitution.md                    # amended to 2.4.0 (above)

src/test/java/com/cocos/trading/
├── model/transfer/                          # amount validity; decision table and boundary
├── service/transfer/                        # canonical form
├── persistence/                             # a cash row and its history appended and read back
├── controller/                              # quickstart 1–8, concurrency pairs, same-key race
└── ArchitectureTest.java                    # US4: adjusted slice rule; only userwrite locks and claims
```

File names are proposals for `/speckit-tasks`; the contracts in [data-model.md](data-model.md) are
what binds.

**Structure Decision**: the single module and four layers of features 001–003. New concept packages
`model/transfer` and use case `service/transfer`; one shared non-use-case package
`service/userwrite`, admitted by the amendment above. `model/ledger` is reused unchanged.

## Phase 0 and Phase 1 outputs

| Artifact | Contents |
|---|---|
| [research.md](research.md) | 14 decisions (R-301 to R-314) with alternatives; facts measured on 2026-11-17 |
| [data-model.md](data-model.md) | Tables read and written, the model's decision table, both services' contracts and step order, a requirement-to-owner map, the ADRs to write |
| [contracts/cash-transfer.openapi.yaml](contracts/cash-transfer.openapi.yaml) | The operation, the closed request, the response, five answers, examples |
| [quickstart.md](quickstart.md) | Nine scenarios with expected figures from the seed, and what each test level covers |

## Requirement coverage

| Requirement | Where it is satisfied |
|---|---|
| FR-001 | `CashTransferController` + `CashTransferService` |
| FR-002 | the use case's parsing of `side` |
| FR-003 | `CashTransfer`'s construction ([R-304](research.md#r-304-the-amount-is-whole-pesos-from-1-to-2147483647-never-rounded)) |
| FR-004 | the closed body ([R-305](research.md#r-305-the-body-is-exactly-side-and-amount-any-other-field-is-refused)) |
| FR-005 | the existence read → `404` |
| FR-006 | `CashTransfer`'s answer on the `MONEDA` instrument at price `1` |
| FR-007, FR-008 | the decision table |
| FR-009, SC-007 | `AccountSnapshot.availableCash()`, unchanged |
| FR-010 | append + history, one transaction |
| FR-011 | `RecordedTransfer` → `CashTransferResponse`, `201` |
| FR-012 | the unchanged fold (`cashEffect`) |
| FR-013 | `DecimalPolicy.money` |
| FR-014, SC-005 | `UserRepository.lockById`, the same row as submission |
| FR-015 – FR-017, SC-006 | `order_idempotency`, the operation-tagged canonical form |
| FR-018, FR-019, SC-008 | `service/userwrite` and the ArchUnit rules; submission's suite and fingerprint unchanged |
| SC-001 – SC-004 | quickstart scenarios 1–5 |
| SC-009 | every refusal of scenario 6 and every rejection is a test |

## Complexity Tracking

| Decision | Why needed | Simpler alternative rejected because |
|---|---|---|
| **The claim flow and the lock call exist twice between US3 and US4** | The journal sequences it: build the protections following submission's pattern, then abstract what is duplicated. The slice rule forbids `service/transfer` importing them from `service/submission`. | Extracting first contradicts the author's sequence and would design the abstraction from one user; importing across use cases breaks the ArchUnit rule. US4 is in this feature's scope, so the duplicate never reaches `develop` alone if the feature merges whole. |
| **A shared subpackage in `service` that is not a use case** | FR-018: lock and claim implemented once for two use cases. It is orchestration, which VIII keeps in the service layer. | Persistence (orchestration outside the service); a top-level package (outside every layer rule); AOP (invisible, self-invocation) — [R-311](research.md#r-311-the-shared-protection-lives-in-serviceuserwrite-author). |
| **`NewOrder.side` widened to `LedgerSide`** | One write path for every ledger row, and a replay must read a cash row back. | A parallel `NewTransfer` write path duplicates the history-row rule ([R-307](research.md#r-307-the-write-model-takes-a-ledgerside-as-the-read-model-already-does)). |
| **A closed request body, unlike submission's** | FR-004 requires refusing, not ignoring. | Refusing only five names leaves the same hole for a sixth; a global setting changes submission (FR-019). |

## Answered by the author

| # | Question | Answer | What this plan does |
|---|---|---|---|
| Q-301 | What path? | **`/cash-transfers`** under the user | `POST /api/v1/users/{userId}/cash-transfers` ([R-301](research.md#r-301-the-endpoint-is-post-apiv1usersuseridcash-transfers-author)) |
| Q-302 | Own response or `OrderResponse`? | **Its own** | `orderId`, `side`, `amount`, `status`, `rejectionReason`, `transferredAt`; no ticker, type or price ([R-302](research.md#r-302-the-response-is-the-transfers-own-shape-not-orderresponse-author)) |
| Q-303 | Where does the shared lock + idempotency live? | **A subpackage of `service`** | `service/userwrite`, with a MINOR amendment of VIII and ADR-0040 ([R-311](research.md#r-311-the-shared-protection-lives-in-serviceuserwrite-author)) |

Earlier answers the spec already records: the instrument is implicit; whole pesos only; one key
namespace per user across both endpoints.

## Deferred, by decision

- A second currency, transfers between users or accounts, deposit origins, caps.
- An endpoint that lists or reads transfers back; the portfolio shows their effect.
- Cancellation (ADR-0006): it will be the third user of `service/userwrite`, which is the reason the
  extraction is worth doing now.
- Expiring idempotency records (unchanged from ADR-0037).
