# Feature Specification: Order Cancellation

**Feature Branch**: `feature/cancelOrder`

**Created**: 2026-11-17

**Status**: Draft

**Input**: Order cancellation endpoint, as scoped by `docs/journal/09CancelOrder.md`. Delivered as
prioritized, independently testable user stories: the cancellation first, then its serialisation
with the user's other writes, then its idempotency.

## Source of Requirements

The challenge statement, [`docs/challenge/backend-challenge.md`](../../docs/challenge/backend-challenge.md),
defines the `CANCELLED` status ("cuando la orden es cancelada por el usuario") and says that only
orders in `NEW` can be cancelled. It asks for no endpoint that cancels. This feature adds one, as
[ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md) and
[ADR-0006](../../docs/adr/0006-dedicated-cancel-endpoint.md) committed the project to: a `NEW` order
reserves funds or shares, and releasing them must be reachable through the API.

Decisions already taken, cited rather than re-decided:

- [ADR-0002](../../docs/adr/0002-orders-as-ledger.md) — `orders` is the ledger. Cancelling changes an
  order's status; it never deletes the order and never writes a compensating row.
- [ADR-0004](../../docs/adr/0004-reserve-funds-on-new-orders.md) — what a `NEW` order reserves is not
  available; once it is cancelled it reserves nothing.
- [ADR-0005](../../docs/adr/0005-order-status-history.md) — the transition to `CANCELLED` appends a row
  to the status history in the same transaction.
- [ADR-0006](../../docs/adr/0006-dedicated-cancel-endpoint.md) — cancellation is its own endpoint and
  only `NEW` orders can be cancelled; anything else is refused without changing state.
- [ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md) — no authentication: the user
  id is untrusted input, and every lookup is scoped by it.
- [ADR-0015](../../docs/adr/0015-the-account-snapshot-is-the-folds-contract.md) — the cash and holdings
  a cancellation releases are visible through the one fold the portfolio reports.
- [ADR-0024](../../docs/adr/0024-every-response-is-validated-against-the-contract.md) — the endpoint is
  added to `docs/api/openapi.yaml` and `docs/api/trading-api.http` as part of its implementation.
- [ADR-0033](../../docs/adr/0033-the-submission-endpoint-shape.md) and
  [ADR-0038](../../docs/adr/0038-the-cash-transfer-endpoint-shape.md) — the user is part of the path of
  every write endpoint, and an unknown user is a missing target.
- [ADR-0036](../../docs/adr/0036-submissions-are-serialised-per-user.md),
  [ADR-0037](../../docs/adr/0037-idempotent-submission-with-a-caller-key.md) and
  [ADR-0040](../../docs/adr/0040-per-user-write-protection-lives-once.md) — the user's existence check,
  the per-user lock and the idempotency claim already live once, for every write endpoint. This
  feature uses them; it does not build a third copy.

### Measurements this spec is built on

Measured on `src/main/resources/db/seed/R__given_dataset.sql` on 2026-11-17. The seed truncates with
`RESTART IDENTITY`, so its orders are numbered `1` to `11` in the order they are inserted; the seed has
not changed since 2026-11-16 (`git log`).

- The seed has four users, `1` to `4`. All eleven orders belong to user `1`.
- User `1` has two orders in `NEW`, both `BUY LIMIT`:
  - order **`5`**: 50 of instrument 45 at `710`, reserving **`35,500.00`**;
  - order **`7`**: 60 of instrument 31 at `1500`, reserving **`90,000.00`**.
  Together they reserve `125,500.00`: settled cash is **`753,000.00`** and available cash
  **`627,500.00`** (the same figures `specs/004-cash-transfer/spec.md` measured).
- The other statuses present: order `3` is already `CANCELLED`, order `6` is `REJECTED`, and orders
  `1`, `2`, `4`, `8`–`11` are `FILLED` — among them `1` (`CASH_IN`) and `8` (`CASH_OUT`) are transfers.
- No seeded order is a `NEW SELL`; one has to be submitted to exercise the release of shares.

## User Scenarios & Testing *(mandatory)*

Every story is one cancellation request: a user names one of their orders, and the order either moves
from `NEW` to `CANCELLED`, releasing what it reserved, or the request is refused with nothing written.

### User Story 1 - Cancel a pending order and get its reservation back (Priority: P1)

A user names one of their orders in `NEW`. The order becomes `CANCELLED`, the transition is recorded in
its history, and what the order reserved — cash for a `BUY`, shares for a `SELL` — is available again
in the user's next portfolio read. An order that is not the user's, does not exist, or is not in `NEW`
is refused and nothing changes.

**Why this priority**: it is the whole endpoint, and the only way a user gets back the cash or shares a
`LIMIT` order holds. Without a matching engine a `LIMIT` order never fills
([ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md)), so without cancellation
whatever it reserved is reserved forever.

**Independent Test**: for user `1`, cancel order `5` and check the portfolio's available cash goes from
`627,500.00` to `663,000.00` while settled cash stays `753,000.00`; cancel it again and check it is
refused; cancel order `2` (`FILLED`) and check it is refused and unchanged.

**Acceptance Scenarios**:

1. **Given** user `1` and their `NEW` order `5`, **When** they cancel it, **Then** the order is
   `CANCELLED`, the response reports it with that status, and one `CANCELLED` row with the reason
   `USER_REQUESTED` is appended to its history in the same transaction
   ([ADR-0005](../../docs/adr/0005-order-status-history.md)).
2. **Given** that cancellation, **When** user `1` reads their portfolio, **Then** available cash is
   `663,000.00` (`627,500.00` + `35,500.00`) and settled cash is still `753,000.00`: a cancellation
   releases a reservation and moves no money.
3. **Given** user `1`, **When** they cancel both orders `5` and `7`, **Then** available cash equals
   settled cash, `753,000.00`: nothing is reserved any more.
4. **Given** a user who submits a `SELL LIMIT` for every share they hold of an instrument, which stays
   `NEW`, **When** they cancel it,
   **Then** the shares it reserved can be sold again: a `MARKET SELL` of that quantity is `FILLED`
   where, before the cancellation, it was `REJECTED` for insufficient holdings.
5. **Given** user `1`, **When** they cancel order `3` (already `CANCELLED`), `6` (`REJECTED`), `2`
   (`FILLED`) or `1` (a `CASH_IN` transfer), **Then** the request is refused as an order that cannot be
   cancelled, nothing is written, and the order's status and history are unchanged.
6. **Given** user `1` has just cancelled order `5`, **When** they cancel it again without an idempotency
   key, **Then** it is refused as an order that cannot be cancelled, and the reservation is not
   released twice: available cash is still `663,000.00`.
7. **Given** user `2` (who has no orders), **When** they ask to cancel order `5`, which belongs to user
   `1`, **Then** the request is refused exactly as for an order that does not exist, and order `5`
   stays `NEW`.
8. **Given** an order id that does not exist, **When** any existing user asks to cancel it, **Then** it
   is refused as a missing target with nothing written.
9. **Given** a user id that does not exist, **When** a cancellation is sent for it, **Then** it is
   refused as a missing target with nothing written, with the same outcome the other write endpoints
   give an unknown user.
10. **Given** an order the user submitted through this API as a `LIMIT` that stayed `NEW`, **When** they
    cancel it, **Then** it behaves exactly as a seeded `NEW` order: cancellation does not depend on how
    or when the order was created.

---

### User Story 2 - A cancellation and the user's other writes are serialised (Priority: P2)

A cancellation changes what the user has available, so it is serialised with every other write of the
same user — submissions, cash transfers and other cancellations — using the protection those
endpoints already share. Two cancellations of the same order cannot both succeed, and a submission
running next to a cancellation sees either the reservation or its release, never half of each. Users
never wait on each other.

**Why this priority**: the journal asks for it ("también se lockea por usuario"), and constitution
principle IV names cancellation explicitly: the read of available cash and the write that changes it
must be serialised, and a reservation must never be released twice. It follows P1 because P1's
rules are what it protects.

**Independent Test**: for user `1`, fire two cancellations of order `5` at the same instant and check
exactly one reports `CANCELLED`, the other is refused as not cancellable, and order `5` has exactly one
`CANCELLED` row in its history.

**Acceptance Scenarios**:

1. **Given** user `1`'s `NEW` order `5`, **When** two cancellations of it are sent concurrently,
   **Then** exactly one succeeds, the other is refused as an order that cannot be cancelled, and the
   history holds exactly one `CANCELLED` transition for it.
2. **Given** user `1` with available cash `627,500.00`, **When** a cancellation of order `5` and a
   withdrawal of `663,000` are sent concurrently, **Then** the outcome is the one some serial order of
   the two would give: either the cancellation runs first and the withdrawal is `FILLED`, or the
   withdrawal runs first and is `REJECTED` and the cancellation then succeeds. Available cash never
   ends below zero and the reservation is never counted as released while it is still held.
3. **Given** a cancellation of one of user `1`'s orders and a write by user `2` sent concurrently,
   **When** both are processed, **Then** neither waits on nor is affected by the other.
4. **Given** a cancellation that cannot get the user's serialisation before the request deadline,
   **When** it times out, **Then** it is answered as the other write endpoints answer a timeout
   ([ADR-0031](../../docs/adr/0031-database-access-has-a-deadline-and-a-bounded-pool.md)) and nothing
   is written.

---

### User Story 3 - The same cancellation sent twice is answered once (Priority: P3)

A client that loses the response and retries must get the same answer, not a refusal that makes it
believe the cancellation failed. A cancellation carries an optional caller-supplied key, with the
semantics the other write endpoints already give it: the first request with the key is executed and
its outcome stored, a repeat with the same request returns that outcome, and a reuse for a different
request is refused as a conflict.

**Why this priority**: without a key a retry of a successful cancellation is refused as "not
cancellable" (P1, scenario 6), which is correct for the ledger but misleading to a client that never
saw the first answer. The journal asks for it ("también es idempotente"), and the protection already
exists for the other write endpoints.

**Independent Test**: for user `1`, cancel order `5` twice with the same key and check both responses
report order `5` as `CANCELLED`, the history holds one transition and available cash is `663,000.00`;
then cancel order `7` with that same key and check it is refused as a conflict and order `7` stays
`NEW`.

**Acceptance Scenarios**:

1. **Given** a cancellation carrying a key, **When** it is sent twice for the same order, **Then** both
   responses report the same order as `CANCELLED`, and exactly one transition is recorded.
2. **Given** that key, **When** the user sends it with a cancellation of a different order, **Then** it
   is refused as a conflict and nothing is written.
3. **Given** a key the user already used for an order submission or a cash transfer, **When** they send
   a cancellation with it, **Then** it is refused as a conflict and is never answered with that
   order or transfer: one user has one key namespace across every write endpoint.
4. **Given** a key that user `1` already used, **When** another user sends a cancellation with it,
   **Then** it is processed as a new request: keys are scoped per user.
5. **Given** two requests with the same key sent concurrently, **When** both are processed, **Then**
   one transition is recorded and both answer with the same outcome.
6. **Given** a cancellation with a key that was refused (the order did not exist, was not the user's,
   or was not `NEW`), **When** it is retried with the same key, **Then** it is evaluated again: a
   refused request writes nothing, its key included.
7. **Given** a cancellation carrying no key, **When** it is processed, **Then** it is executed as in
   P1.

---

### Edge Cases

- **An order of another user.** Refused with the same outcome as an order that does not exist, so the
  endpoint cannot be used to learn which order ids exist for other users. The lookup is scoped by the
  user in the request, as constitution's scope rule requires without authentication.
- **A refused cancellation and an unknown order are distinguishable.** An order that exists for the
  user but is not `NEW` is refused as not cancellable; an order that does not exist for the user is a
  missing target. The caller can tell "you cannot cancel this" from "there is nothing here".
- **Cancelling twice.** The second cancellation finds the order `CANCELLED` and is refused; it never
  releases the reservation again and never appends a second history row. With a key, the retry
  replays the first outcome instead (P3).
- **A transfer.** Transfers are never `NEW`
  ([ADR-0038](../../docs/adr/0038-the-cash-transfer-endpoint-shape.md)), so cancelling one is refused as
  not cancellable, not as an invalid request: the rule is the status, not the side.
- **A `MARKET` order.** A `MARKET` order is born `FILLED` or `REJECTED`, so it is refused as not
  cancellable by the same status rule. The type is not checked; only the status is.
- **A `LIMIT` order that has left `NEW`.** The rule is the status, not the type: if a future matching
  engine fills a `LIMIT` order, it stops being cancellable without this endpoint changing
  (constitution: features must not assume `LIMIT` orders stay `NEW` forever).
- **A malformed order id or user id.** Non-numeric, zero or negative ids are refused as an invalid
  request with nothing written, before any lookup.
- **Cancellation with a ledger anomaly.** If the user's available cash is already negative
  ([ADR-0012](../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md)), cancelling a `NEW`
  order still succeeds: it only removes a reservation and can only raise what is available. Nothing
  about funds or holdings is validated on cancellation.
- **The order's other fields.** Cancellation changes the status only; size, price, instrument, side,
  type and the order's timestamp are left as they were.

## Requirements *(mandatory)*

### Functional Requirements

**The request**

- **FR-001**: The system MUST expose an endpoint that cancels one order of one user, naming the user and
  the order. The request carries nothing else besides the optional idempotency key.
- **FR-002**: A user id or order id that is not a positive whole number MUST be refused as an invalid
  request with nothing written.
- **FR-003**: A cancellation for a user that does not exist MUST be refused with nothing written, with
  the same outcome order submission and cash transfer give an unknown user.
- **FR-004**: A cancellation of an order that does not exist, or that belongs to a different user, MUST
  be refused as a missing target with nothing written, and the two cases MUST be indistinguishable to
  the caller.

**The transition**

- **FR-005**: Only an order whose current status is `NEW` MAY be cancelled. An order in any other status
  MUST be refused as not cancellable, with nothing written and its status and history unchanged; the
  refusal MUST be distinguishable from the missing target of FR-004 and from the invalid request of
  FR-002.
- **FR-006**: Cancelling MUST set the order's status to `CANCELLED` and append exactly one `CANCELLED`
  transition, with the machine-readable reason `USER_REQUESTED`, to its status history in the same
  transaction
  ([ADR-0005](../../docs/adr/0005-order-status-history.md)). No other field of the order changes and no
  other order is written.
- **FR-007**: Whether an order may be cancelled MUST be decided by its status alone, not by its type or
  side.
- **FR-008**: After a cancellation, the order MUST reserve nothing: the next portfolio read for that
  user reports its cash (for a `BUY`) or its shares (for a `SELL`) as available again, through the same
  fold that computes every other figure
  ([ADR-0015](../../docs/adr/0015-the-account-snapshot-is-the-folds-contract.md)). Settled cash and
  settled holdings MUST NOT change.
- **FR-009**: The response to a successful cancellation MUST report the cancelled order: at least its
  identifier, the user, and its new status `CANCELLED`.

**Protections**

- **FR-010**: A cancellation MUST be serialised with every other write of the same user — submissions,
  cash transfers and cancellations — through the per-user protection the other write endpoints share
  ([ADR-0040](../../docs/adr/0040-per-user-write-protection-lives-once.md)), so that the status read that
  decides the cancellation and the write that applies it cannot interleave with another write of that
  user. One user's cancellation MUST NOT wait on another user's write.
- **FR-011**: Two cancellations of the same order MUST NOT both succeed, and an order MUST NOT have more
  than one `CANCELLED` transition in its history.
- **FR-012**: A cancellation MAY carry a caller-supplied idempotency key. Two requests with the same key
  for the same user and the same order MUST record one transition, and every repeat MUST return the
  outcome of the first.
- **FR-013**: A request reusing a key for a different order, or a key the user already used for an order
  submission or a cash transfer, MUST be refused as a conflict and MUST NOT be answered with the earlier
  outcome. Keys MUST be scoped per user, in the one namespace the other write endpoints share.
- **FR-014**: A refused cancellation (FR-002 to FR-005) MUST leave no trace of its key, so a retry with
  the same key is evaluated again.
- **FR-015**: A cancellation carrying no key MUST be processed normally.
- **FR-016**: The existence check, the per-user serialisation and the idempotency claim MUST be the
  shared ones of [ADR-0040](../../docs/adr/0040-per-user-write-protection-lives-once.md); this feature
  MUST NOT implement any of them a second time.

### Key Entities

- **Cancellation (input)**: what the caller sends — the user, the order and an optional idempotency key.
  Not stored as such; what is stored is the order's new status and its transition.
- **Order (updated)**: the ledger row being cancelled. Only its status changes, from `NEW` to
  `CANCELLED`; it stays in the ledger and is never deleted.
- **Order status history (written)**: the one `CANCELLED` transition, with the reason
  `USER_REQUESTED`.
- **Account snapshot (derived)**: the available cash and holdings the portfolio reports, which grow by
  what the cancelled order reserved.
- **Idempotency record (written)**: the association between the user, the key, a fingerprint of the
  cancellation and the order it cancelled — the same record the other write endpoints keep, in one
  namespace per user.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the provided dataset, user `1` cancelling order `5` sees available cash go from
  `627,500.00` to `663,000.00` in their next portfolio read, with settled cash unchanged at
  `753,000.00`.
- **SC-002**: On the same dataset, after cancelling orders `5` and `7`, user `1`'s available cash equals
  their settled cash, to the cent.
- **SC-003**: Every order that is not `NEW` — on the dataset, orders `1`–`4`, `6` and `8`–`11` — is
  refused on cancellation and keeps its status and history unchanged.
- **SC-004**: User `2` cannot cancel any of user `1`'s orders, and the answer they get is identical to
  the one for an order id that does not exist.
- **SC-005**: Two concurrent cancellations of one order end with exactly one `CANCELLED` transition in
  its history and one success, in every run of the test.
- **SC-006**: A cancellation retried with the same key returns the same successful outcome as the first,
  and the order's history holds one transition.
- **SC-007**: A cancelled `SELL LIMIT` leaves its shares sellable again: a `MARKET SELL` of that
  quantity that was `REJECTED` before the cancellation is `FILLED` after it.
- **SC-008**: Every refusal path above — invalid ids, unknown user, unknown or foreign order, not `NEW`,
  key conflict — is covered by a test, not only the happy path (constitution principle V).

## Assumptions

- **The path is `POST /api/v1/users/{userId}/orders/{orderId}/cancel`, and it departs from ADR-0006's
  path** (decided by the author in `docs/journal/09CancelOrder.md`, for cohesion with order
  placement). The journal makes the endpoint take the user and the order, for consistency with every
  other endpoint and because the user is needed anyway for the lock and the scoped lookup.
  [ADR-0006](../../docs/adr/0006-dedicated-cancel-endpoint.md) wrote `POST /api/v1/orders/{orderId}/cancel`
  before ADR-0033 put the user in the path of the write endpoints. ADR-0006's decision — a dedicated
  endpoint, a `POST` action, a state transition rather than a deletion — stands; the new path, the
  response shape and the status codes are recorded at plan time in a new ADR that supersedes
  ADR-0006's path, as constitution VII requires (an accepted ADR is never edited). ADR-0006 weighed
  only a flag on submission and `DELETE`; that ADR also records `PUT` and `PATCH` of the order's status
  as discarded alternatives. The journal writes the path with a trailing slash; the other endpoints have
  none, and whether `/cancel/` is also accepted is settled in that ADR.
- **The cancellation's reason is `USER_REQUESTED`** (decided by the author in the journal). Until now
  the history's reason only ever held a rejection reason (ADR-0005 describes the column as "why, when
  it is a rejection"); a cancellation is not a rejection, so the plan decides how the reason column
  holds both, and whether that extension of ADR-0005's column needs its own ADR. Constitution IV's
  list of rejection reasons does not grow: `USER_REQUESTED` is not one.
- **Not `NEW` is refused, not recorded.** A refused cancellation writes nothing: there is no
  "cancellation rejected" row, and the order keeps its status. This is ADR-0006's "rejected without
  changing state" and the journal's "validación de si es un estado válido para cancelar".
- **Only `NEW` is cancellable, "por ahora".** The journal leaves room for more states later; there are
  none to add today, since `NEW` is the only non-terminal status the statement defines.
- **A cancellation needs no funds check.** It only removes a reservation, so it can never make the user
  overdraw; it is serialised for consistency of the status read, not to protect available cash from
  itself.
- **A foreign order is a missing target**, not a forbidden one. Without authentication there is no
  caller identity to forbid, and answering differently would let anyone probe other users' order ids.
- **The order's existence is checked within the user**, in one lookup scoped by user and order, rather
  than by finding the order first and then comparing its owner — the journal's reason for taking the
  user in the request.
- **The fingerprint's canonical form is the user and the order**, tagged with the operation so a key
  used on another endpoint never matches. Its exact form is a plan-time decision inside the existing
  idempotency mechanism ([ADR-0037](../../docs/adr/0037-idempotent-submission-with-a-caller-key.md)).
- **No new schema is expected.** The status column, the history table and the idempotency table
  already exist; the plan confirms it.
- **The constitution already lists order cancellation** among the API's endpoints, so no amendment is
  needed for the endpoint itself.
- **The functional test for this endpoint is its end-to-end suite**, over real PostgreSQL through
  Testcontainers and validated against the API contract, as for the other write endpoints
  ([ADR-0010](../../docs/adr/0010-testcontainers-with-postgresql-18.md),
  [ADR-0024](../../docs/adr/0024-every-response-is-validated-against-the-contract.md)).
- **Timestamps are server-side.** The history row's time is when the cancellation was recorded.
- **Performance targets are not set.** A cancellation reads one order and writes two rows; any
  structural choice made for it cites a measurement (constitution principle IX).
