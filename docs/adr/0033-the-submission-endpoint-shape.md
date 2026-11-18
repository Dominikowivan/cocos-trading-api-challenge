# ADR-0033: One submission endpoint under the user, where every recorded outcome is a `201`

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [07OrderPlacement](../journal/07OrderPlacement.md)

## Context

The statement's third endpoint sends one order to the market. Three questions had to be answered
before a line of it could be written, and all three are about the same thing: what the caller sees.

1. **Where does the user id live?** The journal lists `userId` among the request's attributes, next
   to the instrument, the type and the side. The portfolio endpoint, built first, takes it as a path
   segment instead.
2. **What status code does a stored rejection get?** The statement requires an order the user cannot
   afford to be *stored* as `REJECTED`. So a request can be perfectly well formed, change the
   database, and still tell the caller "no".
3. **Which failures are not that?** An unknown user, an unknown instrument, a `MONEDA` instrument,
   both a size and an amount, a `MARKET` order carrying a price: none of them produce a row.

The project has no authentication (ADR-0007), so the user id is untrusted input and every query has
to be scoped by it.

## Decision

**`POST /api/v1/users/{userId}/orders`. Every outcome that appended a row to the ledger answers
`201 Created` with the order in the body; a request that wrote nothing never answers `2xx`.**

The body carries the instrument, the type, the side and exactly one of a share count or a total
amount in pesos. The user is in the path, as in `GET /api/v1/users/{userId}/portfolio`.

| Answer | When |
|---|---|
| `201` | the submission produced an order: `FILLED`, `NEW` or `REJECTED` |
| `400` | the request cannot be acted on: both a size and an amount, or neither; a `LIMIT` with no price; a `MARKET` with a price; a side that is not `BUY` or `SELL`; a size or an amount outside the bounds of the ledger's columns; an unknown instrument; a `MONEDA` instrument; a `MARKET` order for an instrument with no price (ADR-0034); a malformed body |
| `404` | no user with that id — the request's target does not exist |
| `409` | the `Idempotency-Key` was used by this user for a different request |
| `503` | the transaction ran past its deadline, including while waiting for another submission of the same user, or no connection came free (ADR-0031, unchanged) |

So a client branches **once**, on whether it got a `2xx`, and then reads `status` for the business
answer. The split between "the market answered about your money" and "your request was malformed" is
the status *class*, and the three recorded outcomes are a *field*.

There is no `Location` header: no endpoint reads a single order back, and the order's effect is
visible in the user's next portfolio read.

## Rationale

**The user in the path.** The scope of a request is then visible in its line in an access log, it is
the same place the only other user-scoped endpoint keeps it, and a path variable is harder to forget
to scope a query by than a body field. It also settles what `404` means here: the target of the
request is a user's order collection, so an unknown user is a missing target, while everything the
*body* names is a `400` — a body reference cannot make a resource missing.

**`201` for a rejection.** All three recorded outcomes really did create a resource, and all three
are readable back through the portfolio. Making the distinction a status code instead would give the
client a second thing to branch on for no new information, and it would be a statement about how the
news feels rather than about what happened.

## Alternatives considered

- **`POST /api/v1/orders` with `userId` in the body**, which is how the journal first listed it.
  Rejected: it splits where the user id lives across two endpoints of the same API.
- **`POST /api/v1/orders` with the user taken from authentication.** There is none (ADR-0007), so
  there is nowhere to take it from.
- **`201` when accepted, `200` when `REJECTED`.** Rejected: the row was created either way.
- **`200` for everything.** Rejected: it loses the only durable effect this endpoint has, which is
  that the ledger grew by one row.
- **`422 Unprocessable Content` for a `REJECTED` order.** Rejected outright: a rejection is a
  successful, recorded business outcome, and a `4xx` tells a client the opposite.
- **`422` for the semantic refusals** (a `MONEDA` instrument, an instrument with no price) and `400`
  only for malformed syntax. Defensible, and rejected for one reason: the spec draws exactly one
  line, between recorded and refused, and a second line inside the refusals would invite arguing
  about which side each rule falls on. This is the alternative a reviewer is most likely to prefer.
- **`404` for an unknown instrument id too.** Rejected: a client cannot tell from a `404` whether
  the user or the instrument was the problem.
- **Bean validation annotations on the request DTO** for the `400`s. Rejected: constitution VIII puts
  request validation in the use case, so the rules would be split between annotations on a DTO and a
  model object. The DTO carries the type and the side as text and the use case parses them, which is
  also how the problem detail gets to name the field and the values that were allowed.

## Consequences

- Cancellation, when it is built (ADR-0006), is expected at
  `POST /api/v1/users/{userId}/orders/{orderId}/cancel`, so the user scope is uniform across the
  write path. That is not decided here.
- **The shape of the request is judged before the user exists.** The refusals that need nothing from
  the database — a type or a side that is not one, both a size and an amount or neither, a size
  outside the ledger's column — are evaluated before the submission takes the lock on the user's
  row, so a malformed body never serialises that user's other submissions while it rolls back. The
  cost is a precedence: a request that is *both* for an unknown user and malformed answers `400`
  rather than `404`. Everything the body names that requires a read — the instrument above all —
  is still judged after the user is found, so `404` keeps its precedence over those.
- A caller that wants to know whether an order was accepted reads `status`, never the status code.
  The contract says so on the field, and the examples in `docs/api/openapi.yaml` show a `REJECTED`
  order answered with `201`.
