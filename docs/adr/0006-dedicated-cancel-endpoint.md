# ADR-0006: A dedicated endpoint for cancelling an order

- **Status:** Superseded by [ADR-0041](0041-the-order-cancellation-endpoint-shape.md)
- **Date:** 2026-11-15
- **Journal:** [01PrimeraLectura](../journal/01PrimeraLectura.md), *Decisiones extra → Endpoint*

## Context

Cancelling an order has to be reachable through the API: an order sitting in `NEW` holds funds or
shares reserved ([ADR-0004](0004-reserve-funds-on-new-orders.md)), and releasing them is a
functional requirement rather than a convenience.

The decision is not whether cancellation exists, but **where it lives**: as another shape of the
order submission endpoint, or as an endpoint of its own.

## Decision

`POST /api/v1/orders/{orderId}/cancel`, a separate endpoint. Only orders in `NEW` can be
cancelled; anything else is rejected without changing state.

The reason is **cohesion of the contract**. Submitting an order and cancelling one are different
operations over different inputs, and folding them together means a payload whose fields are
conditionally meaningless.

## Alternatives considered

- **A flag on the submission endpoint**, something like `cancelOperation: true` instead of a
  `status` field. Discarded because with that flag set, every other attribute of the request —
  instrument, side, type, size, price — stops being used. An endpoint whose payload changes
  meaning depending on one boolean cannot be validated coherently, cannot be documented without
  qualifying every field, and forces the handler to branch into two unrelated operations.
- **`DELETE /orders/{id}`.** Closer to habit, and wrong here: cancelling is a state transition,
  not a deletion. The order stays in the ledger with its history, and `DELETE` would suggest
  otherwise.

## Consequences

- The API exposes four endpoints where the statement asks for three, and the README says why.
- Cancelling writes a transition into `order_status_history`
  ([ADR-0005](0005-order-status-history.md)), so the order's path stays complete.
- Cancellation is idempotent in effect: cancelling an already-cancelled order does not release the
  reservation twice.
- It needs the same concurrency care as submission — cancelling while another request reads
  available cash must not release funds twice.
- The decision is independent of the current scope. If a matching engine is added later and `NEW`
  orders start reaching real terminal states on their own, cancellation remains a distinct
  operation with a distinct contract, and this endpoint does not change.
