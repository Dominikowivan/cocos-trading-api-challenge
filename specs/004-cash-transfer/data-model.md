# Data Model: Cash Transfer

**Feature**: `004-cash-transfer` | **Plan**: [plan.md](plan.md) | **Research**: [research.md](research.md)

What the feature reads, what it writes, and which object owns each rule. Contracts and rules only;
the shape of the code is the implementation's to choose (constitution, Development Workflow).

## 1. Schema

**No migration** ([R-313](research.md#r-313-no-migration)).

### Read

| Table | What | Why |
|---|---|---|
| `users` | the row for `{userId}`, first without a lock (existence), then `FOR UPDATE` | `404`; the per-user serialisation ([R-309](research.md#r-309-serialisation-is-submissions-lock-on-the-same-row-us2)) |
| `instruments` | the one row with `type = 'MONEDA'` | the instrument a transfer is recorded on ([R-303](research.md#r-303-a-transfer-is-the-seeds-row-a-market-order-on-the-moneda-instrument-at-price-1)) |
| `orders` | the user's `FILLED` and `NEW` orders, with their instruments | the fold that yields available cash ([R-308](research.md#r-308-available-cash-is-the-fold-the-portfolio-reports-unchanged)) |
| `order_idempotency` | the row for `(userid, key)` when the claim lost | replay or `409` |
| `order_status_history` | the reason of the order a key names | replaying a `REJECTED` withdrawal with its reason |

### Written, one transaction per transfer

| Table | Row | Columns |
|---|---|---|
| `orders` | exactly one per processed transfer | `instrumentid` = the `MONEDA` instrument; `userid`; `size` = amount in whole pesos; `price` = `1.00`; `side` = `CASH_IN` / `CASH_OUT`; `type` = `MARKET`; `status` = `FILLED` / `REJECTED`; `datetime` = server instant |
| `order_status_history` | exactly one per order (FR-010) | `order_id`; `status`; `reason` = `INSUFFICIENT_FUNDS` or null; `changed_at` = the order's instant |
| `order_idempotency` | one per keyed first request | as for submission (ADR-0037): claimed before the lock, `order_id` attached before commit |

A refused request (`400`, `404`, `409`, `503`) writes nothing: the claim, when one was taken, rolls
back with the transaction.

## 2. Model (plain Java: no Spring, no JPA, no clock)

### Reused unchanged

- `model/ledger` — `AccountLedger`, `AccountSnapshot.availableCash()`.
- `model/order/CashMovement` — the two directions and their cash sign.
- `model/order/RejectionReason.INSUFFICIENT_FUNDS`.
- `model/instrument/InstrumentRef` — `CASH`, `isCash()`.
- `model/Price`, `model/DecimalPolicy` — price `1.00`, money scale for the reported amount.

### Changed

| Type | Change | Why |
|---|---|---|
| `LedgerSide` | declares `name()` | the write path stores the side by name for both kinds of side |
| `NewOrder` | `side` is a `LedgerSide` | one write model for every ledger row ([R-307](research.md#r-307-the-write-model-takes-a-ledgerside-as-the-read-model-already-does)) |

### New, in `model/transfer`

**A cash transfer** — valid by construction:

| Field | Rule | Refusal (→ `400` through the use case) |
|---|---|---|
| direction | a `CashMovement` | — (parsing the text is the use case's) |
| amount | whole pesos: scale `0` once trailing zeros are stripped; `1` ≤ amount ≤ `2,147,483,647` | names the rule and the value; never rounds ([R-304](research.md#r-304-the-amount-is-whole-pesos-from-1-to-2147483647-never-rounded)) |

Given the user's `AccountSnapshot`, the cash instrument, the user and the instant, it answers **the
order to record** (a `NewOrder`) and writes nothing:

| Direction | Condition | Status | Reason |
|---|---|---|---|
| `CASH_IN` | none (FR-007) | `FILLED` | — |
| `CASH_OUT` | `availableCash().compareTo(amount) >= 0` | `FILLED` | — |
| `CASH_OUT` | otherwise | `REJECTED` | `INSUFFICIENT_FUNDS` |

In every case: `kind = MARKET`, `price = 1.00`, `size = amount`, instrument = the cash instrument
passed in. The price of a peso in pesos is a named fact of this concept, stated once.

A negative available cash (an ADR-0012 anomaly) needs no special case: every withdrawal fails the
comparison, and a deposit is unconditional.

## 3. Persistence

| Type | Change |
|---|---|
| `InstrumentRepository` | gains a read of the instruments of a type (`MONEDA`) |
| `OrderMapper.toPlacedOrder` | reads the side back as a `LedgerSide` (via the existing `toLedgerSide`) |
| `OrderMapper.toNewOrder`, `toNewTransition` | unchanged in behaviour; take the widened `NewOrder` |
| `UserRepository.lockById`, `OrderRepository.findOrdersOf` / `append` / `findPlacedOrder`, `OrderStatusHistoryRepository`, `OrderIdempotencyRepository` | reused as-is |

## 4. Service

### `service/transfer` (US1–US3)

The use case and its own types:

| Type | Contract |
|---|---|
| transfer request | the raw request the controller read: user id, side text, amount, the names of any fields outside the body's shape, optional key |
| recorded transfer | what the controller maps: order id, user, direction, amount, status, optional reason, instant |
| invalid transfer exception | a refusal that writes nothing → `400` |

Step order, as a rule (identical to submission's, ADR-0037):

```
read and validate the request, compute the fingerprint when keyed   (no database)
verify the user exists                                              (non-locking)
claim the key, when one was sent
  |- claimed     -> lock the user -> find the cash instrument -> fold -> decide -> append order + history -> attach
  |- not claimed -> read the claim -> same fingerprint ? replay : 409
```

Transaction: one per transfer, `READ COMMITTED`, not read-only (ADR-0036).

**US2 and US3 deliberately copy** submission's lock call, key type and claim flow into
`service/transfer` — the slice rule forbids importing them — and US4 removes the copy. See the
plan's Complexity Tracking.

**Canonical form** of a transfer (one place, [R-310](research.md#r-310-idempotency-the-same-header-the-same-table-a-fingerprint-that-names-the-operation)):
`operation=CASH_TRANSFER;userId=<id>;side=<text as sent>;amount=<whole pesos, plain>`. Order
submission's form is unchanged.

### `service/userwrite` (US4)

Not a use case: no `@Service`, no endpoint. One `@Component` whose contract is:

| Input | Meaning |
|---|---|
| the user | whose row is locked and whose namespace the key lives in |
| the keyed request, if any | the key and the request's fingerprint |
| the work | performs the write under the lock; yields a result that names the order it appended |
| the replay | turns a recorded order id into the use case's result |

| Guarantee | Source |
|---|---|
| unknown user → `UnknownUserException` (`404`), before any claim | ADR-0037 |
| the claim precedes the lock; the lock precedes the work | ADR-0036, ADR-0037 |
| claimed → work, then the order attached to the key, same transaction | FR-015 |
| not claimed → same fingerprint: the replay; different: conflict (`409`) | FR-015, FR-016 |
| runs only inside the caller's transaction | a refused request rolls back its claim |

It owns the key type, the conflict exception and the hashing/comparison of a canonical form. Each use
case owns its canonical form, its work and its replay.

## 5. Controller

- `CashTransferController` — `POST /api/v1/users/{userId}/cash-transfers`, optional
  `Idempotency-Key` header; builds the raw transfer request, calls the service once, maps the
  recorded transfer to the response, answers `201`.
- Request DTO — `side`, `amount`, and whatever else arrived, so the use case can refuse it.
- Response DTO — `orderId`, `userId`, `side`, `amount` (money scale), `status`, `rejectionReason`
  (JSON `null` when absent), `transferredAt`.
- `ApiExceptionHandler` — the transfer's invalid exception → `400`; the unreadable-body detail is
  endpoint-neutral. `404`, `409`, `503` mappings reused.

## 6. Requirement-to-owner map

| Requirement | Owner |
|---|---|
| FR-001 | `CashTransferController` + the transfer service |
| FR-002 | the use case's parsing of `side` into a `CashMovement` |
| FR-003 | the transfer's construction (whole pesos, bounds) |
| FR-004 | the use case, refusing any field outside `side` and `amount` |
| FR-005 | the existence read → `404` |
| FR-006 | the transfer's answer: `MARKET`, price `1`, size = amount, the `MONEDA` instrument |
| FR-007, FR-008 | the transfer's decision table |
| FR-009 | `AccountSnapshot.availableCash()`, unchanged |
| FR-010 | `OrderRepository.append` + `OrderStatusHistoryRepository.appendTransitionOf`, one transaction |
| FR-011 | the recorded transfer → response DTO, `201` |
| FR-012 | the unchanged fold: `cashEffect()` already counts `CASH_IN`/`CASH_OUT` |
| FR-013 | `DecimalPolicy.money` on the reported amount |
| FR-014 | `UserRepository.lockById`, same row as submission |
| FR-015 – FR-017 | `order_idempotency` claim, the transfer's canonical form with its operation tag |
| FR-018 | `service/userwrite`, plus the ArchUnit rule that only it locks and claims |
| FR-019 | submission's suite unchanged; its canonical form unchanged |

## 7. Decisions to record as ADRs

| ADR | Decision | Research |
|---|---|---|
| 0038 | The cash transfer endpoint: path, own response, `201` for every recorded outcome, closed body | R-301, R-302, R-305, R-312 |
| 0039 | A transfer is a `MARKET` order on the `MONEDA` instrument at price `1`, in whole pesos | R-303, R-304 |
| 0040 | Per-user write protection lives once, in `service/userwrite` | R-311 (with the constitution amendment) |

The idempotency namespace shared across endpoints and the reuse of the lock are applications of
ADR-0036 and ADR-0037, not new decisions; ADR-0038 cites them.
