# Data Model: Order Cancellation

**Feature**: `005-order-cancellation` | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

What the feature reads, what it writes, and which object owns each rule. Contracts and rules only;
the shape of the code is the implementation's to choose (constitution, Development Workflow).

## 1. Schema

**No migration.** `orders.status`, `order_status_history` (`V4`, `reason VARCHAR(40)`) and
`order_idempotency` (`V5`) already hold everything a cancellation writes.

### Read

| Table | What | Why |
|---|---|---|
| `users` | the row for `{userId}`, first without a lock (existence), then `FOR UPDATE` | `404`; the per-user serialisation (`UserWrite`, ADR-0040) |
| `orders` | the row with `id = {orderId}` **and** `userid = {userId}`, with its instrument, read under the lock | `404` for a missing or foreign order; the status the model decides on; the response's fields |
| `order_idempotency` | the row for `(userid, key)` when the claim was lost | replay or `409` |
| `order_status_history` | a replayed order's transition: its earliest row (submission, transfer) or its `CANCELLED` row (cancellation) | the replay answers the outcome as first recorded ([R-407](research.md#r-407-a-replay-answers-the-outcome-as-first-recorded-author)) |

### Written, one transaction per cancellation

| Table | Change | Detail |
|---|---|---|
| `orders` | `status` `NEW` → `CANCELLED` on exactly one row | guarded: only where `id` is the order's and `status` is still `NEW`; reports the rows changed ([R-405](research.md#r-405-the-transition-is-a-guarded-update-and-one-history-row)). No other column changes |
| `order_status_history` | exactly one row appended | `order_id`; `status = CANCELLED`; `reason = USER_REQUESTED`; `changed_at` = the server's instant |
| `order_idempotency` | one row per keyed first request | as for the other write endpoints: claimed before the lock, `order_id` = **the cancelled order** attached before commit |

A refused request (`400`, `404`, `409`, `503`) writes nothing: the claim, when one was taken, rolls
back with the transaction (FR-014).

## 2. Model (plain Java: no Spring, no JPA, no clock)

### Reused unchanged

- `model/order/OrderStatus` — `NEW`, `CANCELLED`; `isOpen()` names `NEW` as the open status;
  `contributing()` already excludes `CANCELLED`, which is the release ([R-409](research.md#r-409-the-release-is-the-fold-unchanged)).
- `model/order/RejectionReason` — unchanged; constitution IV's three values.
- `model/ledger` — the fold. Not read by the cancellation; read by the portfolio afterwards.
- `model/user/UserId`, `UnknownUserException`.

### New

**Cancellation reason** — the machine-readable reason a cancellation records. One value,
`USER_REQUESTED`. A type of its own, separate from `RejectionReason`, so a rejection can never carry
it ([R-406](research.md#r-406-the-historys-reason-holds-why-the-order-moved-not-only-rejections)).

**The cancellation rule** — given an order's current status (and, to say so, its id), answers one of:

| Current status | Answer |
|---|---|
| `NEW` | the transition to record: to `CANCELLED`, reason `USER_REQUESTED`, at the instant passed in |
| `FILLED`, `REJECTED`, `CANCELLED` | not cancellable, naming the order and its status (→ `409` through the use case) |

Decided by the status alone (FR-007): the type, the side and whether it is a transfer are not
consulted. Which statuses are cancellable is stated once, here. The instant is an input; the model
has no clock.

### Where the result is described

A cancelled order is reported with the fields the order already has — instrument, side, kind, size,
price, the instant it was placed — and the status `CANCELLED`. The model's existing `NewOrder` ("a
row as it entered the ledger") is what persistence already reads back an order as
(`OrderMapper.toPlacedOrder`); the cancellation's result is built from that read and the new status,
not by re-reading the row after the update.

## 3. Persistence

| Type | Change |
|---|---|
| `OrderRepository` | gains a read of **one order of one user** (by id and user id, with its instrument), absent when either does not match; and the **guarded status transition** (id, expected status, new status → rows changed) |
| `OrderStatusHistoryRepository` | gains the append of a cancellation's transition; gains a read of an order's **earliest** transition (status and reason) and of its `CANCELLED` transition. `reasonOf` reads only a `REJECTED` row's reason, mapped as a `RejectionReason` |
| `OrderMapper` | maps the history's `reason` column by the row's status: `REJECTED` → `RejectionReason`, `CANCELLED` → `CancellationReason`; maps an order id the way `UserMapper.toIdColumn` maps a user id (out of `INT` range → absent) |
| `UserRepository`, `OrderIdempotencyRepository` | reused as-is, through `service/userwrite` |

The guarded transition is a bulk statement: an entity already loaded in the same transaction keeps
its old status in memory. Nothing reads it afterwards, because the result is built from the model's
answer (section 2), and this must stay true.

## 4. Service

### `service/cancellation` (new use case)

| Type | Contract |
|---|---|
| cancellation request | the raw request the controller read: user id, order id, optional key |
| cancelled order | what the controller maps: order id, user, instrument, side, kind, size, price, the instant it was placed, status `CANCELLED`; names its order for `UserWrite` |
| invalid cancellation exception | an id below `1` → `400` |
| unknown order exception | no order with that id for that user → `404`, one detail for missing and foreign |
| order not cancellable exception | the order exists for the user but is not `NEW`, or left `NEW` before the guarded write → `409` |

Step order, as a rule:

```
read the request: both ids >= 1, else 400; fingerprint when keyed      (no database)
UserWrite: verify the user exists                                       (non-locking -> 404)
UserWrite: claim the key, when one was sent
  |- claimed     -> lock the user
  |                 -> read the order of this user (absent -> 404)
  |                 -> ask the model (not cancellable -> 409)
  |                 -> guarded transition (0 rows -> 409) + history row
  |                 -> attach the order to the key
  |- not claimed -> read the claim -> same fingerprint ? replay : 409
```

Transaction: one per cancellation, `READ COMMITTED`, not read-only — the other write endpoints'
(ADR-0036).

**Canonical form** ([R-410](research.md#r-410-the-fingerprint-names-the-operation-the-user-and-the-order)):
`operation=ORDER_CANCELLATION;userId=<id>;orderId=<id>`.

**Replay**: the order the key names, reported with its `CANCELLED` transition.

### `service/submission`, `service/transfer` (changed: the replay)

Each replay reports the order with the status and reason of its **earliest** history row — the
outcome its first request answered — instead of `orders.status`
([R-407](research.md#r-407-a-replay-answers-the-outcome-as-first-recorded-author)). The two replays
are already written twice, once per use case; whether the "order as first recorded" read becomes one
persistence method both call is the refactor step's call (constitution IX), not a new shared
service. No other behaviour of either endpoint changes.

### `service/userwrite` (renamed, not changed)

`AppendedOrder` is renamed to say what its contract is — the order a write is about, to attach to its
key — since a cancellation appends nothing ([R-404](research.md#r-404-the-protections-are-userwrites-unchanged)).
Proposed name `WrittenOrder`; no method, rule or ArchUnit test changes.

## 5. Controller

- An order cancellation controller — `POST /api/v1/users/{userId}/orders/{orderId}/cancel`, no body,
  optional `Idempotency-Key`; builds the raw request, calls the service once, maps the result, answers
  `200`.
- Response DTO — `OrderResponse`, unchanged in shape; it gains a mapping from the cancelled order.
- `ApiExceptionHandler` — invalid cancellation → `400`; unknown order → `404`; not cancellable → `409`.
  `404` for an unknown user, `409` for a key conflict and `503` are reused.

## 6. Contract changes to apply (`docs/api/openapi.yaml`, `docs/api/trading-api.http`)

- The new operation, as proposed in [contracts/order-cancellation.openapi.yaml](contracts/order-cancellation.openapi.yaml).
- `Order.status` enum gains `CANCELLED`; its description replaces "`CANCELLED` never originates here"
  with where it does.
- Submission's and transfer's `Idempotency-Key` descriptions: a replay answers the **first** outcome
  even if the order changed status since; a cancellation shares the namespace.

## 7. Requirement-to-owner map

| Requirement | Owner |
|---|---|
| FR-001 | the cancellation controller + `service/cancellation` |
| FR-002 | the use case's reading of the request → `400` |
| FR-003 | `UserWrite`'s existence read → `404` |
| FR-004 | the order read scoped by user and id → one `404` for both cases |
| FR-005, FR-007 | the model's cancellation rule → `409` |
| FR-006 | guarded transition + history row (`CANCELLED`, `USER_REQUESTED`), one transaction |
| FR-008 | the unchanged fold: `CANCELLED` is not `contributing()` |
| FR-009 | `OrderResponse` with `status: CANCELLED`, `200` |
| FR-010 | `UserWrite` → `UserLock`, the same row as submission and transfer |
| FR-011 | the lock, plus the guarded transition for writers outside it |
| FR-012 – FR-015 | `UserWrite` → `IdempotentWrite`, the operation-tagged canonical form, rollback of a refused claim |
| FR-016 | no lock or claim outside `service/userwrite` — the existing ArchUnit rules |
| (replay of other endpoints) | their replays read the earliest transition |

## 8. Decisions to record as ADRs

| ADR | Decision | Research |
|---|---|---|
| 0041 | The cancellation endpoint: path under the user, `200` with the order, `404` for a missing or foreign order, `409` for not cancellable, no trailing slash; supersedes ADR-0006's path | R-401, R-402, R-403, R-411 |
| 0042 | The history's `reason` holds why the order moved: rejection reasons and `USER_REQUESTED`, as separate types, read by the row's status | R-406 |
| 0043 | An idempotent replay answers the outcome as first recorded, read from the history | R-407 |

Written with this plan on 2026-11-17, with their index rows and ADR-0006 marked superseded. The
serialisation and the idempotency claim are applications of ADR-0036, 0037 and 0040, not new
decisions; ADR-0041 cites them.
