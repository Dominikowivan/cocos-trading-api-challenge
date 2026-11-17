# Research: Order Cancellation

**Feature**: `005-order-cancellation` | **Plan**: [plan.md](plan.md) | **Spec**: [spec.md](spec.md)

Phase 0 of the plan. Every decision below had an alternative worth weighing; each states what was
chosen, why, and what was rejected. Decisions are numbered `R-401` onwards (feature 004 used
`R-3xx`). Three of them were put to the author on 2026-11-17 and are marked **(author)**.

## Measured facts this phase relied on

Measured on 2026-11-17 against the repository at `6d055e6`. Nothing here is estimated.

| Fact | Where it was measured | Consequence |
|---|---|---|
| Seeded orders `5` (`LOMA`, id `45`, `50 × 710`, `BUY LIMIT NEW`) and `7` (`BMA`, id `31`, `60 × 1500`, `BUY LIMIT NEW`); `3` `CANCELLED`, `6` `REJECTED`, the rest `FILLED`; all of user `1` | `R__given_dataset.sql`, lines 224–235; tickers by insertion order of `instruments` (`RESTART IDENTITY`) | the spec's figures hold; `35,500.00` and `90,000.00` are what the two cancellations release |
| User `1` holds `40` `PAMP` (id `47`: `+50 −10`); `PAMP` last closed at `925.85` | `R__given_dataset.sql` | the `SELL LIMIT` of US1 scenario 4 is `40 PAMP`; the `MARKET SELL` it unblocks fills for `37,034.00` |
| The seed truncates `orders` with `CASCADE`, so **no seeded order has a row in `order_status_history`** | `R__given_dataset.sql` line 1; `V4` references `orders(id)` | cancelling a seeded order writes its first and only history row; an order's history may start at any status |
| `OrderStatus.contributing()` is `FILLED` and `NEW`; `Order.reservesCash()` / `reservesShares()` require `NEW` | `model/order/OrderStatus.java`, `Order.java` | a `CANCELLED` order drops out of the fold: the release needs no change to the ledger ([R-409](#r-409-the-release-is-the-fold-unchanged)) |
| `OrderStatusHistoryRepository.reasonOf` maps **every** non-null `reason` through `RejectionReason.valueOf`, with no order on the rows | `persistence/OrderStatusHistoryRepository.java` | once a `USER_REQUESTED` row exists, a keyed submission replay of that order throws `IllegalArgumentException` → `500` ([R-406](#r-406-the-historys-reason-holds-why-the-order-moved-not-only-rejections)) |
| Both replays (`OrderSubmissionService.recordedOrder`, `CashTransferService.recordedTransfer`) read the order's **current** `orders.status` | the two services | after a cancellation a submission replay would answer `CANCELLED`, not its first outcome ([R-407](#r-407-a-replay-answers-the-outcome-as-first-recorded-author)) |
| `order_idempotency.order_id` has **no** unique constraint | `V5__order_idempotency.sql` | a cancellation's key may name an order another key already names (the submission's) |
| `UserWrite.write` takes an `IntFunction<T extends AppendedOrder>` work and replay; `AppendedOrder` exposes only `orderId()` | `service/userwrite/` | cancellation calls it as is; the result names the order it cancelled ([R-404](#r-404-the-protections-are-userwrites-unchanged)) |
| `OrderEntity` has no setter; there is no update path for `orders` | `persistence/entity/OrderEntity.java`, `OrderRepository.java` | the transition needs a new persistence method ([R-405](#r-405-the-transition-is-a-guarded-update-and-one-history-row)) |
| A `userId` of `0` or below reaches `UserWrite`'s existence read and answers `404` on the other write endpoints; the contract declares `minimum: 1` | `UserWrite.requireAnExistingUser`, `docs/api/openapi.yaml` | FR-002 makes cancellation answer `400` instead: a stated asymmetry ([R-403](#r-403-ids-are-validated-before-any-lookup)) |
| The live contract's `Order.status` enum is `[FILLED, NEW, REJECTED]` and says "`CANCELLED` never originates here" | `docs/api/openapi.yaml` | the schema widens to admit `CANCELLED` when cancellation reuses it ([R-402](#r-402-a-cancellation-answers-200-with-the-order-author)) |
| `order_status_history.reason` is `VARCHAR(40)` | `V4__order_status_history.sql` | `USER_REQUESTED` (14) fits; no migration |
| The journal writes the path with a trailing slash; no endpoint of the API has one | `docs/journal/09CancelOrder.md`, controllers | [R-401](#r-401-the-endpoint-is-post-apiv1usersuseridordersorderidcancel) |

## R-401: The endpoint is `POST /api/v1/users/{userId}/orders/{orderId}/cancel`

- **Decision**: `POST /api/v1/users/{userId}/orders/{orderId}/cancel`, no body, optional
  `Idempotency-Key` header. **No trailing slash**, as every other endpoint: `/cancel/` is not a
  second spelling of it. Whether the framework answers `/cancel/` with `404` is checked by a test, not
  assumed. This supersedes the path of ADR-0006, which predates ADR-0033 putting the user in every
  write path; the rest of ADR-0006 — a dedicated endpoint, a `POST` action, a transition and not a
  deletion — stands and is restated in the new ADR.
- **Rationale**: the author's journal, for cohesion with submission (`/users/{userId}/orders`). The
  user in the path gives the lock and the scoped lookup their key without a read to find the owner,
  and makes an unknown user a missing target exactly as on the other write endpoints. A trailing
  slash written by the journal is read as prose, not as a second route: one path per operation keeps
  the contract and `trading-api.http` single-valued.
- **Alternatives considered**: ADR-0006's `POST /api/v1/orders/{orderId}/cancel` (the owner would be
  read from the order, the lock taken after a read, and a foreign order could only be refused after
  learning whose it is); `PATCH /orders/{orderId}` with `{"status": "CANCELLED"}` (a generic status
  write invites every other transition, and the endpoint would have to refuse all but one);
  `PUT …/status` (same objection); `DELETE /orders/{orderId}` (already rejected by ADR-0006: the order
  is not deleted); accepting both `/cancel` and `/cancel/` (two spellings in the contract for no
  caller who needs them).

## R-402: A cancellation answers `200` with the order **(author)**

- **Decision**: a successful cancellation answers **`200 OK`** with the **same `Order` schema order
  submission answers**, carrying `status: CANCELLED` and `rejectionReason: null`. Every other field —
  instrument, ticker, type, side, size, price, `submittedAt` — is the order's, unchanged (FR-009). The
  contract's `Order.status` enum gains `CANCELLED`, and its description stops saying `CANCELLED` never
  appears there.
- **Rationale**: nothing is created — the order already exists, and the history row is not a resource
  the API exposes — so `201` would claim a creation that did not happen. One schema for "an order"
  means a client parses submissions and cancellations the same way. The response describes the order,
  not the act: when it was cancelled is in the history, which the API does not expose today
  ([R-412](#r-412-what-this-feature-deliberately-does-not-build)).
- **Alternatives considered**: `204 No Content` (the client loses the confirmation it would otherwise
  have to fetch, and there is no endpoint to fetch it from); `201` (no resource created); the
  cancellation's own shape — `orderId`, `status`, `cancelledAt`, `reason` — as the transfer has its
  own (a second schema for the same order; the transfer's own shape existed to hide a modelling trick,
  and there is none here).

## R-403: Ids are validated before any lookup

- **Decision**: the use case reads the request first. A `userId` or `orderId` of `0` or below is
  refused with **`400`** naming the id, before the user's existence is read (FR-002). A non-numeric id
  is already a `400` from the framework's conversion (`MethodArgumentTypeMismatchException`). An
  `orderId` above the `orders.id` column's range (`INT`) is a positive whole number that cannot exist:
  it is a **missing target, `404`**, the same treatment `UserMapper` gives an out-of-range user id.
- **Rationale**: FR-002 is the spec's, and the contract already declares `minimum: 1`. The other
  write endpoints answer `404` for a `userId` of `0`, because nothing validates it before the
  existence read: changing them is outside this feature (the spec's FR-016 keeps the shared
  protections as they are), so the asymmetry is recorded in the plan's deferred list rather than
  hidden.
- **Alternatives considered**: `404` for non-positive ids, like the other endpoints (contradicts
  FR-002 and the contract's `minimum`); fixing the other two endpoints in this feature (a behaviour
  change to two endpoints nobody asked for here, with its own tests); `400` for an `orderId` above
  `INT` (it is well-formed by the contract, which has no `maximum`; it simply names nothing).

## R-404: The protections are `UserWrite`'s, unchanged

- **Decision**: the cancellation calls `UserWrite.write(userId, keyed, work, replay)`: existence
  (non-locking → `404`), then the claim when a key was sent, then the user's row lock, then the work —
  ADR-0040's order, unchanged. The work runs under the lock; the replay runs when the claim was lost
  and the fingerprints agree. The cancellation's result names **the order it cancelled**, which is
  what `UserWrite` attaches to the key.
- **Rationale**: FR-010 and FR-016 require the shared protection, and ADR-0040 already names
  cancellation as its third user. The lock serialises the status read with every other write of the
  user, so two cancellations of one order queue and the second reads `CANCELLED` (FR-011), and a
  cancellation and a withdrawal see each other's committed result (`READ COMMITTED`, ADR-0036). The
  order lookup happens **inside** the work, under the lock: reading it before would decide on a status
  another request may be about to change.
- **`AppendedOrder` is a misnomer for this caller**: a cancellation appends no order. The interface's
  contract — "the id of the order this write is about, to attach to its key" — fits; its name does not
  (constitution IX: names state intent). The refactor step of US3 renames it to say what it is
  (proposed: `WrittenOrder`); the rename touches the two existing results and nothing else.
- **Alternatives considered**: calling `UserLock` and `IdempotentWrite` directly (the composition's
  order would then live twice); looking the order up before `UserWrite` to fail fast on a foreign
  order (a read outside the lock, and a `404` for the order that would precede the user's `404`,
  inverting the precedence every write endpoint has).

## R-405: The transition is a guarded update and one history row

- **Decision**: under the lock, the use case reads the order **scoped by user and id** (one lookup,
  FR-004), asks the model whether it can be cancelled, and, when it can, writes two statements in the
  use case's transaction:
  1. `orders.status` set to `CANCELLED` **where the id is the order's and the status is still the one
     the model approved** (`NEW`) — a compare-and-set that reports how many rows it changed;
  2. one `order_status_history` row: `CANCELLED`, reason `USER_REQUESTED`, `changed_at` = the server's
     instant (FR-006).

  If the guarded update changes **zero** rows, the order left `NEW` between the read and the write
  without passing through the user's lock: the request is refused as not cancellable (`409`), and the
  transaction rolls back. No other column of `orders` is written.
- **Rationale**: the lock already makes the guard redundant for every writer that exists today; the
  guard is what keeps FR-011 true for a writer that does not take the user's lock — a matching engine
  filling a `LIMIT` order, which the constitution says features must not assume away. It is a
  concurrency guard on the status the model read, not a business rule: which statuses may be cancelled
  is decided in Java (constitution III), and the SQL only states "only if it is still what I read".
- **Alternatives considered**: loading the entity and setting its status through JPA dirty checking
  (needs a mutable entity, and an unguarded write); a plain `UPDATE … WHERE id = ?` (correct under the
  lock alone, silently wrong the day another writer exists); `SELECT … FOR UPDATE` on the order row as
  well as the user's (two locks to order, for a guard the compare-and-set gives in one statement);
  a `CHECK` or trigger on transitions (business rules in the database, against constitution III and
  ADR-0030).

## R-406: The history's `reason` holds why the order moved, not only rejections

- **Decision**: `order_status_history.reason` holds the machine-readable reason **of the transition
  in its row**: a `RejectionReason` on a `REJECTED` row, `USER_REQUESTED` on a `CANCELLED` row, `null`
  on `NEW` and `FILLED`. In the model the two vocabularies stay **separate types** — the existing
  `RejectionReason` and a new `CancellationReason` with the single value `USER_REQUESTED` — and the
  persistence mapper reads the column according to the row's status. `reasonOf`, which today maps any
  non-null reason as a rejection, reads only the reason of a `REJECTED` row. Constitution IV's list of
  rejection reasons does not grow.
- **Rationale**: the journal fixes the value; ADR-0005 described the column as "why, when it is a
  rejection", so admitting a cancellation's reason extends what ADR-0005 decided and is recorded as a
  new ADR (constitution VII: accepted ADRs are never edited). Two types keep a rejection from ever
  carrying `USER_REQUESTED`, which one merged enum would allow. Reading by status is what fixes the
  measured `500` of a replay over a cancelled order.
- **Alternatives considered**: one `TransitionReason` enum with every value (a `NewOrder` could be
  "rejected for `USER_REQUESTED`"); a second column `cancellation_reason` (a migration for a value the
  existing column, sized for it, already describes); `null` reason on cancellation (the journal asks
  for `USER_REQUESTED`, and it leaves room for a system-initiated cancellation later, such as an
  expiry, to say so); widening `RejectionReason` (constitution IV lists exactly three rejection
  reasons, and a cancellation is not a rejection).

## R-407: A replay answers the outcome as first recorded **(author)**

- **Decision**: a keyed request replayed after its order changed status answers **the outcome its
  first request answered**, not the order's current status. Concretely, each use case's replay
  reports the order with the status and reason of **the transition that write recorded**:
  - submission and transfer — the order's **earliest** history row (lowest id), which is the one
    their append wrote; a `LIMIT` order cancelled since replays as `NEW`;
  - cancellation — the order's `CANCELLED` row; `CANCELLED` is terminal, so this is also its current
    status.

  Every other field comes from `orders`, which only the status column of ever changes.
- **Rationale**: ADR-0037 promises "a retry returns the first outcome", and FR-012 here repeats it.
  Until this feature, current status and first outcome could not differ, so reading `orders.status`
  was a faithful implementation of that promise; cancellation is the first change that separates them.
  A client that timed out on a submission and retries must learn what its submission did, not what
  someone did to the order afterwards — the portfolio is where the current state is read. Every keyed
  order was written through the API, so its submission always left a history row; seeded orders,
  which have none, are never named by a key.
- **Alternatives considered** (put to the author): the current status (what the code does: simpler,
  but the replay of one request would change answer over time, and ADR-0037's promise would need a
  superseding ADR to weaken it); storing the first response body in `order_idempotency` (a migration
  and a second copy of the order, for what the history already records).

## R-408: The model decides from the status alone

- **Decision**: a model rule, in plain Java, answers for an order's current status whether it can be
  cancelled and, if so, the transition to record: to `CANCELLED`, reason `USER_REQUESTED`, at the
  instant the service passes in. Cancellable is exactly `NEW` (FR-005, FR-007); the type and side are
  not consulted. A status that cannot be cancelled is answered as a value the service turns into
  `409`, naming the order and its status.
- **Rationale**: constitution III (rules in Java) and VIII (the model returns what it detects); one
  place states which statuses are cancellable, so a later status that should be — the journal's "por
  ahora" — is added there. `OrderStatus.isOpen()` already names `NEW` as the open status, and the rule
  is expressed through it rather than restating `== NEW`.
- **Alternatives considered**: checking `status == NEW` in the service (a rule outside the model);
  checking the order's type (`LIMIT`) as well (a `MARKET` order is never `NEW`, and a future
  partially-filled state would need the status, not the type); a `WHERE status = 'NEW'` as the only
  check (the rule in SQL).

## R-409: The release is the fold, unchanged

- **Decision**: nothing is added to the ledger fold. A `CANCELLED` order is not `contributing()`, so
  the next portfolio read leaves it out, and what it reserved — cash for a `BUY`, shares for a `SELL`
  — is no longer subtracted from available (FR-008).
- **Rationale**: ADR-0004 and ADR-0015 already define availability as settled minus what `NEW` orders
  reserve, read from one fold (constitution I). Settled cash and holdings cannot change, because a
  `NEW` order never contributed to them. The cancellation itself never reads the fold: it needs no
  funds check (the spec's assumption), and a user whose available cash is already negative
  (ADR-0012) can still cancel.
- **Alternatives considered**: computing and reporting the released amount in the response (a second
  computation of a reservation outside the fold); reading the fold to refuse a cancellation under an
  anomaly (nothing in the spec or the journal asks for it, and it can only raise availability).

## R-410: The fingerprint names the operation, the user and the order

- **Decision**: the cancellation's canonical form is
  `operation=ORDER_CANCELLATION;userId=<id>;orderId=<id>`, hashed with SHA-256 through the shared
  `Fingerprint`, in the same `order_idempotency` table and namespace (ADR-0037, ADR-0040). Submission's
  and transfer's forms are unchanged.
- **Rationale**: with no body, the user and the order are the whole request. The operation tag makes
  a cancellation's form unequal to any submission's (which has no tag) and any transfer's
  (`CASH_TRANSFER`), so a key reused across endpoints always fails the comparison and answers `409`
  before anything is read (FR-013). A cancellation with a key that was refused leaves nothing, because
  the claim rolls back with the transaction (FR-014, ADR-0037).
- **Alternatives considered**: no operation tag (a cancellation's form could not collide with a
  submission's today, but only by accident of their fields); a separate key table per endpoint (two
  namespaces, which ADR-0038 already rejected for transfers).

## R-411: The error map: 200, 400, 404, 409, 503

- **Decision**:

  | Answer | When | Written |
  |---|---|---|
  | `200` | the order was `NEW` and is now `CANCELLED`; or a replay of that | the transition (first time only) |
  | `400` | an id of `0` or below or non-numeric; a blank or over-long key | nothing |
  | `404` | the user does not exist; the order does not exist **or belongs to another user** — the same detail for both | nothing |
  | `409` | the order exists for the user but is not `NEW` **(author)**; or the key names a different request | nothing |
  | `503` | the deadline or the pool (ADR-0031) | nothing |

  Precedence follows the other write endpoints: the request is read (`400`), then the user's
  existence (`404`), then the key (`409` conflict or replay), then, under the lock, the order (`404`)
  and its status (`409`).
- **Rationale**: `409` is RFC 9110's "conflict with the current state of the target resource", which
  is exactly a cancellation of a `FILLED` order; it shares the code with the key conflict, and the
  detail tells them apart. The two `404`s for a missing and a foreign order carry the same detail —
  "Order 5 does not exist for user 2" — so the endpoint does not reveal which ids exist (FR-004).
- **Alternatives considered** (put to the author): `422` for not cancellable (the request is well
  formed and was processed; nothing in it is unprocessable); `403` for a foreign order (there is no
  identity to forbid without authentication, ADR-0007, and it would disclose the order exists);
  `200` with the unchanged order for a non-`NEW` one (a refusal answered as success).

## R-412: What this feature deliberately does not build

- Cancelling any status but `NEW`; cancelling several orders at once; partial cancellation.
- A cancellation reason supplied by the caller: the reason is always `USER_REQUESTED`.
- Reading an order or its history through the API: the response reports the order, the portfolio
  reports the effect.
- Validating `userId ≥ 1` on the other write endpoints and the portfolio ([R-403](#r-403-ids-are-validated-before-any-lookup)).
- Expiring idempotency records: unchanged from ADR-0037.
