# ADR-0043: An idempotent replay answers the outcome as first recorded

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [09CancelOrder](../journal/09CancelOrder.md)
- **Refines:** [ADR-0037](0037-idempotent-submission-with-a-caller-key.md)

## Context

[ADR-0037](0037-idempotent-submission-with-a-caller-key.md) promises that "a retry returns the first
outcome". Submission and transfer implement the replay by reading the order the key names, with its
**current** `orders.status`. Until cancellation existed, an order never changed status after it was
written, so the current status *was* the first outcome.

Cancellation separates them: a `LIMIT` order submitted with a key, then cancelled, would replay as
`CANCELLED` — an answer the submission never gave.

## Decision

**A replay reports the order with the status and reason of the transition its first request
recorded**, read from `order_status_history`, not from `orders.status`:

- submission and transfer — the order's earliest history row, which their append wrote;
- cancellation — the order's `CANCELLED` row.

Every other field comes from `orders`, of which only the status ever changes.

## Rationale

A client that retries after losing a response needs to learn what *its* request did. What has
happened to the order since is the portfolio's to report. Every order a key names was written through
the API, so its first transition is always in the history; seeded orders, which have none, are never
named by a key.

## Alternatives considered

- **The current status.** What the code did. Simpler, but one request's replay would change answer
  over time, which weakens ADR-0037's promise.
- **Storing the first response in `order_idempotency`.** A migration and a second copy of the order,
  for what the history already records.

## Consequences

- No behaviour changes for an order whose status never changed, which, before cancellation, was every
  order.
- A replay reads one more row (the transition) — it already read the history for a rejection reason.
- ADR-0037 is not superseded: its promise is unchanged; this records how it is kept.
