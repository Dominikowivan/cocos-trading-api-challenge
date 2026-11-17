# ADR-0038: One cash transfer endpoint under the user, with its own response and a closed body

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [08CashTransfer](../journal/08CashTransfer.md)

## Context

The statement says incoming and outgoing transfers can be modelled as orders with side `CASH_IN`
and `CASH_OUT`, but asks for no endpoint that creates them. Order submission refuses those two sides
on purpose: the author wanted transfers in their own endpoint, with their own cohesive parameters
(`docs/journal/07OrderPlacement.md`). The journal for this feature asks for one endpoint that takes
the user, the side and an amount; a deposit is never validated, a withdrawal needs enough available
cash and is otherwise recorded `REJECTED`.

Four questions about what the caller sees had to be answered: the path, the status of a rejected
withdrawal, the shape of the response, and what happens to fields that belong to an order.

## Decision

**`POST /api/v1/users/{userId}/cash-transfers`, a body of exactly `side` and `amount`, and a
response of the transfer's own shape. Every recorded outcome answers `201`.**

| Answer | When |
|---|---|
| `201` | the transfer was recorded: a `FILLED` deposit, a `FILLED` or `REJECTED` withdrawal, or a replay of either |
| `400` | a side other than `CASH_IN`/`CASH_OUT`; an amount missing, not whole, below `1` or above the ledger's size column (ADR-0039); a field other than `side` and `amount`; a blank or over-long key; an unreadable body |
| `404` | no user with that id; a deposit does not create one |
| `409` | the `Idempotency-Key` already names a different request of this user — another transfer or an order |
| `503` | the transaction ran past its deadline or no connection came free (ADR-0031) |

The response carries `orderId`, `userId`, `side`, `amount` (money scale, ADR-0016), `status`,
`rejectionReason` (`null` unless rejected) and `transferredAt`.

- **The body is closed.** Any field outside `side` and `amount` is refused with `400` naming it. The
  use case refuses it, not an annotation on the DTO (ADR-0033's reasoning).
- **Protections are submission's.** The transfer takes the same per-user lock (ADR-0036) and the same
  idempotency claim, in the same table (ADR-0037), so a user's transfers and orders share one
  serialisation and one key namespace. The transfer's fingerprint is tagged with the operation, so a
  key used on the other endpoint never matches and answers `409`; submission's fingerprint is not
  changed. Both protections live once, in `service/userwrite` (ADR-0040).

## Rationale

**The path.** The user in the path for the reasons ADR-0033 gave, so an unknown user is a `404` and
everything in the body is a `400`, uniformly across the API. `cash-transfers` and not `transfers`:
the journal excludes transfers between users and between accounts, and a bare `transfers` is how
those would be named.

**`201` for a rejected withdrawal.** The row was created and is readable through the portfolio,
exactly as for an unaffordable order. One rule for both write endpoints: `2xx` means the ledger grew.

**Its own response.** Inside, a transfer is an order on `ARS` at price `1` (ADR-0039). That is the
ledger's representation, not anything the caller asked for; returning `ticker`, `type`, `size` and
`price: 1` would make every client learn that `size` means pesos here. The id keeps the name
`orderId` because it is the order's id, the one the idempotency replay and the ledger use.

**A closed body.** The spec requires that order fields be refused rather than ignored. A list of five
forbidden names would still silently ignore a sixth the caller believed meant something; with two
legitimate fields, closing the body is the simpler rule.

## Alternatives considered

- **`/transfers`** — shorter; ambiguous with the transfer kinds the journal excludes.
- **`/cash`, with the direction in the body** — makes the balance the resource, and the balance has
  no row (ADR-0002).
- **`CASH_IN`/`CASH_OUT` accepted by `POST /orders`** — rejected by the author in the order placement
  journal; its body (instrument, type, size or amount, price) means nothing for a transfer.
- **`/deposits` and `/withdrawals`** — two endpoints where the journal asks for one with a side.
- **Reusing `OrderResponse`** — one schema fewer, at the cost of exposing the modelling trick.
- **Ignoring unknown fields, as submission does** — contradicts the spec. Turning on fail-on-unknown
  globally would change submission's behaviour, which this feature must not do.
- **`422` for a rejected withdrawal** — rejected by ADR-0033 for orders, for the same reason.

## Consequences

- The two write endpoints differ on one point a client can see: submission ignores unknown fields and
  this one refuses them. The contract states it on both operations.
- The unreadable-body detail of `ApiExceptionHandler` becomes endpoint-neutral; no test or contract
  asserted its text.
- The constitution's endpoint list gains cash transfer (2.4.0).
- A transfer is never `NEW`, so cancellation never applies to one.
