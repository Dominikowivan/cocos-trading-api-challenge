# Phase 1 Data Model: Order Placement

**Feature**: `003-order-placement` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md) |
**Research**: [research.md](research.md)

What this feature reads, what it writes, what it maps each into, and which rule each piece owns. It
describes contracts and rules, not the shape of the code: the names below name the question each
piece answers, never how it answers it (constitution, *Development Workflow*).

This is the first feature that **writes**. Two tables are new, one is appended to, and three are read.

## 1. Given schema

### Read

| Column | Type | Read as | Used for |
|---|---|---|---|
| `users.id` | `SERIAL` | `Integer` | the user exists, and the row this feature locks ([R-209](research.md#r-209-one-user-is-serialised-by-a-pessimistic-lock-on-their-users-row)) |
| `instruments.id` | `SERIAL` | `long` | the instrument named in the body (FR-007) |
| `instruments.ticker`, `name` | `VARCHAR` | `String` | reported back on the order |
| `instruments.type` | `VARCHAR(10)` | `String` | `MONEDA` refuses the request (FR-008) |
| `orders.*` of this user, in `FILLED` and `NEW` | — | `Order` | the fold that produces available cash and available holdings (FR-012, FR-013) |
| `marketdata.close` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent | the execution price of a `MARKET` order (FR-009); absent refuses the request ([R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)) |
| `marketdata.date` | `DATE` | not mapped | picks the latest row **per instrument** (FR-009) |

`marketdata.previousclose`, `high`, `low` and `open` are **not read**: the day's move belongs to the
search, and no rule here needs it.

### Written: `orders`

One row per processed submission, whatever its status (FR-021). Every column `V3` requires is
present — which is what [R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)
buys.

| Column | Written as |
|---|---|
| `id` | the database's, from the `SERIAL` sequence ([R-215](research.md#r-215-orderentity-becomes-writable-with-the-database-generating-the-id)) |
| `userid` | the path's user id, after the lock proved it exists |
| `instrumentid` | the body's instrument id, after it proved to exist and not to be `MONEDA` |
| `size` | the size the order carries: the requested one, or the derived `floor(amount / price)`. **`0` on a rejection that never derived a size** |
| `price` | the price that governed the decision: the latest `close` for `MARKET`, the requested price for `LIMIT`. Never absent, never `0` |
| `type` | `MARKET` or `LIMIT`, as requested |
| `side` | `BUY` or `SELL` only (FR-002) |
| `status` | `FILLED` (accepted `MARKET`), `NEW` (accepted `LIMIT`) or `REJECTED` (FR-019, FR-020) |
| `datetime` | the instant the server processed the submission ([R-213](research.md#r-213-the-orders-instant-is-the-servers-supplied-by-the-service)) |

### Change to the schema

Two migrations. `V1`, `V2` and `V3` are untouched (constitution VI).

**`V4__order_status_history.sql`** — the table ADR-0005 accepted, created by the first feature that
writes a transition ([R-208](research.md#r-208-order_status_history-is-created-by-this-feature-in-migration-v4)).

| Column | Type | Purpose |
|---|---|---|
| `id` | `SERIAL PRIMARY KEY` | surrogate key |
| `order_id` | `INT NOT NULL REFERENCES orders(id)` | the order the transition belongs to |
| `status` | `VARCHAR(20) NOT NULL` | the state the order moved **into** |
| `reason` | `VARCHAR(40)` | why, when it is a rejection; absent otherwise |
| `changed_at` | `TIMESTAMP NOT NULL` | when |

Plus an index on `order_id`: the table's only query is "the history of this order". Rows are inserted,
never updated or deleted; `from_status` is deliberately absent (ADR-0005). No `CHECK` on `status` or
`reason` — the vocabulary is the Java enums' (ADR-0030).

**`V5__order_idempotency.sql`** — only needed by user story 6
([R-210](research.md#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing)).

| Column | Type | Purpose |
|---|---|---|
| `id` | `SERIAL PRIMARY KEY` | surrogate key |
| `userid` | `INT NOT NULL REFERENCES users(id)` | the key's scope (FR-030) |
| `idempotency_key` | `VARCHAR(255) NOT NULL` | what the caller sent |
| `request_fingerprint` | `CHAR(64) NOT NULL` | SHA-256 of the canonicalised request, hex |
| `order_id` | `INT REFERENCES orders(id)` | the order the first request produced; filled in the same transaction, so a reader that arrived by conflict always finds it set |
| `created_at` | `TIMESTAMP NOT NULL` | when the key was claimed |

Plus `UNIQUE (userid, idempotency_key)` — the constraint the whole mechanism rests on: it is what
makes "am I the first?" one atomic question, and it is what a concurrent retry blocks on.

## 2. Persistence

Spring Data JPA repositories over JPA entities, mapped by hand (ADR-0014). Entities never leave the
package (constitution VIII, enforced by `ArchitectureTest`).

| Element | Contract |
|---|---|
| `UserRepository.lockById(id)` | **new**. `SELECT … FOR UPDATE` on the user's row, through `@Lock(PESSIMISTIC_WRITE)`. Answers existence (FR-007) and takes the serialisation lock (FR-026) in one statement. Returns absent when there is no such user |
| `OrderRepository.findOrdersOf` | **exists, unchanged**. The fold's input, reused as-is (FR-013) |
| `OrderRepository.save` | the one append to the ledger per submission (FR-021) |
| `OrderEntity` | **exists, extended**: the id is database-generated and a new row can be built from a decision ([R-215](research.md#r-215-orderentity-becomes-writable-with-the-database-generating-the-id)). Its read mapping is untouched |
| `OrderStatusHistoryEntity` / `OrderStatusHistoryRepository` | **new**. One insert per transition, in the same transaction as the order (FR-022) |
| `OrderIdempotencyRepository` | **new**. `claim(userId, key, fingerprint, now)`: a native `INSERT … ON CONFLICT (userid, idempotency_key) DO NOTHING`, returning how many rows it wrote — `1` means this request is the first, `0` means the key was already used. `findByUser(userId, key)` reads the committed row back, and the order id is attached to it once the order exists |
| `InstrumentRepository.findById` | **exists** (from `JpaRepository`). The instrument named in the body |
| `MarketDataRepository.findLatestMarketData` | **exists, unchanged**. Called with the one instrument being traded (FR-009) |
| `InstrumentMapper.toInstrumentRef` | **new, shared**. The one entity → `InstrumentRef` mapping, called by `InstrumentSearchMapper`, `OrderMapper` and this feature ([R-214](research.md#r-214-one-mapping-from-an-instrument-row-to-an-instrument-shared)) |
| `OrderMapper` | **exists, extended**: a decision → a new `orders` row and its history row; a saved row → what the response reports |
| `MarketDataMapper.toCloses` | **exists, unchanged**; its `Closes` now lives in `model/instrument` ([R-205](research.md#r-205-the-execution-price-is-the-models-choice-not-the-services)) |

**Why the lock is a repository method and not raw SQL in the service**: the service orchestrates and
does not write SQL; "give me this user, and hold it" is a question persistence answers.

## 3. Model (plain Java, no Spring, no JPA, no clock)

### Exists, reused unchanged

| Element | What this feature asks of it |
|---|---|
| `AccountLedger` / `AccountSnapshot` | `availableCash()` for a `BUY`, `availableQuantityOf(instrumentId)` for a `SELL` — the same fold the portfolio reports (FR-012, FR-013, ADR-0015). **Nothing is added to it** |
| `Order`, `OrderSide`, `OrderKind`, `OrderStatus` | the ledger's vocabulary. `OrderSide` gains only the ability to say whether it moves cash rather than an instrument, so `CASH_IN` and `CASH_OUT` can be refused by name (FR-002) |
| `InstrumentRef` | the instrument, and `isCash()` for FR-008 — the same exclusion the search applies |
| `UserId` | the path's user id as a value |
| `DecimalPolicy` | gains one named question: how many whole shares fit in an amount at a price — scale `0`, `FLOOR` (FR-017, [R-207](research.md#r-207-the-derivations-floor-and-the-bounds-are-stated-in-one-place-each)) |
| `Closes` | **moves** from `model/portfolio` to `model/instrument`: it answers a question about the market, not about a portfolio (constitution VIII) |

### New, in `model/submission`

| Element | The question it answers, and the rules it owns |
|---|---|
| `Submission` | "What order does this request become?" Valid by construction: it exists only for a tradable side, an instrument that is not cash, exactly one of a size or an amount, a price when and only when the type is `LIMIT` (FR-003, FR-005). Answers **which price governs** given the latest closes — the requested one for `LIMIT`, the latest `close` for `MARKET`, absent only for a `MARKET` order with no close (FR-009, FR-011, FR-018, [R-205](research.md#r-205-the-execution-price-is-the-models-choice-not-the-services)) — and, given a snapshot and that price, **what the outcome is** |
| `TradeQuantity` | The mutually exclusive pair (FR-003): either an exact whole size or a total amount in pesos, never both and never neither. Answers "what does this need at this price" (`size × price`, or the amount itself) and "what size does it produce at this price" (the size, or `floor(amount / price)`) — the two halves of FR-014 to FR-018. Bounds enforced at construction ([R-207](research.md#r-207-the-derivations-floor-and-the-bounds-are-stated-in-one-place-each)) |
| `OrderDecision` | The outcome as one value: the status, the size, the price, and the reason when rejected. Returned, never written — the service persists it ([R-206](research.md#r-206-one-object-decides-the-outcome-and-it-returns-a-decision-rather-than-writing-one)) |
| `RejectionReason` | Exactly three machine-readable causes: insufficient funds, insufficient holdings, a size below one share (FR-019). "No price" is **not** among them ([R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)) |

**The one rule, written once** (FR-014 to FR-018), for both sides and both types:

| Step | `BUY` | `SELL` |
|---|---|---|
| available | `snapshot.availableCash()` | `snapshot.availableQuantityOf(instrument) × price` |
| needed | `size × price`, or the amount | the same |
| if `available < needed` | `REJECTED`, insufficient funds | `REJECTED`, insufficient holdings |
| size | the requested size, or `floor(amount / price)` | the same |
| if `size == 0` | `REJECTED`, below one share | `REJECTED`, below one share |
| otherwise | `FILLED` if `MARKET`, `NEW` if `LIMIT` | the same |

Two consequences worth stating because tests target them: a holding at or below zero rejects every
`SELL` (FR-015 — `BMA` at **−10** in the dataset), and a `LIMIT` order's price substitutes on *both*
sides, which is why `30,000.00` of `PAMP` sells 30 shares at a limit of `1,000.00` and 32 at the
market's `925.85` (SC-005).

## 4. Service: `service/submission`

| Element | Contract |
|---|---|
| `OrderSubmissionService` | One method, one transaction, **`READ COMMITTED`** ([R-211](research.md#r-211-the-transaction-is-read-committed-deliberately-unlike-the-reads)), not read-only. It orchestrates and decides nothing: check the user exists (a non-locking read on the primary key) → claim the key if there is one → lock the user → read the ledger and the latest close → let the model decide → append the order and its history row → attach the order to the key → return what happened. A claim that wrote nothing skips straight to the replay and never takes the lock ([ADR-0037](../../docs/adr/0037-idempotent-submission-with-a-caller-key.md)) |
| `SubmissionRequest` | What the controller hands over, raw: the user id, the instrument id, the type and side as text, the size, the amount, the price, and the key. Nothing is validated yet — validating it against the rules is the use case's job (constitution VIII) |
| `SubmittedOrder` | What the controller gets back: the order's id, its user and instrument, side, type, status, size, price, reason and instant. No DTO reaches this signature |
| `InvalidSubmissionException` | The one refusal that writes nothing, naming the rule it broke and the allowed values — `400` ([R-212](research.md#r-212-the-error-map-400-404-409-503)) |
| `UnknownSubmissionUserException` | No user with that id — `404`. Its own exception, because a use case may not depend on another's (`ArchitectureTest`) |
| `IdempotencyConflictException` | The key was used for a different request — `409` (FR-029) |
| `RequestFingerprint` | The canonical form of a request and its SHA-256, in one place, so the hash a retry computes cannot drift from the hash the first request stored |

**The order of the steps is itself a rule**: the key is claimed before any work, so a retry that
blocks on the unique index holds no row lock and a replay never takes one; the user's existence is
read before the claim, because the claim's `userid` is a foreign key and an unknown user must still
be a `404`; the user lock is taken before the ledger is read, so the figures the decision uses cannot
move under it; and the order, the history row and the key's `order_id` are written in the one
transaction, so a rollback leaves no trace and a retry of a rolled-back request is a first request
again.

## 5. Controller

| Element | Contract |
|---|---|
| `OrderSubmissionController` | `POST /api/v1/users/{userId}/orders` ([R-201](research.md#r-201-the-endpoint-is-post-apiv1usersuseridorders)). Reads the path variable, the body DTO and the optional `Idempotency-Key` header, builds one `SubmissionRequest`, calls one service method, maps the result to a DTO, answers `201` ([R-202](research.md#r-202-every-recorded-outcome-answers-201-created-a-request-that-writes-nothing-never-does)). It reads no configuration and interprets no rule |
| `OrderSubmissionRequest` (DTO) | The body: `instrumentId`, `type`, `side`, `size`, `amount`, `price`. `type` and `side` are **text**, so the use case can refuse an unknown value with a message that names what was allowed instead of failing in the JSON parser |
| `OrderResponse` (DTO) | The order as the contract declares it; an absent reason is JSON `null` |
| `ApiExceptionHandler` | **exists, extended**: the three new exceptions → `400`, `404`, `409`, and a malformed body → `400`. The `503` mapping is unchanged (ADR-0031) and now also covers a submission that waited too long on another submission's lock |

## 6. Requirement-to-owner map

| Requirement | Where it is satisfied |
|---|---|
| FR-001 | `OrderSubmissionController` + `OrderSubmissionService` |
| FR-002 | `Submission`'s construction, over `OrderSide`'s own answer about cash movements |
| FR-003, FR-004 | `TradeQuantity`'s construction |
| FR-005 | `Submission`'s construction: a price exactly when the type is `LIMIT` |
| FR-006 | The request DTO has no status and no cancel flag |
| FR-007 | `UserRepository.lockById` (404) and `InstrumentRepository.findById` (400) |
| FR-008 | `InstrumentRef.isCash()`, refused before anything is read |
| FR-009 | `MarketDataRepository.findLatestMarketData` for the one instrument, latest row per instrument |
| FR-010 | **Amended**: refused as an invalid request, nothing written ([R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection), ADR-0034) |
| FR-011 | `Submission`'s price answer: `LIMIT` never consults the closes |
| FR-012, FR-013 | `AccountSnapshot.availableCash()` / `availableQuantityOf`, from `AccountLedger` |
| FR-014, FR-015 | The decision's available-versus-needed comparison, exact-size path |
| FR-016, FR-017, FR-018 | The same comparison, amount path, with `DecimalPolicy`'s floor and the price substituted on both sides |
| FR-019 | `RejectionReason` on the order and on its history row |
| FR-020 | The decision's status: `FILLED` for `MARKET`, `NEW` for `LIMIT` |
| FR-021 | One `OrderRepository.save` per processed submission |
| FR-022 | The history insert, same transaction (`V4`) |
| FR-023 | `SubmittedOrder` → `OrderResponse`, `201` for every recorded outcome |
| FR-024 | `DecimalPolicy` (ADR-0016); the floor is the only rounding of a share count |
| FR-025 | Nothing caches: the portfolio folds the ledger on every read |
| FR-026, FR-027 | `UserRepository.lockById`, first statement of the transaction |
| FR-028 to FR-031 | `OrderIdempotencyRepository.claim` + `RequestFingerprint`, `V5`, and the key's absence meaning "process normally" |

## 7. Decisions to record as ADRs

Constitution VII: a decision with alternatives worth weighing is an ADR, and `docs/adr/README.md` is
updated in the same change.

| ADR | Decision | Research |
|---|---|---|
| 0033 | The submission endpoint's shape: under the user, `201` for every recorded outcome, `400` for a request that writes nothing | [R-201](research.md#r-201-the-endpoint-is-post-apiv1usersuseridorders), [R-202](research.md#r-202-every-recorded-outcome-answers-201-created-a-request-that-writes-nothing-never-does), [R-212](research.md#r-212-the-error-map-400-404-409-503) |
| 0034 | An instrument with no price refuses the submission instead of recording a `REJECTED` order — **a departure from the statement, so the README says so** | [R-203](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection) |
| 0035 | One user's submissions are serialised by a pessimistic lock on their `users` row, at `READ COMMITTED` | [R-209](research.md#r-209-one-user-is-serialised-by-a-pessimistic-lock-on-their-users-row), [R-211](research.md#r-211-the-transaction-is-read-committed-deliberately-unlike-the-reads) |
| 0036 | Idempotency: a caller key in a header, a request fingerprint, and `INSERT … ON CONFLICT DO NOTHING` | [R-210](research.md#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing) |
