# Quickstart: Validating Order Placement

**Feature**: `003-order-placement` | **Plan**: [plan.md](plan.md) |
**Contract**: [contracts/order-submission.openapi.yaml](contracts/order-submission.openapi.yaml)
(design-time; the live one is [docs/api/openapi.yaml](../../docs/api/openapi.yaml))

How to run the feature and prove it works end to end. Scenarios only — no implementation code; the
response shape lives in the contract, the rules in [spec.md](spec.md) and the decisions in
[research.md](research.md).

Every figure below was computed from `src/main/resources/db/seed/R__given_dataset.sql` on
**2026-11-17** by folding user `1`'s ledger the way `AccountLedger` does, and is listed in
[research.md](research.md#measured-facts-this-phase-relied-on). None of it is estimated.

> **This feature writes.** Unlike the portfolio and the search, every scenario below changes the
> database, so they are **not** independent of each other: each one assumes the starting state of
> the seed. Reset between runs with `docker compose down -v && docker compose up -d`, or run them in
> the order given and track the cash as you go. The automated suite gets a fresh container per class
> (Testcontainers), which is why it does not have this problem.

## Prerequisites

- JDK 21
- Docker (local database and Testcontainers)

## Run it

```bash
docker compose up -d
./mvnw spring-boot:run
```

PostgreSQL 18.7 on port **5433**. `spring-boot:run` starts with the `local` profile, so Flyway
applies `db/migration` — including this feature's `V4__order_status_history.sql` and
`V5__order_idempotency.sql` — and then loads the provided dataset from `db/seed` (ADR-0029). The app
is on `http://localhost:8080`.

Every request below is also in [`docs/api/trading-api.http`](../../docs/api/trading-api.http),
runnable from IntelliJ's HTTP Client or VS Code's REST Client; the `curl` lines are for readers
without either.

## The starting state

```bash
curl -s http://localhost:8080/api/v1/users/1/portfolio | jq
```

| Figure | Value |
|---|---|
| `settledCash` | `753,000.00` |
| `availableCash` | **`627,500.00`** — `125,500.00` is reserved by two `NEW BUY LIMIT` orders |
| Holdings | `PAMP` **40**, `METR` **500**, `BMA` **−10** |
| `PAMP` (id 47) latest `close` | **`925.85`** |

Nothing below needs a fixture: `PGR` (id 3) has no price in the provided data, `ARS` (id 66) is the
`MONEDA` instrument, and `BMA` is already negative.

---

## Scenario 1 — Buy an exact number of shares at market (US1, SC-001)

```bash
curl -s -X POST http://localhost:8080/api/v1/users/1/orders \
  -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 100}' | jq
```

`201`, `status: "FILLED"`, `size: 100`, `price: 925.85`. The order cost `92,585.00`, so the next
portfolio read shows `availableCash` **`534,915.00`**.

## Scenario 2 — The funds boundary, to the cent (US1, SC-002)

Against the untouched seed (`627,500.00` available):

```bash
# 677 PAMP = 626,800.45 -> FILLED
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 677}' | jq '.status'
# 678 PAMP = 627,726.30 -> REJECTED, INSUFFICIENT_FUNDS
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 678}' | jq '.status, .rejectionReason'
```

Both answer `201`: one `FILLED`, one `REJECTED` — the rejection is **stored**, which is what the
statement asks for. Check it is really in the ledger: the rejected order changes no figure in the
portfolio.

## Scenario 3 — Sell what you hold, and what you do not (US1, SC-003)

```bash
# 40 PAMP held -> FILLED at 925.85 for 37,034.00; the position goes to zero
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "SELL", "size": 40}' | jq '.status, .price'
# 41 -> REJECTED, INSUFFICIENT_HOLDINGS
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "SELL", "size": 41}' | jq '.rejectionReason'
# BMA is -10: every SELL is REJECTED, and the anomaly is not corrected (ADR-0012)
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 31, "type": "MARKET", "side": "SELL", "size": 1}' | jq '.rejectionReason'
```

## Scenario 4 — A user with no orders (US1 scenario 7)

User `2` exists in the seed and has never traded.

```bash
curl -s -X POST http://localhost:8080/api/v1/users/2/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 1}' | jq '.rejectionReason'
curl -s -X POST http://localhost:8080/api/v1/users/2/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "SELL", "size": 1}' | jq '.rejectionReason'
```

`INSUFFICIENT_FUNDS` and `INSUFFICIENT_HOLDINGS`, both `REJECTED` and both stored.

## Scenario 5 — Spend a total amount at market (US2, SC-004)

```bash
# 100,000.00 / 925.85 -> 108 shares for 99,991.80; 8.20 stays unspent
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "amount": 100000.00}' | jq '.size, .price'
# 30,000.00 of a 40-share position -> 32 shares for 29,627.20, 8 left
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "SELL", "amount": 30000.00}' | jq '.size'
```

`108` and `32`. The amount is a ceiling, not a target.

## Scenario 6 — The amount path's rejections (US2 scenarios 2, 3, 6, 8)

| Request | Outcome |
|---|---|
| `BUY  amount 700000.00` | `REJECTED`, `INSUFFICIENT_FUNDS` — the amount alone exceeds `627,500.00`; no size is derived, so `size` is `0` |
| `BUY  amount 500.00` | `REJECTED`, `SIZE_BELOW_ONE_SHARE` — the money is there, it just does not reach one share at `925.85` |
| `SELL amount 40000.00` | `REJECTED`, `INSUFFICIENT_HOLDINGS` — 40 `PAMP` are worth `37,034.00` |
| `SELL amount 900.00` | `REJECTED`, `SIZE_BELOW_ONE_SHARE` |
| `BUY  amount 627500.00` | `FILLED`, **677** shares for `626,800.45` — the whole available cash |
| `SELL amount 37034.00` | `FILLED`, **40** shares — the position, to the cent |

## Scenario 7 — A request whose shape is wrong is refused, not rejected (US2 scenario 9, FR-003 to FR-008)

Every one of these answers `400` with a problem detail and **writes nothing** — which is how a
caller tells a client bug from the market's answer about their money.

```bash
# both a size and an amount
-d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 10, "amount": 1000.00}'
# neither
-d '{"instrumentId": 47, "type": "MARKET", "side": "BUY"}'
# a MARKET order carrying a price: refused, never ignored
-d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 10, "price": 900.00}'
# a LIMIT order with no price
-d '{"instrumentId": 47, "type": "LIMIT", "side": "BUY", "size": 10}'
# a side that is not an order against the market
-d '{"instrumentId": 66, "type": "MARKET", "side": "CASH_IN", "size": 1000}'
# cash is not tradable here
-d '{"instrumentId": 66, "type": "MARKET", "side": "BUY", "size": 10}'
# an instrument that does not exist
-d '{"instrumentId": 9999, "type": "MARKET", "side": "BUY", "size": 10}'
# size 0, a negative size, a fractional size, a negative amount
```

An unknown **user** is a `404` instead, because it is the request's target:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/v1/users/99999/orders \
  -H 'Content-Type: application/json' -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 1}'
```

## Scenario 8 — An instrument with no price (SC-009, R-203)

```bash
# PGR (id 3) has no marketdata row: a MARKET order has no execution price -> 400, nothing written
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 3, "type": "MARKET", "side": "BUY", "size": 1}' | jq
# the same instrument with a LIMIT order is accepted: the price came in the request
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 3, "type": "LIMIT", "side": "BUY", "size": 1, "price": 100.00}' | jq '.status'
```

`400` then `201 NEW`. This is the plan's one departure from the statement
([ADR-0034](../../docs/adr/README.md)); the README says so.

## Scenario 9 — A limit order reserves what it commits (US3, SC-006, SC-007)

```bash
# NEW, price 900.00, reserving 90,000.00
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "LIMIT", "side": "BUY", "size": 100, "price": 900.00}' | jq '.status'
curl -s http://localhost:8080/api/v1/users/1/portfolio | jq '.availableCash, .settledCash'
```

`availableCash` **`537,500.00`**, `settledCash` still **`753,000.00`** — the money is committed, not
spent. Then the share side:

```bash
# reserve the whole PAMP position
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "LIMIT", "side": "SELL", "size": 40, "price": 1000.00}' | jq '.status'
# now one more share cannot be sold, while cash is untouched
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "SELL", "size": 1}' | jq '.rejectionReason'
```

`NEW`, then `INSUFFICIENT_HOLDINGS`. A limit order validated far above the market (`price: 2000.00`)
is also `NEW`: a price nobody will trade at is not a rejection reason, and nothing fills it
(ADR-0007).

## Scenario 10 — A limit order for an amount is bounded by your own price (US4, SC-005)

```bash
# BUY 100,000.00 at 900.00 -> 111 shares (not the 108 the close yields), reserving 99,900.00
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "LIMIT", "side": "BUY", "amount": 100000.00, "price": 900.00}' | jq '.size'
# SELL 30,000.00 at 1,000.00 -> 30 shares (not 32): the position is valued at the limit price
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "LIMIT", "side": "SELL", "amount": 30000.00, "price": 1000.00}' | jq '.size'
# the same request at 500.00 -> REJECTED: at that price the 40 shares are worth 20,000.00
curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
  -d '{"instrumentId": 47, "type": "LIMIT", "side": "SELL", "amount": 30000.00, "price": 500.00}' | jq '.rejectionReason'
```

`111`, `30`, `INSUFFICIENT_HOLDINGS`. The 30-versus-32 pair is the whole of FR-018: the price
substitutes on **both** sides of the comparison.

## Scenario 11 — Two simultaneous orders cannot spend the same pesos (US5, SC-010)

`400 PAMP` costs `370,340.00`; two of them are `740,680.00` against `627,500.00` available.

```bash
for i in 1 2; do
  curl -s -X POST http://localhost:8080/api/v1/users/1/orders -H 'Content-Type: application/json' \
    -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 400}' | jq -c '{status, rejectionReason}' &
done; wait
```

Exactly one `FILLED` and one `REJECTED` / `INSUFFICIENT_FUNDS`, in **every** run — never both filled,
never both rejected. The same shape with `SELL 40` twice fills one and rejects the other, and the
position never goes negative from these writes.

Two concurrent submissions that both fit (`size: 100` twice) both fill. Two submissions for two
**different** users never wait on each other: the lock is per user (FR-026).

The automated proof is a Testcontainers test that fires the requests from two threads against the
real database and asserts the pair of outcomes, not a `curl` race.

## Scenario 12 — The same order sent twice is executed once (US6, SC-011)

```bash
KEY=$(uuidgen)
for i in 1 2; do
  curl -s -X POST http://localhost:8080/api/v1/users/1/orders \
    -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
    -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 100}' | jq -c '{orderId, status}'
done
```

The **same** `orderId` twice, and one order in the ledger — check with the portfolio: cash fell by
`92,585.00` once. Then:

```bash
# the same key with a different body -> 409, nothing written
curl -s -X POST http://localhost:8080/api/v1/users/1/orders \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 7}' | jq
# the same key for another user -> processed as a new request (keys are per user)
curl -s -X POST http://localhost:8080/api/v1/users/2/orders \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $KEY" \
  -d '{"instrumentId": 47, "type": "MARKET", "side": "BUY", "size": 1}' | jq '.status'
# a REJECTED outcome is also replayed, and not re-decided
# a submission with no key is processed normally
```

## Scenario 13 — The ledger and the audit trail agree

After any of the above, directly against the database:

```bash
docker compose exec -T postgres psql -U trading -d trading -c \
  "SELECT o.id, o.status, o.size, o.price, h.status AS history_status, h.reason
     FROM orders o JOIN order_status_history h ON h.order_id = o.id
    WHERE o.userid = 1 ORDER BY o.id DESC LIMIT 10;"
```

Every order this feature wrote has exactly one history row, with the same status and, when rejected,
the reason (FR-022, ADR-0005). No row has a `NULL` price, and `size` is `0` only on a rejection that
never derived one.

## What the automated suite covers

`./mvnw verify` — the whole of it, with the architecture rules and the formatting check inside the
build, and every HTTP response validated against `docs/api/openapi.yaml` (ADR-0024).

| Level | What is tested there |
|---|---|
| Plain Java, no Spring, no database | the request's validity rules, the price that governs, the available-versus-needed comparison, the floor derivation and its zero case, the status each outcome takes, the reason each rejection carries |
| Testcontainers, persistence | the insert of an order and its history row in one transaction, the user-row lock, the idempotency claim and its conflict, `V4` and `V5` applying on top of the given schema |
| Testcontainers + MockMvc | scenarios 1 to 13, every rejection path and every refusal path, the concurrency pair from two threads, and the idempotent replay |

Constitution V: every rejection path above is a test, not only the happy ones (SC-014).
