# ADR-0005: An append-only `order_status_history` table

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [01PrimeraLectura](../journal/01PrimeraLectura.md), *Decisiones extra → Tabla adicional*

## Context

`orders.status` is a mutable column holding the current state. An order can go
`NEW → CANCELLED` or `NEW → REJECTED`, and the column only ever shows where it ended up.

For a broker that means the intermediate states are unrecoverable. Nobody can answer when an order
was rejected, why, or whether it was ever live — and "why was this rejected" is exactly the
question support and compliance ask. Overwriting that history is not acceptable for a system
holding other people's money.

## Decision

Add an append-only table that records every state transition:

| Column | Purpose |
|---|---|
| `id` | surrogate key |
| `order_id` | the order the transition belongs to |
| `status` | the state the order moved **into** |
| `reason` | why, when it is a rejection: no funds, no holdings, no price |
| `changed_at` | when |

Rows are inserted, never updated or deleted. `orders.status` stays as it is, as the current-state
cache the schema already defines.

## Alternatives considered

- **Keep only `orders.status`.** What the provided schema does. Discarded for the reason above:
  it loses the path.
- **Also store `from_status`.** Considered and dropped: the previous state is already the
  `status` of the preceding row for the same order. Storing it again is a denormalisation of the
  same kind criticised in `marketdata` — a derived value kept in two places with no rule keeping
  them consistent.
- **Event sourcing the whole order.** Rebuilding the order from its events rather than storing
  current state. A larger change than this exercise justifies, and it would fight the given
  schema rather than extend it.

## Consequences

- `orders.status` and the last row of the history can in principle disagree. They are written in
  the same transaction so they do not, and the history is the authority if they ever did.
- Rejections carry a machine-readable reason, so the API can explain itself without parsing text.
- One extra insert per transition. At this scale, irrelevant.
- It is the first migration after the given schema, which is what `baseline-on-migrate` in the
  staging profile exists to allow ([ADR-0009](0009-flyway-and-provided-database-as-staging.md)).
