# ADR-0047: One claim per order, stated as a constraint

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [10Refactors](../journal/10Refactors.md)
- **Refines:** [ADR-0037](0037-idempotent-submission-with-a-caller-key.md)

## Context

`order_idempotency` records which order a caller's key produced. `UNIQUE (userid, idempotency_key)`
([ADR-0037](0037-idempotent-submission-with-a-caller-key.md)) states one half of the relationship:
one key claims one outcome. The other half was left to the code: **one order answers to at most one
key.**

It holds by construction. A claim that wins the `INSERT … ON CONFLICT DO NOTHING` does the work and
attaches the order it created; a claim that loses replays and attaches nothing. No path writes a
second key against an order that already has one. Nothing in the schema said so, so a future write
path — a second endpoint, a retry that attaches instead of replaying, a repair script — could have
written one, and the first symptom would have been two keys replaying the same order as if each had
produced it.

## Decision

**`V6__one_claim_per_order.sql` adds `UNIQUE (order_id)` to `order_idempotency`.**

```sql
ALTER TABLE order_idempotency ADD CONSTRAINT uq_order_idempotency_order_id UNIQUE (order_id);
```

PostgreSQL's `UNIQUE` admits any number of `NULL`s, which is what this table needs: a claim is
written before the order exists and `order_id` is filled afterwards, so unattached claims coexist
freely and only attached ones are constrained
([ADR-0030](0030-the-schema-requires-columns-not-business-rules.md) is why that column is the one
nullable column of the table).

## Rationale

The invariant was already true; this makes it **unrepresentable to break**, which is the difference
between a property the code happens to have and one the database guarantees. It costs one index on a
table that is written once per keyed request.

It is the same reasoning the schema already applies twice: `UNIQUE (instrumentid, date)` is what
makes "the latest price" a single row ([ADR-0044](0044-the-latest-price-is-one-index-seek-per-instrument.md)),
and `UNIQUE (userid, idempotency_key)` is what makes the claim atomic rather than checked. A
constraint that encodes a rule is also documentation that cannot drift from the code.

Reviewing other public solutions to this challenge is what surfaced it: one of them declares the
equivalent column `NOT NULL UNIQUE`, because it writes the record after the order rather than before.
That ordering is not adopted here — claiming first is what lets a replay skip the per-user lock
([ADR-0036](0036-submissions-are-serialised-per-user.md)) — but the uniqueness half of it applies
either way.

## Alternatives considered

- **Leave it to the code.** The state before this ADR. Correct today, and silent about why.
- **`NOT NULL UNIQUE`, writing the claim after the order.** Simpler schema — one fewer nullable
  column and no two-phase claim — and it is what at least one other solution does. Rejected because
  it moves the mutual exclusion from the `INSERT` to the lock: the key can only be checked once the
  user's row is held, so every replay queues behind that user's writes. The claim-first ordering of
  ADR-0037 is the reason a retry costs nothing.
- **A partial unique index** (`WHERE order_id IS NOT NULL`). Identical behaviour, since `UNIQUE`
  already ignores `NULL`s, with a condition a reader has to verify is redundant.

## Consequences

- An attempt to attach a second key to an order is a `DataIntegrityViolationException`, and no code
  path produces one.
- `OrderIdempotencySchemaTest` asserts both halves: two claims cannot name the same order, and two
  claims with no order yet are both accepted.
- `V6` runs on the provided database like every other migration (ADR-0009). It is an `ALTER TABLE`
  on a table this solution created, so it touches nothing that was given.
