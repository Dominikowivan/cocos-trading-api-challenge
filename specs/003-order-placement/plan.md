# Implementation Plan: Order Placement

**Branch**: `feature/orderPlacement` (feature directory `003-order-placement`) | **Date**: 2026-11-17 |
**Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/003-order-placement/spec.md`

## Summary

One endpoint, `POST /api/v1/users/{userId}/orders`, that submits exactly one order to the market and
**appends exactly one row to the `orders` ledger** — the first feature in this project that writes.
A `MARKET` order executes at the instrument's latest `close` and is persisted `FILLED`; a `LIMIT`
order is validated at the price in the request and is persisted `NEW`, reserving what it commits
(ADR-0004, ADR-0007). An order the user cannot afford, cannot cover in shares, or that derives no
whole share is persisted `REJECTED` with a machine-readable reason, as the statement requires, and
answered with `201` like the other two: **a rejection is a recorded outcome, a malformed request is
not** (FR-019, FR-023).

The quantity is **exactly one of** a share count or a total amount in pesos. One rule serves both
sides and both order types (FR-014 to FR-018): compare what is *available* — cash for a `BUY`,
`available holding × price` for a `SELL` — against what the request needs, then take
`floor(amount / price)` shares, rejecting a derived size of zero. **The price substitutes on both
sides of that comparison**, which is why `30,000.00` of `PAMP` sells 30 shares at a limit of
`1,000.00` and 32 at the market's `925.85`.

Validation consumes **the same fold the portfolio reports** —
`new AccountLedger(orders).snapshot()`, asked for `availableCash()` and
`availableQuantityOf(instrumentId)` — so available cash cannot be computed a second way and SC-012
holds by construction rather than by comparison (ADR-0015). Nothing is added to the fold; this is the
call site `availableQuantityOf` was written for and has never had.

The business rules live in **plain Java model objects** that return a decision and write nothing: the
validated submission answers which price governs and what the outcome is, and the service persists
it. The service orchestrates and decides nothing: claim the idempotency key if there is one, lock the
user's row, read the ledger and the latest close, let the model decide, append the order and its
status-history row, attach the order to the key, answer.

**Two protections, built last on purpose** (the author's sequencing, `docs/journal/07OrderPlacement.md`):
submissions for one user are serialised by a **pessimistic lock on their `users` row**, so two
concurrent requests cannot both commit against the same availability and no user ever waits on
another's order; and an optional **`Idempotency-Key` header** makes a retry of the same request return
the first outcome instead of trading again, through a unique `(userid, key)` and
`INSERT … ON CONFLICT DO NOTHING` at `READ COMMITTED`.

**Two migrations**: `V4__order_status_history.sql`, the table ADR-0005 accepted and no feature has
needed until now, and `V5__order_idempotency.sql`. `V1`, `V2` and `V3` are untouched. **No new
dependency.**

**One departure from the spec and from the statement, decided by the author on 2026-11-17**: a
`MARKET` submission for an instrument with no price (`PGR`, `IRCP`) is **refused with `400` and
nothing is written**, instead of being recorded as a `REJECTED` order. An instrument that does not
quote is a property of the request, knowable before any of the user's money is considered, and
refusing it keeps every row in `orders` priced — so `V3`'s `NOT NULL` on `price` stands and no money
figure is ever written as `0` when it is unknown
([R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection),
ADR-0034). The spec is amended to match, dated and marked; the README states the departure.

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.2 (`webmvc`, `data-jpa`, `flyway`), PostgreSQL JDBC driver.
**Nothing is added** — the SHA-256 of the request fingerprint comes from `java.security.MessageDigest`.

**Storage**: PostgreSQL 18.7. Tables read: `users`, `instruments`, `orders`, `marketdata`. Tables
written: **`orders`** (one row per submission), **`order_status_history`** (new, `V4`),
**`order_idempotency`** (new, `V5`).

**Schema facts that drive the write** (identifiers lowercase; `V1__given_schema.sql` writes them
unquoted):

| Column | Type | Consequence for this feature |
|---|---|---|
| `orders.id` | `SERIAL` | the id is the database's; the entity becomes insertable with a generated identity ([R-215](research.md#r-215-orderentity-becomes-writable-with-the-database-generating-the-id)) |
| `orders.size` | `INT NOT NULL` (`V3`) | a size is `1` to `2,147,483,647`; `0` is written only on a rejection that derived no size, which `V3` allows because ADR-0030 deliberately dropped `CHECK (size > 0)` |
| `orders.price` | `NUMERIC(10,2) NOT NULL` (`V3`) | every written row carries a price — the one case with no price is refused, not recorded ([R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)); a price or amount is at most `99,999,999.99` |
| `orders.type`, `side`, `status` | `VARCHAR NOT NULL` | the vocabulary is the Java enums'; no `CHECK` is added (ADR-0030) |
| `orders.datetime` | `TIMESTAMP NOT NULL` | server-side instant, supplied by the service ([R-213](research.md#r-213-the-orders-instant-is-the-servers-supplied-by-the-service)) |
| `marketdata.close` | `NUMERIC(10,2)`, nullable | absent for `PGR`, `IRCP`, `ARS`; the `MARKET` price comes from the latest row **per instrument** |
| `users.id` | `SERIAL PRIMARY KEY` | the row this feature takes `FOR UPDATE`, which also answers existence |

**Migrations**: `V4__order_status_history.sql` (`order_id`, `status`, `reason`, `changed_at`, FK to
`orders`, index on `order_id`) and `V5__order_idempotency.sql` (`userid`, `idempotency_key`,
`request_fingerprint`, `order_id`, `created_at`, `UNIQUE (userid, idempotency_key)`). Shapes in
[data-model.md](data-model.md#change-to-the-schema).

**Configuration**: **none added**. The bounds on a size, a price and an amount are the capacity of
the columns, not policy, so they are stated in the model and not as properties — the opposite answer
to instrument search's three limits, for a stated reason
([R-207](research.md#r-207-the-derivations-floor-and-the-bounds-are-stated-in-one-place-each)). The
transaction deadline and the pool stay as ADR-0031 set them.

**Transaction**: one per submission, **`READ COMMITTED`**, not read-only — deliberately unlike the
two read endpoints' `REPEATABLE_READ`, because a submission must see what the transaction it waited
for committed ([R-211](research.md#r-211-the-transaction-is-read-committed-deliberately-unlike-the-reads)).
The first statement is the user-row lock; the order, its history row and the key's `order_id` are
written in that same transaction, so a rollback leaves no trace.

**Testing**: JUnit 5. The request's validity rules, the governing price, the available-versus-needed
comparison, the floor and its zero case, and the status and reason of every outcome as **plain Java
with no Spring and no database**; the inserts, the lock and the idempotency claim on Testcontainers
against `postgres:18.7`, built from the same migrations and seed as local development (constitution V,
ADR-0010); MockMvc for HTTP, with every response validated against `docs/api/openapi.yaml`
(ADR-0024). Concurrency (US5) is tested from two threads against the real database, and the idempotent
replay likewise — neither is testable without an engine. `./mvnw verify`.

**Target Platform**: Linux server / JVM 21; local development on Docker Compose (PostgreSQL on port
5433), the API itself runnable in Docker (ADR-0025).

**Project Type**: Single-module Maven web service in layers: controller → service → model and
persistence (constitution VIII).

**Performance Goals**: none set by the statement. The cost of a submission is one lock, one read of
the user's contributing orders, one latest-price query and two or three inserts. Serialising one
user's submissions is a deliberate cost the author accepted; no user waits on another
([R-209](research.md#r-209-one-user-is-serialised-by-a-pessimistic-lock-on-their-users-row)).

**Constraints**: `BigDecimal` on every money path, `compareTo` never `equals` (constitution II). One
scale and rounding policy, `DecimalPolicy`, which gains exactly one named question — how many whole
shares fit in an amount at a price, scale `0`, `FLOOR`. No comments in source (VII). Model free of
Spring, Jakarta, JPA and web types, and of any clock (VIII,
[R-213](research.md#r-213-the-orders-instant-is-the-servers-supplied-by-the-service)). The user id is
untrusted input, so every query is scoped by it.

**Scale/Scope**: one endpoint, six user stories, two migrations, 66 instruments and 11 seeded orders.

## Constitution Check

*Evaluated against constitution 2.3.0, before Phase 0 and re-evaluated after Phase 1 design.*

| Principle | How this feature complies | Verdict |
|---|---|---|
| **I. The ledger is the single source of truth** | No positions table, no balance column, no cached figure. Validation reads `new AccountLedger(orders).snapshot()` — the same fold, the same object, the same filters the portfolio uses — and asks it for `availableCash()` and `availableQuantityOf()`; the reservation subtraction stays inside the snapshot ([R-204](research.md#r-204-validation-reads-the-fold-the-portfolio-reports-unchanged), ADR-0015). This feature is the only writer, and it appends. | PASS |
| **II. Money is exact** | `BigDecimal` throughout, including the request DTO's `amount` and `price` and every test figure. The one new division, `floor(amount / price)`, names its scale (`0`) and `RoundingMode` (`FLOOR`) in `DecimalPolicy`, not at a call site ([R-207](research.md#r-207-the-derivations-floor-and-the-bounds-are-stated-in-one-place-each)). Comparisons by `compareTo`: the "available < needed" test and the "derived size is zero" test are exactly where `equals` would be wrong. No `double` or `float`. | PASS |
| **III. Business logic in Java, not in the database** | Every rule — the request's validity, which price governs, availability, the derivation, the status, the reason — is in `model/submission`, testable with no database. The new tables carry a foreign key, a unique constraint and `NOT NULL`s: integrity, not rules. **No trigger writes the history row**, which was the tempting option and is rejected in [R-208](research.md#r-208-order_status_history-is-created-by-this-feature-in-migration-v4). `ON CONFLICT DO NOTHING` is a unique constraint being used, not a rule in SQL. | PASS |
| **IV. Order state is auditable and race-free** | Every transition inserts a row into `order_status_history` in the same transaction as the order (FR-022, `V4`). Rejections carry a machine-readable reason. Reading availability and inserting the order that consumes it happen under a pessimistic lock on the user's row, taken as the transaction's first statement, per user (FR-026, FR-027, [R-209](research.md#r-209-one-user-is-serialised-by-a-pessimistic-lock-on-their-users-row)). Cancellation is a separate feature and will be covered by the same rule. **One follow-up**, not a violation: the principle's parenthetical lists "no price" among the rejection reasons, and after [R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection) that outcome is no longer a rejection — so no rejection lacks a reason, but the list names something that cannot occur. See *Constitution follow-up* below. | PASS, with a flagged amendment |
| **V. Tests run on the real engine** | Plain-Java tests for every model rule and every rejection reason; Testcontainers for the inserts, the lock, the claim, and both migrations applying on top of the given schema; Testcontainers + MockMvc for the thirteen quickstart scenarios, the concurrency pair from two threads and the idempotent replay. No H2, no mocked repository, no test pointed at staging. Every rejection and refusal path is a test, not only the happy ones (SC-014). | PASS |
| **VI. Schema changes only through migrations** | Two new Flyway migrations; `V1` immutable and `V2`, `V3` untouched. The seed stays opt-in and out of `staging` (ADR-0029). Every scenario is reachable from the provided dataset with no fixture: `PGR` has no price, `ARS` is the `MONEDA` instrument, `BMA` is `−10`, user `2` has never traded. **Deploy note**: `V4` and `V5` only create tables, so they validate no existing row and cannot fail on staging data the way `V3` could (ADR-0030). | PASS |
| **VII. No comments; reasoning in documents** | No comments in the code. Four decisions with real alternatives become ADRs — 0033 the endpoint's shape, 0034 the no-price refusal, 0035 the per-user lock, 0036 idempotency — with `docs/adr/README.md` updated in the same change. The departure from the statement (0034) is stated in the README, as VII requires. `docs/journal/` is not touched. | PASS |
| **VIII. Layers** | `OrderSubmissionController` translates HTTP only: path variable, body DTO, optional header → one raw `SubmissionRequest` → one service call → one response DTO, `201`. It reads no configuration and interprets no rule. `OrderSubmissionService` and its own request, response, fingerprint and exception types sit together in `service/submission`, depending on no other use case (ArchUnit). The model is plain Java in `model/submission` plus the unchanged `model/ledger`, and holds no repository, no Spring type and no clock; it returns a decision for the service to persist. Entities stay in `persistence`, mapped by hand. `Closes` moves to `model/instrument` because it answers a question about the market, not about a portfolio — the split VIII requires in the change that adds the second concept. | PASS |
| **IX. Design for change** | One rule in one place: the availability comparison is written once for two sides and two types; the governing price is answered once ([R-205](research.md#r-205-the-execution-price-is-the-models-choice-not-the-services)); the floor lives in `DecimalPolicy`; the request fingerprint has one canonical form, so a retry cannot hash differently from the first request; the entity → `InstrumentRef` mapping is deduplicated into one place instead of gaining a third copy ([R-214](research.md#r-214-one-mapping-from-an-instrument-row-to-an-instrument-shared)). Objects expose behaviour: the submission answers "what order does this become", `TradeQuantity` answers "what do you need and what size does that make", and no caller reassembles a decision from parts. No `Optional` parameter: the absent `MARKET` close is resolved by the service before the model is asked. No extension point is invented — no `Clock`, no configurable limits, no interface with one implementation. Every story ends with the refactor task the workflow requires. | PASS |

**Gate verdict**: **PASS**. No principle is violated and the Complexity Tracking table below records
the two costs taken deliberately.

### Constitution follow-up (for the author, not done here)

Principle IV's rejection-reason list and ADR-0005's `reason` column example both name "no price",
which [R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)
turns into a `400` rather than a recorded rejection. Governance requires a conflict between the
constitution and an accepted decision to be resolved in the same change, so this plan **flags** it
rather than diverging silently: a `PATCH` amendment through `/speckit-constitution` that drops "no
price" from the parenthetical, with ADR-0034 as the reason. ADR-0005 is accepted and is not edited;
ADR-0034 cites it. Nothing in the implementation depends on the amendment, which is why it is a
follow-up and not a gate.

## Project Structure

### Documentation (this feature)

```text
specs/003-order-placement/
├── spec.md                                  # Input (amended 2026-11-17: the no-price case is refused, not recorded)
├── plan.md                                  # This file
├── research.md                              # Phase 0: 17 decisions with alternatives (R-201 to R-217) and the measured dataset
├── data-model.md                            # Phase 1: what is read and written, the two migrations, the model, the service, a requirement map
├── quickstart.md                            # Phase 1: thirteen runnable validation scenarios
├── contracts/
│   └── order-submission.openapi.yaml        # Phase 1 design-time proposal; the live contract is docs/api/openapi.yaml
├── checklists/
│   └── requirements.md                      # EXISTS — from /speckit-specify
└── tasks.md                                 # Phase 2 (/speckit-tasks — NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
src/main/java/com/cocos/trading/
├── controller/
│   ├── OrderSubmissionController.java       # POST /api/v1/users/{userId}/orders; HTTP only, answers 201
│   ├── ApiExceptionHandler.java             # EXISTS — gains 400 (invalid submission, malformed body), 404, 409
│   └── dto/
│       ├── OrderSubmissionRequest.java      # record; type and side as text so the use case names what was allowed
│       └── OrderResponse.java               # record; absent reason -> JSON null
├── service/submission/                      # the use case and its own request, response and failures
│   ├── OrderSubmissionService.java          # @Transactional(READ_COMMITTED); claim, lock, fold, decide, append, answer
│   ├── SubmissionRequest.java               # the raw request the controller read, including the optional key
│   ├── SubmittedOrder.java                  # what the controller maps to a DTO
│   ├── RequestFingerprint.java              # the canonical form of a request and its SHA-256, in one place
│   ├── InvalidSubmissionException.java      # the refusals that write nothing, naming the rule -> 400
│   ├── UnknownSubmissionUserException.java  # no such user -> 404 (its own: use cases may not depend on each other)
│   └── IdempotencyConflictException.java    # the key was used for a different request -> 409
├── model/submission/                        # plain Java: the rules
│   ├── Submission.java                      # valid by construction; which price governs; what the outcome is
│   ├── TradeQuantity.java                   # exactly one of a size or an amount; what it needs, what size it makes
│   ├── OrderDecision.java                   # status, size, price, reason — returned, never written
│   └── RejectionReason.java                 # INSUFFICIENT_FUNDS, INSUFFICIENT_HOLDINGS, SIZE_BELOW_ONE_SHARE
├── model/
│   ├── DecimalPolicy.java                   # EXISTS — gains the whole-shares-in-an-amount question (scale 0, FLOOR)
│   ├── ledger/                              # EXISTS, UNCHANGED — AccountLedger, AccountSnapshot, Holding
│   ├── order/OrderSide.java                 # EXISTS — gains only "does this side move cash" so CASH_IN/OUT are refusable
│   └── instrument/Closes.java               # MOVED from model/portfolio: a market fact, not a portfolio concept
└── persistence/
    ├── UserRepository.java                  # EXISTS — gains lockById: SELECT … FOR UPDATE, existence and the lock in one
    ├── OrderRepository.java                 # EXISTS — findOrdersOf reused as-is; save appends the order
    ├── OrderStatusHistoryRepository.java    # new
    ├── OrderIdempotencyRepository.java      # new: claim (INSERT … ON CONFLICT DO NOTHING) and read-back
    ├── entity/
    │   ├── OrderEntity.java                 # EXISTS — becomes insertable, id generated by the SERIAL sequence
    │   ├── OrderStatusHistoryEntity.java    # new
    │   └── OrderIdempotencyEntity.java      # new
    └── mapper/
        ├── InstrumentMapper.java            # new, shared: the one entity -> InstrumentRef mapping
        ├── OrderMapper.java                 # EXISTS — gains decision -> row + history row, row -> SubmittedOrder
        └── InstrumentSearchMapper.java      # EXISTS — calls the shared mapping instead of its own copy

src/main/resources/db/migration/
├── V4__order_status_history.sql             # the table ADR-0005 accepted; needed by story 1
└── V5__order_idempotency.sql                # needed by story 6 only

docs/api/
├── openapi.yaml                             # EXISTS — gains the operation and its schemas (ADR-0024)
└── trading-api.http                         # EXISTS — gains the submission section

docs/adr/
├── 0033-the-submission-endpoint-shape.md
├── 0034-an-unpriced-instrument-refuses-the-submission.md
├── 0035-submissions-are-serialised-per-user.md
├── 0036-idempotent-submission-with-a-caller-key.md
└── README.md                                # EXISTS — gains all four rows in the same change (VII)

README.md                                    # EXISTS — states the ADR-0034 departure from the statement,
                                             # and that the statement's functional test is the e2e suite

src/test/java/com/cocos/trading/
├── model/submission/                        # plain Java: validity, price, availability, derivation, outcomes
├── service/submission/                      # plain Java: the request fingerprint's canonical form
├── persistence/                             # Testcontainers: the inserts, the lock, the claim, V4 and V5
└── controller/                              # Testcontainers + MockMvc: quickstart 1-13, concurrency, replay
```

**Structure Decision**: the single Maven module and the four layers of features 001 and 002, with
`service` grouped by use case (`service/submission`), `model` by concept (`model/submission`,
`model/ledger`, `model/instrument`) and `controller` and `persistence` by role — enforced by
`ArchitectureTest`. The only move is `Closes`, from `model/portfolio` to `model/instrument`, for the
reason constitution VIII gives. `model/ledger` is reused **unchanged**, which is the point of
ADR-0015.

## Phase 0 and Phase 1 outputs

| Artifact | Contents |
|---|---|
| [research.md](research.md) | 17 decisions with alternatives and rationale (R-201 to R-217); the dataset folded and every derived figure recomputed on 2026-11-17; four questions answered by the author that day |
| [data-model.md](data-model.md) | What is read, what is written, the two migrations column by column, the model and the one rule it owns, the service's step order as a rule, and a requirement-to-owner map |
| [contracts/order-submission.openapi.yaml](contracts/order-submission.openapi.yaml) | The design-time proposal: the operation, the request, the order, the five answers and the examples, keyed to requirements |
| [quickstart.md](quickstart.md) | Thirteen validation scenarios with expected figures, all reachable from the provided seed with no fixture, plus what each test level covers |

## Requirement coverage

| Requirement | Where it is satisfied |
|---|---|
| FR-001 | `OrderSubmissionController` + `OrderSubmissionService` |
| FR-002 | `Submission`'s construction, over `OrderSide`'s own answer about cash movements |
| FR-003, FR-004 | `TradeQuantity`'s construction: exactly one of the two, within the columns' bounds |
| FR-005 | `Submission`'s construction: a price exactly when the type is `LIMIT` |
| FR-006 | the request DTO carries no status and no cancel flag |
| FR-007 | `UserRepository.lockById` → 404; `InstrumentRepository.findById` → 400 |
| FR-008 | `InstrumentRef.isCash()`, the same exclusion the search applies |
| FR-009 | `MarketDataRepository.findLatestMarketData` for the one instrument, latest row per instrument |
| FR-010 | **amended**: refused with 400, nothing written ([R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection), ADR-0034) |
| FR-011 | `Submission`'s price answer: a `LIMIT` order never consults the closes |
| FR-012, FR-013 | `AccountSnapshot.availableCash()` and `availableQuantityOf()`, unchanged |
| FR-014 to FR-018 | the one availability comparison, with `DecimalPolicy`'s floor and the price substituted on both sides |
| FR-019 | `RejectionReason` on the order and on its history row |
| FR-020 | the decision's status: `FILLED` for `MARKET`, `NEW` for `LIMIT` |
| FR-021 | one `OrderRepository.save` per processed submission |
| FR-022 | the history insert, same transaction (`V4`) |
| FR-023 | `SubmittedOrder` → `OrderResponse`, `201` for every recorded outcome ([R-202](research.md#r-202-every-recorded-outcome-answers-201-created-a-request-that-writes-nothing-never-does)) |
| FR-024 | `DecimalPolicy` (ADR-0016); the floor is the only rounding of a share count |
| FR-025 | nothing caches: the portfolio folds the ledger on every read |
| FR-026, FR-027 | `UserRepository.lockById`, the transaction's first statement ([R-209](research.md#r-209-one-user-is-serialised-by-a-pessimistic-lock-on-their-users-row)) |
| FR-028 to FR-031 | `OrderIdempotencyRepository.claim` + `RequestFingerprint`, `V5`, and a missing key meaning "process normally" |
| SC-001 to SC-009, SC-011 to SC-013 | [quickstart.md](quickstart.md) scenarios 1-13, asserted by the MockMvc suite |
| SC-010 | the two-thread Testcontainers test of story 5 |
| SC-014 | every rejection and refusal path has a test (constitution V) |

## Complexity Tracking

| Decision | Why needed | Simpler alternative rejected because |
|---|---|---|
| **A pessimistic lock on the user's row around the whole submission** ([R-209](research.md#r-209-one-user-is-serialised-by-a-pessimistic-lock-on-their-users-row)) | Without it every validation in stories 1-4 is advisory: two concurrent requests each read `627,500.00` and together spend `740,680.00`. Constitution IV requires read-and-insert to be serialised before submission is done. | Validating without serialising is the hole ADR-0004 describes. `SERIALIZABLE` with retries needs retry machinery and makes the idempotency path harder to reason about; an advisory lock is invisible from the schema; locking the user's orders locks nothing for a user with none — the exact case where two funded buys race. |
| **An idempotency table, a fingerprint and a claim before the work** ([R-210](research.md#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing)) | A client that loses the response and retries must not end up with two orders (FR-028). Check-then-act is a race; the unique index decides atomically. | A `SELECT` before the insert lets two retries both trade. A fingerprint with no caller key would refuse a legitimate second identical order. Answering every repeat with `409` leaves a timed-out client with no way to learn what happened. |
| **Three statements before the first write** — lock, ledger, latest close | The decision needs the user to exist, the availability the portfolio reports, and the governing price, and all three must describe the state the lock froze. | Folding availability in SQL is a second source of truth (constitution I, ADR-0015). Reading before locking is the race itself. |

## Answered by the author

| # | Question | Answer | What this plan does |
|---|---|---|---|
| Q-201 | Does the endpoint name the user in the path or in the body? | **in the path** | `POST /api/v1/users/{userId}/orders`, symmetric with the portfolio; which also settles that an unknown user is a `404` and anything the body names is a `400` ([R-201](research.md#r-201-the-endpoint-is-post-apiv1usersuseridorders), [R-212](research.md#r-212-the-error-map-400-404-409-503)) |
| Q-202 | How does the idempotency key travel? | **an `Idempotency-Key` header** | The body is exactly what gets fingerprinted, with no field to exclude from the hash ([R-210](research.md#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing)) |
| Q-203 | A `MARKET` order for an instrument with no close has no price to record, and `orders.price` is `NOT NULL`. What goes in the column? | **nothing — refuse the request, write no row** | `400` with a detail naming the instrument; `V3`'s guard stands, no migration relaxes it, and no money figure is written as `0` when it is unknown. The one departure from the statement, so ADR-0034 and the README ([R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)) |
| Q-204 | What status does a stored `REJECTED` order answer with? | **`201`, like the other two** | One status class for the whole business path; `400`, `404`, `409` and `503` mean nothing was written, which is the distinction FR-023 asks for ([R-202](research.md#r-202-every-recorded-outcome-answers-201-created-a-request-that-writes-nothing-never-does)) |

## Deferred, by decision

- **Cancellation** — ADR-0006 and its own feature. This feature produces `NEW` orders it cannot
  retire; the same lock rule will apply to the release of a reservation (constitution IV).
- **Cash transfers** (`CASH_IN` / `CASH_OUT`) — their own endpoint with its own cohesive parameters,
  its own spec and its own decision, as the author argued in the journal. Not this endpoint (FR-002,
  FR-008).
- **A matching engine** — ADR-0007. Nothing here assumes a `LIMIT` order stays `NEW` forever.
- **An order-history endpoint**, the natural reader of `order_status_history`. No requirement behind
  it yet ([R-216](research.md#r-216-no-endpoint-reads-an-order-back-the-portfolio-does)).
- **Expiring idempotency records.** No requirement states a retention window, so the table grows by
  one row per keyed submission and nothing purges it. Stated rather than invented
  ([R-210](research.md#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing)).
- **A `Clock` in the service**, for a test that wants to fix the order's instant. One line, no caller
  today ([R-213](research.md#r-213-the-orders-instant-is-the-servers-supplied-by-the-service)).
- **The `PATCH` constitution amendment** that drops "no price" from principle IV's rejection-reason
  list — flagged above, the author's to run.
