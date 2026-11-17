# Phase 0 Research: Order Placement

**Feature**: `003-order-placement` | **Date**: 2026-11-17 | **Spec**: [spec.md](spec.md)

This phase resolves what the spec left to plan time — the shape of the endpoint, where each rule
lives, what the ledger row looks like for every outcome, how one user's submissions are serialised,
and how an idempotency key travels and is matched — and records the alternatives weighed for each.

The spec names its own open questions: "the exact mechanism [of serialisation] is a plan-time
decision with an ADR", and "how the key travels, how the request fingerprint is computed and how
long records are kept are plan-time decisions". Those are
[R-209](#r-209-one-user-is-serialised-by-a-pessimistic-lock-on-their-users-row) and
[R-210](#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing).

Nothing here restates the challenge statement (constitution, *Technology and Scope Constraints*) or
re-decides what an accepted ADR already settled; those are cited.

> **Four decisions were taken by the author on 2026-11-17, in answer to questions put at the start
> of this phase.** The endpoint lives under the user
> ([R-201](#r-201-the-endpoint-is-post-apiv1usersuseridorders)); the idempotency key travels in a
> header ([R-210](#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing));
> every recorded outcome answers `201`
> ([R-202](#r-202-every-recorded-outcome-answers-201-created-a-request-that-writes-nothing-never-does));
> and **a submission for an instrument with no price is refused rather than recorded**
> ([R-203](#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)),
> which changes FR-010 and is the one place this plan departs from its own spec. The spec has been
> amended to match, dated and marked.

## Measured facts this phase relied on

Measured on `src/main/resources/db/seed/R__given_dataset.sql` on **2026-11-17**, by parsing the seed
and folding user `1`'s ledger exactly as `AccountLedger` does. Nothing below is estimated, and every
figure the spec states was reproduced rather than copied.

| Fact | Value |
|---|---|
| Orders in the seed | 11, all for user `1` |
| User `1` settled cash | `753,000.00` |
| Reserved by `NEW BUY LIMIT` orders | `125,500.00` — 50 `LOMA` at `710.00`, 60 `BMA` at `1,500.00` |
| **User `1` available cash** | **`627,500.00`** |
| User `1` holdings from `FILLED` orders | `PAMP` **40**, `METR` **500**, `BMA` **−10** |
| Reserved shares | none — no `SELL LIMIT` is `NEW`, so available holdings equal the holdings |
| `PAMP` (id 47) latest row | `2023-07-14`, `close` **925.85** |
| `BMA` (id 31) / `METR` (id 54) / `LOMA` (id 45) latest `close` | 1502.80 / 229.50 / 734.65 |
| Instruments with no `marketdata` row | 3: `PGR` (id 3), `IRCP` (id 10), `ARS` (id 66, `MONEDA`) |
| The one `MONEDA` instrument | `ARS` / `PESOS`, id 66, carrying the seed's `CASH_IN` and `CASH_OUT` |
| A seeded `REJECTED` order exists | yes — `PAMP SELL` 100 at `950.00`, which **carries a price** |

Derived figures, each computed at `close` 925.85 with the floor of FR-017:

| Request | Derived size | Amount it moves | Verdict against `627,500.00` / 40 shares |
|---|---|---|---|
| `BUY` 100 shares | 100 | `92,585.00` | accepted; available cash becomes `534,915.00` |
| `BUY` 677 / 678 shares | — | `626,800.45` / `627,726.30` | **677 accepted, 678 rejected** — the boundary, to the cent |
| `BUY` amount `100,000.00` | **108** | `99,991.80` | accepted; `8.20` left unspent |
| `BUY` amount `627,500.00` | **677** | `626,800.45` | accepted — the whole available cash |
| `BUY` amount `700,000.00` | not derived | — | rejected: the amount alone exceeds available cash |
| `BUY` amount `500.00` | **0** | — | rejected: the amount does not reach one share |
| `SELL` amount `30,000.00` | **32** | `29,627.20` | accepted; 8 shares remain |
| `SELL` amount `37,034.00` | **40** | `37,034.00` | accepted — the position is worth exactly that |
| `SELL` amount `40,000.00` | not derived | — | rejected: the position is worth `37,034.00` |
| `LIMIT BUY` amount `100,000.00` at `900.00` | **111** | reserves `99,900.00` | accepted `NEW` — 111, not the 108 the market close yields |
| `LIMIT SELL` amount `30,000.00` at `1,000.00` | **30** | reserves 30 shares | accepted `NEW` — 30, not 32: the limit price governs both sides |
| `LIMIT SELL` amount `30,000.00` at `500.00` | not derived | — | rejected: at their own price the position is worth `20,000.00` |
| Two concurrent `BUY` 400 shares | — | `370,340.00` each, `740,680.00` together | exactly one can be accepted |

The seeded `REJECTED` order matters for [R-203](#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection):
the dataset's own rejection carries a price, so "every row in `orders` has a price" is a property
the provided data already has, not one this feature invents.

---

## R-201: The endpoint is `POST /api/v1/users/{userId}/orders`

**Decision** (author, 2026-11-17). The user is a path segment, as in
`GET /api/v1/users/{userId}/portfolio`. The body carries the instrument, the type, the side and
exactly one of a size or a total amount.

**Rationale**: every query must be scoped by the user id and the id is untrusted input
(constitution, *Technology and Scope Constraints*). Keeping it in the URI means the scope of a
request is visible in its line in an access log, and it is the same place the only other user-scoped
endpoint keeps it. It also settles what `404` means here: the target of the request is a user's
order collection, so an unknown user is a missing target
([R-212](#r-212-the-error-map-400-404-409-503)).

**Alternatives considered**

- **`POST /api/v1/orders` with `userId` in the body**, which is how `docs/journal/07OrderPlacement.md`
  lists the request attributes. Rejected by the author: it splits where the user id lives across two
  endpoints, and a body field is easier to forget to scope a query by than a path variable the
  handler cannot ignore.
- **`POST /api/v1/orders` with the user taken from authentication.** There is no authentication
  (ADR-0007), so there is nowhere to take it from.

**Consequence**: cancellation, when it is built (ADR-0006), is expected at
`POST /api/v1/users/{userId}/orders/{orderId}/cancel`, so the user scope is uniform across the
write path. That is not decided here.

---

## R-202: Every recorded outcome answers `201 Created`; a request that writes nothing never does

**Decision** (author, 2026-11-17). `FILLED`, `NEW` and `REJECTED` all answer `201` with the order in
the body. A request that writes nothing answers `400`, `404`, `409` or `503`.

**Rationale**: FR-023 requires a caller to distinguish "the market answered about your money" from
"your request was malformed". All three recorded outcomes appended exactly one row to the ledger —
a resource really was created — and all three are readable back. Making the split *status class*
versus *status field* rather than *status code* versus *status code* means a client branches once, on
whether it got a `2xx`, and then reads `status` for the business answer.

**Alternatives considered**

- **`201` when accepted, `200` when `REJECTED`.** Rejected: the row was created either way, so the
  distinction would be about how the news feels rather than about what happened, and it gives the
  client a second thing to branch on for no new information.
- **`200` for everything.** Rejected: it loses the signal that the ledger grew by one row, which is
  the only durable effect this endpoint has.
- **`422 Unprocessable Content` for a `REJECTED` order.** Rejected outright: a rejection is a
  successful, recorded business outcome, and a `4xx` tells a client the opposite.

**Consequence**: no `Location` header. There is no `GET /orders/{id}` to point at
([R-216](#r-216-no-endpoint-reads-an-order-back-the-portfolio-does)), and inventing one to satisfy a
convention would be an endpoint the constitution does not list.

---

## R-203: An instrument with no price refuses the submission; it is **not** a recorded rejection

**Decision** (author, 2026-11-17). A `MARKET` submission for an instrument with no `marketdata` row —
`PGR` (id 3), `IRCP` (id 10) — is **refused with `400` and nothing is written**. The other three
rejections (no funds, no holdings, a derived size of zero) are still recorded as `REJECTED` orders.

**What made this a question.** `V3__required_columns.sql` made `orders.price` `NOT NULL` one day
earlier (ADR-0030), and the price a `MARKET` order records is the close it executed at. For this one
rejection there is no close, so there is no price — and the row has to carry one.

**Rationale**: an instrument that does not quote is a property of the *request*, like an instrument
id that does not exist or a `MONEDA` instrument (FR-007, FR-008): it is knowable before any of the
user's money is considered, and the answer does not depend on their account at all. Refusing it
keeps three invariants that would otherwise each cost something:

- **Every row in `orders` has a price.** `V3`'s guard stands unchanged, no migration relaxes it, and
  no reader of the ledger ever meets a priced-at-nothing order. The provided dataset already has
  this property.
- **No money figure is ever written as `0` when it is unknown** — ADR-0013, from the inside of the
  database rather than only on the wire.
- **The reason column stays about the user's money.** Every recorded `REJECTED` row is a statement
  about funds, holdings or size: things the account decides.

**Alternatives considered**

- **Make `price` nullable and record the row with an absent price.** The honest rendering of "there
  was no price", and ADR-0013's own logic. Rejected by the author: it gives back the `NOT NULL`
  guard added the day before, and it introduces a second rule — "a price is absent only on a
  `REJECTED` row" — that only Java enforces, on the one table the schema protects most.
- **Record `0.00`.** Rejected: it writes a money figure that is false into the ledger, which is
  exactly what ADR-0013 forbids. A later reader cannot tell it from an order that really traded at
  zero.
- **Reject with a derived size of zero instead.** Rejected: the size is not the problem, and the
  reason would misdescribe the cause.

**Consequences, stated plainly because this is a departure**

- It **departs from FR-010 and from the challenge statement's "store the rejection"**, for the
  no-price case only. Constitution VII requires the departure to be recorded in an ADR and stated in
  the README; both are tasks of this feature (ADR-0034).
- The spec is amended in the same change: FR-010, user story 1's scenario 8, the "instrument with no
  price" edge case and SC-009 now describe the refusal. The amendment is dated and marked, and the
  reasoning stays here.
- **Constitution IV's parenthetical ("no funds, no holdings, no price") becomes stale**, as does the
  `reason` example in ADR-0005. Neither is violated — a no-price submission is no longer a rejection,
  so no rejection lacks a reason — but the list names an outcome that can no longer occur. Resolving
  it is a `PATCH` amendment through `/speckit-constitution`, flagged in the plan's Constitution Check
  and **not** done silently here.
- A `LIMIT` order for `PGR` or `IRCP` is unaffected and still accepted (FR-011): its price came in
  the request.

---

## R-204: Validation reads the fold the portfolio reports, unchanged

**Decision**. The submission reads the user's contributing orders through the existing
`OrderRepository.findOrdersOf(userId, {FILLED, NEW})` and folds them with
`new AccountLedger(orders).snapshot()`. It asks the snapshot `availableCash()` for a `BUY` and
`availableQuantityOf(instrumentId)` for a `SELL`. **Nothing is added to the fold and nothing
computes availability a second way** (FR-012, FR-013, ADR-0015, constitution I).

**Rationale**: this is the call site ADR-0015 was written for. `availableQuantityOf` has had no
production caller until now, which that ADR states explicitly as the point of the decision rather
than an oversight. SC-012 — "the available cash this feature validates against equals, to the cent,
the available cash the portfolio reports" — is then true by construction and not by a test that
compares two implementations.

**Alternatives considered**

- **A `SELECT sum(...)` that computes available cash in SQL.** Faster, and it would make the lock
  unnecessary for the read. Rejected by constitution III (business rules in Java) and I (one fold),
  and it is the second source of truth ADR-0015 exists to prevent.
- **Reading only the orders of the instrument being traded, for a `SELL`.** Rejected: available cash
  needs the whole ledger anyway for a `BUY`, and two different read shapes for two sides is the
  divergence again.

---

## R-205: The execution price is the model's choice, not the service's

**Decision**. The submission itself answers which price applies, given the latest closes the service
read: a `LIMIT` submission answers with the price in the request, a `MARKET` submission with the
instrument's latest `close`, and absence is possible only for `MARKET`
([R-203](#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)).
The service reads `MarketDataRepository.findLatestMarketData` for the one instrument and hands the
result in; it never picks a price.

**Rationale**: FR-018 is the feature's sharpest rule — the price substitutes on **both** sides of the
amount comparison, which is what makes a `LIMIT SELL` of `30,000.00` sell 30 shares and not 32. If
the service chose the price and passed a number, that rule would be split between a service and a
model object, and the `LIMIT`-versus-`MARKET` difference would be an `if` in orchestration code.
Keeping it in the model makes "which price governs" one question with one answer, testable as plain
Java (constitution V).

**Alternatives considered**

- **The service resolves the price and the model receives a `BigDecimal`.** Simpler signature, and
  the model would not need to know the order's type. Rejected for the reason above.
- **Validate a `LIMIT` order against the market close as a sanity check.** Rejected: FR-011 and the
  spec's "a limit price that makes no market sense" edge case both say a `LIMIT` order never reads
  market data, and a price nobody will trade at is not an error.

**Where `Closes` lives.** The value that answers "the latest close of this instrument" is
`model/portfolio/Closes` today, because the portfolio was the first reader. It is a market fact, not
a portfolio concept, and constitution VIII requires a package that starts holding a second concept to
be split in the change that adds it — so it **moves to `model/instrument`**, next to `DailyClose` and
`DailyCloses`, in this feature. The portfolio's import changes; nothing else does.

---

## R-206: One object decides the outcome, and it returns a decision rather than writing one

**Decision**. The validated submission, the account snapshot and the governing price produce **one
decision value**: the status the order takes (`FILLED` for an accepted `MARKET`, `NEW` for an
accepted `LIMIT`, `REJECTED` otherwise), the size the order carries, the price it is recorded at and,
when rejected, the machine-readable reason. The service persists that decision; the model writes
nothing and knows nothing about persistence (constitution VIII).

The rule it applies, once, for both sides and both types (FR-014 to FR-018):

| | `BUY` | `SELL` |
|---|---|---|
| What is available | `availableCash()` | `availableQuantityOf(instrument) × price` |
| What the request needs | an exact size: `size × price`; an amount: the amount itself | the same |
| Rejected when | available < needed | available < needed |
| The size that results | the exact size, or `floor(amount / price)` | the same |
| Also rejected when | the resulting size is `0` | the same |

**Rationale**: the spec's own consolidation ("lo que queda, idéntico para los dos lados") is one rule
with two lookups, not four rules. Writing it once means the four user stories differ only in which
lookup and which price they feed it, which is why P2, P3 and P4 add no new rejection path to P1.

The reasons are an enum of exactly three values — insufficient funds, insufficient holdings, a size
below one share — after
[R-203](#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)
moved no-price out of the set.

**Alternatives considered**

- **Throw an exception per rejection and catch it in the service.** Rejected: a rejection is a
  recorded business outcome, not a failure (FR-019, FR-023), and exceptions as return values would
  put the "which status does this become" decision in a `catch` block.
- **A boolean `isValid` plus separate getters for the derived size.** Rejected under constitution IX:
  the caller would reassemble the decision, and the "reject when the size is zero" rule would have
  somewhere else to live.
- **Two objects, one per order type.** Rejected: the only difference is the price, which
  [R-205](#r-205-the-execution-price-is-the-models-choice-not-the-services) already localises, so the
  second class would duplicate the whole comparison to change one lookup.

---

## R-207: The derivation's floor, and the bounds, are stated in one place each

**Decision**. `floor(amount / price)` is a division, so constitution II requires it to name its scale
and `RoundingMode`: scale `0`, `RoundingMode.FLOOR`, added to `DecimalPolicy` as a named question
("how many whole shares fit in this amount at this price") rather than written at the call site.

Both operands are positive by construction, so `FLOOR` and `DOWN` agree; `FLOOR` is chosen because it
is what FR-017 says, and a reader should not have to prove the operands' sign to check the policy.

**The bounds come from the columns, not from configuration.**

| Bound | Value | Where it comes from |
|---|---|---|
| Size | `1` to `2,147,483,647` | `orders.size` is `INT` |
| A price or an amount | greater than `0`, at most `99,999,999.99` | `orders.price` is `NUMERIC(10,2)` |

A request outside those is refused as an invalid request (FR-004, the spec's "a huge size or amount"
edge case), **before** any money is looked at, so nothing can overflow a computation or fail at
`INSERT` time. A size derived from an amount is checked against the same ceiling.

**Rationale**: instrument search made its three limits configuration because they are policy — how
much of a market a deployment lets a client pull per request (ADR-0032). These are not policy: they
are the capacity of a column. A dial that must match a column's type is a dial that can be set to a
wrong value, and setting it would produce a `500` from the database instead of a `400` from the
endpoint. Same principle, opposite answer, for a stated reason.

**Alternative considered**: widening `orders.size` to `BIGINT` and `price` to `NUMERIC(14,2)` so the
bounds get larger. Rejected: no requirement needs it, `V1` is the given schema, and the ceiling is
already far above any order the dataset can fund.

---

## R-208: `order_status_history` is created by this feature, in migration `V4`

**Decision**. ADR-0005 decided the table in feature 001 and no feature has needed it yet, because
nothing wrote to the ledger. This feature is the first writer (FR-022), so it adds
`V4__order_status_history.sql` with exactly the columns that ADR accepted — `id`, `order_id`,
`status`, `reason`, `changed_at` — plus a foreign key to `orders(id)` and an index on `order_id`, and
inserts one row in the same transaction as the order it describes.

Only the first transition exists so far: into `FILLED`, `NEW` or `REJECTED`. `CANCELLED` arrives with
cancellation (ADR-0006), and nothing here assumes the table has one row per order.

**Rationale**: the migration belongs to the feature that first depends on it, and the write is in the
same transaction because that is the whole content of constitution IV's first bullet — history that
can disagree with `orders.status` is not history.

**Alternatives considered**

- **A trigger that writes the history row** when `orders.status` changes. It would be impossible to
  forget. Rejected by constitution III: it puts a rule in SQL, invisible from the code and
  untestable in milliseconds.
- **Deferring the table until cancellation**, since only one transition exists today. Rejected: FR-022
  requires the first transition to be recorded, and the audit trail's value is that it has no gap at
  the start.
- **Writing the reason only on `orders`** and skipping the history for rejections. Rejected: the
  reason is a property of the transition, and ADR-0005 put it there.

---

## R-209: One user is serialised by a pessimistic lock on their `users` row

**Decision** (the author's, in `docs/journal/07OrderPlacement.md`, taken as given here). The
submission transaction begins with `SELECT … FROM users WHERE id = :id FOR UPDATE`. Everything after
it — the fold, the decision, the two inserts — runs under that row lock, so two submissions for the
same user are processed one after the other, and submissions for different users never meet
(FR-026, FR-027, constitution IV).

The same statement answers "does this user exist", so existence and the lock cost one round trip
rather than two, and the endpoint cannot read a ledger it never locked.

**Rationale**: the lock has to be taken on something that exists for every user and is not what we
are about to write. The `users` row is exactly that: one row per user, never contended by another
feature, and the natural granularity of the invariant, which is "this user's available cash". The
cost is explicit and the author accepted it: one user's orders are sequenced at the database.

**Alternatives considered**

- **`SERIALIZABLE` isolation with a retry loop.** PostgreSQL would detect the conflict and abort one
  transaction with `40001`, which is correct and lock-free. Rejected: it needs retry machinery to
  avoid turning a race into a `500` for the loser, retries make the idempotency path harder to reason
  about, and a conflict that aborts after the work is done costs more than a lock taken before it.
- **A PostgreSQL advisory lock** (`pg_advisory_xact_lock(userId)`). Needs no row and no table.
  Rejected: the lock key is a bare integer in a global namespace shared with any future use, and it
  is a lock nobody can see from the schema.
- **An optimistic `version` column on `users`.** Rejected: it adds a column to the given schema to
  protect an invariant that is not about the user row's contents, and it still fails late.
- **Locking the user's existing `orders` rows** (`FOR UPDATE` over the fold's own query). It would
  lock exactly what was read. Rejected: a user with no orders locks nothing, which is precisely the
  case where two concurrent `CASH_IN`-funded buys could race, and the lock set grows with the user's
  history.
- **A table-level lock, or an application-level mutex.** Rejected: the first serialises every user
  against every other, the second does not survive a second instance of the API.

**Consequence**: a submission that waits on another user's own submission can exceed the transaction
deadline and answer `503` (ADR-0031). That is the right answer — the request was valid and nothing
was written — and it needs no new handler.

---

## R-210: Idempotency: an `Idempotency-Key` header, a fingerprint, and `INSERT … ON CONFLICT DO NOTHING`

**Decision** (the transport is the author's, 2026-11-17; the mechanism is the journal's).

- The key travels in the **`Idempotency-Key` request header**, optional (FR-031).
- A new table holds `(userid, idempotency_key)` **unique**, a fingerprint of the request, and the
  `order_id` it produced. Keys are therefore scoped per user (FR-030).
- The transaction starts with `INSERT … ON CONFLICT DO NOTHING`. **Inserting a row means this
  request is the first**: the work proceeds and the row's `order_id` is filled in the same
  transaction. **Inserting nothing means the key was used**: the committed row is read back, its
  fingerprint compared, and the order it names is returned (FR-028) — or the request is refused as a
  conflict if the fingerprint differs (FR-029).
- The fingerprint is **SHA-256 over the canonicalised request**: the path's user id and the body's
  fields in a fixed order, with money normalised to scale 2, stored as 64 hex characters. The key
  itself is not part of it, which is one reason it is a header and not a body field.
- **Retention**: rows are kept. Nothing expires them in this exercise, and a purge policy with no
  stated retention requirement would be an invented rule; the plan says so rather than leaving it
  unsaid.

**Rationale for the header**: `Idempotency-Key` is the convention clients and proxies already know,
and it keeps the key out of the body — so the body *is* the fingerprinted thing, with no field to
remember to exclude from the hash. A key inside the body would make the fingerprint a function of a
subset of the body, and "which subset" is exactly the kind of rule that rots.

**Why `ON CONFLICT DO NOTHING` and not a `SELECT` first**: a check-then-act is a race. Two concurrent
retries would both find no row and both trade. The unique index makes the decision atomically, and
the loser learns it lost by getting zero rows back.

**Why `READ COMMITTED`** ([R-211](#r-211-the-transaction-is-read-committed-deliberately-unlike-the-reads)):
the second transaction's insert blocks on the uncommitted unique key until the first commits, and then
must see the committed row. That is `READ COMMITTED`'s behaviour; under `REPEATABLE_READ` the same
insert raises a serialisation failure instead, and the retry becomes an error the client has to
interpret. This is the journal's own observation — "importante que la idempotencia sea read commited,
sino la segunda transacción no ve lo que la primera comiteó" — and it is why the `order_id` column is
nullable: the first transaction fills it before committing, so a reader that got there by conflict
always finds it set.

**Alternatives considered**

- **The key in the request body**, as the journal sketches. Rejected by the author, for the
  fingerprint reason above.
- **Fingerprinting by storing the whole request body** instead of a hash. Rejected: it stores user
  data for no gain, and comparing bodies means deciding whether whitespace and key order matter — a
  canonical form is needed either way, so hash the canonical form.
- **Answering a repeat with `409` regardless of the body.** Rejected: FR-028 requires a retry of the
  *same* request to get the first outcome. A client that timed out has no other way to learn what
  happened.
- **A unique index on the fingerprint alone, with no caller key.** Rejected: two deliberate identical
  orders are legitimate — a user may well buy 100 `PAMP` twice — so a body-only guard would refuse a
  real second order. The key is what makes "this is a retry" the caller's statement rather than a
  guess.
- **Expiring keys after 24 hours.** Rejected for now: no requirement states a window, and the table
  grows by one row per keyed submission. Written down as deferred rather than implemented.

---

## R-211: The transaction is `READ COMMITTED`, deliberately, unlike the reads

**Decision**. `OrderSubmissionService` runs each submission in one transaction at **`READ COMMITTED`**
— PostgreSQL's default, annotated explicitly so the contrast is visible — while `PortfolioService` and
`InstrumentSearchService` run at `REPEATABLE_READ`.

**Rationale**: the two need different things. A read needs every statement in the response to describe
one instant, which `REPEATABLE_READ` gives. A write needs to see what the transaction it just waited
for committed, which is `READ COMMITTED` — both for the idempotency conflict
([R-210](#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing))
and for the fold after the row lock: the lock is acquired first, so by the time the ledger is read
there is no concurrent writer for this user, and the snapshot must be the *current* one, not one taken
before the wait.

**Alternative considered**: `REPEATABLE_READ` here too, for uniformity. Rejected concretely: the
second of two concurrent submissions would read a snapshot taken before the first committed, so it
would validate against stale availability — the lock would serialise the requests and then hand the
second one the figures the first already spent — and the idempotent retry would fail with `40001`.

---

## R-212: The error map: 400, 404, 409, 503

**Decision**. One rejection type per answer, each naming the rule it broke in its detail.

| Answer | When |
|---|---|
| `201` | the submission produced an order: `FILLED`, `NEW` or `REJECTED` ([R-202](#r-202-every-recorded-outcome-answers-201-created-a-request-that-writes-nothing-never-does)) |
| `400` | the request cannot be interpreted or acted on: both a size and an amount, or neither; a `LIMIT` with no price; a `MARKET` with a price; a side that is not `BUY` or `SELL`; a size or amount outside the bounds of [R-207](#r-207-the-derivations-floor-and-the-bounds-are-stated-in-one-place-each); an unknown instrument; a `MONEDA` instrument; an instrument with no price on a `MARKET` order ([R-203](#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection)); a malformed body |
| `404` | no user with that id — the request's target does not exist ([R-201](#r-201-the-endpoint-is-post-apiv1usersuseridorders)) |
| `409` | the `Idempotency-Key` was used by this user for a different request (FR-029) |
| `503` | the transaction ran past its deadline, including while waiting on the user's lock, or no connection came free (ADR-0031, unchanged) |

**Rationale**: `404` for the user keeps the portfolio's answer for the same mistake on the same path
segment. Everything the body names is a `400`, because the target existed and the content did not make
sense — a body reference cannot make a resource missing. `409` is what "this key is already spoken
for" means.

**Alternatives considered**

- **`404` for an unknown instrument id too.** Rejected: the instrument is not the request's target, and
  a client cannot tell from a `404` whether the user or the instrument was the problem.
- **`422 Unprocessable Content` for the semantic refusals** (`MONEDA`, no price) and `400` only for
  malformed syntax. Defensible, and rejected for one reason: the spec draws exactly one line, between
  "recorded" and "refused", and a second line inside the refusals would invite arguing about which
  side each rule falls on. Noted as the alternative a reviewer may prefer.
- **Bean validation annotations on the request DTO** to produce the `400`s. Rejected: constitution VIII
  puts request validation in the use case, so the rules would be split between annotations on a DTO and
  a model object. The DTO carries the type and side as strings and the use case parses them, which is
  also how the detail gets to name the field and the allowed values.

---

## R-213: The order's instant is the server's, supplied by the service

**Decision**. The caller sends no timestamp; the service stamps the order and its history row with the
instant it processed the submission, and hands it to the model and the mapper. The model never reads a
clock.

**Rationale**: a client-supplied time on a ledger row is a client-supplied fact about when money moved.
And a model object that calls `LocalDateTime.now()` is a model object whose tests cannot state what
time it is, which is the kind of dependency constitution V exists to keep out.

**Alternative considered**: injecting a `java.time.Clock` into the service so tests can fix the instant.
Not done: nothing asserts on the timestamp's value, so it would be an extension point with no second
use (constitution IX). The one-line change is noted here for the first test that needs it.

---

## R-214: One mapping from an instrument row to an instrument, shared

**Decision**. The entity-to-`InstrumentRef` mapping exists three times after this feature — in
`InstrumentSearchMapper`, privately in `OrderMapper`, and in the submission's own lookup. It becomes
**one** `InstrumentMapper.toInstrumentRef(entity)` that the other two call.

**Rationale**: constitution IX allows duplicated structure and forbids a duplicated rule. "Which four
columns an instrument is made of, and which of them is the cash type" is closer to a rule than to
structure, and a fourth copy is how the feature after this one gets a fifth. It is a small refactor
inside this feature's own change, not a drive-by.

---

## R-215: `OrderEntity` becomes writable, with the database generating the id

**Decision**. `orders.id` is `SERIAL`, so the entity's id is database-generated on insert (JPA
`IDENTITY`); the entity gains a way to build a new row from the decision, and keeps its read path
untouched. The order and its status-history row are inserted through Spring Data repositories, in the
same transaction, with hand-written mappers (ADR-0014, constitution VIII).

A `REJECTED` row records the size that was derived — `0` when the rejection happened before a size
existed — and the price that governed the comparison. `V3` allows both: ADR-0030 deliberately removed
`CHECK (size > 0)`, and `price` is always present after
[R-203](#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection).

**Rationale**: the id has to be the database's, because the ledger's order is `(datetime, id)` and the
sequence is what the given schema provides. `IDENTITY` costs the loss of JDBC insert batching, which is
irrelevant at one order per request and is stated so nobody has to wonder whether it was considered.

**Alternative considered**: a client-generated UUID primary key, which would let the response be
assembled before the insert. Rejected: it changes the given schema's `id` column, and the idempotency
record already solves the problem a pre-known id would solve.

---

## R-216: No endpoint reads an order back; the portfolio does

**Decision**. This feature adds one endpoint. The order it produced is in the response, and its effect
is visible in the next portfolio read (FR-025, SC-008): a `FILLED` order moves cash and the position, a
`NEW` order lowers available cash or available holdings, a `REJECTED` one changes neither.

**Rationale**: the constitution lists the API's endpoints — portfolio, search, submission, cancellation
— and a `GET /orders/{id}` is not among them. A client that lost the response does not need one either:
that is what the idempotency key is for
([R-210](#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing)).

**Deferred**, written down rather than invented: an order history endpoint, which is the natural reader
of `order_status_history` and has no requirement behind it yet.

---

## R-217: What this feature deliberately does not build

| Not built | Why |
|---|---|
| A matching engine | ADR-0007. A `LIMIT` order is born `NEW` and nothing fills it |
| Cancellation | ADR-0006 and a separate feature, so this feature produces `NEW` orders it cannot retire |
| Cash transfers (`CASH_IN` / `CASH_OUT`) | FR-002, FR-008: not orders against the market. A dedicated endpoint with its own cohesive parameters, its own spec and its own decision |
| Partial fills | No matching engine, so an order is all-or-nothing. Deriving a size from an amount happens before the order exists and is not a partial fill |
| Order expiry or time in force | The statement defines none, so none is invented |
| A key-expiry job | [R-210](#r-210-idempotency-an-idempotency-key-header-a-fingerprint-and-insert--on-conflict-do-nothing) |
