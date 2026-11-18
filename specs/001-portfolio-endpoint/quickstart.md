# Quickstart: Validating the Portfolio Endpoint

**Feature**: `001-portfolio-endpoint` | **Plan**: [plan.md](plan.md) | **Contract**: [portfolio.openapi.yaml](contracts/portfolio.openapi.yaml)

How to run the feature and prove it works end to end. Scenarios only — no implementation code; the
response shape lives in the contract and the rules in [data-model.md](data-model.md).

## Prerequisites

- JDK 21
- Docker (local database and Testcontainers)

## Run it

```bash
docker compose up -d
./mvnw spring-boot:run
```

PostgreSQL 18.7 on port **5433**; Flyway applies `db/migration` and loads `db/seed`
(the provided dataset). The app is on `http://localhost:8080`.

Every request below is also in [`docs/api/trading-api.http`](../../docs/api/trading-api.http),
runnable from IntelliJ's HTTP Client or VS Code's REST Client (research R-023) — the `curl` lines
here are for readers without either.

## Scenario 1 — the seeded user (SC-001 to SC-004, SC-012, SC-009)

```bash
curl -s http://localhost:8080/api/v1/users/1/portfolio | jq
```

Expected — every figure below was computed by hand from `V1000__given_dataset.sql`:

| Field | Value | Why |
|---|---|---|
| `settledCash` | `753000.00` | pesos effect of every `FILLED` order, reservations included |
| `availableCash` | `627500.00` | settled − reserved `125,500.00` (50 LOMA at `710` + 60 BMA at `1,500`, both `BUY LIMIT NEW`) |
| `totalAccountValue` | `889756.00` | `753,000.00` + `37,034.00` + `114,750.00` − `15,028.00` |
| `positions[]` | BMA, METR, PAMP | ticker ascending |

| Ticker | `quantity` | `marketValue` | `totalReturnPercent` |
|---|---|---|---|
| BMA | `-10` | `-15028.00` | `null` |
| METR | `500` | `114750.00` | `-8.20` |
| PAMP | `40` | `37034.00` | `-0.45` |

Three things to check beyond the numbers:

1. **No `ARS` position.** User `1` has `CASH_IN` and `CASH_OUT` rows on instrument 66 (`ARS`,
   `MONEDA`); they are cash, never a holding (FR-005).
2. **`totalAccountValue` is not `null`.** `ARS` has no market data. If the exclusion above ran
   after the unpriced-position rule, this field would be `null` — that ordering is FR-023, and
   this assertion is what pins it (SC-012).
3. **Exactly one ERROR line in the application log**, naming `userId=1 ticker=BMA quantity=-10`,
   and the request still `200`s ([contracts/diagnostics.md](contracts/diagnostics.md), SC-009).

Closes-the-loop check (SC-007): `settledCash + Σ marketValue = 753,000.00 + 136,756.00 =
889,756.00 = totalAccountValue`, verifiable from the response alone — which is why settled cash is
published (research R-016). Note that `availableCash + Σ marketValue` is `764,256.00` and
deliberately **not** the total: the `125,500.00` gap is what the open `BUY LIMIT` orders commit,
and the total counts reserved pesos because they are still the user's money (FR-012, SC-004).

## Scenario 2 — a user who has never traded (SC-005, FR-014)

Users `2`, `3` and `4` exist in the seed with no orders at all, so this needs no fixture.

```bash
curl -s http://localhost:8080/api/v1/users/2/portfolio | jq
```

Expect `200`, `positions: []`, `availableCash: 0.00`, `settledCash: 0.00`,
`totalAccountValue: 0.00` — zeros, not `null`, because those values are known (ADR-0013).

## Scenario 3 — an id that is not a user (SC-011, FR-024)

```bash
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/users/999/portfolio   # 404
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/api/v1/users/abc/portfolio   # 400
```

Both answer `application/problem+json`. The point of the pair: `404` for user `999` is
distinguishable from Scenario 2's `200` with an empty portfolio — a wrong id is never served as a
real account that has never traded.

## Scenario 4 — a held instrument with no price (FR-020 to FR-022, SC-010)

Not reachable through the API and not present in the seed (ADR-0013): `PGR` and `IRCP` have no
market data and nobody holds them, and no order can create the state — a `MARKET` order has no
`close` to execute against and a `LIMIT` order never executes (ADR-0007). So it is produced by
writing the ledger row directly, which is the same thing the test fixture does (research R-020).

```bash
docker compose exec -T postgres psql -U trading -d trading <<'SQL'
INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime)
VALUES (66, 3, 500000, 1, 'CASH_IN', 'FILLED', 'MARKET', '2023-07-14 10:00:00'),
       (3,  3, 25,     900, 'BUY',    'FILLED', 'MARKET', '2023-07-14 11:00:00');
SQL
curl -s http://localhost:8080/api/v1/users/3/portfolio | jq
```

Expect: `PGR` listed with `quantity: 25`, `marketValue: null`, `totalReturnPercent: null`;
`settledCash` and `availableCash` both `477500.00` (cash is still known, and nothing is reserved);
**`totalAccountValue: null`** — one unpriced
holding suppresses the total rather than publishing a partial sum; and one WARN line naming
`userId=3 ticker=PGR`. No ERROR: nothing is broken, a price is merely unknown.

Reset with `docker compose down -v && docker compose up -d`.

## Scenario 5 — a position taken through zero (SC-013, FR-008)

User `1`'s BMA already crosses zero (`0 → +20 → -10`). Buying 30 at the latest close reopens it
long, and the crossing resets the basis: the 10 shares that closed the short carry nothing forward.

```bash
docker compose exec -T postgres psql -U trading -d trading <<'SQL'
INSERT INTO orders (instrumentid, userid, size, price, side, status, type, datetime)
VALUES (31, 1, 30, 1502.80, 'BUY', 'FILLED', 'MARKET', '2023-07-14 16:00:00');
SQL
curl -s http://localhost:8080/api/v1/users/1/portfolio | jq '.positions[] | select(.ticker=="BMA")'
```

Expect `quantity: 20`, `marketValue: 30056.00`, `totalReturnPercent: 0.00` — the basis is
`1,502.80` per share, the same as the close, so the return is exactly zero. Any non-zero return
here means the pre-crossing `1,540` leaked into the basis — carrying it forward over all 20 shares
would read `-2.41%`. The ERROR from Scenario 1 stops appearing,
because the position is no longer short.

## Scenario 6 — two reads agree (SC-006)

```bash
diff <(curl -s http://localhost:8080/api/v1/users/1/portfolio) \
     <(curl -s http://localhost:8080/api/v1/users/1/portfolio) && echo identical
```

No intervening order, identical bytes — including the position order, which is why ticker
ascending is fixed rather than left to map iteration (research R-017). This holds because the
portfolio is a pure function of the rows it reads, not because of any transaction: these are two
requests and no isolation level spans them (research R-012).

## The automated suite

```bash
./mvnw verify
```

What proves what:

| Level | Runs on | Covers |
|---|---|---|
| Unit, plain Java | no Spring, no database | the fold (quantities, statuses, cash, reservations, zero and through-zero), the cost-basis walk, the projection's absent/diagnostic rules against a recording fake |
| Persistence, Testcontainers | `postgres:18.7`, same migrations and seed | the ledger query's ordering, fetch join and status filter — user `1`'s `CANCELLED` and `REJECTED` rows must not come back at all (R-024) — the latest-close query picking `2023-07-14` **per instrument**, and user existence |
| Functional, Testcontainers + MockMvc | full context | Scenarios 1 to 6 above as assertions, including the `404`/`400` pair and the `@Sql` fixture for Scenario 4 |

No test touches the provided (staging) database, and no test stands PostgreSQL in with H2
(constitution V, ADR-0010). Rejection paths — unknown id, malformed id, unpriced holding, short
position — ship with the feature, not after it.
