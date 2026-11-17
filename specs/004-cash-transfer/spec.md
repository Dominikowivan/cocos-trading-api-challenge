# Feature Specification: Cash Transfer

**Feature Branch**: `feature/cashTransfer` (not created yet; the spec was written on `develop`)

**Created**: 2026-11-17

**Status**: Draft

**Input**: Cash transfer endpoint, as scoped by `docs/journal/08CashTransfer.md` and announced in
`docs/journal/07OrderPlacement.md`. Delivered as prioritized, independently testable user stories,
sequenced as the author sequenced them: the endpoint first, then its protections, then the
extraction of the protections it shares with order submission.

## Source of Requirements

The challenge statement, [`docs/challenge/backend-challenge.md`](../../docs/challenge/backend-challenge.md),
says that incoming and outgoing transfers can be modelled as orders with side `CASH_IN` and
`CASH_OUT`, and that cash (`ARS`) is an instrument of type `MONEDA`. It does not ask for an endpoint
that creates them. This feature adds one, by the author's decision in
`docs/journal/07OrderPlacement.md` ("vamos a poner otro endpoint para transferencias de MONEDAS")
and `docs/journal/08CashTransfer.md`.

Decisions already taken, cited rather than re-decided:

- [ADR-0002](../../docs/adr/0002-orders-as-ledger.md) — `orders` is the ledger, and a transfer is a
  row in it. No balance column is introduced.
- [ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md) — what a `NEW` order reserves is not
  available, so a withdrawal is checked against available cash, never against settled cash.
- [ADR-0005](../../docs/adr/0005-order-status-history.md) — every status transition appends a row to
  the status history in the same transaction.
- [ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md) — no authentication: the
  user id arrives as untrusted input.
- [ADR-0015](../../docs/adr/0015-the-account-snapshot-is-the-folds-contract.md) — available cash comes
  from the one fold over the ledger that the portfolio reports.
- [ADR-0016](../../docs/adr/0016-decimal-scale-and-rounding-policy.md) — the decimal scale and
  rounding every money figure follows.
- [ADR-0024](../../docs/adr/0024-every-response-is-validated-against-the-contract.md) — the endpoint
  is added to `docs/api/openapi.yaml` and `docs/api/trading-api.http` as part of its implementation.
- [ADR-0033](../../docs/adr/0033-the-submission-endpoint-shape.md) — a recorded outcome, `FILLED` or
  `REJECTED`, is a successful response; a request that cannot be acted on is refused with nothing
  written; an unknown user is a missing target.
- [ADR-0036](../../docs/adr/0036-submissions-are-serialised-per-user.md) and
  [ADR-0037](../../docs/adr/0037-idempotent-submission-with-a-caller-key.md) — the per-user
  serialisation and the caller-supplied idempotency key that order submission already has. This
  feature reuses both, and its last story extracts them so that both endpoints share one
  implementation.

### Measurements this spec is built on

Measured on `src/main/resources/db/seed/R__given_dataset.sql` for `specs/003-order-placement/spec.md`
(2026-11-17). The seed has not changed since 2026-11-16 (`git log`, checked 2026-11-17), so the
figures stand:

- User `1` has settled cash **`753,000.00`** and reserves `125,500.00` in two `BUY LIMIT` orders still
  `NEW`, leaving **available cash `627,500.00`**.
- The only `MONEDA` instrument is `ARS` / `PESOS`, id **66**.
- The seed's two transfers are recorded as `MARKET` orders on instrument 66, status `FILLED`, with the
  pesos amount as the **size** and a **price of `1`**: a `CASH_IN` of size `1000000` and a `CASH_OUT`
  of size `100000`, both for user `1`.
- `orders.size` is an `INT` and `orders.price` a `NUMERIC(10, 2)` (`V1__given_schema.sql`).
- `PAMP` (id 47) last closed at **`925.85`**, so a `MARKET BUY` of 400 `PAMP` costs `370,340.00`.

## User Scenarios & Testing *(mandatory)*

Every story is one transfer request for one user: a deposit (`CASH_IN`) or a withdrawal
(`CASH_OUT`) of an amount of pesos. Each ends with an order recorded in the ledger whose effect the
caller can read back through the portfolio.

### User Story 1 - Deposit and withdraw pesos (Priority: P1)

A user sends a transfer naming the direction and an amount of pesos. A deposit is always accepted
and recorded `FILLED`: where the money comes from is not modelled. A withdrawal is accepted and
recorded `FILLED` if the user has that much available cash; otherwise it is recorded `REJECTED` with
the reason, exactly as an unaffordable order is. There is no instrument, no order type and no price
in the request: the movement is in pesos, and pesos are the one `MONEDA` instrument.

**Why this priority**: it is the whole endpoint, and the first way into the API for a user who has no
money yet. Without it, every user except the ones the seed funded can only ever be rejected.

**Independent Test**: for a user with no orders, deposit `100,000` and check the portfolio reports
`100,000.00` available; withdraw `100,001` and check it is `REJECTED`; withdraw `100,000` and check it
is `FILLED` and the cash is back to zero. For user `1`, withdraw `700,000` and check it is `REJECTED`
although their settled cash is `753,000.00`.

**Acceptance Scenarios**:

1. **Given** a user with no orders, **When** they deposit `100,000`, **Then** a `CASH_IN` order is
   recorded `FILLED` on instrument `ARS` for that amount, and their settled and available cash both
   become `100,000.00`.
2. **Given** that user, **When** they then withdraw `100,000`, **Then** a `CASH_OUT` order is recorded
   `FILLED` and their cash returns to `0.00`.
3. **Given** user `1` (available cash `627,500.00`), **When** they withdraw `627,500`, **Then** it is
   `FILLED`; **When** they withdraw `627,501` instead, **Then** it is `REJECTED` for insufficient
   funds — the boundary is available cash, to the peso.
4. **Given** user `1` (settled cash `753,000.00`, available `627,500.00`), **When** they withdraw
   `700,000`, **Then** it is `REJECTED`: the pesos their `NEW` limit orders reserve cannot be
   withdrawn ([ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md)).
5. **Given** a user with no orders, **When** they withdraw any amount, **Then** it is `REJECTED` for
   insufficient funds, and the rejected order is recorded and reported, not an error.
6. **Given** a user with no orders, **When** they deposit `500,000` and then place a `MARKET BUY` of
   100 `PAMP`, **Then** the purchase is `FILLED`: a deposit is immediately spendable.
7. **Given** any transfer, accepted or rejected, **When** it is recorded, **Then** its status
   transition is appended to the status history in the same transaction
   ([ADR-0005](../../docs/adr/0005-order-status-history.md)).
8. **Given** a `REJECTED` withdrawal, **When** the user reads their portfolio, **Then** their cash is
   unchanged: a rejected transfer moves nothing.

---

### User Story 2 - A withdrawal and an order cannot spend the same pesos (Priority: P2)

A withdrawal and an order of the same user, or two withdrawals, arriving at the same moment are
serialised with each other and with every other submission of that user, so they cannot both read
the same available cash and both commit against it. Users never wait on each other.

**Why this priority**: without it the funds check of P1 is advisory — a withdrawal and a purchase can
each see `627,500.00` and together spend more than the user has. The journal names it explicitly
("UserLock también porque afectaría múltiples órdenes en paralelo"), and constitution principle IV
requires it before the feature is done. It follows P1 on purpose, as it did in order placement.

**Independent Test**: for user `1`, fire a withdrawal of `400,000` and a `MARKET BUY` of 400 `PAMP`
(`370,340.00`) at the same instant — `770,340.00` together against `627,500.00` available — and check
exactly one is recorded `FILLED` and the other `REJECTED`.

**Acceptance Scenarios**:

1. **Given** user `1` with available cash `627,500.00`, **When** two withdrawals of `400,000` are sent
   concurrently, **Then** exactly one is `FILLED` and the other is `REJECTED` for insufficient funds.
2. **Given** the same state, **When** a withdrawal of `400,000` and a `MARKET BUY` of 400 `PAMP` are
   sent concurrently, **Then** exactly one of them succeeds and the other is `REJECTED` for
   insufficient funds — transfers and orders share one serialisation per user.
3. **Given** two concurrent withdrawals that both fit in the available cash, **When** both are
   processed, **Then** both are `FILLED` and the final cash reflects both.
4. **Given** concurrent transfers by two different users, **When** both are processed, **Then**
   neither waits on nor is rejected because of the other.
5. **Given** any mix of concurrent transfers and orders for one user, **When** all are processed,
   **Then** available cash is never driven below zero by them — the same invariant a one-at-a-time
   sequence would leave.

---

### User Story 3 - The same transfer sent twice moves money once (Priority: P3)

A client that loses the response and retries must not deposit or withdraw twice. A transfer carries
an optional caller-supplied key, with the same semantics order submission already gives one: the
first request with the key is executed and its outcome stored against it, a repeat with the same body
returns that outcome, and a reuse with a different body is refused as a conflict.

**Why this priority**: a duplicated withdrawal takes the user's money twice and a duplicated deposit
invents money; a transfer is the clearest side effect in the API. It is orthogonal to P1's rules and
is built after them, as the journal sequences it.

**Independent Test**: deposit `100,000` for a user with no orders twice with the same key and check
the portfolio reports `100,000.00`, not `200,000.00`, and both responses name the same order; then
send a withdrawal with that same key and check it is refused as a conflict.

**Acceptance Scenarios**:

1. **Given** a transfer carrying a key, **When** it is sent twice with an identical body, **Then**
   exactly one order exists and both responses report it, with the same identifier and status.
2. **Given** the same key, **When** a second request carries a different direction or amount,
   **Then** it is refused as a conflict and nothing is written.
3. **Given** a withdrawal `REJECTED` for insufficient funds, **When** it is retried with the same key
   after the user has deposited enough, **Then** the stored `REJECTED` outcome is returned: a decision
   already taken is not re-taken, and a new attempt needs a new key.
4. **Given** two requests with the same key sent concurrently, **When** both are processed, **Then**
   the key never yields two orders.
5. **Given** a key that user `1` already used, **When** user `2` sends a transfer with it, **Then** it
   is processed as a new request: keys are scoped per user.
6. **Given** a key the user already used for an order submission, **When** they send a transfer with
   it, **Then** it is refused as a conflict and is never answered with that order: one user has one
   key namespace across both endpoints.
7. **Given** a transfer carrying no key, **When** it is processed, **Then** it is executed normally.

---

### User Story 4 - Serialisation and idempotency live in one place (Priority: P4)

After P2 and P3, the per-user lock and the idempotency claim exist twice — once for orders and once
for transfers. They are extracted into one shared abstraction that both endpoints use, so the next
endpoint with a side effect (cancellation) gets both protections without a third copy. Nothing a
caller observes changes.

**Why this priority**: it is a refactor with no new behaviour, so it is only safe once both endpoints
are proven by their tests. The journal states the intent explicitly: "ABSTRAEMOS la lógica duplicada
de claim de idempotencia y lockeo de usuario … va a ser mucho más limpio que viva en su propia
abstracción". It is also what constitution principle IX requires: a rule exists in exactly one place.

**Independent Test**: the full suite of order submission and of cash transfer passes unchanged, with
no test edited to fit the refactor, and the lock and the claim each have exactly one implementation.

**Acceptance Scenarios**:

1. **Given** the extraction, **When** the order submission suite (every story of
   `specs/003-order-placement/spec.md`, concurrency and idempotency included) runs, **Then** it
   passes without any test being changed.
2. **Given** the extraction, **When** the cash transfer suite (P1–P3) runs, **Then** it passes
   without any test being changed.
3. **Given** the codebase after the extraction, **When** it is reviewed, **Then** acquiring the
   per-user lock and claiming, matching and answering an idempotency key are each written once, and
   neither use case depends on the other to get them.

---

### Edge Cases

- **A rejected withdrawal is an outcome, not a failure.** It is recorded `REJECTED` with a reason and
  answered successfully, exactly as an unaffordable order is (ADR-0033). A malformed transfer is
  refused with nothing written, and the two are distinguishable by the caller.
- **An unknown user.** A transfer for a user id that does not exist is refused as a missing target
  and nothing is written, as in order submission. A deposit is not a way to create a user.
- **A direction other than `CASH_IN` or `CASH_OUT`.** `BUY` and `SELL` are refused as invalid here,
  as `CASH_IN` and `CASH_OUT` are refused by order submission: each endpoint accepts only its own
  sides.
- **Zero, negative or missing amount.** Refused as an invalid request; a deposit of nothing is not a
  deposit.
- **An amount with centavos.** A transfer is whole pesos only (clarified 2026-11-17 by the author):
  `100.50` is refused as an invalid request with nothing written. The seed records a transfer as
  size = amount at price `1`, and `orders.size` is an `INT`, so centavos have no place in that shape.
- **An amount larger than the ledger can hold.** Refused as an invalid request rather than
  overflowing, for both directions.
- **A withdrawal that empties the account.** Withdrawing exactly the available cash is `FILLED` and
  leaves `0.00` available; there is no minimum balance.
- **Pending reservations.** Pesos reserved by `NEW BUY` orders are not available for withdrawal. A
  `NEW SELL` order reserves shares, not pesos, so it does not limit a withdrawal.
- **A negative balance in the data.** If a user's available cash is already below zero (a ledger
  anomaly, [ADR-0012](../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md)), every
  withdrawal is `REJECTED` and a deposit is accepted and simply adds to it. The anomaly is not
  repaired.
- **Fields that belong to an order.** A transfer carries no instrument, order type, price, size or
  status. If the caller sends any of them, the request is refused rather than the field being
  ignored, as order submission refuses a price on a `MARKET` order.
- **A deposit has no upper bound but the ledger's.** Where the money comes from is not modelled, so
  there is no anti-fraud limit, no daily cap and no source account.

## Requirements *(mandatory)*

### Functional Requirements

**The request**

- **FR-001**: The system MUST expose an endpoint that records one cash transfer for one user, naming
  the user, the direction (`CASH_IN` or `CASH_OUT`) and an amount in pesos.
- **FR-002**: The direction MUST be `CASH_IN` or `CASH_OUT` only.
- **FR-003**: The amount MUST be a whole number of pesos, greater than zero and no larger than the
  ledger's size column can hold; otherwise the request MUST be refused as invalid with nothing
  written. An amount with centavos is refused, not rounded.
- **FR-004**: The request MUST NOT carry an instrument, an order type, a price, a size or a status;
  a request that carries one MUST be refused rather than having the field ignored.
- **FR-005**: A transfer for a user that does not exist MUST be refused with nothing written, with
  the same outcome order submission gives an unknown user.

**The recorded transfer**

- **FR-006**: A transfer MUST be recorded as one order in the ledger on the `MONEDA` instrument
  (`ARS`, id 66), with side `CASH_IN` or `CASH_OUT`, in the same shape the provided dataset records
  its transfers — the amount as the size and a price of `1` — so that the portfolio's fold counts
  it without change.
- **FR-007**: A `CASH_IN` MUST be recorded `FILLED` without any validation of funds or origin.
- **FR-008**: A `CASH_OUT` MUST be recorded `FILLED` if the user's available cash is at least the
  amount, and `REJECTED` with the machine-readable reason insufficient funds otherwise.
- **FR-009**: The available cash a withdrawal is checked against MUST come from the same fold over the
  ledger that the portfolio reports — settled cash minus what `NEW` orders reserve — and MUST NOT be
  computed a second way ([ADR-0015](../../docs/adr/0015-the-account-snapshot-is-the-folds-contract.md)).
- **FR-010**: Every transfer, accepted or rejected, MUST append exactly one order and its status
  transition to the status history in the same transaction
  ([ADR-0005](../../docs/adr/0005-order-status-history.md)).
- **FR-011**: The response MUST report the recorded transfer: its identifier, its direction, its
  amount, its status and, when rejected, its reason. A `REJECTED` withdrawal MUST be reported as a
  successful request with that status, distinguishable from a refused invalid one.
- **FR-012**: A transfer's effect MUST be visible in the next portfolio read for that user: a
  `FILLED` deposit raises settled and available cash, a `FILLED` withdrawal lowers them, and a
  `REJECTED` one changes neither.
- **FR-013**: Money figures MUST be exact decimals in pesos with the scale and rounding of
  [ADR-0016](../../docs/adr/0016-decimal-scale-and-rounding-policy.md).

**Protections**

- **FR-014**: Reading available cash and recording the withdrawal that consumes it MUST be
  serialised per user, in the same serialisation order submission uses, so that no combination of
  concurrent transfers and orders for one user can commit against the same available cash. One
  user's transfer MUST NOT wait on another user's.
- **FR-015**: A transfer MAY carry a caller-supplied idempotency key. Two requests with the same key
  and the same body, for the same user, MUST produce exactly one order, and every repeat MUST return
  the outcome of the first — including when that outcome was `REJECTED`.
- **FR-016**: A request reusing a key with a different body MUST be refused as a conflict and MUST
  NOT be answered with the earlier outcome. Keys MUST be scoped per user and shared between order
  submission and cash transfer, so a key already used for an order is a conflict on a transfer and
  the other way round.
- **FR-017**: A transfer carrying no key MUST be processed normally.

**Shared protections**

- **FR-018**: After this feature, acquiring the per-user serialisation and claiming, matching and
  answering an idempotency key MUST each be implemented once and used by both order submission and
  cash transfer, without either use case depending on the other.
- **FR-019**: The extraction in FR-018 MUST NOT change any behaviour of order submission that its
  specification and tests define.

### Key Entities

- **Transfer (input)**: what the caller sends — user, direction (`CASH_IN` / `CASH_OUT`), amount in
  pesos, and an optional idempotency key. Not stored as such; what is stored is the resulting order.
- **Order (written)**: one row in the ledger on the `MONEDA` instrument, with the transfer's
  direction as its side, recorded `FILLED` or `REJECTED`. Transfers and market orders share the
  ledger; nothing else is introduced to hold balances.
- **Order status history (written)**: the transfer's one status transition, with the rejection reason
  when there is one.
- **Account snapshot (derived)**: the available cash a withdrawal is checked against, folded from the
  ledger by the same rule the portfolio reports.
- **Idempotency record (written)**: the association between a user, a caller-supplied key, a
  fingerprint of the request and the order it produced — the same record order submission keeps, one
  namespace per user.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A user with no orders who deposits `100,000` sees `100,000.00` of available cash in
  their next portfolio read, and can immediately buy 100 `PAMP` at market with it.
- **SC-002**: On the provided dataset, user `1` withdrawing `627,500` is `FILLED` and withdrawing
  `627,501` is `REJECTED`, so the funds boundary is exact.
- **SC-003**: On the same dataset, user `1` withdrawing `700,000` is `REJECTED` although their settled
  cash is `753,000.00`: reserved pesos are never withdrawable.
- **SC-004**: Every transfer a request produces is readable back from the ledger with a status, and,
  when rejected, a reason; no request ends with nothing recorded except a refused invalid one.
- **SC-005**: A concurrent withdrawal of `400,000` and `MARKET BUY` of 400 `PAMP` for user `1` end
  with exactly one accepted and one `REJECTED`, in every run of the test.
- **SC-006**: Sending the same deposit twice with one key leaves the user's cash raised once, and both
  responses name the same order.
- **SC-007**: The available cash a withdrawal is validated against equals, to the cent, the available
  cash the portfolio reports for the same user at the same moment.
- **SC-008**: After the extraction, the order submission suite and the cash transfer suite both pass
  with no test modified, and the lock and the idempotency claim each exist in exactly one place.
- **SC-009**: Every rejection and refusal path above is covered by a test, not only the happy paths
  (constitution principle V).

## Assumptions

- **The instrument is implicit** (confirmed by the author, 2026-11-17). The journal's request is user, side and amount. The provided data
  has exactly one `MONEDA` instrument, `ARS`, so a transfer is always recorded on it and the caller
  does not name it. Supporting a second currency is out of scope; it would add the instrument to the
  request.
- **Whole pesos, in the seed's shape** (clarified 2026-11-17). A transfer's amount is recorded as the
  order's size at price `1`, exactly as the provided dataset's transfers are, so its ceiling is the
  largest `INT` (`2,147,483,647` pesos). Centavos would need a different row shape (size `1` at price
  = amount, or size in centavos at price `0.01`), each with a lower ceiling and a departure from the
  seed; nothing in the statement asks for them.
- **Where the money comes from, or goes, is not modelled.** No bank accounts, no transfers between
  users and no movements between accounts (`docs/journal/08CashTransfer.md`). A deposit is accepted
  on the caller's word, which is acceptable only because there is no authentication either
  ([ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md)).
- **A transfer settles immediately.** There is no `NEW` transfer and no pending state: a deposit or a
  withdrawal is `FILLED` or `REJECTED` when it is recorded, as the seed's transfers are. Cancelling a
  transfer is therefore never possible.
- **A rejected withdrawal is recorded, not refused.** The journal says the order "queda rejected",
  which is how an unaffordable order is already treated (ADR-0033); the reason is the existing
  insufficient-funds reason, so constitution principle IV's list of rejection reasons does not grow.
- **The journal's sequence is the delivery order.** The endpoint first (P1), then the lock and the
  idempotency claim following the pattern of order submission (P2, P3), then the extraction of what
  both endpoints duplicate (P4). The protections are inside this feature's scope, not deferred.
- **One key namespace per user across both endpoints** (confirmed by the author, 2026-11-17). A caller who reuses a key on the other
  endpoint gets a conflict, never the other endpoint's outcome. Keeping two namespaces would let the
  same key mean two different operations for one user, which is the confusion idempotency keys exist
  to prevent.
- **The extraction's shape is a plan-time decision.** Constitution principle VIII forbids one use case
  depending on another and anything at the root of `service`, so the shared protection cannot live in
  either use case's package; where it lives, and whether that needs a constitution amendment or an
  ADR, is decided and recorded at plan time.
- **The constitution lists the API's endpoints as portfolio, search, submission and cancellation.**
  It also says a new endpoint requires a spec, which this is. The list is amended through
  `/speckit-constitution` when the plan passes its Constitution Check, and the endpoint's shape — its
  path and its status codes — gets an ADR if it had alternatives.
- **The functional test for this endpoint is its end-to-end suite**, over real PostgreSQL through
  Testcontainers and validated against the API contract, as for order submission
  ([ADR-0010](../../docs/adr/0010-testcontainers-with-postgresql-18.md),
  [ADR-0024](../../docs/adr/0024-every-response-is-validated-against-the-contract.md)).
- **Timestamps are server-side.** The caller does not send when the transfer happened.
- **Performance targets are not set.** A transfer reads one user's ledger and writes two rows, like a
  submission; any structural choice made for it cites a measurement (constitution principle IX).
