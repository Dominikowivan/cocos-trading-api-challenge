# ADR-0037: An idempotent submission is a caller key, a fingerprint and `INSERT … ON CONFLICT DO NOTHING`

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [07OrderPlacement](../journal/07OrderPlacement.md)

## Context

A submission is not safe to retry. A client whose connection drops after the request reached the
server has no way to tell "it never arrived" from "it traded and the answer was lost", and the only
recovery available to it — send it again — buys the shares twice.

Nothing in the ledger can tell the two apart either: a user may legitimately buy 100 `PAMP` twice in
a row, so two identical rows are not evidence of a mistake. Whether a second request is a retry or a
second order is the **caller's** statement to make, not something the server can infer.

## Decision

**An optional `Idempotency-Key` request header makes a retry return the first outcome instead of
trading again.**

- A new table, `order_idempotency` (`V5`), holds `(userid, idempotency_key)` **unique**, a
  fingerprint of the request, and the `order_id` the request produced. Keys are therefore scoped per
  user: the same key sent by two users is two independent requests (FR-030).
- The transaction claims the key **before any work and before the user's row is locked**, with
  `INSERT … ON CONFLICT (userid, idempotency_key) DO NOTHING`.
  **One row written means this request is the first**: the work proceeds and the claim's `order_id`
  is filled in the same transaction. **No row written means the key was used**: the committed row is
  read back and its fingerprint compared — equal, and the order it names is returned (FR-028);
  different, and the request is refused `409` (FR-029).
- The fingerprint is **SHA-256 over the canonicalised request**, stored as 64 hex characters: the
  path's user id and the body's fields in a fixed order, every decimal normalised to scale 2. It is
  computed from the **parsed** request, so JSON key order, whitespace and `900` against `900.00`
  cannot make a retry look like a new request. The key itself is not part of it, which is one reason
  it is a header and not a body field.
- `RequestFingerprint` is the one place the canonical form exists, so the hash a retry computes
  cannot drift from the hash the first request stored.
- **Retention**: rows are kept. Nothing expires them in this exercise, and a purge policy with no
  stated retention requirement would be an invented rule.

## Rationale

**The header.** `Idempotency-Key` is the convention clients, SDKs and proxies already know, and it
keeps the key out of the body — so the body *is* the fingerprinted thing, with no field to remember
to exclude from the hash. A key inside the body makes the fingerprint a function of a subset of the
body, and "which subset" is exactly the kind of rule that rots.

**`ON CONFLICT DO NOTHING` and not a `SELECT` first.** A check-then-act is a race: two concurrent
retries would both find no row and both trade. The unique index decides atomically, and the loser
learns it lost by getting zero rows back.

**`READ COMMITTED`** (ADR-0036). The loser's insert blocks on the uncommitted unique key until the
winner commits, and must then **see** the committed row. That is `READ COMMITTED`'s behaviour; under
`REPEATABLE_READ` the same insert raises a serialisation failure and the retry becomes an error the
client has to interpret. It is also why `order_id` is nullable: the winner fills it before
committing, so a reader that arrived by conflict always finds it set.

## The step order, and the extra read it costs

```
read and validate the request, compute the fingerprint   (no database)
verify the user exists                                   SELECT on the primary key, no lock
claim the key, when one was sent                         INSERT … ON CONFLICT DO NOTHING
  |- one row written  -> lock the user -> fold -> decide -> append order + history -> attach
  |- no row written   -> read the committed claim -> fingerprints agree ? replay : 409
```

**The claim comes before the lock, and that ordering is the point.** A retry that lost the claim
decides nothing and writes nothing, so it has no business holding an exclusive lock on the user's
row: it reads the committed outcome and answers. A replay therefore **never queues behind another
submission for the same user**, which is exactly the load a retrying client generates. Putting the
lock first would still be correct, but every duplicate would take the write lock only to discover
it had nothing to do.

**The cost is one extra round trip**: the user's existence is checked by a separate non-locking
`SELECT` on the primary key before the claim, instead of falling out of the lock (ADR-0036).
`order_idempotency.userid` is a foreign key to `users`, so claiming a key for a user that does not
exist would raise a constraint violation instead of the `404` FR-007 requires. The alternatives were
to drop that foreign key, or to infer "unknown user" from the constraint violation; both trade a
declared guarantee, or a brittle inference, for one indexed lookup on an endpoint whose priority is
being safe rather than being fast. The author took that trade deliberately (2026-11-17).

**No deadlock.** Every path acquires in the same order — key, then user — and an unkeyed submission
takes only the user lock and never waits on a key. A keyed request that already claimed waits on the
user lock while holding its own key entry; nothing waits on that entry except a retry of the same
key, which holds no user lock. There is no cycle.

## Alternatives considered

- **The key in the request body.** Rejected for the fingerprint reason above.
- **Storing the whole request body instead of a hash.** Rejected: it keeps user data for no gain, and
  comparing bodies still means deciding whether whitespace and key order matter — a canonical form is
  needed either way, so hash the canonical form.
- **Answering `409` to any repeat of a key, regardless of the body.** Rejected: FR-028 requires a
  retry of the *same* request to get the first outcome. A client that timed out has no other way to
  learn what happened.
- **A unique index on the fingerprint alone, with no caller key.** Rejected: two deliberate identical
  orders are legitimate, so a body-only guard would refuse a real second order.
- **Expiring keys after 24 hours.** Rejected for now: no requirement states a window. Written down as
  deferred rather than implemented.

## Consequences

- A keyed request that is **refused** (`400`) or that fails for any reason leaves no claim: the
  rollback takes the claim with it, so the key is free and the corrected request may reuse it. This
  is why the claim lives inside the submission's transaction rather than in a filter in front of it.
- A **replay and a `409` both answer while another transaction holds the user's row**, which is
  asserted directly: the row is locked from a second connection and the retry still returns the
  first order.
- A `REJECTED` outcome is replayed exactly as a `FILLED` one is — including its reason, which is read
  back from `order_status_history`, since `orders` does not carry it.
- The table grows by one row per keyed submission and nothing prunes it. Stated, not hidden.
- Two requests with the same key sent at the same instant produce exactly one order, and both
  answers name it. That is a two-thread Testcontainers test, not an argument.
