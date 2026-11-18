# Quickstart: Validating Cash Transfer

**Feature**: `004-cash-transfer` | **Plan**: [plan.md](plan.md) |
**Contract**: [contracts/cash-transfer.openapi.yaml](contracts/cash-transfer.openapi.yaml)
(design-time; the live one is [docs/api/openapi.yaml](../../docs/api/openapi.yaml))

How to run the feature and prove it works end to end. Scenarios only — no implementation code; the
response shape lives in the contract, the rules in [spec.md](spec.md) and the decisions in
[research.md](research.md).

Every figure below comes from `src/main/resources/db/seed/R__given_dataset.sql`, folded the way
`AccountLedger` does, and is listed in
[research.md](research.md#measured-facts-this-phase-relied-on). None of it is estimated.

> **This feature writes.** Each scenario assumes the seed's starting state. Reset between scenarios
> with `docker compose down -v && docker compose up -d`, or track the cash as you go. The automated
> suite gets a fresh container per class, so it does not have this problem.

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

| User | Settled cash | Reserved by `NEW BUY` | Available cash |
|---|---|---|---|
| `1` | `753,000.00` | `125,500.00` (50 × 710 + 60 × 1,500) | `627,500.00` |
| `2`, `3`, `4` | `0.00` | `0.00` | `0.00` — no orders at all |

`PAMP` (id `47`) last closed at `925.85`.

```bash
curl -s localhost:8080/api/v1/users/1/portfolio   # available cash 627500.00
```

## Scenario 1 — Deposit, withdraw, empty the account (US1 scenarios 1, 2; SC-001)

```bash
curl -s -X POST localhost:8080/api/v1/users/2/cash-transfers \
  -H 'Content-Type: application/json' -d '{"side":"CASH_IN","amount":100000}'
```

Expect `201`, `side: CASH_IN`, `amount: 100000.00`, `status: FILLED`, `rejectionReason: null`. The
portfolio of user `2` then reports settled and available cash `100000.00`.

Then withdraw `100001` → `201`, `REJECTED`, `INSUFFICIENT_FUNDS`, cash unchanged (US1 scenario 8).
Withdraw `100000` → `201`, `FILLED`, cash `0.00`.

## Scenario 2 — The funds boundary, to the peso (US1 scenario 3; SC-002)

User `1`: withdraw `627501` → `REJECTED`. Withdraw `627500` → `FILLED`; available cash `0.00`.

## Scenario 3 — Reserved pesos cannot be withdrawn (US1 scenario 4; SC-003)

User `1`: withdraw `700000` → `REJECTED` with `INSUFFICIENT_FUNDS`, although settled cash is
`753,000.00`. The portfolio still reports available `627,500.00`.

## Scenario 4 — A user with no orders withdraws (US1 scenario 5)

User `3`: withdraw `1` → `201`, `REJECTED`. The rejected order exists in the ledger with a history
row carrying the reason (US1 scenario 7).

## Scenario 5 — A deposit is immediately spendable (US1 scenario 6; SC-001)

User `4`: deposit `500000`, then `POST /api/v1/users/4/orders` with
`{"instrumentId":47,"type":"MARKET","side":"BUY","size":100}` → `201`, `FILLED` at `925.85`;
available cash `407,415.00`.

## Scenario 6 — Requests that are refused, not rejected (edge cases; FR-002 to FR-005)

Each answers the status shown and **writes nothing** (the user's order count is unchanged):

| Body / target | Answer |
|---|---|
| `{"side":"BUY","amount":100}` | `400` — not a transfer side |
| `{"side":"CASH_IN"}` | `400` — amount missing |
| `{"side":"CASH_IN","amount":0}`, `-5` | `400` — below one peso |
| `{"side":"CASH_IN","amount":100.50}` | `400` — not whole pesos |
| `{"side":"CASH_IN","amount":100.00}` | `201` — the same as `100` |
| `{"side":"CASH_IN","amount":2147483648}` | `400` — beyond the ledger's size column |
| `{"side":"CASH_IN","amount":100,"instrumentId":66}` (and `type`, `price`, `size`, `status`) | `400` — a field outside the transfer's shape |
| user `999` with a valid body | `404` |
| user `999` with `{"side":"BUY"}` | `400` — the body is judged first (ADR-0033) |
| `Idempotency-Key:` blank | `400` |

## Scenario 7 — A withdrawal and an order cannot spend the same pesos (US2; SC-005)

Not reproducible by hand reliably; the automated suite fires these from two threads at the same
instant, against user `1` (`627,500.00` available):

| Pair | Expected |
|---|---|
| withdraw `400000` + withdraw `400000` | exactly one `FILLED`, one `REJECTED` |
| withdraw `400000` + `MARKET BUY` 400 `PAMP` (`370,340.00`) | exactly one accepted, one `REJECTED` |
| withdraw `300000` + withdraw `300000` | both `FILLED`; available `27,500.00` |
| user `1` withdraws while user `2`'s row is held locked from another connection | user `1` is not delayed |

In every run, available cash is never below zero afterwards.

## Scenario 8 — The same transfer sent twice moves money once (US3; SC-006)

```bash
for i in 1 2; do
  curl -s -X POST localhost:8080/api/v1/users/2/cash-transfers -H 'Idempotency-Key: dep-1' \
    -H 'Content-Type: application/json' -d '{"side":"CASH_IN","amount":100000}'
done
```

Both answers carry the same `orderId`; user `2`'s cash is `100000.00`, not `200000.00`. Then:

| Request | Expected |
|---|---|
| `dep-1` with `{"side":"CASH_OUT","amount":100000}` | `409`, nothing written |
| `dep-1` with `{"side":"CASH_IN","amount":100000.00}` | `201`, the same order (same request) |
| user `3` sends `dep-1` | processed as new: keys are per user |
| user `2` withdraws `200000` with key `w-1` → `REJECTED`; deposits more; retries `w-1` | the stored `REJECTED` outcome, with its reason |
| user `1` places an order with key `k-1`, then a transfer with `k-1` | `409` on the transfer — one namespace across endpoints |
| two requests with the same key at the same instant (automated) | one order; both answers name it |

## Scenario 9 — The extraction changes nothing (US4; SC-008)

```bash
./mvnw verify
```

After US4, the order submission suite and the cash transfer suite pass with **no test changed**, and
`ArchitectureTest` asserts that only `service/userwrite` takes the user lock and claims a key.

## What the automated suite covers

| Level | What |
|---|---|
| Plain Java (`model/transfer`) | amount validity and bounds; the decision table, including the exact boundary and a negative available cash |
| Plain Java (`service/transfer`) | the canonical form: operation tag, `100` = `100.00`, differs from any order form |
| Testcontainers (`persistence`) | a `CASH_IN`/`CASH_OUT` row and its history row are appended and read back with their side |
| Testcontainers + MockMvc (`controller`) | scenarios 1–8, every response validated against `docs/api/openapi.yaml`; the two-thread pairs of scenario 7; concurrent same-key requests |
| ArchUnit | the adjusted slice rule; only `service/userwrite` locks and claims |
