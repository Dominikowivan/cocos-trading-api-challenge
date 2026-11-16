# Phase 0 Research: Portfolio Endpoint

**Feature**: `001-portfolio-endpoint` | **Date**: 2026-11-15 | **Plan**: [plan.md](plan.md)

Every technical decision this feature needs, with the alternatives that were weighed and why the
chosen one won. Decisions already taken elsewhere (ADR-0002, 0003, 0004, 0007, 0012, 0013) are
cited, never re-decided.

Three entries are marked **ADR candidate**: they had alternatives worth weighing, they are not
recorded anywhere yet, and they bind decisions beyond this feature — so under constitution
principle VII each owes an ADR written at implementation time, with `docs/adr/README.md` updated in
the same change. The proposed numbers are reserved here so the plan can reference them.

| # | ADR candidate | Proposed record |
|---|---|---|
| [R-001](#r-001-data-access-with-spring-data-jpa) | Data access strategy | ADR-0014 |
| [R-004](#r-004-the-fold-returns-one-account-snapshot) | The ledger fold's contract | ADR-0015 |
| [R-007](#r-007-decimal-scale-and-rounding-policy) | Scale and rounding policy | ADR-0016 |

Two decisions that were first drafted as ADR candidates — [R-011](#r-011-diagnostics-are-a-domain-port-not-a-logger-call)
(diagnostics as a domain port) and [R-013](#r-013-errors-as-rfc-9457-problemdetail)
(`ProblemDetail`) — are **recorded here and nowhere else**. Both are structural choices inside a
feature rather than decisions the rest of the codebase has to live with: a seam for an assertion,
and a media type the framework already produces. This file is their record; if either is ever
reversed, the reversal is what deserves an ADR.

**Amended 2026-11-15, after the first implementation.** The author rejected the ports-and-adapters
shape this research assumed: model objects fetched their own data through ports, and the flow of a
request could not be followed. Constitution 2.0.0 and
[ADR-0017](../../docs/adr/0017-layers-with-a-service-that-orchestrates.md) replace it with layers —
the service orchestrates repositories, mappers and a pure model. R-011 is reversed by that ADR; R-004,
R-012, R-014 and R-024 keep their decisions with the mechanics restated. Each carries a note. The
entries are otherwise left as written, because they are the record of how the decisions were
reached.

## Measured facts this phase relied on

Measured on `src/main/resources/db/seed/V1000__given_dataset.sql` on 2026-11-15, not estimated:

- 66 instruments, 65 `ACCIONES` and one `MONEDA` (`ARS`, id 66, name `PESOS`).
- 126 `marketdata` rows: exactly two per instrument for 63 instruments, dated `2023-07-13` and
  `2023-07-14`. `PGR`, `IRCP` and `ARS` have none.
- **No row has a NULL `close`.** `open`, `high` and `low` are NULL in two rows each. The column is
  still nullable, which is why [R-019](#r-019-a-null-close-is-an-unknown-price) exists.
- All 11 `orders` rows belong to user `1`. Users `2`, `3` and `4` have no orders at all, so
  SC-005 (an existing user who has never traded) needs no fixture beyond the seed.
- User `1`: settled cash `753,000.00`, reserved `125,500.00` (two `BUY LIMIT NEW`: 50 LOMA at
  `710` and 60 BMA at `1,500`), available `627,500.00`; positions PAMP `40`, METR `500`, BMA
  `-10`; latest closes `925.85`, `229.50`, `1,502.80`; values `37,034.00`, `114,750.00`,
  `-15,028.00`; total `889,756.00`. Every acceptance number in the spec reproduced by hand from
  the seed.

## Decisions

### R-001: Data access with Spring Data JPA

**ADR candidate — ADR-0014.** The challenge statement asks explicitly for the data-access strategy
to be chosen and justified, and nothing in the repository records one yet.

**Decision**: Spring Data JPA (Hibernate) entities for `users`, `instruments`, `orders` and
`marketdata`, with repository interfaces in the persistence adapter and **hand-written mapping**
into domain types (constitution VIII). Reads that JPQL cannot express use `@Query(nativeQuery =
true)` on the same repositories. `ddl-auto: validate` stays on, so the entities are checked against
the Flyway schema at startup instead of being trusted.

**Rationale**, in order of weight:

1. **It is already the project's data-access dependency.** `spring-boot-starter-data-jpa` and
   `spring-boot-starter-data-jpa-test` are in `pom.xml` from the skeleton commit, and CI builds
   against them. Choosing JPA adds nothing to the build; choosing anything else adds a dependency
   *and* leaves two access styles in a four-table project.
2. **`ddl-auto: validate` checks the mapping against the real schema at startup.** The entities
   are verified against what Flyway actually created — in local runs, in every Testcontainers
   test and on staging — so a column renamed in a migration fails fast instead of at the first
   query. That check is free here and has no equivalent in a row-mapper approach.
3. **One abstraction covers both query shapes.** Where JPQL cannot express a query — the
   latest-close resolution, [R-002](#r-002-latest-close-per-instrument-via-distinct-on) — the same
   repository takes native SQL, so there is no second mapping layer for the awkward case.

The cost usually levelled at JPA — entities leaking into business logic — is already blocked by
principle VIII: entities never cross into the domain, and the mapping is hand-written.

**Honest scope of this decision**: for *this* feature the choice barely matters, since it is two
queries and a hand-written mapping either way. It is recorded as an ADR because it binds the write
features, and because the alternatives below are strong enough that a reviewer deserves the reason
rather than an inherited default.

**Alternatives considered**:

- **`JdbcClient` with explicit SQL and row mappers.** Attractive for this feature alone: the
  portfolio is two queries, and hand-written SQL is the most direct expression of them. Rejected
  because order submission would then hand-write its own insert, its status-history insert
  (ADR-0005) and its locking read, and because the mapping layer would exist twice — once per
  access style — which is the wrong place to economise.
- **Spring Data JDBC.** Lighter than JPA, still a repository abstraction, and its aggregate model
  (an order root with its status history as a child collection) fits ADR-0005 well. It also
  **does support pessimistic locking**: `@Lock(LockMode.PESSIMISTIC_WRITE)` on a derived query
  method, documented in
  [Transactionality](https://docs.spring.io/spring-data/relational/reference/jdbc/transactions.html),
  so locking is not the discriminator. Rejected on two narrower points: that `@Lock` is
  [documented as ignored on string-based `@Query` methods](https://github.com/spring-projects/spring-data-relational/blob/4.1.0/src/main/antora/modules/ROOT/pages/jdbc/transactions.adoc),
  which is the shape a `SELECT … FOR UPDATE` over a user's ledger would take; and that it offers
  no startup validation of the mapping against the schema (point 2 above). A close call, and the
  one alternative worth revisiting if JPA's session semantics ever cause trouble on the write path.
- **jOOQ.** Type-safe SQL and the best fit for query-heavy work. **Managed by the Spring Boot
  BOM** — `jooq-bom` 3.21.9, `spring-boot-starter-jooq` and the `jooq-codegen-maven` plugin are all
  in `spring-boot-dependencies-4.1.2.pom` (measured) — so "an unmanaged dependency" is not a reason
  and the earlier draft of this entry was wrong to say so. Rejected on build shape: the type-safe
  API comes from code generation, which needs the schema available during the build, so CI must
  apply the migrations to a database before compiling. That is real upkeep for four tables and
  three queries. Its compile-time schema check is *stronger* than point 2, which is why this is a
  trade and not a dismissal.
- **JPA with derived-query methods only, no native SQL.** Rejected because the latest-close
  resolution cannot be expressed in JPQL without loading rows that will be thrown away.

### R-002: Latest close per instrument via `DISTINCT ON`

**Decision**: one native PostgreSQL query resolving the latest priced row **per instrument**:

```sql
SELECT DISTINCT ON (m.instrumentid) m.instrumentid, m.close
FROM marketdata m
WHERE m.instrumentid = ANY(:instrumentIds)
ORDER BY m.instrumentid, m.date DESC
```

No tiebreak below `date`, and none is needed: `UNIQUE (instrumentid, date)`
([R-018](#r-018-indexes-arrive-as-a-new-flyway-migration)) makes `(instrumentid, date DESC)` a
total order over the table, so "the latest row of this instrument" is a single row by construction
rather than by a sort rule. The same unique index is what the query sorts on.

**Rationale**: it is a single index-friendly scan, returns exactly one row per instrument, and it
satisfies FR-006 structurally — the latest row is resolved per instrument, so no shared "as of"
date can creep in. Constitution III permits this: SQL is allowed to read and aggregate; what it may
not carry is business rules, and "the newest row wins" is row selection, not a rule about money.

**Alternatives considered**:

- **`ROW_NUMBER() OVER (PARTITION BY instrumentid ORDER BY date DESC)` in a subquery.** Portable
  across engines and equally correct. Rejected on readability only; portability buys nothing,
  since constitution and ADR-0010 pin PostgreSQL 18.7 in every environment including tests.
- **Correlated `MAX(date)` subquery.** The textbook form, and the slowest: it scans `marketdata`
  twice, and it returns two rows rather than one if a date ever repeats.
- **One global `MAX(date)` applied to every instrument.** Cheapest query, and wrong: it is exactly
  the bug FR-006 forbids. In the seed every instrument happens to share the same two dates, so this
  would pass every test built from the seed and fail the first time a feed lags one instrument.
  Named here so the shortcut is rejected on the record rather than rediscovered.
- **Load every `marketdata` row for the held instruments and pick the latest in Java.** Tempting
  because the seed holds only two rows per instrument. Rejected: two rows is a property of this
  dataset, not of the table, and the amount of data read would grow with price history.

### R-003: Prices are not an input to the fold

**Decision**: two queries, two steps. The fold reads only `orders` (joined to `instruments` for
ticker and type) and produces an `AccountSnapshot`. Market prices are fetched afterwards, for the
instrument ids the snapshot reports as held, and applied by a separate projection step.

**Rationale**: FR-016 requires order validation to consume the same fold. Validation needs
quantities and available cash; it has no use for a market price. Feeding prices into the fold would
force that future caller to supply something it does not care about, and would make the fold's unit
tests depend on price fixtures. It also keeps the second query's `IN` list as small as the number
of instruments actually held.

**Alternatives considered**:

- **A single query joining `orders`, `instruments` and the latest `marketdata`.** One round trip.
  Rejected for the coupling above, and because the join repeats the same close on every order row
  of the instrument.
- **A price lookup port called lazily per position.** Rejected: that is N+1 queries, and the
  position count is known before the first lookup.

### R-004: The fold returns one account snapshot

**ADR candidate — ADR-0015.** ADR-0002 decided *that* one service owns the fold; it did not decide
what that service hands back, and FR-016's "the two cannot disagree" is a property of the shape,
not of the file layout.

> **Amended (ADR-0017):** `AccountLedger` is now a model object built from the entries the service
> read — `new AccountLedger(orders).snapshot()` — not a service holding a port. The contract below
> is unchanged. The "single pass" was later replaced by one small method per figure, for
> readability; see ADR-0015's consequences.

**Decision**: a domain service `AccountLedger` with one entry point, `snapshotOf(userId)`, whose
return value `AccountSnapshot` is computed in a **single pass** over the user's orders and carries
everything either reader needs:

| Member | Meaning | Who reads it |
|---|---|---|
| `settledCash()` | pesos effect of every `FILLED` order | portfolio total (FR-012) |
| `availableCash()` | settled minus cash reserved by `BUY LIMIT NEW` | portfolio (FR-011), `BUY` validation |
| `holdings()` | one `Holding` per instrument with a non-zero `FILLED` quantity | positions list (FR-003) |
| `availableQuantityOf(instrumentId)` | held minus shares reserved by `SELL LIMIT NEW` | `SELL` validation |

`Holding` exposes `quantity()` and `totalCost()`; the cost accumulator is internal to the walk.
Members are behaviour, not data bags (constitution IX): a caller asks the snapshot a question
rather than fetching accumulators and doing arithmetic of its own.

Reserved **shares** are computed even though this feature never reports them. That is the point:
constitution I requires validation to check available holdings, and computing them in a second
place later is how the two truths come back.

**Rationale**: one pass, one object, one place where the signs of `BUY`/`SELL`/`CASH_IN`/`CASH_OUT`
and the meaning of each status are decided. A future validator that calls `snapshotOf` cannot
disagree with the portfolio, because it is reading the same value.

**Alternatives considered**:

- **Separate methods per figure (`availableCash(userId)`, `quantityOf(userId, instrumentId)`).**
  Reads well at the call site. Rejected: each call re-folds the ledger, so a portfolio becomes one
  query per figure, and nothing stops the two call sites from folding with different filters —
  precisely the divergence FR-016 forbids.
- **Return a `Map<InstrumentId, BigDecimal>` plus a cash `BigDecimal`.** Fewer types. Rejected
  under constitution IX: the caller then owns the arithmetic, and the "subtract reservations" rule
  would be duplicated in the validator.
- **Let order validation own the fold and have the portfolio consume the validator.** Rejected:
  it points the dependency the wrong way — a read-only projection would depend on the write path.
- **An event-sourced projection kept in memory.** Rejected as the snapshot-plus-delta design
  ADR-0002 deliberately deferred.

### R-005: The cost-basis walk carries quantity and average cost

**Decision**: the walk over an instrument's `FILLED` orders keeps two values, signed `quantity` and
`averageCost` per share, and applies four cases per order:

| Case | Effect |
|---|---|
| position is flat | quantity becomes the order's signed size; average cost becomes its price |
| order extends the position (same sign) | average cost becomes the weighted average of the two legs |
| order reduces it without reaching zero | quantity shrinks; **average cost unchanged** |
| order reaches zero | both reset to zero |
| order crosses through zero | quantity is the remainder; average cost becomes the crossing order's price (FR-008) |

`totalCost()` is `quantity × averageCost`. Total return is `(marketValue − totalCost) / totalCost`,
expressed in percent units.

**Rationale**: this is ADR-0003's weighted average with FR-008's crossing rule, held in two
accumulators as that ADR promised. Keeping *average cost* rather than *total cost* means the
reduce case needs no division at all — the division happens once, when two legs are averaged — so
rounding enters the walk in one identifiable place ([R-007](#r-007-decimal-scale-and-rounding-policy)).

**Alternatives considered**:

- **Keep a running `totalCost` instead of `averageCost`.** Equivalent in exact arithmetic.
  Rejected because every partial sale then requires `totalCost × (newQuantity / oldQuantity)`, a
  division per reducing order, and rounding error accumulates across the walk.
- **Keep a queue of lots (FIFO) and derive the average from it.** Rejected by ADR-0003; it also
  reintroduces the state that ADR chose to avoid.
- **Treat a crossing order as two orders (one closing, one opening) before the walk.** Clean
  conceptually and genuinely close to the chosen form. Rejected because splitting a ledger row into
  two synthetic rows means the walk no longer operates on what the ledger says, and the split logic
  needs its own tests anyway; the crossing case inside the walk is three lines.

### R-006: The walk is ordered by `(datetime, id)`

**Decision**: `ORDER BY o.datetime ASC, o.id ASC`. `orders.datetime` is `timestamp without time
zone` and maps to `LocalDateTime`; no zone conversion is performed anywhere in this feature.

**Rationale**: weighted average cost with a crossing rule is order-dependent, so an undefined
order makes the result non-deterministic and breaks SC-006 (two identical requests, identical
figures). `datetime` has second resolution and is not unique in principle; `id` is the insertion
order and a primary key, so the pair is a total order.

**Alternatives considered**:

- **`ORDER BY datetime` alone.** Rejected: two orders sharing a timestamp would be folded in
  whatever order PostgreSQL returned, and for a crossing pair that changes the cost basis.
- **`ORDER BY id` alone.** Equivalent on this dataset and rejected on meaning: the ledger's
  chronology is `datetime`, and an out-of-order backfill would make `id` lie.
- **Sort in Java after loading.** Rejected: the database already has the rows in an index-ordered
  scan, and the sort would be untested work.

### R-007: Decimal scale and rounding policy

**ADR candidate — ADR-0016.** Constitution II requires every division and rescale to state its
scale and `RoundingMode`; it does not say which, and FR-015 demands one stated policy. Two
reviewers computing `-0.45%` by hand under different policies would disagree and both be right.

**Decision**:

| Figure | Scale | Rounding |
|---|---|---|
| Money on the wire (market value, available cash, total account value) | 2 | `HALF_UP` |
| Quantity | 0 | exact, no rounding needed (`size` is an `INT`) |
| Total return, in percent units | 2 | `HALF_UP` |
| Internal average cost per share | 10 | `HALF_UP` |

Money arithmetic itself (`quantity × close`, sums) is exact — `NUMERIC(10,2)` inputs multiplied by
an integer cannot need rounding — so the output `setScale(2, HALF_UP)` is a normalisation, not a
loss. The only genuine division in the feature is the return ratio, plus the weighted average
inside the walk.

**Rationale**: `HALF_UP` is what a reviewer reproducing the numbers on paper or in a spreadsheet
applies, which matters for an exercise whose acceptance criteria are hand-computed figures. Scale
10 for the internal average is wide enough that the ratio's second decimal is never in doubt
(`37,034 / 37,200` needs five digits to settle at `-0.45`), and it is stated rather than inherited
from a `MathContext` default.

**Alternatives considered**:

- **`HALF_EVEN` (banker's rounding) everywhere.** The accounting convention, and the better choice
  when rounding is applied repeatedly to money that is then summed. Rejected because nothing here
  rounds money — only the two derived figures round, and each rounds once — so its advantage does
  not apply, while its disadvantage (a reviewer's hand calculation disagreeing at a `.005` edge)
  does. Worth revisiting if a future feature rounds intermediate money.
- **`MathContext.DECIMAL64` for the internal average.** Fewer decisions. Rejected: significant
  digits are not a scale, so the guarantee is harder to state and it silently rounds with
  `HALF_EVEN`, contradicting the row above.
- **Return as a ratio (`-0.0045`) rather than percent units (`-0.45`).** Cleaner for arithmetic.
  Rejected: the statement asks for `rendimiento total (%)`, and the spec's acceptance numbers are
  percent units. The field name carries the unit ([R-016](#r-016-the-response-carries-exactly-the-three-figures-asked-for)).
- **Scale 4 on the return, for basis-point precision.** Rejected: it publishes precision the
  inputs do not have and would make SC-003's `-0.45` an approximation of the contract.

### R-008: Quantity is a `BigDecimal` of scale 0

**Decision**: quantities are `BigDecimal` with scale 0 throughout the domain, even though `size` is
an `INT` and fractional shares do not exist.

**Rationale**: constitution II: anything that takes part in money arithmetic is `BigDecimal`, and
quantity is multiplied by a close price. The alternative forces a conversion at exactly the moment
money is computed, which is the moment to avoid surprises. Scale 0 keeps JSON output an integer
(`-10`, not `-10.00`).

**Alternatives considered**:

- **`long` for quantity, converting at the multiplication.** Honest about shares being whole and
  cheaper. Rejected on the constitution's wording and because the conversion point multiplies: the
  walk, the market value and the reservation subtraction each need it.
- **A `Quantity` value type wrapping the `BigDecimal`.** Rejected, but **not** on constitution IX's
  "no second real use" clause, which the first draft of this entry cited. That reasoning was
  inconsistent: `UserId` is a one-line wrapper too, and it survives. The distinction is where each
  type earns its keep. `UserId` exists **at a boundary**: it appears in all four port signatures
  and in both diagnostics, where a bare `long` is silently interchangeable with an instrument id or
  an order id, and the compiler would not notice. `Quantity` would wrap a value that crosses no
  boundary — it is born in the fold, consumed by the projection two classes away, and never
  travels as a bare parameter next to something it could be confused with. What it would prevent
  instead is mixing a quantity with an amount of money, and the cheap half of that guard is
  `BigDecimal` plus names; the expensive half is a `Money` type, which this feature does not have.
  **If `Money` is ever introduced, `Quantity` comes with it** — one without the other is the
  inconsistency, not the pair.

### R-009: Cash is detected by instrument type, and every filled order moves it

**Decision**: an instrument is cash when its `type` is `MONEDA`. Settled cash is the sum over the
user's `FILLED` orders of `± size × price`, with `CASH_IN` and `SELL` adding and `CASH_OUT` and
`BUY` subtracting — the same expression for all four sides. A `MONEDA` instrument never becomes a
position, and that exclusion is applied while classifying the row, which is **before** any price
is looked up (FR-023).

**Rationale**: the statement models cash as a `MONEDA` instrument, so the type is the contract and
the `ARS` ticker is data. `CASH_IN` rows carry `price = 1`, so `size × price` is the transferred
amount and the uniform expression needs no special case. FR-023's ordering is structural here
rather than a rule to remember: cash rows never reach the positions map, so the unpriced-position
check cannot see them.

**Alternatives considered**:

- **Detect cash by ticker `ARS`.** Rejected: it hard-codes one row of `instruments` and would
  break on a second currency, while the type is what the statement specifies.
- **Fold only the `ARS` rows' `size` for cash.** The literal reading of "use the `size` column",
  and wrong: it yields `900,000.00` for user `1` instead of `753,000.00`, because purchases and
  sales move pesos without writing an `ARS` row. FR-010 exists to close this; recorded here so the
  measured counter-example stays attached to the decision.
- **A dedicated cash accumulator per `MONEDA` instrument, keyed by instrument.** Rejected as
  unjustified generality: all prices are in pesos by the statement's own constraint, and a second
  currency would need conversion rules this feature does not have.

### R-010: Absent is `Optional` in the domain and `null` on the wire

**Decision**: domain types carry `Optional<BigDecimal>` for market value, total return and total
account value. The response DTO carries nullable `BigDecimal` and the field is **always present**
in the JSON, serialised as `null`.

**Rationale**: ADR-0013 decided absent-not-zero; this is how it is represented. `Optional` in the
domain means the projection cannot forget the case — the compiler asks. An always-present `null`
on the wire means a client distinguishes "unknown" from "field I forgot to read", and the response
shape never changes between users.

**Alternatives considered**:

- **Omit the field when unknown** (`JsonInclude.NON_NULL`). Smaller payloads. Rejected: absence of
  a key is indistinguishable from a contract change or a client bug, and ADR-0013's whole point is
  that the unknown must be legible.
- **Nullable `BigDecimal` inside the domain too.** Fewer wrappers. Rejected: nothing then forces
  the projection to handle the unpriced case, and FR-020/021 are exactly that case.
- **A sentinel such as `-1` or a `valued: false` flag per position.** Rejected — the flag variant
  is the alternative ADR-0012 already weighed and discarded on contract cost.

### R-011: Diagnostics are a domain port, not a logger call

> **Reversed 2026-11-15 by [ADR-0017](../../docs/adr/0017-layers-with-a-service-that-orchestrates.md).**
> The model now returns the anomalies (`Portfolio.shortPositions()`, `unpricedPositions()`) and
> `PortfolioService` logs them; the port, its logging adapter and the recording fake are gone. This
> is close to the second alternative below, with the two objections to it answered: the logging
> happens in the service, not in the web layer, so it fires once per valuation whoever calls the
> service; and the anomalies live on the model `Portfolio`, while the HTTP response is a separate
> DTO that never carries them. SC-009's "exactly one" is still asserted in a plain unit test, now
> on the returned list.

FR-019 and FR-022 make two log records functional requirements with acceptance criteria (SC-009
asserts *exactly one* error entry for user `1`). A requirement that is asserted needs a seam; which
seam is a decision with alternatives, recorded here. **No ADR**: it is a testing seam inside one
feature, and nothing outside the portfolio has to agree with it. The levels it carries *are* a
cross-cutting contract, and those are already ADR-0012's and ADR-0013's.

**Decision**: the domain declares a port with two named methods —
`shortPositionDetected(userId, ticker, quantity)` and `marketDataMissing(userId, ticker)` — and the
projection calls it. The logging adapter implements it with SLF4J at `error` and `warn`
respectively. Unit tests assert against a recording fake; no test captures log output.

**Rationale**: the port names the *event*, so the level, wording and destination are the adapter's
business and can change without touching the domain. It keeps the diagnostic an observable outcome
of the projection rather than a side effect buried in it, and it makes SC-009's "exactly one"
assertable in a millisecond-fast unit test.

**Alternatives considered**:

- **SLF4J directly in the domain.** Legal under constitution VIII — SLF4J is a neutral facade, not
  Spring, Jakarta, JPA, web or persistence — and the shortest path. Rejected because asserting on
  it needs a log appender or capture extension in the test, which tests the logging framework's
  configuration alongside the rule, and because the level then lives in the domain where the
  constitution says reasoning about destinations does not belong.
- **Return the anomalies in the result and let the web adapter log them.** Keeps the domain free of
  any output. Rejected: the diagnostic would fire once per HTTP call rather than once per
  projection, so the future order-validation caller of the same fold would silently log nothing,
  and the anomaly list would be a field on a response object that must never serialise it.
- **Spring `ApplicationEventPublisher`.** Rejected outright: it puts Spring in the domain.
- **Micrometer counters instead of logs.** Not an alternative — FR-019 and FR-022 require the
  identifying fields, and a counter carries none — but a reasonable **addition**, and left open for
  the author rather than decided here: see
  [Q-002](#q-002-a-micrometer-counter-alongside-each-diagnostic).

### R-012: The read-only transaction boundary sits in a thin application service

> **Amended (ADR-0017):** the boundary is still one `REPEATABLE_READ` read-only transaction, now on
> `service/PortfolioService`, which is no longer thin: it orchestrates the three reads, the mapping
> and the model. The `@Configuration` wiring of model objects is gone, because the model is not a
> bean. The isolation reasoning below is unchanged.

**Decision**: a single Spring-managed class in `application`, annotated

```java
@Transactional(isolation = Isolation.REPEATABLE_READ, readOnly = true)
```

is the use case entry point the controller calls. It delegates straight to the domain services and
holds no rule of its own. The domain services are plain Java objects wired in a `@Configuration`
class, so no Spring annotation enters the domain package.

**Rationale**: the feature issues three queries — user existence, ledger, prices — and the response
is only coherent if all three describe the same instant. **A transaction alone does not give that.**
PostgreSQL's default isolation is `READ COMMITTED`, under which every statement takes its own
snapshot; `readOnly = true` is a Hibernate/JDBC hint about writing, not about visibility, and
changes nothing here. So an order filling between the ledger query and the price query would be
visible to the second and not the first. `REPEATABLE_READ` takes one snapshot at the first statement
and holds it for the transaction, which is the actual guarantee the response needs. On PostgreSQL
that costs nothing for a read-only transaction: there are no read locks and no blocking, and a
read-only transaction cannot hit a serialization failure.

`readOnly = true` is kept for what it does do: Hibernate skips dirty checking, and the annotation
states that this path never writes.

The boundary cannot live in the domain, which may not import Spring (constitution VIII), and should
not live in the controller, so one class exists for it.

**What this does *not* buy, said plainly**: SC-006 asks that two *consecutive requests* return
identical figures. No isolation level addresses that — they are two transactions, and the ledger may
legitimately change between them. SC-006 holds for a different reason: the portfolio is a pure
function of the rows it reads, with a total order on the walk
([R-006](#r-006-the-walk-is-ordered-by-datetime-id)) and a fixed position order
([R-017](#r-017-positions-are-ordered-by-ticker)), so identical data yields identical bytes. What
`REPEATABLE_READ` buys is *intra-request* coherence: the cash, the quantities and the prices in one
response are all as of one instant.

**Alternatives considered**:

- **`READ COMMITTED` (the default) with a transaction.** What the first draft of this entry
  assumed was enough. Rejected on the mechanics above: three statements, three snapshots.
- **`SERIALIZABLE`.** Also one snapshot, and strictly stronger. Rejected as unnecessary: with no
  writes there is nothing to serialise against, and it introduces a serialization-failure path
  (and retry logic) that a read-only transaction has no need of. Constitution IV requires
  serialisable or locking for *order submission*, which is a different path.
- **No explicit transaction, three autocommit queries.** Rejected for the same reason as
  `READ COMMITTED`, with a connection round trip per query on top.
- **`@Transactional` on the controller method.** No extra class. Rejected: it makes the web
  adapter the owner of the persistence boundary, and the use case stops being callable from
  anywhere but HTTP.
- **`@Transactional` on each repository adapter.** Rejected: three transactions, which is the same
  visibility problem with more annotations.
- **One query returning everything, so that coherence is a property of the statement.** Genuinely
  removes the need for isolation here. Rejected in [R-003](#r-003-prices-are-not-an-input-to-the-fold)
  on coupling grounds — the fold must stay price-free for FR-016 — and it would not survive the
  write features, which need a boundary regardless.
- **A `TransactionTemplate` used inside a domain service.** Rejected: Spring in the domain again.
- **Fold the existence check into the ledger query.** Considered and kept separate; see
  [R-014](#r-014-user-existence-is-checked-explicitly).

This is the one structural judgement call in the plan — a class whose only reason to exist is an
annotation — and it is recorded in the plan's Complexity Tracking rather than left implicit.

### R-013: Errors as RFC 9457 `ProblemDetail`

Recorded here; **no ADR**. The first draft reserved one on the grounds that the format binds the
later endpoints, but there is little to decide: `ProblemDetail` is what Spring Boot already
produces for its own exceptions, so the real choice is whether to *replace* it with something
bespoke. Order rejection's machine-readable reason (constitution IV) is a decision about the reason
codes, and that ADR belongs to the order feature.

**Decision**: `ProblemDetail` (RFC 9457, `application/problem+json`), produced by a
`@RestControllerAdvice`. `spring.mvc.problemdetails.enabled: true` turns it on for the framework's
own exceptions as well, so an unknown user id (404), a malformed one (400) and an unexpected
failure (500) all answer in one shape. The domain throws a plain `UnknownUserException`; the advice
maps it.

**Rationale**: it is a standard with a registered media type, it is what Spring Boot already
produces when enabled, and it carries extension members — which is where a rejection reason code
will go without inventing a second error envelope.

**Alternatives considered**:

- **A hand-rolled `{ "error": …, "message": … }` DTO.** Full control. Rejected: it duplicates what
  the framework emits for exceptions the advice does not catch, so clients would face two error
  shapes, and it is a bespoke standard where a real one exists.
- **Spring Boot's default error body (`/error`, the `DefaultErrorAttributes` map).** Free.
  Rejected: it leaks `path` and `exception` details, has no media type of its own, and no place
  for a reason code.
- **Bare status codes with empty bodies.** Rejected: a 400 that does not say which id was
  malformed is a support ticket.

### R-014: User existence is checked explicitly

> **Amended (ADR-0017):** the service asks `UserRepository.existsById`, after `UserMapper` narrows the id
> to the `INT` column (an id out of range cannot exist). Same decision, no port.

**Decision**: the use case asks a `Users` port `exists(userId)` and throws `UnknownUserException`
before folding anything. FR-024 (404) versus FR-014 (empty portfolio) is otherwise undecidable:
both cases produce zero orders.

**Alternatives considered**:

- **Infer existence from the presence of orders.** Rejected: it makes a user who has never traded
  indistinguishable from one who does not exist, which SC-011 explicitly tests apart.
- **Join `users` into the ledger query and detect the empty result.** One fewer round trip, and
  rejected on clarity: an outer join whose only purpose is to make an empty result mean two
  different things is harder to read than `existsById`, and the saving is one indexed primary-key
  lookup inside the same transaction.

### R-015: Money is a JSON number

**Decision**: money and quantity serialise as JSON numbers, with
`JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN` enabled so no value can ever appear in
scientific notation.

**Rationale**: Jackson writes `BigDecimal` from its `toString()`, so a value scaled to 2 decimals
reaches the wire as `889756.00` — the scale is part of the payload, which is what FR-015 asks for.
Numbers are what the statement's own vocabulary implies and what any client's JSON parser expects
for an amount.

**Alternatives considered**:

- **Money as a string** (`"889756.00"`). The defensible choice for a real payments API: it cannot
  be mangled by a client whose JSON numbers are IEEE 754 doubles. Rejected here because this API's
  amounts are far inside the exactly-representable range, and strings would make every example in
  the README and every assertion in the tests read like a workaround. Revisit if a client ever
  reports precision loss.
- **Minor units as integers** (centavos). Rejected: it changes the meaning of every field in the
  statement's terms and pushes the scale decision onto clients.

### R-016: The response carries the three figures asked for, plus settled cash

**Decision**: `totalAccountValue`, `availableCash`, **`settledCash`**, and `positions[]` with
`instrumentId`, `ticker`, `name`, `quantity`, `marketValue` and `totalReturnPercent`. No
`reservedCash`.

**Rationale**: FR-001 names three things, and SC-007 asks for a property the three cannot express —
*the reported total account value equals settled cash plus the sum of the position values shown in
the same response*. Without `settledCash` on the wire that criterion is not checkable from a
response at all: a reader sees `availableCash` `627,500.00`, positions summing to `136,756.00`, and
a total of `889,756.00`, with no way to account for the `125,500.00` difference. Publishing settled
cash makes the response self-reconciling, which is worth more than contract minimalism — and it is
the figure the total is actually built from (FR-012).

`reservedCash` is **not** added: it is exactly `settledCash − availableCash`, so it would be a
derived field with no information of its own.

`instrumentId` is included because a client that wants to act on a position needs the id order
submission will ask for; `name` because the positions list is the one place a user reads their
holdings. `totalReturnPercent` carries its unit in its name, which is the cheapest defence against
a client dividing by 100 twice.

The three figures are distinct and the names say which is which:

| Field | Meaning |
|---|---|
| `settledCash` | pesos the account holds, reservations included — the accounting balance (FR-010) |
| `availableCash` | pesos free to trade: settled minus what `BUY LIMIT NEW` orders commit (FR-011) |
| `totalAccountValue` | `settledCash` + Σ `marketValue` (FR-012) |

**Alternatives considered**:

- **Only the three figures FR-001 names** (the first draft of this entry). Minimal, and rejected:
  it makes SC-007 unverifiable from the response and leaves a `125,500.00` discrepancy that looks
  like a bug to anyone who adds up the fields. Minimalism was the wrong value to optimise here.
- **Also expose `reservedCash`.** Rejected as derived, per above. If reservations ever need
  breaking down per order, that is a different endpoint.
- **Spanish field names, matching the statement.** Rejected: the code is English throughout; the
  statement's terms are mapped in the README.
- **Nest quantity/value/return under a `metrics` object.** Rejected as structure for its own sake.

### R-017: Positions are ordered by ticker

**Decision**: ascending by ticker.

**Rationale**: the spec leaves ordering to plan time and SC-006 requires two requests to be
identical, so *some* total order must be fixed. Ticker is stable, meaningful to a reader scanning
the list, and independent of values that may be absent.

**Alternatives considered**:

- **Market value descending.** The most useful order for a human. Rejected: an absent market value
  has no position in that ordering, so the unpriced case would need a tiebreak rule that is pure
  presentation.
- **Ledger order (first acquisition first).** Rejected: it requires carrying an ordering key
  through the fold for a presentational purpose.
- **Unspecified / whatever the map iteration yields.** Rejected: it breaks SC-006.

### R-018: Indexes arrive as a new Flyway migration

> **Amended 2026-11-15:** `idx_orders_userid_instrumentid` was dropped from `V2` before it was
> committed or applied anywhere. The portfolio's only `orders` query filters by `(userid, status)`
> and never reads it. ADR-0002 names it as a mitigation; it arrives with the first feature that has
> a query filtering by user and instrument, if one does (constitution IX: a structure chosen for
> performance cites a real use). Order validation reads the same snapshot through
> `(userid, status)`, so it is not obviously that feature.

**Decision**: `V2__portfolio_read_indexes.sql`:

```sql
CREATE INDEX        idx_orders_userid_status       ON orders     (userid, status);
CREATE INDEX        idx_orders_userid_instrumentid ON orders     (userid, instrumentid);
CREATE UNIQUE INDEX uq_marketdata_instrumentid_date ON marketdata (instrumentid, date);
```

**Rationale**: ADR-0002 accepted a read that scans the user's history and named the first two
indexes as its mitigation; this is the feature that makes the promise due.

The third is **unique on purpose and does two jobs at once**. It is an integrity constraint —
`marketdata` is one price row per instrument per day, and nothing in the given schema says so, so a
duplicate feed delivery could silently create two closes for the same day. And it is the index
`DISTINCT ON (instrumentid) … ORDER BY instrumentid, date DESC` reads
([R-002](#r-002-latest-close-per-instrument-via-distinct-on)), which is why the query needs no
tiebreak: the constraint makes "the latest row of this instrument" unique by construction instead of
by a sort rule that would have to be remembered at every call site. Constitution VI permits this —
"schema constraints that protect integrity are allowed" — and it is a constraint, not a business
rule: it says nothing about money.

`V1__given_schema.sql` is immutable (constitution VI), so this is a new migration.

**Risk, stated because it cannot be measured from here**: a `CREATE UNIQUE INDEX` fails if the data
already violates it. The provided dataset satisfies it — measured, exactly one row per
`(instrumentid, date)` across 126 rows — but **the staging database was not measured**, since the
credentials are not in this repository. If staging holds a duplicate, the migration fails there and
the duplicate is the finding. That is the right failure: it is exactly the condition the constraint
exists to catch, and a failed migration is visible, whereas a wrong close is not.

**Alternatives considered**:

- **A plain (non-unique) index on `(instrumentid, date DESC)`.** What this entry originally said.
  Serves the query identically and protects nothing, leaving the latest-row pick dependent on a
  tiebreak column. Rejected once the constraint was seen to be free.
- **No indexes; the dataset is tiny.** True and short-sighted: the query plans a reviewer would
  look at are the point, and the ADR already committed to them.
- **A `UNIQUE` table constraint rather than a unique index.** Equivalent in PostgreSQL (it is
  implemented as a unique index). A unique index is used because `DESC` ordering can be expressed
  on it if ever needed, and because it reads as one statement alongside the other two.
- **A covering/partial index on `status = 'FILLED'`.** Rejected: the fold reads `NEW` rows too, so
  a partial index would serve only half the query.

### R-019: A NULL `close` is an unknown price

**Decision**: a held instrument whose latest row has a NULL `close`, or that has no row at all, is
unpriced — FR-020 to FR-022 apply identically, including the warning.

**Rationale**: `marketdata.close` is nullable in the given schema. Measured: no seed row has a NULL
`close`, so this is defensive, and it is the same question ADR-0013 answered — we do not know the
price. Letting a NULL through would mean a `NullPointerException` deep in the projection or, worse,
a value treated as zero.

**Alternatives considered**:

- **Fall back to the latest row with a non-NULL `close`.** Plausible and rejected for the reason
  ADR-0013 rejected every fallback: the field means "latest close", and serving an earlier one
  silently makes the consumer's reading wrong.
- **Treat NULL as a data error (error level, like ADR-0012).** Rejected: no invariant of ours is
  broken; the feed simply has not told us, which is the warning case by ADR-0013's own distinction.

### R-020: The unpriced-position fixture is built by the test, not by a migration

**Decision**: the functional test for FR-020 to FR-022 inserts its own rows — a `FILLED BUY` of
`PGR` for a user who holds nothing else — with `@Sql` inside the Testcontainers database. No new
migration and no addition to `db/seed`.

**Rationale**: ADR-0013 established that the seed cannot reach this state once `ARS` is excluded as
cash, so a fixture is required. Migrations are shared with staging (constitution VI) and the seed is
the provided dataset, kept as given; test-owned data belongs with the test that needs it.

**Alternatives considered**:

- **Add the rows to `db/seed`.** Rejected: it stops being the provided dataset, and every other
  test's expected numbers would shift.
- **A separate Flyway location for test-only data.** Rejected as machinery for one fixture; `@Sql`
  already scopes it to the test class.
- **Unit-test the projection only, with a fake price port.** Necessary but not sufficient: the
  unit test proves the rule, the functional test proves the wiring serves `null` over HTTP.

### R-021: The endpoint is `GET /api/v1/users/{userId}/portfolio`

**Decision**: as already proposed in the README, with `userId` bound as a `long`.

**Rationale**: the portfolio belongs to a user, so it reads as a sub-resource; `/api/v1` is fixed by
the constitution. Binding as `long` makes a malformed id a framework-level type mismatch, which the
advice maps to 400 — FR's malformed-id case needs no hand-written parsing.

**Alternatives considered**:

- **`GET /api/v1/portfolio?userId=1`.** Rejected: once authentication exists the id disappears from
  the request, and a query parameter would leave the path meaningless. The sub-resource path
  survives that change as `/api/v1/users/me/portfolio`.
- **`GET /api/v1/portfolios/{userId}`.** Rejected: a portfolio is not an independently identified
  resource here; it is a projection of a user.

### R-022: The design-time contract is a hand-written OpenAPI document

**Decision**: `contracts/portfolio.openapi.yaml`, written by hand and reviewed as part of this
feature, is the contract the implementation is checked against. Whether the *running application*
also serves generated API documentation is a separate question and is **left open** —
[Q-001](#q-001-springdoc-openapi-in-the-running-application).

**Rationale**: the contract is a Phase 1 design artifact — it exists before the controller does,
and it is what the controller is checked against. A generated document describes whatever was
built, which is the opposite direction and cannot catch a contract regression. That argument is
about the design-time artifact only; it says nothing against *also* publishing generated docs at
runtime, which is Q-001.

**Alternatives considered**:

- **No contract artifact; the tests are the contract.** Rejected: Phase 1 owes one, and a reviewer
  reading the response shape should not have to read assertions to find it.
- **Generate the design-time contract from annotations on a stub controller.** Rejected: it makes
  the code the source and the document the output, so the document can never disagree.

### R-023: A REST Client `.http` file ships with the feature

**Decision**: `docs/api/trading-api.http` — the statement's optional "collection for Postman,
Insomnia or REST Client" — with the portfolio requests: the seeded user, a user who has never
traded, the unknown id (404) and the malformed id (400). A `@host` variable at the top, one later
section per endpoint as the other features land.

**Rationale**: `.http` is plain text, so it reviews and diffs like code and needs no export step;
IntelliJ's HTTP Client runs it natively and VS Code's REST Client extension reads the same format.
It doubles as the executable form of [quickstart.md](quickstart.md)'s scenarios — a reviewer can
read the expected figures there and fire the requests from here.

**Alternatives considered**:

- **A Postman collection (`.json`).** The most widely recognised format. Rejected: it is generated
  JSON that no one reads in a diff, it carries environment files alongside it, and it must be
  re-exported by hand whenever a request changes.
- **A shell script of `curl` calls.** Already effectively present in the quickstart. Rejected as
  the shipped artifact: it is not a collection a reviewer can click through, and quoting JSON in
  shell is noise.
- **Nothing — the quickstart's `curl` lines are enough.** Rejected: it is the one nice-to-have the
  statement names, and it costs a file.

### R-024: The ledger query fetches only the statuses the fold uses

> **Amended (ADR-0017):** the set still comes from the model (`OrderStatus.contributing()`), but it
> is the service that passes it to `OrderRepository.findOrdersOf`, through `OrderMapper.toStatusColumns`.
> The fold no longer asks for anything: it folds what it is given. Persistence still binds a
> parameter and decides nothing.

**Decision**: the ledger query carries `and o.status in :statuses`, and the set comes **from the
domain** — `OrderStatus.contributing()` returns `{FILLED, NEW}`, `AccountLedger` passes it to the
port, the adapter binds it as a parameter. `CANCELLED` and `REJECTED` rows are never read and never
mapped. The predicate is served by `idx_orders_userid_status`
([R-018](#r-018-indexes-arrive-as-a-new-flyway-migration)), which is now the index this query reads.

**Rationale**: FR-004 says those two statuses contribute to nothing, so fetching them means paying
for rows, for the `instruments` join on each of them, and for a domain object per row, in order to
discard them a moment later. On the seed that is 2 rows of user `1`'s 11 — but the proportion is not
bounded by anything: a client that submits orders which are rejected for lack of funds, or cancels
and resubmits, accumulates exactly the rows the portfolio never uses, and the read grows with the
user's whole history (ADR-0002) rather than with their positions.

Passing the set from the domain is what makes this clean rather than a leak. The question *"which
statuses matter"* is answered once, in `OrderStatus`, and SQL receives the answer as a parameter;
the question *"what does each status mean"* — settled, reserving, ignored — stays in `Order`.
Constitution III is satisfied on its own terms: the adapter selects rows, it does not decide rules.

**This entry corrects an inconsistency in the first draft of the plan**, which fetched every status
and justified it as keeping FR-004 out of SQL. That argument does not survive next to
[R-002](#r-002-latest-close-per-instrument-via-distinct-on), where selecting the row that matters in
SQL was accepted as reading rather than as a business rule. One of the two had to give, and the one
that costs data was the wrong one to keep. Raised by the author on review.

**Alternatives considered**:

- **Fetch every status and filter in the fold** (the first draft). Rejected above. Its one real
  merit: the port has a single-argument signature and the fold is the only place a status appears.
- **Hard-code `where o.status in ('FILLED', 'NEW')` in the adapter.** The shortest form, and the
  one that earns the original objection: the set of statuses that matter would then exist in two
  places — the JPQL string and `Order`'s predicates — and a future status would be added to
  one of them. Rejected for that, not for SQL hygiene.
- **Two queries, one per status** (`FILLED` rows for the fold, `NEW` rows for the reservations).
  Rejected: two round trips for one pass, and the walk needs the settled rows in order anyway.
- **A database-side aggregation returning quantities and cash directly.** Rejected by constitution
  III and ADR-0002 — that is the fold, and it belongs in Java.

**Consequence to watch**: nothing in this feature reads a `CANCELLED` or `REJECTED` row, and
cancellation will fetch its order by id rather than through this port. If a later feature needs to
*count* rejections, it gets its own query; it must not widen this one, because every row it adds is
read on every portfolio request.

## Open questions for the author

Two decisions are **not taken here**, deliberately. Both are additions that would be reasonable and
neither blocks implementation; each changes what ships, so the call is the author's.

**Answered on 2026-11-15, at implementation time: both deferred.** Neither ships with feature
`001-portfolio-endpoint`. The hand-written contract in `contracts/` stays the reference for the
response shape, and the two diagnostics stay log-only. Both remain cheap to add later — Q-002
especially, because the model already names the two anomalies — and neither is rejected
on its merits. The arguments on both sides are kept below as they were put.

### Q-001: `springdoc-openapi` in the running application

Adding `springdoc-openapi-starter-webmvc-ui` would serve generated OpenAPI and a Swagger UI from the
running app, so a reviewer could explore the API without reading a YAML file or installing an HTTP
client.

- **For**: discoverability, and it is what reviewers of a Spring API often look for first; the
  statement's nice-to-have is about exactly this kind of convenience. It complements rather than
  replaces the artifact in `contracts/`.
- **Against** (the R-022 argument, which stands as one side of this): a generated document mirrors
  the code, so it can never contradict it and therefore cannot catch a regression — the
  hand-written contract keeps that job. It also needs annotations on the controller to produce
  anything readable, and it is a dependency the BOM does not manage, so its version becomes ours to
  track.
- **Unmeasured**: whether the version that pairs with Spring Boot 4.1.2 is released. Worth checking
  before committing to it.

### Q-002: A Micrometer counter alongside each diagnostic

The service could also increment a counter next to each log call —
`portfolio.anomaly{kind="short_position"}` and `portfolio.anomaly{kind="missing_market_data"}`.

- **For**: an alert on "short positions served per hour" is a threshold on a counter, not a log
  grep, and a counter survives log retention. The cardinality objection raised in an earlier draft
  of [R-011](#r-011-diagnostics-are-a-domain-port-not-a-logger-call) was wrong and is withdrawn: the
  counter needs **no** user id or ticker tag, because the log already carries the identity — the
  counter answers *how often*, the log answers *to whom*.
- **Against**: it adds `spring-boot-starter-actuator` and a second output for one event, with no
  collector in this project to scrape it, so nothing would read it during the exercise.
- **Cost if deferred**: low. `Portfolio.shortPositions()` and `unpricedPositions()` already name
  the two anomalies, so the counter is two lines in `PortfolioService` whenever a metrics stack
  exists.

### Resolved, not open

No NEEDS CLARIFICATION remains in the spec. The three questions it raised were answered in the
clarification round and recorded in ADR-0012, ADR-0013 and FR-024.
