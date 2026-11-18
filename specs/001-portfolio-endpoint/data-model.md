# Phase 1 Data Model: Portfolio Endpoint

**Feature**: `001-portfolio-endpoint` | **Date**: 2026-11-15 | **Plan**: [plan.md](plan.md)

Three layers, deliberately distinct: the **given schema** (unchanged), the **persistence layer**
that reads it (repositories, entities, mappers), and the **model** the rules are written against. Nothing
from the first two appears in a model signature, and the model never calls persistence: the
service reads, maps and hands the model its data (constitution VIII, ADR-0017).

## 1. Given schema (read as-is)

`V1__given_schema.sql` is immutable (constitution VI). **All identifiers are lowercase in
PostgreSQL** — the migration writes `instrumentId`, `previousClose` and `accountNumber` unquoted, so
the actual column names are `instrumentid`, `previousclose` and `accountnumber`. Native SQL and
`@Column` names use the lowercase form.

| Table | Columns this feature reads | Notes |
|---|---|---|
| `users` | `id` | existence only (FR-024); `email` and `accountnumber` unused |
| `instruments` | `id`, `ticker`, `name`, `type` | `type = 'MONEDA'` marks cash (R-009) |
| `orders` | `id`, `instrumentid`, `userid`, `size`, `price`, `side`, `status`, `datetime` | the ledger |
| `marketdata` | `instrumentid`, `close`, `date` | `previousclose`, `open`, `high`, `low` are **never read** (FR-009) |

Type mapping that matters:

| Column | SQL type | Java type | Why it matters |
|---|---|---|---|
| `orders.datetime` | `timestamp without time zone` | `LocalDateTime` | no zone conversion anywhere in this feature (R-006) |
| `orders.size` | `INT` | `int` in the entity, `BigDecimal` scale 0 in the domain | constitution II (R-008) |
| `orders.price` | `NUMERIC(10,2)` | `BigDecimal` | never `double`, including in tests |
| `marketdata.date` | `DATE` | `LocalDate` | day resolution; `UNIQUE (instrumentid, date)` makes the latest row per instrument unique, so the query needs no tiebreak (R-002, R-018) |
| `marketdata.close` | `NUMERIC(10,2)`, **nullable** | `BigDecimal` or absent | NULL is an unknown price (R-019) |

Enumerated string columns, with the values this feature recognises:

| Column | Values | Unrecognised value |
|---|---|---|
| `orders.side` | `BUY`, `SELL`, `CASH_IN`, `CASH_OUT` | rejected at mapping time, not silently ignored |
| `orders.status` | `NEW`, `FILLED`, `REJECTED`, `CANCELLED` | same |
| `orders.type` | `MARKET`, `LIMIT` | same |
| `instruments.type` | `MONEDA`, anything else | anything else is a tradable instrument |

Mapping failure is loud: an unknown `side` or `status` cannot be folded without guessing a sign or
a settlement meaning, and guessing is how money goes wrong. It surfaces as a 500 with a
`ProblemDetail`, which is correct — the data contradicts the schema's vocabulary.

### Change to the schema

One new migration, `V2__portfolio_read_indexes.sql` (R-018). No table or column changes; no
positions table and no balance column, by ADR-0002.

```sql
CREATE INDEX        idx_orders_userid_status        ON orders     (userid, status);
CREATE UNIQUE INDEX uq_marketdata_instrumentid_date ON marketdata (instrumentid, date);
```

`(userid, instrumentid)`, which ADR-0002 also names, is **not** here: no query of this feature
reads it, so it arrives with the first feature whose query does (R-018).

The second adds a **constraint**, not just an index: one price row per instrument per day, which the
given schema never stated. It protects against a duplicate feed delivery and it is the index the
latest-close query reads, which is why that query carries no tiebreak. Permitted by constitution VI
("schema constraints that protect integrity are allowed") — it constrains shape, not money. The
provided dataset satisfies it (measured: one row per `(instrumentid, date)` across all 126 rows);
**staging is unmeasured**, and if it holds a duplicate the migration fails there, which is the
constraint doing its job.

## 2. Persistence

JPA entities, one per table, in `persistence/entity`, read through Spring Data repositories in
`persistence` and turned into model values by hand-written mappers in `persistence/mapper` (R-001,
ADR-0014). Entities are never passed into the model.

| Repository | Query | Mapper |
|---|---|---|
| `UserRepository` | `existsById` | `UserMapper.toIdColumn(UserId)` — absent when the id is outside `INT`, so the user cannot exist |
| `OrderRepository` | `findOrdersOf(userId, statuses)` | `OrderMapper.toOrders`, and `toStatusColumns` for the status parameter |
| `MarketDataRepository` | `findLatestCloses(instrumentIds)` → `List<LatestClose>` (typed projection, quoted aliases) — reads nothing for an empty list | `MarketDataMapper.toCloses` → `Closes` — a NULL `close` is dropped, so it reads as no price (R-019) |

| Entity | Shape | Relationships |
|---|---|---|
| `UserEntity` | `id` | — |
| `InstrumentEntity` | `id`, `ticker`, `name`, `type` | — |
| `OrderEntity` | `id`, `userId`, `size`, `price`, `side`, `status`, `type`, `datetime` | `@ManyToOne(LAZY)` `InstrumentEntity`, fetch-joined by the ledger query |
| `MarketDataEntity` | `id`, `instrumentId`, `close`, `date` | — (only the latest-close projection is read) |

`ddl-auto: validate` stays on, so these are checked against the Flyway schema at startup.

Two queries serve the feature:

| Query | Form | Returns |
|---|---|---|
| ledger | `select o from OrderEntity o join fetch o.instrument where o.userId = :userId and o.status in :statuses order by o.datetime asc, o.id asc` | the user's orders in the statuses the fold uses |
| latest close | native `SELECT DISTINCT ON (instrumentid) …` (R-002) | one `(instrumentId, close)` per requested instrument; **absent** means no row |

`:statuses` is **not** a literal in the SQL: the model names the set — `OrderStatus.contributing()`
returns `FILLED` and `NEW` — and the service passes it to the repository, so persistence never decides
which statuses matter (R-024). `CANCELLED` and `REJECTED` rows are therefore never read nor
mapped, and the predicate is served by `idx_orders_userid_status`. What each status *means* stays in
`Order` (FR-004).

## 3. Model

Plain Java. No Spring, Jakarta, JPA or web type, and no reference to a repository or service
(constitution VIII); no comments (VII); behaviour over state (IX). It receives data and returns
data.

### Value types

| Type | Shape | Behaviour |
|---|---|---|
| `UserId` | `long` | — (exists because it crosses every layer signature and both diagnostics, where a bare `long` is interchangeable with an instrument or order id; quantity crosses no such boundary, which is why there is no `Quantity` type — R-008) |
| `InstrumentRef` | `instrumentId`, `ticker`, `name`, `type` | `isCash()` → `type == MONEDA` |
| `Order` | `orderId`, `InstrumentRef`, `OrderSide`, `OrderStatus`, `OrderKind`, `quantity`, `price`, `occurredAt` | `isSettled()`, `reservesCash()`, `reservesShares()`, `cashEffect()`, `signedQuantity()` |
| `OrderSide` | enum `BUY`, `SELL`, `CASH_IN`, `CASH_OUT` | `cashSign()`, `positionSign()` |
| `OrderStatus` | enum `NEW`, `FILLED`, `REJECTED`, `CANCELLED` | `isSettled()`, `isOpen()`, and the static `contributing()` → `{FILLED, NEW}`, which is what the service asks the repository for (R-024) |
| `OrderKind` | enum `MARKET`, `LIMIT` | — |

`Order` answers the questions the fold asks, so the fold contains no `switch` over sides and
statuses:

| Question | Answer |
|---|---|
| `isSettled()` | `status == FILLED` |
| `cashEffect()` | `isSettled() ? side.cashSign() × quantity × price : ZERO` — `CASH_IN`/`SELL` positive, `CASH_OUT`/`BUY` negative (FR-010, R-009) |
| `signedQuantity()` | `isSettled() && !instrument.isCash() ? side.positionSign() × quantity : ZERO` (FR-003, FR-005) |
| `reservesCash()` | `status == NEW && kind == LIMIT && side == BUY` (FR-011) |
| `reservesShares()` | `status == NEW && kind == LIMIT && side == SELL` (constitution I) |

`CASH_IN` and `CASH_OUT` have no `positionSign()`; they can only arrive on a `MONEDA` instrument,
and `isCash()` already excludes those rows from positions, so the cash sides never reach the
position path.

### The fold (FR-016, ADR-0002, R-004)

```text
AccountLedger(List<Order> orders)
    snapshot() -> AccountSnapshot
```

The orders arrive in `(occurredAt, orderId)` order. The snapshot is made of four figures, each
defined by its rule:

| Figure | Rule |
|---|---|
| settled cash | the sum of every order's `cashEffect()` |
| reserved cash | the sum of `committedAmount()` over the orders that `reservesCash()` |
| holdings | per instrument, the weighted-average walk (R-005) over the orders that `movesPosition()`, **in arrival order** — the only figure that depends on order; flat holdings are not listed (FR-025) |
| reserved shares | per instrument, the sum of `quantity` over the orders that `reservesShares()` |

`AccountSnapshot` is the single value both readers consume:

| Member | Returns | Read by |
|---|---|---|
| `settledCash()` | `BigDecimal` | total account value (FR-012) |
| `availableCash()` | `BigDecimal` = settled − reserved cash | portfolio (FR-011); future `BUY` validation |
| `holdings()` | `List<Holding>`, non-zero quantities only (FR-025) | positions list |
| `heldInstrumentIds()` | `List<Long>` | the service, to ask for the closes of what is held |
| `availableQuantityOf(instrumentId)` | `BigDecimal` = held − reserved shares | future `SELL` validation |

| `Holding` | Returns |
|---|---|
| `instrument()` | `InstrumentRef` |
| `quantity()` | signed `BigDecimal`, scale 0 |
| `totalCost()` | `quantity × averageCost`, from the walk |
| `isShort()` | `quantity < 0` (FR-017 to FR-019) |

**Scope boundary**: `availableQuantityOf` and `availableCash` are *computed and tested* here, but
nothing validates an order in this feature. Order submission, cancellation and validation are out
of scope (spec Assumptions); this feature only guarantees the value they will read.

### The cost-basis walk (ADR-0003, FR-007, FR-008, R-005)

State: signed `quantity`, `averageCost` per share (scale 10, `HALF_UP`).

| Incoming settled entry | Resulting state |
|---|---|
| position flat | `quantity = signed`, `averageCost = price` |
| same sign as position | `averageCost = (|q|·avg + |s|·price) / (|q| + |s|)`, `quantity += signed` |
| opposite sign, `|s| < |q|` | `quantity += signed`, `averageCost` unchanged |
| opposite sign, `|s| == |q|` | `quantity = 0`, `averageCost = 0` (reset) |
| opposite sign, `|s| > |q|` | `quantity += signed`, `averageCost = price` (crossing, FR-008) |

The only division is the weighted average, and it happens once per extending order.

### The valuation (prices applied)

```text
Portfolio.valued(AccountSnapshot snapshot, Closes closes) -> Portfolio
```

Per holding:

1. close absent → market value and return absent (FR-020), and the account total is suppressed
   (FR-021).
2. market value = `quantity × close`, scaled to 2 `HALF_UP`.
3. total return = absent when the holding is short (FR-018), when the market value is absent, or
   when `totalCost()` is zero; otherwise `(marketValue − totalCost) / totalCost × 100`, scaled to
   2 `HALF_UP` (R-007).

The two anomalies are returned, not emitted: `shortPositions()` (FR-019) and `unpricedPositions()`
(FR-022). The service logs each one ([contracts/diagnostics.md](contracts/diagnostics.md)).

Cash never reaches this loop: `isCash()` excluded it during the fold, which is FR-023's ordering
made structural (R-009).

| `Portfolio` | Returns |
|---|---|
| `totalAccountValue()` | `Optional<BigDecimal>` — `settledCash + Σ marketValue`, absent if any position is unpriced (FR-012, FR-021) |
| `availableCash()` | `BigDecimal` (FR-011) |
| `settledCash()` | `BigDecimal` — carried through from the snapshot so the response is self-reconciling (SC-007, R-016) |
| `positions()` | `List<Position>`, ticker ascending (R-017) |
| `shortPositions()` | the positions with a negative quantity (FR-019) |
| `unpricedPositions()` | the positions with no known close (FR-022) |

| `Position` | Returns |
|---|---|
| `instrument()` | `InstrumentRef` |
| `quantity()` | `BigDecimal`, scale 0 |
| `marketValue()` | `Optional<BigDecimal>`, scale 2 |
| `totalReturnPercent()` | `Optional<BigDecimal>`, scale 2, percent units |
| `isShort()` | `quantity < 0` |
| `isPriced()` | `marketValue` present |

### The service (orchestration)

`PortfolioService.portfolioOf(UserId)`, one `REPEATABLE_READ` read-only transaction (R-012):

1. `UserMapper.toIdColumn` + `UserRepository.existsById`; otherwise `UnknownUserException` → 404 (FR-024,
   R-014).
2. `OrderRepository.findOrdersOf(userId, contributing statuses)` → `OrderMapper` → `AccountLedger` →
   `snapshot()`.
3. `MarketDataRepository.findLatestCloses(snapshot.heldInstrumentIds())` → `MarketDataMapper` →
   `Portfolio.valued(snapshot, closes)`.
4. Log `shortPositions()` at `ERROR` and `unpricedPositions()` at `WARN`; return the portfolio.

## State transitions

None. The portfolio is read-only (FR-002); it reads `orders.status` and never changes it. Status
transitions belong to order submission and cancellation, with `order_status_history` (ADR-0005)
and the locking of constitution IV.

## Entity-to-requirement map

| Spec entity | Where it lives |
|---|---|
| User | `UserId`, `UserRepository`, `UserEntity` |
| Order | `Order`, `OrderEntity` |
| Instrument | `InstrumentRef`, `InstrumentEntity` |
| Market data | `MarketDataRepository`, `MarketDataEntity` — only the latest `close` |
| Position (derived) | `Holding` in the fold, `Position` in the valuation |
| Portfolio (derived) | `Portfolio` |
