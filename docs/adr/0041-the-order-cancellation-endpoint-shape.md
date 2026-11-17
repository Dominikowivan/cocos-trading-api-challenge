# ADR-0041: Cancellation is `POST …/users/{userId}/orders/{orderId}/cancel`, answering the order

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [09CancelOrder](../journal/09CancelOrder.md)
- **Supersedes:** the path of [ADR-0006](0006-dedicated-cancel-endpoint.md)

## Context

[ADR-0006](0006-dedicated-cancel-endpoint.md) decided that cancelling is an endpoint of its own, a
`POST` action and a state transition, and wrote its path as `POST /api/v1/orders/{orderId}/cancel`.
That was before [ADR-0033](0033-the-submission-endpoint-shape.md) put the user in the path of every
write endpoint. The journal for this feature asks for the user and the order in the request, for
consistency with the other endpoints and because the user is what the lock and the scoped lookup need.

What the caller sees also had to be decided: the status and body of a success, and how a missing
order, an order of another user and an order that is not `NEW` are told apart.

## Decision

**`POST /api/v1/users/{userId}/orders/{orderId}/cancel`, no body, an optional `Idempotency-Key`.**

| Answer | When | Written |
|---|---|---|
| `200` with the `Order` schema, `status: CANCELLED` | the order was `NEW`; or a replay of that cancellation | the transition, the first time |
| `400` | an id is `0` or below, or not a number; the key is blank or too long | nothing |
| `404` | the user does not exist; no order with that id **for that user** — missing and foreign answer identically | nothing |
| `409` | the order is not `NEW`; or the key names a different request | nothing |
| `503` | the deadline or the pool ([ADR-0031](0031-database-access-has-a-deadline-and-a-bounded-pool.md)) | nothing |

The path has no trailing slash, like every other endpoint. The cancellation is serialised with the
user's other writes and made idempotent by `UserWrite`
([ADR-0036](0036-submissions-are-serialised-per-user.md),
[ADR-0037](0037-idempotent-submission-with-a-caller-key.md),
[ADR-0040](0040-per-user-write-protection-lives-once.md)); its fingerprint is tagged
`ORDER_CANCELLATION`, so a key used on another endpoint is a `409`.

What ADR-0006 decided besides the path still holds: a dedicated endpoint, a `POST` action, a
transition recorded in the history and never a deletion, only `NEW` orders, and a refusal that
changes nothing.

## Rationale

- **`200`, not `201`.** Nothing is created; the order already existed.
- **The `Order` schema.** A client reads an order the same way whichever endpoint answered it. The
  transfer has its own schema because storing it as an order is a modelling choice the caller should
  not see; a cancelled order *is* an order.
- **`409` for not cancellable.** RFC 9110: the request conflicts with the current state of the target
  resource. The request is well formed and was processed, so `422` describes it less well. The key
  conflict shares the code; the detail tells them apart.
- **`404` for a foreign order.** Without authentication there is no caller to forbid
  ([ADR-0007](0007-out-of-scope-matching-engine-and-auth.md)), and a distinct answer would let anyone
  learn which order ids exist.
- **`400` for ids below `1`.** The contract already declares `minimum: 1`. The other write endpoints
  answer `404` for a `userId` of `0`, because nothing checks it before the existence read; aligning
  them is left to a change of its own.

## Alternatives considered

- **ADR-0006's `POST /api/v1/orders/{orderId}/cancel`.** The owner would have to be read from the
  order, outside the user's lock, and a foreign order refused only after learning whose it is.
- **`PATCH /orders/{orderId}` with a status, or `PUT …/status`.** A generic status write invites every
  other transition, all of which the endpoint would have to refuse.
- **`204 No Content`.** The caller loses the confirmation, and there is no endpoint to read the order
  back.
- **The cancellation's own response** (`orderId`, `status`, `cancelledAt`, `reason`). A second schema
  for the same order.
- **`422` for not cancellable; `403` for a foreign order.** See the rationale.
- **Accepting `/cancel/` as well.** Two spellings in the contract for no caller who needs them.

## Consequences

- The live contract's `Order.status` admits `CANCELLED`.
- ADR-0006 is marked superseded; the README's endpoint list cites this ADR.
- An order cancelled by mistake cannot be restored: `CANCELLED` is terminal, and a new order is the
  way back.
