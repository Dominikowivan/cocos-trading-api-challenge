# Feature Specification: Portfolio Endpoint

**Feature Branch**: `feature/portfolio`

**Created**: 2026-11-15

**Status**: Draft

**Input**: Portfolio endpoint, as described in `docs/challenge/backend-challenge.md` (the source of requirements). Delivered as prioritized, independently testable user stories, one per portfolio attribute.

## Source of Requirements

The behaviour this feature must deliver is defined in the challenge statement,
[`docs/challenge/backend-challenge.md`](../../docs/challenge/backend-challenge.md) — see its
*Portfolio* bullet in **Resumen**, the *Consideraciones funcionales* bullets on `FILLED` orders,
`size`, cash as a `MONEDA` instrument and `close` prices, and the **Base de datos** section. That
document is kept verbatim and is not restated here; this spec only adds what it leaves open.

Decisions already taken, cited rather than re-decided (0012 and 0013 came out of this spec's
clarifications):

- [ADR-0002](../../docs/adr/0002-orders-as-ledger.md) — `orders` is the ledger; the portfolio is a
  projection computed on read, owned by one fold that order validation also consumes.
- [ADR-0003](../../docs/adr/0003-weighted-average-cost.md) — cost basis is weighted average cost,
  not FIFO.
- [ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md) — `NEW` limit orders reserve what
  they commit; available cash is the balance minus those reservations.
- [ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md) — no authentication, so
  the user is identified by an untrusted id supplied with the request.
- [ADR-0012](../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md) — a ledger anomaly
  (a negative position) is served as-is and logged as an error, never hidden and never fatal.
- [ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md) — an unknown money figure
  is absent rather than `0`, and it suppresses the account total; logged as a warning.

## User Scenarios & Testing *(mandatory)*

Every story below is read-only and observed through one portfolio request for a given user. Each
adds one attribute to the answer, so each can be built, tested and demonstrated on its own: a
portfolio that reports only quantities is already useful, and every later story strictly adds a
field. The acceptance numbers are the ones measured on the provided dataset for user `1`.

### User Story 1 - Quantity held per instrument (Priority: P1)

A user asks for their portfolio and sees, for each instrument they hold, how many shares they
hold. Quantity is the signed sum of the `size` of that instrument's `FILLED` orders: `BUY` adds,
`SELL` subtracts. Orders in any other status do not contribute. `CASH_IN` and `CASH_OUT` move
pesos (instrument `ARS`, type `MONEDA`) and are not a position, so pesos never appear in the
list of instruments.

**Why this priority**: it is the foundation every other position attribute multiplies or walks
over. Without a correct quantity, market value and return cannot be right, and nothing else in
the feature can be verified.

**Independent Test**: request the portfolio for user `1` and compare the instrument/quantity
pairs against the dataset; no prices and no cash are needed to judge the result.

**Acceptance Scenarios**:

1. **Given** the provided dataset, **When** user `1` requests their portfolio, **Then** the
   positions include PAMP with quantity `40`, METR with quantity `500` and BMA with quantity
   `-10`.
2. **Given** a user whose orders for an instrument include `NEW`, `CANCELLED` and `REJECTED`
   rows, **When** that user requests their portfolio, **Then** those rows change no quantity.
3. **Given** a user with `CASH_IN` and `CASH_OUT` orders, **When** that user requests their
   portfolio, **Then** no position for the pesos instrument (`ARS`, type `MONEDA`) appears in the
   list.
4. **Given** a user with no orders at all, **When** that user requests their portfolio, **Then**
   the position list is empty.

---

### User Story 2 - Market value per position (Priority: P2)

For each position the user sees its total monetary value in pesos: quantity times the latest
`close` of that instrument. "Latest" is resolved per instrument — the most recent market data row
for that instrument — not by picking one global date and applying it to everything.

**Why this priority**: it is the first figure that answers "what is this worth", and it is the
input to the total account value. It depends only on P1.

**Independent Test**: request the portfolio for user `1` and check each position's value equals
its quantity times that instrument's latest close.

**Acceptance Scenarios**:

1. **Given** the provided dataset, **When** user `1` requests their portfolio, **Then** PAMP
   reports a market value of `37,034.00`, METR `114,750.00` and BMA `-15,028.00`.
2. **Given** two instruments whose most recent market data rows carry different dates, **When**
   the portfolio is computed, **Then** each position uses its own instrument's latest row.
3. **Given** a held instrument with no market data row at all, **When** the portfolio is computed,
   **Then** the position is still listed with its quantity, its market value and total return are
   reported as absent (`null`, never `0`), the total account value is also absent, and a
   warning-level diagnostic naming the user and the instrument is recorded.

---

### User Story 3 - Total return per position (Priority: P3)

For each position the user sees its total return as a percentage, telling them whether they are
up or down on that holding overall. The cost basis is weighted average cost
([ADR-0003](../../docs/adr/0003-weighted-average-cost.md)): the instrument's `FILLED` orders are
walked in chronological order accumulating total cost and total quantity, and the basis resets
when the position returns to zero. Return is `(market value − cost) / cost`.

The walk must also handle an order that takes the position **through** zero rather than to it — a
`SELL` larger than the holding, or a `BUY` larger than a short. The rule: the crossing resets the
cost basis, and whatever quantity ends up on the far side takes the price of the crossing order as
its cost. The part of the order that closed the old position is spent closing it; only the
remainder opens the new one.

This is the *total* return on the user's own cost, not the daily return of the instrument
(`close` against `previousClose`), which this feature does not report.

**Why this priority**: it is the most derived of the three position attributes and the only one
whose definition required a decision, so it lands after the two it is built on.

**Independent Test**: request the portfolio for user `1` and compare each position's percentage
against the weighted-average cost computed by hand from that instrument's `FILLED` orders.

**Acceptance Scenarios**:

1. **Given** the provided dataset, **When** user `1` requests their portfolio, **Then** PAMP
   reports a total return of `-0.45%` and METR `-8.20%`.
2. **Given** an instrument bought at two different prices with a partial sale in between, **When**
   its return is computed, **Then** the remaining cost is the weighted average of the purchases,
   not the cost of the oldest lot.
3. **Given** an instrument whose position was closed to zero and then reopened, **When** its
   return is computed, **Then** only the orders after the reset contribute to the cost basis.
4. **Given** user `1` on the provided dataset, whose BMA holding already crosses zero
   (`BUY FILLED` 20 at `1,540` then `SELL FILLED` 30 at `1,530`, so `0 → +20 → -10`), **When** a
   further `BUY` of 30 BMA fills at the latest close of `1,502.80`, **Then** the position becomes
   `+20` with a cost basis of `1,502.80` per share — the 10 shares that closed the short are not
   carried into the new basis, and nothing from before the crossing is.
5. **Given** a position of `+20` bought at `100` and a `SELL` of `50` at `120`, **When** the walk
   reaches that order, **Then** the resulting `-30` takes `120` as its basis and the `100` is
   discarded.
6. **Given** a position whose quantity is negative, **When** its return is computed, **Then** the
   return is reported as absent (`null`), because a negative cost basis makes the ratio
   meaningless — never as `0` and never as a division by zero.
7. **Given** the provided dataset, **When** user `1` requests their portfolio, **Then** BMA
   reports no total return, consistent with its negative quantity.

---

### User Story 4 - Available cash (Priority: P4)

The user sees the pesos they have available to trade. Settled cash is the signed fold of the
pesos instrument over `FILLED` orders (`CASH_IN` and sale proceeds add, `CASH_OUT` and purchases
subtract). Available cash is that settled cash minus the funds reserved by `BUY LIMIT` orders
still in `NEW` ([ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md)).

**Why this priority**: it is a distinct number from the positions and the one the user needs
before placing an order, so it is reported as buying power rather than as an accounting balance.
It is independent of P1–P3 and could be built in any order relative to them; it is placed here
because the statement lists it after the holdings.

**Independent Test**: request the portfolio for user `1` and check available cash equals settled
cash minus reserved funds; verified with and without an open `BUY LIMIT` order.

**Acceptance Scenarios**:

1. **Given** the provided dataset, **When** user `1` requests their portfolio, **Then** available
   cash is `627,500.00`, from settled cash `753,000.00` minus `125,500.00` reserved.
2. **Given** a user with settled cash and no `NEW` orders, **When** that user requests their
   portfolio, **Then** available cash equals settled cash.
3. **Given** a user whose only pending order is a `SELL LIMIT` in `NEW`, **When** that user
   requests their portfolio, **Then** available cash is unchanged, because a `SELL` reserves
   shares and not pesos.
4. **Given** a user with no orders at all, **When** that user requests their portfolio, **Then**
   available cash is `0`.

---

### User Story 5 - Total account value (Priority: P5)

The user sees one figure for the whole account: the full settled cash — reservations included,
because reserved pesos are still the user's money — plus the market value of every position.

**Why this priority**: it is a sum over everything above, so it is only meaningful once the parts
are correct, and it is the last thing that can be verified.

**Independent Test**: request the portfolio for user `1` and check the total equals settled cash
plus the sum of the reported position values.

**Acceptance Scenarios**:

1. **Given** the provided dataset, **When** user `1` requests their portfolio, **Then** the total
   account value is `889,756.00`.
2. **Given** a user with an open `BUY LIMIT` order, **When** that user requests their portfolio,
   **Then** the total account value uses settled cash, so it is strictly greater than available
   cash plus positions whenever something is reserved.
3. **Given** a user with no orders at all, **When** that user requests their portfolio, **Then**
   the total account value is `0`.

---

### Edge Cases

- **Negative position (a broken business rule, reported loudly).** The dataset leaves user `1`
  holding BMA `-10`: more sold than bought. The API does not allow a user to go short, so a
  negative quantity in the ledger is a rule that has already been violated — the damage is done
  and cannot be undone by hiding it. The portfolio therefore reports it **as-is**: the position is
  listed with quantity `-10` and market value `-15,028.00`, so the user sees the truth about their
  own account and the request does not fail. Its total return is absent, since a negative cost
  basis makes the ratio meaningless. At the same time the condition **makes noise**: an
  error-level diagnostic naming the user id, the instrument and the quantity, so the technical
  team learns that something went wrong rather than finding out from a customer. The total
  account value includes the negative contribution, which is why it is `889,756.00`.
- **Held instrument with no market data.** Unlike the case above, this is not a broken rule: an
  instrument can legitimately be held before it has ever traded. The position is listed with its
  quantity and `null` market value and return, and the total account value is `null` too — any
  other number would be a lie about money. A warning-level diagnostic is recorded so it does not
  pass in silence.
- **`ARS` is unpriced, and anyone who has ever received a transfer has rows in it.** Measured on
  `V1000__given_dataset.sql`: exactly
  three of the 66 instruments have no market data — `PGR`, `IRCP` and **`ARS`**, the `MONEDA`
  instrument, in which user `1` has rows (a `CASH_IN` and a `CASH_OUT`); his settled cash is
  `753,000.00` (FR-010). `ARS` must be excluded as cash (FR-005) *before*
  the unpriced-position rule is applied (FR-021), or the most ordinary account in the dataset
  reports a `null` total account value instead of `889,756.00`. The ordering of those two rules is
  therefore load-bearing, not incidental — see FR-023.
  With `ARS` set aside, no user in the seed holds `PGR` or `IRCP`, and the API cannot put them
  there: a `MARKET` order has no `close` to execute against and a `LIMIT` order never executes
  ([ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md)). The unpriced-position
  path is reachable only through data written outside the API, or a feed that stops covering an
  instrument already held, so it is covered by a constructed fixture rather than by the seed.
- **Unknown user id.** The id is untrusted input
  ([ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md)). A request for a user
  id that does not exist is rejected as not found — the default for any lookup by an id that is
  not there — rather than answered with an empty portfolio, so a wrong id is never mistaken for
  a real account that has never traded.
- **User with no orders.** An existing user who has never traded gets an empty position list,
  zero available cash and zero total account value — not an error, and distinguishable from the
  unknown-id case above because that one is rejected.
- **Instrument with multiple market data rows.** The dataset carries two days per instrument; only
  the latest row of that instrument is used, and `previousClose` is never read by this feature.
- **Position that nets to zero.** An instrument fully bought and fully sold holds quantity `0`.
  It is not a holding, so it is not listed — and unlike the negative case it is not an anomaly, so
  it produces no diagnostic.
- **Malformed user id.** A user id that is not a valid identifier is rejected as a bad request
  rather than treated as unknown.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a read-only portfolio query scoped by user id, returning the
  total account value, the pesos available to trade, and the list of instruments held.
- **FR-002**: The system MUST compute every reported figure from the `orders` ledger and
  `marketdata` at request time, with no stored position or balance
  ([ADR-0002](../../docs/adr/0002-orders-as-ledger.md)).
- **FR-003**: Quantity per instrument MUST be the signed sum of `size` over that user's `FILLED`
  orders for that instrument, with `BUY` positive and `SELL` negative.
- **FR-004**: Orders in `NEW`, `CANCELLED` or `REJECTED` MUST NOT contribute to any quantity,
  market value or return.
- **FR-005**: The pesos instrument (`ARS`, type `MONEDA`) MUST NOT appear as a position; its
  movements MUST be reported only as cash.
- **FR-006**: Market value per position MUST be quantity times the latest `close` of that
  instrument, resolved per instrument rather than at one shared date.
- **FR-007**: Total return per position MUST be `(market value − cost) / cost`, with cost built by
  walking that instrument's `FILLED` orders chronologically under weighted average cost and
  resetting whenever the position returns to zero
  ([ADR-0003](../../docs/adr/0003-weighted-average-cost.md)).
- **FR-008**: When an order takes the position through zero rather than to zero, the cost basis
  MUST reset at the crossing, and the quantity remaining on the far side MUST take the price of
  that crossing order as its cost. Nothing from before the crossing may carry over.
- **FR-009**: The system MUST NOT report the daily return (`close` against `previousClose`) as the
  total return.
- **FR-010**: Settled cash MUST be the pesos effect of **every** `FILLED` order of the user, not
  only the rows for the pesos instrument: `CASH_IN` and `SELL` proceeds add, `CASH_OUT` and `BUY`
  outlays subtract. Folding only the pesos instrument's `size` gives a different and wrong number
  — `900,000.00` instead of `753,000.00` for user `1` on the provided dataset.
- **FR-011**: Available cash MUST be settled cash minus the pesos reserved by that user's `BUY
  LIMIT` orders in `NEW`; `SELL LIMIT` orders in `NEW` MUST NOT reduce it
  ([ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md)).
- **FR-012**: Total account value MUST be settled cash — including reserved pesos — plus the sum
  of the market value of every position.
- **FR-013**: The query MUST return only data belonging to the requested user id; no other user's
  orders may influence any figure.
- **FR-014**: A user with no orders MUST receive an empty position list with zero available cash
  and zero total account value, not an error.
- **FR-015**: Money and quantity figures MUST be reported as exact decimal amounts in pesos, with
  a stated scale and rounding for the return percentage.
- **FR-016**: The fold that produces quantities, settled cash and available cash MUST be the same
  one order validation consumes, so the two cannot disagree
  ([ADR-0002](../../docs/adr/0002-orders-as-ledger.md)).
- **FR-017** ([ADR-0012](../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md)): A
  position whose quantity is negative MUST be reported as-is — listed, with its
  negative quantity and negative market value included in the total account value — and MUST NOT
  cause the request to fail.
- **FR-018**: A position whose quantity is negative MUST report no total return, and MUST NOT
  report `0`, a sign-flipped percentage, or divide by zero.
- **FR-019**: A negative quantity MUST produce an error-level diagnostic carrying the user id, the
  instrument and the quantity, because it means a business rule that forbids short positions has
  already been violated and the technical team MUST be able to find out without being told by a
  customer.
- **FR-020** ([ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md)): A position
  whose instrument has no market data MUST still be listed with its
  quantity, and MUST report its market value and total return as absent rather than as `0`, since
  `0` asserts a real value that is not known.
- **FR-021**: When any listed position has no market data, the total account value MUST be
  reported as absent rather than as a partial sum, because a partial sum misstates the value of
  the account.
- **FR-022**: A position whose instrument has no market data MUST produce a warning-level
  diagnostic naming the user and the instrument, so the condition is never passed over silently.
  It MUST NOT be an error, because an instrument that has not yet traded is a legitimate state
  and not a broken rule.
- **FR-023**: The exclusion of the pesos instrument as cash (FR-005) MUST be applied before the
  unpriced-position rules (FR-020, FR-021). `ARS` has no market data in the provided dataset and
  is held by every user who has received a transfer, so the reverse order yields a `null` total
  account value for user `1`, whose correct total is `889,756.00`.
- **FR-024**: A request for a user id that does not exist MUST be rejected as not found, rather
  than answered with an empty portfolio.
- **FR-025**: Positions with a quantity of zero MUST NOT be listed and MUST NOT produce a
  diagnostic.

### Key Entities

- **User**: the account the portfolio is computed for, identified by the id supplied with the
  request.
- **Order (ledger entry)**: one movement — `BUY`, `SELL`, `CASH_IN` or `CASH_OUT` — for a user and
  an instrument, with a size, a price, a type, a status and a timestamp. The timestamp orders the
  walk that builds the cost basis; the status decides whether the row counts as settled, as a
  reservation, or not at all.
- **Instrument**: a tradable asset with a ticker, a name and a type. The type `MONEDA` marks the
  pesos instrument, which is cash rather than a position.
- **Market data**: a daily price row per instrument. Only the latest row's `close` is used.
- **Position (derived)**: an instrument the user holds, carrying quantity, market value and total
  return. Not stored.
- **Portfolio (derived)**: total account value, available cash and the list of positions. Not
  stored.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: For user `1` on the provided dataset, the portfolio reports PAMP `40`, METR `500`
  and BMA `-10` shares — every quantity matching the ledger exactly, with no rounding drift.
- **SC-002**: For the same user, position values are PAMP `37,034.00`, METR `114,750.00` and BMA
  `-15,028.00` pesos.
- **SC-003**: For the same user, PAMP reports `-0.45%` and METR `-8.20%` total return, and BMA
  reports none.
- **SC-004**: For the same user, settled cash is `753,000.00`, reserved pesos `125,500.00`,
  available cash `627,500.00` and total account value `889,756.00`.
- **SC-005**: A user who has never traded receives an empty portfolio: no positions, zero
  available cash, zero total account value.
- **SC-006**: Two consecutive portfolio requests with no intervening order return identical
  figures, and the available cash reported here equals the available cash order validation uses
  for the same user at the same moment.
- **SC-007**: The reported total account value equals settled cash plus the sum of the position
  values shown in the same response, for every user in the dataset.
- **SC-008**: A user asking for their portfolio gets the complete answer in a single request —
  total value, buying power and holdings — with no follow-up call needed to compute any of the
  three.
- **SC-009**: Every short position served is traceable afterwards from the operational record: for
  user `1` on the provided dataset, exactly one error-level entry is produced, naming BMA and
  `-10`, and the request still succeeds.
- **SC-010**: No response ever reports `0` for a money figure that is merely unknown; unknown
  values are absent, so a reader can always tell "nothing" from "not known".
- **SC-011**: A request for a user id that does not exist is rejected, and is distinguishable from
  the response given to a real user who has never traded.
- **SC-012**: User `1`'s total account value is `889,756.00` and not `null`, proving the pesos
  instrument was treated as cash rather than as an unpriced position.
- **SC-013**: A position taken through zero reports a cost basis equal to the price of the order
  that crossed: 30 BMA bought at `1,502.80` against the existing `-10` yields `+20` at `1,502.80`,
  with a total return of `0%` at that same close.

## Assumptions

- The portfolio is read-only: it reports state and never writes to the ledger.
- The user is identified by an id supplied with the request, since there is no authentication
  ([ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md)). That id is untrusted,
  so every figure is scoped by it.
- All prices in the dataset are already in pesos, as the statement specifies; no currency
  conversion takes place.
- Reporting total return as a percentage, consistent with the statement's `rendimiento total (%)`.
- Positions with a non-zero quantity are the ones worth reporting; an instrument the user has
  traded but no longer holds is not a position.
- **"Make noise" means a diagnostic, not a failed request.** For the negative position the request
  succeeds and returns the position; the loud failure signal is the error-level record (FR-019),
  not a rejection. Confirmed by the author on 2026-11-15.
- **Absent, not zero, is the general rule for unknown money**
  ([ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md)). The author stated it for the
  missing-market-data case; it is applied to the negative position's return as well, because the
  same reasoning holds — `0` claims a real value, and no value is known.
- **The total account value is absent whenever any held position lacks market data.** This is
  deliberate: one unpriced holding makes the whole total unknowable, and a partial sum would be a
  wrong number about money rather than a missing one. It means a single unpriced instrument
  suppresses the account total, which is accepted as the honest trade-off.
- Diagnostic levels (error for the short position, warning for missing market data) are a
  requirement of this feature because they are the only way the two anomalies are ever noticed;
  the destination and format of those records is a plan-time concern.
- The provided dataset is used as the fixture for the acceptance numbers above; those numbers were
  measured on `src/main/resources/db/seed/V1000__given_dataset.sql`, not estimated — including
  the finding that the three unpriced instruments are `PGR`, `IRCP` and `ARS`, that `ARS` is held
  by user `1` but excluded as cash, and that therefore no *position* in the seed lacks market
  data, so the FR-020..022 path needs a constructed fixture.
- Both anomaly decisions had real alternatives, so under constitution principle VII each is
  recorded: reporting a short position rather than rejecting it in
  [ADR-0012](../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md), and suppressing the
  account total rather than reporting a partial sum in
  [ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md). The error-versus-warning
  distinction between the two cases is the substance of that pair.
- Ordering of the positions in the response is not specified by the statement and is treated as a
  presentation choice, settled at plan time.
- Performance targets are not set by the statement. The cost of this read is understood to grow
  with the user's order history ([ADR-0002](../../docs/adr/0002-orders-as-ledger.md)); a snapshot
  is explicitly deferred there, so it is out of scope here.
- Instrument search, order submission and order cancellation are separate features and out of
  scope for this spec, which only consumes the same fold they do.
