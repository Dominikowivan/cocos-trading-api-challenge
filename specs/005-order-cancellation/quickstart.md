# Quickstart: Validating Order Cancellation

**Feature**: `005-order-cancellation` | **Plan**: [plan.md](plan.md) |
**Contract**: [contracts/order-cancellation.openapi.yaml](contracts/order-cancellation.openapi.yaml)
(design-time; the live one is [docs/api/openapi.yaml](../../docs/api/openapi.yaml))

How to run the feature and prove it works end to end. Scenarios only — no implementation code; the
response shape lives in the contract, the rules in [spec.md](spec.md) and the decisions in
[research.md](research.md).

Every figure below comes from `src/main/resources/db/seed/R__given_dataset.sql` and is listed in
[research.md](research.md#measured-facts-this-phase-relied-on). None of it is estimated.

> **This feature writes.** Each scenario assumes the seed's starting state. Reset between scenarios
> with `docker compose down -v && docker compose up -d`. The automated suite gets a fresh container
> per class, so it does not have this problem. Order ids `12` onwards assume a fresh seed: the first
> order written through the API is `12`.

## Prerequisites

- JDK 21
- Docker (local database and Testcontainers)

## Run it

```bash
docker compose up -d
./mvnw spring-boot:run
```

PostgreSQL 18.7 on port **5433**; the `local` profile applies `db/migration` (no new migration in
this feature) and loads the provided dataset (ADR-0029). The app is on `http://localhost:8080`.
Every request below is also added to [`docs/api/trading-api.http`](../../docs/api/trading-api.http).

## The starting state

| Order | User | What | Status | Reserves |
|---|---|---|---|---|
| `5` | `1` | `BUY LIMIT 50 LOMA @ 710` | `NEW` | `35,500.00` cash |
| `7` | `1` | `BUY LIMIT 60 BMA @ 1,500` | `NEW` | `90,000.00` cash |
| `3` | `1` | `BUY LIMIT 50 PAMP @ 920` | `CANCELLED` | — |
| `6` | `1` | `SELL MARKET 100 PAMP` | `REJECTED` | — |
| `1`, `2`, `4`, `8`–`11` | `1` | `1` is `CASH_IN`, `8` is `CASH_OUT` | `FILLED` | — |

User `1`: settled cash `753,000.00`, available `627,500.00`, `40` `PAMP` held (last close `925.85`).
Users `2`–`4` have no orders.

## Scenario 1 — Cancel a pending buy (US1 scenarios 1, 2; SC-001)

```bash
curl -s -X POST localhost:8080/api/v1/users/1/orders/5/cancel
curl -s localhost:8080/api/v1/users/1/portfolio
```

Expect `200`, `orderId: 5`, `ticker: LOMA`, `type: LIMIT`, `side: BUY`, `size: 50`, `price: 710.00`,
`status: CANCELLED`, `rejectionReason: null`. The portfolio reports available cash **`663,000.00`**
and settled cash still **`753,000.00`**. The database holds one `order_status_history` row for order
`5`: `CANCELLED`, `USER_REQUESTED`.

## Scenario 2 — Nothing reserved any more (US1 scenario 3; SC-002)

After scenario 1, cancel order `7`. Expect `200`; available cash equals settled cash,
**`753,000.00`**.

## Scenario 3 — Cancelling twice, and every status that is not `NEW` (US1 scenarios 5, 6; SC-003)

After scenario 1, cancel order `5` again → **`409`**, detail naming order `5` and `CANCELLED`;
available cash still `663,000.00`; still one history row.

On a fresh seed, cancel orders `3`, `6`, `2` and `1` → each **`409`**, each order's status and history
unchanged.

## Scenario 4 — Someone else's order, and orders that do not exist (US1 scenarios 7–9; SC-004)

| Request | Expect |
|---|---|
| `POST /api/v1/users/2/orders/5/cancel` | `404`, `Order 5 does not exist for user 2`; order `5` stays `NEW` |
| `POST /api/v1/users/2/orders/999/cancel` | `404`, `Order 999 does not exist for user 2` — the same shape of answer |
| `POST /api/v1/users/999/orders/5/cancel` | `404`, `User 999 does not exist` |
| `POST /api/v1/users/1/orders/0/cancel`, `…/orders/-5/cancel`, `/users/0/orders/5/cancel` | `400` |
| `POST /api/v1/users/1/orders/abc/cancel` | `400` |
| `POST /api/v1/users/1/orders/5/cancel/` | `404`: no trailing-slash spelling (R-401) |

## Scenario 5 — A cancelled sell frees its shares (US1 scenarios 4, 10; SC-007)

On a fresh seed, as user `1`:

1. Submit `SELL LIMIT 40 PAMP @ 1000` → `201`, order `12`, `NEW`.
2. Submit `SELL MARKET 40 PAMP` → `201`, order `13`, `REJECTED`, `INSUFFICIENT_HOLDINGS`: all forty
   are reserved.
3. Cancel order `12` → `200`, `CANCELLED`.
4. Submit `SELL MARKET 40 PAMP` again → `201`, order `14`, `FILLED` at `925.85`; settled cash grows
   by `37,034.00`.

Order `12` now has two history rows: `NEW`, then `CANCELLED` / `USER_REQUESTED`.

## Scenario 6 — Retried with a key (US3; SC-006)

On a fresh seed:

| Request | Expect |
|---|---|
| cancel `5` with `Idempotency-Key: cancel-5` | `200`, `CANCELLED` |
| the same again | `200`, the same order; one history row; available cash `663,000.00` |
| cancel `7` with `cancel-5` | `409` (key names a different request); order `7` stays `NEW` |
| cancel `999` with `k-404`, then cancel `7` with `k-404` | `404`, then `200`: a refused request leaves no key |
| user `2` cancels `999` with `cancel-5` | `404`, not `409`: keys are per user |

## Scenario 7 — A submission's key after its order was cancelled (R-407)

On a fresh seed, as user `1`:

1. Submit `BUY LIMIT 10 PAMP @ 900` with `Idempotency-Key: buy-1` → `201`, order `12`, `NEW`.
2. Cancel order `12` with `Idempotency-Key: buy-1` → **`409`**: the key belongs to the submission.
3. Cancel order `12` with no key → `200`, `CANCELLED`.
4. Repeat step 1 exactly → `201`, order `12`, **`NEW`** — the first outcome, not the current status.

## Scenario 8 — Concurrency (US2; SC-005)

Not reproducible reliably by hand; covered by two-thread Testcontainers tests:

- two cancellations of order `5` at once → exactly one `200`, one `409`, one history row;
- a cancellation of `5` and a `CASH_OUT` of `663,000` at once → either the withdrawal is `FILLED`
  after the cancellation, or `REJECTED` before it; available cash never below zero;
- a cancellation for user `1` and a deposit for user `2` at once → neither waits on the other;
- a cancellation that cannot take user `1`'s lock before the deadline → `503`, nothing written.

## What each test level covers

| Level | Covers |
|---|---|
| Model (plain Java) | the cancellation rule for each of the four statuses; the reason type |
| Persistence (Testcontainers) | the scoped order read (own, foreign, missing); the guarded transition (`NEW` → 1 row, otherwise 0); the history row; the earliest-transition read; `reasonOf` over a `CANCELLED` row |
| Web (MockMvc, Testcontainers, contract-validated) | scenarios 1–7, every response checked against `docs/api/openapi.yaml` |
| Concurrency (Testcontainers, two threads) | scenario 8 |
| Architecture (ArchUnit) | unchanged rules: only `service/userwrite` locks and claims; `service/cancellation` depends on no other use case |

```bash
./mvnw verify
```
