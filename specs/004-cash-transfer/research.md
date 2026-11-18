# Research: Cash Transfer

**Feature**: `004-cash-transfer` | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Phase 0 of the plan. Every decision below had an alternative worth weighing; each states what was
chosen, why, and what was rejected. Decisions are numbered `R-301` onwards (feature 003 used
`R-2xx`). Three of them were put to the author on 2026-11-17 and are marked **(author)**.

## Measured facts this phase relied on

Measured on 2026-11-17 against the repository at `fc037b2`. Nothing here is estimated.

| Fact | Where it was measured | Consequence |
|---|---|---|
| The seed was last changed on 2026-11-16 | `git log -1 -- src/main/resources/db/seed/` | the spec's figures (measured for feature 003) still hold |
| User `1`: settled cash `753,000.00`, reserved by two `NEW BUY LIMIT` orders `35,500.00 + 90,000.00`, available `627,500.00` | folding the eleven seeded orders by hand, the way `AccountLedger` does | the funds boundary of SC-002 and SC-003 |
| Users `2`, `3`, `4` have no orders | `R__given_dataset.sql`: every seeded order has `userId = 1` | the "user with no orders" of every story, no fixture needed |
| `ARS` / `PESOS` is the only `MONEDA` instrument, id `66` | `R__given_dataset.sql` | the instrument a transfer is recorded on |
| The seed's two transfers: `(66, 1, 1000000, 1, 'CASH_IN', 'FILLED', 'MARKET')` and `(66, 1, 100000, 1, 'CASH_OUT', 'FILLED', 'MARKET')` | `R__given_dataset.sql` | the row shape this feature writes ([R-303](#r-303-a-transfer-is-the-seeds-row-a-market-order-on-the-moneda-instrument-at-price-1)) |
| `orders.size INT`, `orders.price NUMERIC(10,2)`, `orders.side VARCHAR(10)`, `orders.type VARCHAR(10)` | `V1__given_schema.sql`; `V3` adds `NOT NULL` | `CASH_OUT` (8 characters) fits; the amount ceiling is `2,147,483,647` |
| `PAMP` (id `47`) last close `925.85` | `R__given_dataset.sql` | `400 PAMP` costs `370,340.00`; `100 PAMP` costs `92,585.00` |
| `order_idempotency` has `UNIQUE (userid, idempotency_key)` and no column naming the endpoint | `V5__order_idempotency.sql` | one key namespace per user across both endpoints comes for free ([R-310](#r-310-idempotency-the-same-header-the-same-table-a-fingerprint-that-names-the-operation)) |
| The read model `Order` already takes a `LedgerSide` (`OrderSide` or `CashMovement`); the write model `NewOrder` takes only `OrderSide`; `OrderMapper.toPlacedOrder` reads the side back with `OrderSide.valueOf` | `model/order/Order.java`, `NewOrder.java`, `persistence/mapper/OrderMapper.java` | the write path must widen to `LedgerSide` ([R-307](#r-307-the-write-model-takes-a-ledgerside-as-the-read-model-already-does)) |
| Order submission **ignores** unknown body fields (`status`, `cancel`) | `SubmissionBodyFieldsTest` | the transfer endpoint refuses them instead, a stated asymmetry ([R-305](#r-305-the-body-is-exactly-side-and-amount-any-other-field-is-refused)) |
| `ApiExceptionHandler.malformedBody` answers "not a readable order submission" for every endpoint; no test or contract asserts that text | `grep` over `src/` and `docs/api/` | the detail becomes endpoint-neutral with no behaviour change ([R-312](#r-312-the-error-map-is-order-submissions-400-404-409-503)) |

## R-301: The endpoint is `POST /api/v1/users/{userId}/cash-transfers` **(author)**

- **Decision**: `POST /api/v1/users/{userId}/cash-transfers`, the user in the path as in
  ADR-0033. Every recorded outcome — a `FILLED` deposit, a `FILLED` or `REJECTED` withdrawal —
  answers `201 Created`.
- **Rationale**: the user scope lives where it lives for the portfolio and for orders, so an
  unknown user is a missing target (`404`) and everything in the body is a `400`. `cash-transfers`
  names what moves: the journal rules out transfers between users and between accounts, and a bare
  `transfers` would read as exactly those. `201` for a rejection follows ADR-0033's reasoning
  unchanged — the row was created either way.
- **Alternatives considered**: `/transfers` (ambiguous with the excluded transfer kinds); `/cash`
  with the direction in the body (the resource would be the balance, which has no row — ADR-0002);
  `POST /orders` accepting `CASH_IN`/`CASH_OUT` (the author already rejected mixing them into
  submission in `docs/journal/07OrderPlacement.md`, and submission refuses them, FR-002 of 003);
  two endpoints, `/deposits` and `/withdrawals` (the journal asks for one with a side).

## R-302: The response is the transfer's own shape, not `OrderResponse` **(author)**

- **Decision**: the body is `orderId`, `userId`, `side`, `amount`, `status`, `rejectionReason`
  (`null` when accepted) and `transferredAt`. `amount` is reported at money scale (ADR-0016).
- **Rationale**: a transfer is stored as an order on `ARS` at price `1` ([R-303](#r-303-a-transfer-is-the-seeds-row-a-market-order-on-the-moneda-instrument-at-price-1)).
  That is how the ledger records it, not something the caller asked for: the caller never named an
  instrument, a type or a price, so it should never have to read them back. `orderId` keeps the name
  because it *is* the order's id — the idempotency replay and the ledger both speak of orders.
- **Alternatives considered**: reusing `OrderResponse` (one schema fewer in the contract, but
  `ticker: ARS`, `type: MARKET`, `size` and `price: 1` leak the modelling trick, and a client would
  have to know that `size` means pesos here); calling the id `transferId` (a second name for the same
  key, which the replay and the portfolio would not use).

## R-303: A transfer is the seed's row: a `MARKET` order on the `MONEDA` instrument at price `1`

- **Decision**: one row in `orders` with `instrumentId` = the `MONEDA` instrument, `side` =
  `CASH_IN`/`CASH_OUT`, `type = MARKET`, `size` = the amount in whole pesos, `price = 1.00`, status
  `FILLED` or `REJECTED`. The `MONEDA` instrument is **looked up by type**, not by the literal id
  `66`; the lookup expects exactly one and treats zero or several as a fault of the deployment's data
  (a `500`, logged), never as the caller's fault.
- **Rationale**: it is the shape the provided data already uses, so `Order.cashEffect()` (`sign ×
  size × price`) counts it, `Order.movesPosition()` already excludes the cash instrument, and the
  portfolio needs no change (FR-006, FR-012). The id is the database's, not a constant of the domain:
  `InstrumentRef.CASH` already names the type, and that is what identifies cash everywhere else
  (`isCash()`, the search's exclusion).
- **Alternatives considered**: `size = 1`, `price = amount` (allows centavos, but caps a transfer at
  `99,999,999.99` and departs from the seed; rejected with the author's whole-pesos clarification);
  size in centavos at price `0.01` (caps at `21,474,836.47` and makes `size` mean something different
  per instrument); hardcoding id `66` (a fact of one dataset, wrong on any other); the instrument in
  the request (the author confirmed it is implicit; a second currency would add it).

## R-304: The amount is whole pesos, from `1` to `2,147,483,647`, never rounded

- **Decision**: the amount must be present, a whole number (`100` and `100.00` are both one hundred
  pesos; `100.50` is refused), at least `1` and at most the largest `INT`. A violation is a `400`
  naming the rule, with nothing written. The bound is the column's capacity, so it is stated once in
  the model, not as configuration (the reasoning of 003's R-207).
- **Rationale**: the author clarified whole pesos on 2026-11-17; `orders.size` is an `INT`.
  Accepting trailing zeros mirrors how `TradeQuantity` reads a size, so `100.00` is not a surprise
  refusal; rounding `100.50` would move money the caller did not ask to move.
- **Alternatives considered**: rounding to the peso (silently changes the amount); refusing `100.00`
  (a JSON client that always writes two decimals could never deposit); configurable maximum (a policy
  nobody stated — the spec rules out caps).

## R-305: The body is exactly `side` and `amount`; any other field is refused

- **Decision**: a body carrying any field other than `side` and `amount` is refused with `400`,
  naming the field. This covers FR-004's five (instrument, type, price, size, status) and anything
  else.
- **Rationale**: FR-004 requires refusing, not ignoring, and a closed list of five would still
  silently ignore a sixth (`currency`, `userId`) that the caller believed meant something. With two
  fields, a closed body is the simplest rule that satisfies it. Order submission keeps ignoring
  unknown fields (`SubmissionBodyFieldsTest`); FR-019 forbids changing it here, and the asymmetry is
  stated in the contract rather than hidden. Where the check lives is an implementation choice; the
  rule — a request naming a field outside the transfer's shape is refused by the use case, not by
  annotations on the DTO — follows ADR-0033's rejection of bean validation.
- **Alternatives considered**: refusing only the five named fields (leaves the hole above); turning
  on Jackson's fail-on-unknown globally (changes order submission's behaviour, which FR-019 forbids);
  ignoring them like submission does (contradicts FR-004).

## R-306: The model decides; the deposit decision is unconditional

- **Decision**: a new concept package, `model/transfer`, holds a transfer that is valid by
  construction (a `CashMovement` and a whole-peso amount) and answers what order it becomes against an
  `AccountSnapshot`: `CASH_IN` → `FILLED`; `CASH_OUT` → `FILLED` when `availableCash()` is **at
  least** the amount (`compareTo(...) >= 0`), else `REJECTED` with `INSUFFICIENT_FUNDS`. It returns
  the order to write and writes nothing.
- **Rationale**: the same split as submission (003's R-206): rules in plain Java, testable with no
  database, the service persisting what the model returns. `INSUFFICIENT_FUNDS` already exists, so
  constitution IV's list of reasons does not grow. Every transfer reads the fold, deposits included:
  one path, one step order (which [R-311](#r-311-the-shared-protection-lives-in-serviceuserwrite-author)
  extracts), and the cost of one read was not measured to matter (constitution IX).
- **Alternatives considered**: putting the comparison in the service (a rule outside the model);
  reusing `PricedSubmission` with a cash "instrument" (submission refuses `MONEDA` by construction and
  its amount/size duality means nothing here); skipping the fold for deposits (a second path, chosen
  for a performance nobody measured).

## R-307: The write model takes a `LedgerSide`, as the read model already does

- **Decision**: `NewOrder.side` widens from `OrderSide` to `LedgerSide`; `LedgerSide` declares
  `name()` (both enums already have it); `OrderMapper.toPlacedOrder` reads the side back through the
  existing `toLedgerSide`, so a stored `CASH_IN` row can be read back for a replay. Submission's own
  types are unchanged in behaviour; `SubmittedOrder` carries the wider type and `OrderResponse` still
  writes `side().name()`.
- **Rationale**: `NewOrder` is "a row about to enter the ledger", and the ledger already holds both
  kinds of side — `Order` (the read model) takes `LedgerSide` for exactly that reason. One write path
  means one `append`, one history row mapping and one read-back for both endpoints (FR-010).
- **Alternatives considered**: a separate `NewTransfer` record with its own mapper and history
  mapping (a second write path for the same table, duplicating the rule that every row gets a
  history row); making `CashMovement` values of `OrderSide` (submission would then have to refuse its
  own enum's values, undoing the split `LedgerSide` draws between trading and moving cash).

## R-308: Available cash is the fold the portfolio reports, unchanged

- **Decision**: `new AccountLedger(orders).snapshot().availableCash()`, over the user's contributing
  orders, exactly as submission reads it (003's R-204). Nothing is added to the fold.
- **Rationale**: FR-009 and SC-007 hold by construction (ADR-0015, constitution I). Settled cash minus
  `NEW BUY` reservations is already what `availableCash()` returns; `NEW SELL` reserves shares, so it
  does not limit a withdrawal — the spec's edge case, true with no new code.
- **Alternatives considered**: a `SUM` in SQL (a second source of truth); settled cash (contradicts
  ADR-0004 and SC-003).

## R-309: Serialisation is submission's lock, on the same row (US2)

- **Decision**: the transfer's transaction runs at `READ COMMITTED` and takes
  `UserRepository.lockById` — `SELECT … FOR UPDATE` on the user's `users` row — before reading the
  fold, as ADR-0036 decided for submission. No new lock, no new decision.
- **Rationale**: FR-014 requires transfers and orders to share **one** serialisation per user. The
  same row is that serialisation: a withdrawal and a `MARKET BUY` for user `1` queue on the same lock
  and the second sees what the first committed (`READ COMMITTED`, ADR-0036's rationale). Different
  users lock different rows.
- **Alternatives considered**: a separate lock for transfers (would let a withdrawal and a purchase
  race — the exact failure of US2 scenario 2); deposits skipping the lock (safe in isolation, but a
  second step order to maintain and to extract).

## R-310: Idempotency: the same header, the same table, a fingerprint that names the operation

- **Decision**: an optional `Idempotency-Key` header, claimed in `order_idempotency` with the same
  `INSERT … ON CONFLICT DO NOTHING`, before the lock, as ADR-0037 decided. The transfer's canonical
  form **begins with an operation tag** — `operation=CASH_TRANSFER;userId=…;side=…;amount=…`, the
  amount as whole pesos — and is hashed with SHA-256. Order submission's canonical form is **not
  changed**. A replay reads the order the key names and its reason from `order_status_history`, as
  submission's does.
- **Rationale**: the table's unique `(userid, idempotency_key)` already makes one namespace per user
  across both endpoints (FR-016, confirmed by the author). The operation tag guarantees a transfer's
  form can never equal an order's, so a key used on the other endpoint always fails the fingerprint
  comparison and answers `409` — before anything reads the other endpoint's order. Leaving the order
  form untouched keeps every fingerprint already stored valid (FR-019). Normalising the amount to
  whole pesos makes `100` and `100.00` the same request, as `900` and `900.00` are for orders.
- **Alternatives considered**: an `operation` column in `order_idempotency` (a migration to encode
  what the fingerprint already distinguishes); two tables (two namespaces, which the author rejected);
  tagging the order form too (changes stored fingerprints, a behaviour change FR-019 forbids).

## R-311: The shared protection lives in `service/userwrite` **(author)**

- **Decision**: after US2 and US3, the per-user write protection is extracted (US4) into one
  subpackage of `service` that is **not a use case**: `service/userwrite` (name proposed; final in the
  ADR). It holds one Spring `@Component` — not a `@Service` — that runs a user's write inside the
  caller's transaction (it requires one, and refuses to start its own) and owns, once:
  1. that the user exists (non-locking read → `404`);
  2. the claim of the key, when one was sent, and its outcome — attach the order, replay the recorded
     outcome, or `409`;
  3. the lock on the user's row, taken after the claim and before the work.

  Each use case keeps what is its own: its canonical form, its work (read, decide, append), and how
  it reads a recorded order back into its own response. The key type, the conflict exception and the
  hashing of a canonical form move there. The step order is ADR-0037's, unchanged.

  This needs, in the same change: an **ADR**, a **MINOR constitution amendment** to principle VIII
  (`service` may hold one shared, non-use-case subpackage for write protections; use cases may
  depend on it and it on none of them), and the ArchUnit slice rule adjusted to exactly that —
  plus a new rule that only `service/userwrite` calls `lockById` and `claim`, which is what makes
  SC-008's "exactly one place" checked rather than asserted.
- **Rationale**: the lock and the claim are orchestration (a transaction's statements in a required
  order), and constitution VIII puts orchestration in the service layer. A use case may not depend
  on another, so the shared code cannot live in `service/submission` or `service/transfer`; a named
  subpackage keeps it out of the root, which VIII forbids. Requiring the caller's transaction keeps
  ADR-0037's consequence — a refused request rolls its claim back — true by construction.
- **Alternatives considered** (put to the author): persistence (the lock and the claim already are
  repository methods, but the claim → work → attach / replay → conflict flow is orchestration, which
  VIII keeps out of persistence); a new top-level package outside the four layers (outside every
  ArchUnit layer rule unless a fifth layer is added — a larger change for one class); an AOP
  annotation (`@SerialisedPerUser`) (invisible at the call site and broken by self-invocation);
  extracting first and building US2/US3 on it (contradicts the journal's sequence: "primero se hace
  el endpoint… después… ABSTRAEMOS").

## R-312: The error map is order submission's: 400, 404, 409, 503

- **Decision**: `400` for an invalid side, a missing / fractional / out-of-range amount, a field
  outside the body's shape, a blank or over-long key, or an unreadable body; `404` for an unknown user;
  `409` for a key reused with a different request (including a key used for an order); `503` for a
  deadline or pool exhaustion (ADR-0031, unchanged). The body's shape is judged before the user is
  looked up, so a malformed request for an unknown user answers `400` (ADR-0033's precedence). The
  unreadable-body detail becomes endpoint-neutral, since it is now shared by two endpoints and no test
  or contract asserts its text.
- **Rationale**: the same distinction as ADR-0033 — `201` means a row was written, anything else
  means nothing was — so a client handles both write endpoints the same way.
- **Alternatives considered**: `422` for a `REJECTED` withdrawal (rejected by ADR-0033 for orders,
  for the same reason); a separate error vocabulary for transfers (two maps for one API).

## R-313: No migration

- **Decision**: the feature writes `orders`, `order_status_history` and `order_idempotency`, all of
  which exist (`V1`, `V4`, `V5`). No column, index or constraint is added.
- **Rationale**: every column the row needs exists and is wide enough (measured above); the unique
  index already scopes keys per user; the user's orders are already read through `V2`'s index.
- **Alternatives considered**: an `operation` column on `order_idempotency` (see R-310); a
  `CHECK (side IN …)` (business vocabulary in the schema, against ADR-0030).

## R-314: What this feature deliberately does not build

- A second currency, transfers between users or accounts, bank accounts, origins of a deposit, caps,
  anti-fraud limits — out of scope by the journal and the spec.
- An endpoint that lists transfers or reads one back: the portfolio shows their effect, as for orders
  (003's R-216).
- Cancelling a transfer: a transfer is never `NEW`, so there is nothing to cancel.
- Expiring idempotency records: unchanged from ADR-0037.
