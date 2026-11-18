# ADR-0042: The history's `reason` says why the order moved, not only why it was rejected

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [09CancelOrder](../journal/09CancelOrder.md)
- **Extends:** [ADR-0005](0005-order-status-history.md)

## Context

[ADR-0005](0005-order-status-history.md) gave `order_status_history` a `reason` column described as
"why, when it is a rejection". Until now only `REJECTED` rows carried one. The journal asks that a
cancellation record the reason `USER_REQUESTED`.

The code reads the column assuming the same: `OrderStatusHistoryRepository.reasonOf` maps every
non-null reason as a `RejectionReason`, with no order on the rows. With a `USER_REQUESTED` row in an
order's history, a keyed replay of that order's submission would fail with a `500`.

## Decision

**`reason` holds the machine-readable reason of the transition in its row**: a rejection reason on a
`REJECTED` row, `USER_REQUESTED` on a `CANCELLED` row, `null` on `NEW` and `FILLED`.

In the model the two vocabularies are **separate types**: `RejectionReason` keeps its three values
(constitution IV), and a cancellation reason type holds `USER_REQUESTED`. Persistence reads the column
according to the row's status, and the rejection reason of an order is read from its `REJECTED` row
only.

## Rationale

The column was sized and named for a reason, and nothing about it is specific to rejections except
ADR-0005's wording. Two types keep a rejection from ever carrying `USER_REQUESTED`, which one merged
enum would allow, and leave room for a later cancellation that is not the user's — an expiry, say —
to say so with a value of its own.

## Alternatives considered

- **One `TransitionReason` enum with every value.** An order could then be "rejected for
  `USER_REQUESTED`".
- **A second column, `cancellation_reason`.** A migration for what the existing column describes.
- **No reason on a cancellation.** Contradicts the journal, and loses the distinction a future
  non-user cancellation would need.
- **Adding `USER_REQUESTED` to `RejectionReason`.** A cancellation is not a rejection; constitution IV
  lists exactly the rejection reasons.

## Consequences

- No migration: `reason VARCHAR(40)` holds `USER_REQUESTED`.
- Reading a reason requires knowing its row's status; the mapper does it in one place.
- ADR-0005 is not superseded: the table, its append-only rule and its other columns are unchanged.
