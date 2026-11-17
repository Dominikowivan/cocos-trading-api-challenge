# Feature Specification: Order Placement

**Feature Branch**: `feature/orderPlacement`

**Created**: 2026-11-17

**Status**: Draft

> **Amended 2026-11-17, at plan time, by the author's decision.** A `MARKET` submission for an
> instrument with no market price is now **refused as an invalid request with nothing written**,
> instead of being recorded as a `REJECTED` order. An instrument that does not quote is a property of
> the request, knowable before any of the user's money is considered, and refusing it keeps every row
> in `orders` carrying a price — so `V3`'s `NOT NULL` on `price` stands and no money figure is written
> as `0` when it is unknown. It is a departure from the challenge statement for that one case, so it
> is recorded in ADR-0034 and stated in the README. The reasoning and the alternatives are in
> [research.md](research.md#r-203-an-instrument-with-no-price-refuses-the-submission-it-is-not-a-recorded-rejection).
> Touched below: FR-010, FR-019, user story 1 scenario 8, the "instrument with no price" edge case,
> SC-009 and the *Rejection reason* entity. Everything else is as it was specified.

**Input**: Order submission endpoint, as described in `docs/challenge/backend-challenge.md` (the source of requirements) and scoped by `docs/journal/07OrderPlacement.md`. Delivered as prioritized, independently testable user stories, one per order kind and per protection.

## Source of Requirements

The behaviour this feature must deliver is defined in the challenge statement,
[`docs/challenge/backend-challenge.md`](../../docs/challenge/backend-challenge.md) — see its
*Enviar una orden al mercado* bullet in **Resumen**, the *Consideraciones funcionales* bullets on
sending either an exact number of shares or a total investment amount in pesos (with no fractional
shares), on `side`, on the `NEW` / `FILLED` / `REJECTED` / `CANCELLED` statuses, on `MARKET` filling
immediately at the last `close` while `LIMIT` is born `NEW`, on rejecting an order larger than what
is available (pesos for a purchase, shares for a sale), on transfers modelled as `CASH_IN` /
`CASH_OUT`, and on computing holdings and available pesos from the `orders` table. That document is
kept verbatim and is not restated here; this spec only adds what it leaves open.

Decisions already taken, cited rather than re-decided:

- [ADR-0002](../../docs/adr/0002-orders-as-ledger.md) — `orders` is the ledger. This feature is the
  only one that appends to it, and it validates against the same projection the portfolio reports.
- [ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md) — a `NEW` limit order reserves what
  it commits: pesos for a `BUY`, shares for a `SELL`. Validation reads available cash and available
  holdings, never the raw balance.
- [ADR-0005](../../docs/adr/0005-order-status-history.md) — every status transition appends a row to
  `order_status_history` in the same transaction.
- [ADR-0006](../../docs/adr/0006-dedicated-cancel-endpoint.md) — cancellation is its own endpoint, so
  this feature never receives a status or a "cancel" flag. Reaffirmed in
  `docs/journal/07OrderPlacement.md`: on a cancel request almost every other parameter of a
  submission is meaningless.
- [ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md) — no matching engine and
  no authentication. A `LIMIT` order is born `NEW` and leaves that state only by cancellation; the
  user id arrives as untrusted input and every read is scoped by it.
- [ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md) — an unknown money figure is
  absent, never `0`. A `MARKET` order against an instrument with no price has no execution price and
  is never executed at zero; as amended, it is refused rather than recorded (ADR-0034).
- [ADR-0015](../../docs/adr/0015-the-account-snapshot-is-the-folds-contract.md) — the fold over the
  ledger returns one account snapshot. Order validation consumes that snapshot; it does not compute
  available cash a second way.
- [ADR-0016](../../docs/adr/0016-decimal-scale-and-rounding-policy.md) — the decimal scale and
  rounding every money figure follows.
- [ADR-0024](../../docs/adr/0024-every-response-is-validated-against-the-contract.md) — the endpoint
  is added to `docs/api/openapi.yaml` and `docs/api/trading-api.http` as part of its implementation.

Unlike the portfolio and the search, this feature **writes**: it appends exactly one order to the
ledger per accepted request, and a rejected order is written too — the statement requires a
rejection to be stored as `REJECTED`.

### Measurements this spec is built on

Every figure below was measured on `src/main/resources/db/seed/R__given_dataset.sql`
(2026-11-17), not estimated, and agrees with the figures the portfolio spec reports for the same
user:

- User `1` has settled cash `753,000.00` and reserves `125,500.00` in two `BUY LIMIT` orders still
  `NEW` (50 `LOMA` at `710.00` and 60 `BMA` at `1,500.00`), leaving **available cash
  `627,500.00`**.
- User `1`'s holdings from `FILLED` orders: `PAMP` **40**, `METR` **500**, `BMA` **−10** (a ledger
  anomaly, served as-is per [ADR-0012](../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md)).
  No `SELL` order is `NEW`, so available holdings equal the holdings.
- Latest closes: `PAMP` (id 47) **925.85**, `METR` (id 54) **229.50**, `BMA` (id 31) **1,502.80**,
  `LOMA` (id 45) **734.65**.
- `925.85 × 677 = 626,800.45` and `925.85 × 678 = 627,726.30`, so 677 `PAMP` is the largest
  `MARKET BUY` user `1` can afford and 678 is the first they cannot.
- `925.85 × 40 = 37,034.00` exactly, so user `1`'s whole `PAMP` position is worth `37,034.00` at the
  last close.
- Three instruments have no market data row: `PGR` (id 3), `IRCP` (id 10) and `ARS` (id 66). `PGR`
  and `IRCP` are tradable instruments with no price, reachable from the provided seed, so the
  "no price" path needs no constructed fixture.
- The only `MONEDA` instrument is `ARS` / `PESOS`, id 66, and the 11 seeded orders include one
  `CASH_IN` and one `CASH_OUT` against it.

## User Scenarios & Testing *(mandatory)*

Every story below is one submission request for one user and one instrument, and each ends with an
order persisted in the ledger whose status the caller can read back through the portfolio. The
stories are ordered as the author sequenced them in `docs/journal/07OrderPlacement.md`: all of
`MARKET` first, then all of `LIMIT`, then the two protections that are orthogonal to the business
rules and are deliberately built last so they do not add concurrency to something already complex.

### User Story 1 - Buy or sell an exact number of shares at market (Priority: P1)

A user sends a `MARKET` order naming an instrument, a side and a number of shares. There is no price
in the request: a purchase is executed at the instrument's last `close` if the user can pay for it,
a sale is executed if the user holds the shares, and the order is persisted `FILLED`. If they cannot
pay, or do not hold the shares, the order is persisted `REJECTED` with the reason — not silently
dropped and not an error the caller has to interpret.

**Why this priority**: it is the smallest complete slice of the statement's third endpoint and the
only one that needs nothing but a price the market already published. Every later story reuses its
validation, its persistence and its response shape; a `MARKET` order for an exact size is already a
usable trading API.

**Independent Test**: for user `1`, buy 100 `PAMP` and check the order is `FILLED` at `925.85`;
buy 1,000 `PAMP` and check it is `REJECTED` for insufficient funds; sell 40 `PAMP` and 41 `PAMP` and
check the first is `FILLED` and the second `REJECTED`. The portfolio before and after each request
is the only other thing needed to judge the result.

**Acceptance Scenarios**:

1. **Given** the provided dataset and user `1` (available cash `627,500.00`), **When** they send a
   `MARKET BUY` of 100 `PAMP`, **Then** an order is recorded `FILLED` with size `100` and price
   `925.85`, the instrument's last close, and the user's available cash drops by `92,585.00`.
2. **Given** the same state, **When** they send a `MARKET BUY` of 1,000 `PAMP` (`925,850.00`),
   **Then** the order is recorded `REJECTED` with a machine-readable reason of insufficient funds,
   their cash is unchanged, and the response reports the rejected order rather than failing.
3. **Given** the same state, **When** they send a `MARKET BUY` of 677 `PAMP` (`626,800.45`), **Then**
   it is `FILLED`; **When** they send 678 (`627,726.30`) instead, **Then** it is `REJECTED` — the
   boundary is available cash, to the cent.
4. **Given** user `1` holding 40 `PAMP`, **When** they send a `MARKET SELL` of 40 `PAMP`, **Then** it
   is `FILLED` at `925.85` for `37,034.00` and the position goes to zero.
5. **Given** the same holding, **When** they send a `MARKET SELL` of 41 `PAMP`, **Then** it is
   `REJECTED` with a reason of insufficient holdings.
6. **Given** user `1` holding `BMA` **−10**, **When** they send any `MARKET SELL` of `BMA`, **Then**
   it is `REJECTED` for insufficient holdings: a negative position is reported, never sold from.
7. **Given** a user with no orders at all, **When** they send any `MARKET BUY`, **Then** it is
   `REJECTED` for insufficient funds, and **When** they send any `MARKET SELL`, **Then** it is
   `REJECTED` for insufficient holdings.
8. **Given** instrument `PGR` (id 3), which has no market data row, **When** a user sends a `MARKET`
   order for it, **Then** the request is refused and nothing is written — never executed at `0`
   ([ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md)), and never recorded as a
   `REJECTED` order either (amended 2026-11-17, ADR-0034): the instrument does not quote, which is
   knowable before the user's money is looked at.
9. **Given** any accepted or rejected submission, **When** the order is persisted, **Then** its
   status transition is recorded in the status history in the same transaction
   ([ADR-0005](../../docs/adr/0005-order-status-history.md)).

---

### User Story 2 - Spend a total amount at market, not a share count (Priority: P2)

A user who thinks in pesos rather than in shares sends a `MARKET` order with a total investment
amount instead of a size: "buy up to `100,000.00` of `PAMP`", "sell about `30,000.00` of `PAMP`".
The system derives the largest whole number of shares that fits in that amount at the last close,
and no fraction of a share is ever traded. Size and total amount are mutually exclusive: a request
carries exactly one of them.

**Why this priority**: it is an explicit requirement of the statement and the second half of the
`MARKET` endpoint, but it is built on P1's validation and execution, so it adds one derivation step
to a path that already works.

**Independent Test**: for user `1`, buy `100,000.00` of `PAMP` and check 108 shares are bought for
`99,991.80`; buy `700,000.00` and check it is rejected; sell `30,000.00` of `PAMP` and check 32
shares are sold; sell `40,000.00` and check it is rejected.

**Acceptance Scenarios**:

1. **Given** user `1` (available cash `627,500.00`) and `PAMP` at `925.85`, **When** they send a
   `MARKET BUY` for a total amount of `100,000.00`, **Then** an order for **108** shares is
   `FILLED` for `99,991.80` — `floor(100,000.00 / 925.85)` — and the remaining `8.20` is not spent.
2. **Given** the same state, **When** they send a `MARKET BUY` for `700,000.00`, which exceeds
   available cash, **Then** the order is `REJECTED` for insufficient funds and no size is derived.
3. **Given** the same state, **When** they send a `MARKET BUY` for `500.00`, less than one share,
   **Then** the order is `REJECTED`: the derived size is `0` and an order for no shares is not an
   order.
4. **Given** the same state, **When** they send a `MARKET BUY` for exactly `627,500.00`, their whole
   available cash, **Then** 677 shares are `FILLED` for `626,800.45`.
5. **Given** user `1` holding 40 `PAMP` (worth `37,034.00` at the last close), **When** they send a
   `MARKET SELL` for a total amount of `30,000.00`, **Then** **32** shares are `FILLED` for
   `29,627.20` and 8 shares remain.
6. **Given** the same holding, **When** they send a `MARKET SELL` for `40,000.00`, more than the
   position is worth, **Then** the order is `REJECTED` for insufficient holdings.
7. **Given** the same holding, **When** they send a `MARKET SELL` for exactly `37,034.00`, **Then**
   all 40 shares are `FILLED` and the position goes to zero.
8. **Given** the same holding, **When** they send a `MARKET SELL` for `900.00`, less than one share,
   **Then** the order is `REJECTED` with the derived size `0`.
9. **Given** any request, **When** it carries both a size and a total amount, or neither, **Then** it
   is refused as an invalid request and no order is recorded — the rejection is about the request's
   shape, not about the user's money.

---

### User Story 3 - Place a limit order for an exact number of shares (Priority: P3)

A user sends a `LIMIT` order naming an instrument, a side, a number of shares and the price they are
willing to trade at. The order is validated against that price — not against the market — and
persisted `NEW`, reserving what it commits: pesos for a purchase, shares for a sale. The reservation
is immediately visible as lower available cash or lower available holdings, so a second order cannot
spend the same money twice. Nothing fills it: with no matching engine it stays `NEW` until cancelled
([ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md)).

**Why this priority**: it completes the statement's two order types and is the first story whose
order does not settle, so it is what makes reservations observable. It depends on P1's validation
shape but swaps the price source, which is the whole difference.

**Independent Test**: for user `1`, place a `LIMIT BUY` of 100 `PAMP` at `900.00` and check the order
is `NEW` and available cash falls to `537,500.00`; place a `LIMIT SELL` of 40 `PAMP` at `1,000.00`
and check available `PAMP` falls to `0` while cash is unchanged; then place a `MARKET SELL` of 1
`PAMP` and check it is `REJECTED`.

**Acceptance Scenarios**:

1. **Given** user `1` (available cash `627,500.00`), **When** they send a `LIMIT BUY` of 100 `PAMP`
   at `900.00`, **Then** the order is recorded `NEW` with price `900.00`, and their available cash
   becomes `537,500.00` while their settled cash is unchanged.
2. **Given** the same state, **When** they send a `LIMIT BUY` of 700 `PAMP` at `900.00`
   (`630,000.00`), **Then** it is `REJECTED` for insufficient funds — validated at `900.00`, the
   price they sent, never at the market's `925.85`.
3. **Given** the same state, **When** they send a `LIMIT BUY` of 100 `PAMP` at `2,000.00`, far above
   the market, **Then** it is `NEW`: a price nobody will trade at is not a rejection reason, and the
   order simply never fills.
4. **Given** user `1` holding 40 `PAMP`, **When** they send a `LIMIT SELL` of 40 `PAMP` at
   `1,000.00`, **Then** the order is `NEW`, their available `PAMP` becomes `0`, and their available
   cash is unchanged — a sale reserves shares, not pesos.
5. **Given** that `NEW` sale, **When** they then send a `MARKET SELL` of 1 `PAMP`, **Then** it is
   `REJECTED` for insufficient holdings, because the 40 shares are already committed.
6. **Given** the same holding, **When** they send a `LIMIT SELL` of 41 `PAMP` at any price, **Then**
   it is `REJECTED` for insufficient holdings.
7. **Given** instrument `PGR` (id 3), which has no market data, **When** a user sends a `LIMIT` order
   for it, **Then** it is validated and recorded normally: the price came in the request, so the
   missing close is irrelevant.
8. **Given** any request of type `LIMIT`, **When** it carries no price, **Then** it is refused as an
   invalid request; **When** a request of type `MARKET` carries a price, **Then** it is refused too,
   rather than the price being silently ignored.

---

### User Story 4 - Place a limit order for a total amount (Priority: P4)

A user combines the previous two: a `LIMIT` order for a total amount of pesos at their own price.
The derivation is the same as P2 with one substitution — the price they sent replaces the market
close on **both** sides of the comparison — so what limits them is their own price, not the market's.
The resulting order is `NEW` and reserves what it commits, exactly as in P3.

**Why this priority**: it is the last of the four submission paths and the only one that needs both
previous derivations; it adds no new rule, only the combination, so it is the cheapest story to build
last and the clearest to verify once P2 and P3 hold.

**Independent Test**: for user `1`, send a `LIMIT BUY` for `100,000.00` at `900.00` and check 111
shares are `NEW` reserving `99,900.00`; send a `LIMIT SELL` of `PAMP` for `30,000.00` at `1,000.00`
and check 30 shares are reserved — 30, not the 32 the same amount buys at the market close, which is
what proves the substitution.

**Acceptance Scenarios**:

1. **Given** user `1` (available cash `627,500.00`), **When** they send a `LIMIT BUY` for a total
   amount of `100,000.00` at `900.00`, **Then** an order for **111** shares —
   `floor(100,000.00 / 900.00)` — is recorded `NEW`, reserving `99,900.00`.
2. **Given** the same state, **When** they send a `LIMIT BUY` for `700,000.00` at any price, **Then**
   it is `REJECTED` for insufficient funds: the amount alone already exceeds available cash.
3. **Given** the same state, **When** they send a `LIMIT BUY` for `800.00` at `900.00`, **Then** it is
   `REJECTED` with the derived size `0`.
4. **Given** user `1` holding 40 `PAMP`, **When** they send a `LIMIT SELL` for `30,000.00` at
   `1,000.00`, **Then** **30** shares are recorded `NEW` and reserved — the position is valued at the
   limit price (`40 × 1,000.00 = 40,000.00`), not at the close.
5. **Given** the same holding, **When** they send a `LIMIT SELL` for `50,000.00` at `1,000.00`,
   **Then** it is `REJECTED` for insufficient holdings, because the position is worth `40,000.00` at
   that price.
6. **Given** the same holding and a limit price of `500.00`, **When** they send a `LIMIT SELL` for
   `30,000.00`, **Then** it is `REJECTED`: at their own price the position is worth `20,000.00`. The
   same request would have been accepted at `1,000.00`.

---

### User Story 5 - Two simultaneous orders cannot spend the same pesos (Priority: P5)

Two different orders for the same user arriving at the same moment are serialised, so they cannot
both read the same available cash and both commit against it. One of them wins and the other is
rejected on the balance the first one left. Orders of *different* users are not affected: one user's
submissions never wait on another's.

**Why this priority**: without it every validation in P1–P4 is advisory — two concurrent requests can
each see `627,500.00` and together spend `740,680.00`. It is placed after the business rules
deliberately (`docs/journal/07OrderPlacement.md`): the author chose not to add concurrency control to
a path whose rules were not yet proven, and constitution principle IV requires it before the feature
is done.

**Independent Test**: fire two `MARKET BUY` orders of 400 `PAMP` for user `1` at the same instant
(`370,340.00` each, `740,680.00` together, against `627,500.00` available) and check exactly one is
`FILLED` and one `REJECTED`, never both filled and never both rejected; then fire one order for each
of two different users and check neither is delayed by the other.

**Acceptance Scenarios**:

1. **Given** user `1` with available cash `627,500.00`, **When** two `MARKET BUY` orders of 400
   `PAMP` are submitted concurrently, **Then** exactly one is `FILLED` and the other is `REJECTED`
   for insufficient funds, and the resulting available cash is the one the single fill implies.
2. **Given** user `1` holding 40 `PAMP`, **When** two `MARKET SELL` orders of 40 `PAMP` are submitted
   concurrently, **Then** exactly one is `FILLED` and the other is `REJECTED` for insufficient
   holdings, and the position never goes negative from this feature's own writes.
3. **Given** two concurrent submissions that *both* fit in the available cash, **When** both are
   processed, **Then** both are `FILLED` and the final available cash reflects both.
4. **Given** concurrent submissions by two different users, **When** both are processed, **Then**
   neither is rejected on account of the other and both complete.
5. **Given** any number of concurrent submissions for one user, **When** they have all been
   processed, **Then** the ledger satisfies the same invariant a sequence of one-at-a-time requests
   would: available cash is never negative and no instrument's available holdings are driven below
   zero by these orders.

---

### User Story 6 - The same order sent twice is executed once (Priority: P6)

A client that loses the response and retries — a timeout, a tapped button, a queue redelivery — must
not end up with two orders. A submission carries a caller-supplied key; the first request with that
key is executed and its outcome is stored against it, and any repeat of the *same* request with the
*same* key returns that first outcome instead of trading again. A request reusing a key with a
*different* body is refused as a conflict rather than quietly answered with somebody else's order.

**Why this priority**: it protects against a duplicate, which is a client-side accident, while P5
protects the ledger's correctness; and it is orthogonal to every rule above
(`docs/journal/07OrderPlacement.md`: "esto va PREVIO y ORTOGONAL a lo que tiene que pasar en el resto
de la transacción"). It is built last so the rest of the endpoint is proven first.

**Independent Test**: send the same `MARKET BUY` of 100 `PAMP` for user `1` twice with the same key
and check only one order exists in the ledger and both responses describe it; then send a different
order with that same key and check it is refused as a conflict.

**Acceptance Scenarios**:

1. **Given** a submission carrying an idempotency key, **When** it is sent twice with an identical
   body, **Then** exactly one order exists in the ledger and both responses report that same order,
   with the same identifier and status.
2. **Given** the same key, **When** a second request carries a different body, **Then** it is refused
   as a conflict and no second order is recorded.
3. **Given** two requests with the same key submitted concurrently, **When** both are processed,
   **Then** one executes and the other returns the first one's stored outcome; the key never yields
   two orders.
4. **Given** a key already used by user `1`, **When** user `2` submits with that same key, **Then**
   it is processed as a new request: keys are scoped per user and never collide across users.
5. **Given** a request that is rejected on the business rules (`REJECTED`), **When** it is retried
   with the same key, **Then** the stored `REJECTED` order is returned and a second `REJECTED` row is
   not written: a decision already taken is not re-taken.
6. **Given** a submission carrying no key, **When** it is processed, **Then** it is executed normally
   — the protection is available to callers who ask for it and the endpoint stays usable without it.

---

### Edge Cases

- **Rejected is an outcome, not a failure.** An order the user cannot afford is persisted `REJECTED`
  and reported as such with a success status; the statement requires it to be stored. A malformed
  request (no instrument, both size and amount, a `LIMIT` without a price) is a different thing: it
  is refused, nothing is written, and the two are distinguishable by the caller.
- **An unknown user or instrument.** A submission naming a user id or an instrument id that does not
  exist is refused as an invalid request and nothing is written. It is not a `REJECTED` order: there
  is no account to reject it against. With no authentication the user id is untrusted input and is
  checked like any other field.
- **Cash is not tradable here.** A submission naming a `MONEDA` instrument (`ARS`, id 66) is refused:
  a deposit or a withdrawal has no operable instrument, no execution price and no funds check against
  the market. `CASH_IN` and `CASH_OUT` exist in the ledger and count towards cash, and they enter it
  through a transfer of their own, which is **out of scope here** (see Assumptions).
- **An instrument with no price.** `MARKET` needs the last close, so a `MARKET` order for `PGR`
  (id 3) or `IRCP` (id 10) is **refused as an invalid request with nothing written** (amended
  2026-11-17, ADR-0034), alongside an unknown instrument and a `MONEDA` one: all three are things the
  request got wrong, decidable without reading the account. A `LIMIT` order for the same instrument
  is fine: its price came in the request.
- **Size must be a whole, positive number of shares.** `0`, a negative size and a fractional size are
  refused as invalid requests. No fraction of a share is ever traded, including on the amount paths,
  where the derived size is floored.
- **A total amount that buys nothing.** An amount smaller than one share's price derives a size of
  `0` and is `REJECTED` — the user has the money, it just does not reach one share. An amount of `0`
  or a negative amount is an invalid request, not a rejection.
- **The amount is a ceiling, not a target.** `100,000.00` of `PAMP` at `925.85` spends `99,991.80`
  and leaves `8.20` unspent. The order is not stretched to a 109th share, and the leftover is not an
  error.
- **A negative position.** User `1` holds `BMA` **−10**, a ledger anomaly
  ([ADR-0012](../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md)). Any `SELL` of `BMA`
  is `REJECTED`: available holdings below zero are reported, never traded from, and the anomaly is
  not corrected by this feature.
- **An order against your own open order.** A user with a `NEW BUY LIMIT` has less available cash than
  settled cash (user `1`: `627,500.00` against `753,000.00`), and a user with a `NEW SELL LIMIT` has
  fewer available shares than held. Both are enforced on the available figure
  ([ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md)), so a user cannot commit the same
  pesos or the same shares twice by splitting them across two orders.
- **A limit price that makes no market sense.** A `BUY` far above or a `SELL` far below the close is
  accepted and stays `NEW`. With no matching engine there is nothing to fill it and no rule it
  breaks; it is the user's price.
- **A price or an amount with more precision than money has.** A price or amount carrying more
  decimals than the money policy allows is handled under
  [ADR-0016](../../docs/adr/0016-decimal-scale-and-rounding-policy.md); it never silently changes the
  figure the caller sees reported back on the order.
- **A huge size or amount.** A size or amount beyond what the ledger can represent is refused as an
  invalid request rather than overflowing a computation; within range, it is simply `REJECTED` for
  insufficient funds.
- **Status and cancellation never arrive here.** A submission has no status field and no "cancel"
  flag: the status is derived from the order type and the validation, and cancelling is a separate
  endpoint ([ADR-0006](../../docs/adr/0006-dedicated-cancel-endpoint.md)).

## Requirements *(mandatory)*

### Functional Requirements

**The request**

- **FR-001**: The system MUST expose an endpoint that submits one order to the market, naming the
  user, the instrument, the order type (`MARKET` or `LIMIT`), the side (`BUY` or `SELL`) and what to
  trade — either an exact number of shares or a total amount in pesos.
- **FR-002**: The side MUST be `BUY` or `SELL` only. `CASH_IN` and `CASH_OUT` MUST NOT be accepted
  here: they are not orders against the market.
- **FR-003**: The quantity MUST be expressed as exactly one of a share count or a total amount in
  pesos. A request carrying both, or neither, MUST be refused as an invalid request with nothing
  written.
- **FR-004**: A share count MUST be a whole number greater than zero; a total amount MUST be greater
  than zero. Values outside that MUST be refused as invalid requests.
- **FR-005**: A `LIMIT` request MUST carry a price greater than zero. A `MARKET` request MUST NOT
  carry a price, and one that does MUST be refused rather than having the price ignored.
- **FR-006**: A request MUST NOT carry an order status or a cancellation flag; cancelling is a
  separate endpoint ([ADR-0006](../../docs/adr/0006-dedicated-cancel-endpoint.md)).
- **FR-007**: A request naming a user or an instrument that does not exist MUST be refused as an
  invalid request, and nothing MUST be written to the ledger.
- **FR-008**: A request naming an instrument of type `MONEDA` MUST be refused as an invalid request:
  cash is not a tradable asset here, which is the same exclusion the search applies
  (`specs/002-instrument-search/spec.md`, FR-023).

**The execution price**

- **FR-009**: A `MARKET` order MUST be priced at the instrument's latest `close`, resolved per
  instrument rather than at one shared date.
- **FR-010** *(amended 2026-11-17, ADR-0034)*: A `MARKET` order for an instrument with no market data
  MUST be refused as an invalid request, with nothing written to the ledger, and MUST NOT be executed
  at `0`
  ([ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md)).
- **FR-011**: A `LIMIT` order MUST be priced at the price sent in the request, and MUST NOT read
  market data for validation or for execution. It is therefore valid for an instrument with no market
  data.

**Validation against what is available**

- **FR-012**: Validation MUST use available cash and available holdings — the balance minus what
  `NEW` orders reserve — never the settled balance or the raw holding
  ([ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md)).
- **FR-013**: The figures used for validation MUST come from the same fold over the ledger that the
  portfolio endpoint reports ([ADR-0015](../../docs/adr/0015-the-account-snapshot-is-the-folds-contract.md)).
  Available cash MUST NOT be computed a second way.
- **FR-014**: For a `BUY` of an exact size, the order MUST be rejected unless available cash is at
  least `size × price`, where the price is the one FR-009 or FR-011 establishes.
- **FR-015**: For a `SELL` of an exact size, the order MUST be rejected unless the user's available
  holding of that instrument is at least that size. A holding at or below zero MUST reject every
  `SELL`.
- **FR-016**: For a total amount, the comparison MUST be: available (cash for a `BUY`;
  `available holding × price` for a `SELL`) against the requested amount. If available is less than
  the amount, the order MUST be `REJECTED`.
- **FR-017**: Otherwise the size MUST be `floor(amount / price)`, and if that size is `0` the order
  MUST be `REJECTED`. No fraction of a share is ever traded, and the unspent remainder of the amount
  is neither spent nor reported as an error.
- **FR-018**: The price in FR-016 and FR-017 MUST be the latest `close` for a `MARKET` order and the
  requested price for a `LIMIT` order, on **both** sides of the comparison, so a `LIMIT` order is
  bounded by the user's own price rather than by the market's.
- **FR-019** *(amended 2026-11-17, ADR-0034)*: Every rejection MUST be persisted as an order in
  status `REJECTED` carrying a machine-readable reason: insufficient funds, insufficient holdings, or
  a derived size of zero. "No price available" is no longer among them: FR-010 refuses that request
  instead of recording it, so every reason a stored rejection carries is a statement about the user's
  money.

**The resulting order**

- **FR-020**: An accepted `MARKET` order MUST be persisted `FILLED`; an accepted `LIMIT` order MUST
  be persisted `NEW` and MUST NOT fill
  ([ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md)).
- **FR-021**: Exactly one order MUST be appended to the ledger per processed submission, whatever its
  status, with its instrument, user, side, type, size, price, status and timestamp.
- **FR-022**: Every status transition — including the first, into `FILLED`, `NEW` or `REJECTED` —
  MUST append a row to the status history in the same transaction as the order
  ([ADR-0005](../../docs/adr/0005-order-status-history.md)).
- **FR-023**: The response MUST report the resulting order: its identifier, its status, the side and
  type, the size actually ordered (the derived one on the amount paths), the price it was recorded at
  and, when rejected, the reason. A `REJECTED` order MUST be reported as a successful submission with
  that status, distinguishable from a refused invalid request.
- **FR-024**: Money and quantity figures MUST be exact decimals in pesos with the scale and rounding
  of [ADR-0016](../../docs/adr/0016-decimal-scale-and-rounding-policy.md); the only rounding of a
  share count is the floor of FR-017.
- **FR-025**: An order's effect MUST be visible in the next portfolio read for that user: a `FILLED`
  order moves cash and the position, a `NEW` order lowers available cash or available holdings, and a
  `REJECTED` order changes neither.

**Protections**

- **FR-026**: Reading the available figures and appending the order that consumes them MUST happen so
  that two concurrent submissions for the same user cannot both commit against the same availability.
  The serialisation MUST be per user: a submission MUST NOT wait on another user's
  (constitution principle IV).
- **FR-027**: After any set of concurrent submissions for one user, the ledger MUST satisfy the same
  invariants a one-at-a-time sequence would: these orders MUST NOT drive available cash below zero or
  an available holding below zero.
- **FR-028**: A submission MAY carry a caller-supplied idempotency key. Two requests with the same
  key and the same body, for the same user, MUST produce exactly one order, and every repeat MUST
  return the outcome of the first — including when that outcome was `REJECTED`.
- **FR-029**: A request reusing a key with a different body MUST be refused as a conflict, and MUST
  NOT be answered with the earlier order.
- **FR-030**: Idempotency keys MUST be scoped per user, so the same key used by two users is two
  independent requests.
- **FR-031**: A submission carrying no idempotency key MUST be processed normally.

### Key Entities

- **Order (written)**: one row in the ledger — user, instrument, side (`BUY` / `SELL` here), type
  (`MARKET` / `LIMIT`), size in whole shares, the price it was recorded at, its status (`FILLED`,
  `NEW` or `REJECTED` from this feature) and its timestamp. This feature is the only one that appends
  to the ledger.
- **Order status history (written)**: an append-only record of each status transition of an order,
  written in the same transaction ([ADR-0005](../../docs/adr/0005-order-status-history.md)).
- **Submission (input)**: what the caller sends — user, instrument, type, side, exactly one of size
  or total amount, a price when the type is `LIMIT`, and an optional idempotency key. Not stored as
  such; what is stored is the resulting order.
- **Account snapshot (derived)**: available cash, available holdings per instrument and the settled
  figures behind them, folded from the ledger. Read here, reported by the portfolio
  ([ADR-0015](../../docs/adr/0015-the-account-snapshot-is-the-folds-contract.md)).
- **Rejection reason (derived)**: the machine-readable cause recorded on a `REJECTED` order —
  insufficient funds, insufficient holdings, derived size of zero (amended 2026-11-17: no longer
  "no price available", ADR-0034).
- **Idempotency record (written)**: the association between a user, a caller-supplied key, a
  fingerprint of the request and the order it produced, used to answer a repeat without trading
  again.
- **Market data (read)**: the daily price rows; only the instrument's latest `close` is read, and only
  for `MARKET` orders.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the provided dataset, user `1` buying 100 `PAMP` at market gets a `FILLED` order of
  100 shares at `925.85`, and their available cash falls from `627,500.00` to `534,915.00`.
- **SC-002**: On the same dataset, user `1` buying 677 `PAMP` at market is `FILLED` and buying 678 is
  `REJECTED`, so the funds boundary is exact to the cent and not approximate.
- **SC-003**: On the same dataset, user `1` selling 40 `PAMP` at market is `FILLED` and selling 41 is
  `REJECTED`; selling any `BMA`, where the position is `−10`, is always `REJECTED`.
- **SC-004**: On the same dataset, user `1` buying `100,000.00` of `PAMP` at market gets exactly 108
  shares for `99,991.80`, and selling `30,000.00` of `PAMP` sells exactly 32 shares for `29,627.20`.
- **SC-005**: On the same dataset, user `1` selling `30,000.00` of `PAMP` with a limit price of
  `1,000.00` gets 30 shares, not the 32 the same amount yields at market — the limit price governs
  both sides of the calculation.
- **SC-006**: On the same dataset, user `1` placing a `LIMIT BUY` of 100 `PAMP` at `900.00` sees the
  order `NEW` and their available cash drop to `537,500.00` with their settled cash unchanged at
  `753,000.00`.
- **SC-007**: A `LIMIT SELL` of a user's whole position leaves them unable to sell one more share of
  it, while their available cash is untouched.
- **SC-008**: Every order a submission produces — `FILLED`, `NEW` or `REJECTED` — is readable back
  from the ledger with a status and, when rejected, a reason; no submission ever ends with nothing
  recorded except a refused invalid request.
- **SC-009** *(amended 2026-11-17, ADR-0034)*: A `MARKET` order for `PGR` or `IRCP`, which have no
  price, is refused with nothing written to the ledger; a `LIMIT` order for the same instruments is
  accepted.
- **SC-010**: Two concurrent `MARKET BUY` orders of 400 `PAMP` for user `1` (`740,680.00` together
  against `627,500.00` available) end with exactly one `FILLED` and one `REJECTED`, in every run of
  the test.
- **SC-011**: Submitting the same order twice with one idempotency key leaves exactly one order in
  the ledger, and both responses name it.
- **SC-012**: The available cash this feature validates against equals, to the cent, the available
  cash the portfolio endpoint reports for the same user at the same moment.
- **SC-013**: A user can go from a search result to a submitted order using only the instrument id the
  search returned, with no further lookup (`specs/002-instrument-search/spec.md`, SC-008).
- **SC-014**: Every rejection path above is covered by a test, not only the happy paths
  (constitution principle V).

## Assumptions

- **Cash transfers are not this endpoint.** `CASH_IN` and `CASH_OUT` exist in the ledger and count
  towards cash, but they are not created here (FR-002, FR-008), decided by the author in
  `docs/journal/07OrderPlacement.md`: a deposit has no operable instrument, no execution price and
  no funds check against the market, and on a `MONEDA` instrument "size" and "a total amount" are the
  same number, so the amount path is meaningless. A dedicated transfer endpoint is intended, with its
  own cohesive parameters, and is **a separate feature**: it is not in this spec's scope, and the
  constitution lists the API's endpoints as portfolio, search, submission and cancellation, so adding
  it needs its own spec and decision.
- **Cancellation is a separate feature** ([ADR-0006](../../docs/adr/0006-dedicated-cancel-endpoint.md)),
  reaffirmed in the journal. This feature therefore produces `NEW` orders that it cannot itself
  retire; that is expected and not a gap.
- **The consolidated amount rule is the one implemented.** The journal first sketches a `BUY` by
  amount as the smaller of `floor(amount / price)` and `floor(available / price)`, then settles on:
  reject if available is less than the amount, otherwise `size = floor(amount / price)`, reject if the
  size is `0` (FR-016, FR-017). The two agree — once available is at least the amount, the
  amount-derived size is already the smaller — so the second, which the journal states as "lo que
  queda, idéntico para los dos lados", is taken as the rule, and the redundant minimum is not
  implemented.
- **A rejection is a recorded business outcome; a malformed request is not.** The statement requires
  an unaffordable order to be stored as `REJECTED`, so that path answers successfully with a
  `REJECTED` order (FR-019, FR-023). A request that cannot be interpreted — both size and amount, a
  `LIMIT` without a price, an unknown instrument — is refused with nothing written. The split is a
  design decision of this spec: the first is the market's answer about the user's money, the second is
  a bug in the caller, and conflating them would hide client bugs inside the ledger.
- **A `MARKET` request carrying a price is refused rather than ignored** (FR-005). Silently dropping a
  field the caller clearly meant is how a client ends up believing it placed a limit order.
- **The two protections are built last, on purpose.** The per-user lock (P5) and the idempotency key
  (P6) are orthogonal to the business rules and are sequenced after them by the author, who did not
  want concurrency added to a path whose rules were unproven. They are inside this feature's scope,
  not deferred out of it: constitution principle IV requires the serialisation before submission can
  be called done.
- **Serialisation is per user, and the cost is accepted.** One user's orders are processed one at a
  time; the author states this explicitly as the price being paid, and judges it correct. No
  cross-user contention is introduced. The exact mechanism is a plan-time decision with an ADR.
- **The idempotency key is supplied by the caller and is optional** (FR-028, FR-031). The endpoint
  stays usable without it, and a caller that wants exactly-once semantics on retries opts in. How the
  key travels, how the request fingerprint is computed and how long records are kept are plan-time
  decisions.
- **Validation reads the live ledger, not a cached figure.** Available cash and holdings are folded at
  submission time from `FILLED` and `NEW` orders, so an order placed a second ago is already
  accounted for. No stored balance is introduced (constitution principle I).
- **The order is recorded at the price used for validation** — the latest `close` for `MARKET`, the
  requested price for `LIMIT` — so the ledger row explains, on its own, why the order was accepted.
- **Timestamps are server-side.** The caller does not send the order's time; the system records when it
  processed the submission.
- **The functional test the statement asks for is the end-to-end suite.** The statement requires "un
  test funcional sobre la función para enviar una orden"; this project's web tests exercise the
  endpoint over a real PostgreSQL through Testcontainers, validated against the API contract
  ([ADR-0010](../../docs/adr/0010-testcontainers-with-postgresql-18.md),
  [ADR-0024](../../docs/adr/0024-every-response-is-validated-against-the-contract.md)), which is that
  test and more. The README states this so a reviewer looking for the requested test knows where it
  is.
- **Partial fills do not exist.** With no matching engine an order is all-or-nothing: `MARKET` fills
  completely at the close or is rejected, and `LIMIT` is born `NEW` whole. The amount paths derive a
  size before the order exists, which is not a partial fill.
- **No order expiry, no time in force.** A `NEW` order stays `NEW` indefinitely until cancelled; the
  statement defines no expiry and none is invented here.
- **Performance targets are not set by the statement.** The submission reads one user's ledger rows
  plus one market data row and writes two; on the provided dataset user `1` has 11 orders. Any index
  or structural choice made for it is justified at plan time by a measurement
  (constitution principle IX), under the deadline and bounded pool of
  [ADR-0031](../../docs/adr/0031-database-access-has-a-deadline-and-a-bounded-pool.md).
- The ledger anomaly in the provided data (`BMA` at `−10` for user `1`) is left as it is. This feature
  refuses to sell from it (FR-015) and does not repair it
  ([ADR-0012](../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md)).
