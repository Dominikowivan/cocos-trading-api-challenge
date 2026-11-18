# ADR-0036: Submissions are serialised per user by a pessimistic lock on their `users` row

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [07OrderPlacement](../journal/07OrderPlacement.md)
- **Corrected:** 2026-11-17 — the Decision said the lock is emitted as `FOR UPDATE`. It is not:
  Hibernate emits `FOR NO KEY UPDATE`. The decision is unchanged and so is the behaviour; what was
  wrong was the statement of fact, so it is fixed in place rather than left to mislead.

## Context

Every submission validates the request against the user's **available** cash and holdings, and that
availability is a fold of the user's own `orders` rows (ADR-0015). Read and write are therefore two
statements over the same data: a request reads the fold, decides, and appends a row that changes it.

Two submissions for the same user that overlap in time both read the same availability and both
decide against it. `400 PAMP` at `925.85` costs `370,340.00`; user `1` has `627,500.00` available,
so two of them — `740,680.00` together — must not both be accepted. Nothing in user stories 1 to 4
prevents it: their validation is correct and, until something serialises the pair, advisory.

The isolation level of the reads is `REPEATABLE_READ` (ADR-0014), which gives a read one instant but
does not stop two writers from committing on top of the same snapshot.

## Decision

**A submission's transaction begins by locking the user's `users` row, and runs at
`READ COMMITTED`.**

- The lock is taken on the **`users` row**, as the first statement of the work, before the fold,
  the decision and the two inserts, so the endpoint can never fold a ledger it did not lock.
  `UserRepository.lockById` is that statement, declared with `@Lock(PESSIMISTIC_WRITE)`.
- **It is not the existence check.** "Does this user exist" is answered earlier, by a separate
  non-locking read on the primary key, because the idempotency claim runs before the lock and its
  `userid` is a foreign key to `users`
  ([ADR-0037](0037-idempotent-submission-with-a-caller-key.md) explains the ordering). The lock
  therefore does one thing. It still answers `404` if the row has gone by the time it runs, which
  is the truthful answer, though nothing in this system deletes users.
- **A replayed idempotent retry never takes this lock at all**: it decides nothing and writes
  nothing, so it does not queue behind another submission for the same user
  ([ADR-0037](0037-idempotent-submission-with-a-caller-key.md)).
- **The lock is emitted as `FOR NO KEY UPDATE`, not `FOR UPDATE`**, which is what Hibernate's
  PostgreSQL dialect translates `PESSIMISTIC_WRITE` into. Measured, not assumed: the statement that
  runs is `select ue1_0.id from users ue1_0 where ue1_0.id=? for no key update of ue1_0`. Three
  consequences, each measured against PostgreSQL 18.7:
  - it **conflicts with itself**, so two submissions for the same user still serialise — which is
    the property this ADR buys, and the concurrency tests prove it end to end;
  - it does **not** conflict with `FOR KEY SHARE`, the lock a foreign-key check takes, which is why
    Hibernate picks it: a true `FOR UPDATE` would make every `INSERT INTO orders` for this user
    queue behind the lock holder on the foreign-key check alone;
  - therefore a transaction that wrote an order **without taking this lock would not be blocked by
    it**. A `FOR UPDATE` would have blocked it. What closes that gap is not the lock but the
    ArchUnit rule that only `service/userwrite` may take it ([ADR-0040](0040-per-user-write-protection-lives-once.md)):
    the architectural rule carries part of the concurrency guarantee.
- `OrderSubmissionService` is annotated `@Transactional(isolation = READ_COMMITTED)` — explicitly,
  although it is PostgreSQL's default, so the contrast with the `REPEATABLE_READ` reads is visible
  where it is chosen.

Two submissions for the same user are processed one after the other. Submissions for different users
never meet.

## Rationale

The lock has to be taken on something that exists for every user and is not what is about to be
written. The `users` row is exactly that: one row per user, contended by no other feature, and the
natural granularity of the invariant, which is "this user's availability".

`READ COMMITTED` and not `REPEATABLE_READ`, deliberately: the second transaction waits for the first
and must then see **what the first committed**. Under `REPEATABLE_READ` its snapshot would predate
the wait, so it would validate against figures the first submission already spent — the lock would
serialise the requests and then hand the loser stale numbers. A read needs one instant; a write needs
the current one.

## Alternatives considered

- **`SERIALIZABLE` with a retry loop.** Correct and lock-free; PostgreSQL would abort one transaction
  with `40001`. Rejected: it needs retry machinery or the loser gets a `500`, retries make the
  idempotency path (ADR-0037) harder to reason about, and a conflict detected after the work is done
  costs more than a lock taken before it.
- **A PostgreSQL advisory lock** (`pg_advisory_xact_lock(userId)`). Needs no row. Rejected: the key
  is a bare integer in a global namespace shared with any future use, and it is a lock nobody can see
  from the schema.
- **An optimistic `version` column on `users`.** Rejected: it adds a column to the given schema to
  protect an invariant that is not about that row's contents, and it still fails late.
- **Locking the user's existing `orders` rows** with `FOR UPDATE` over the fold's own query. It would
  lock exactly what is read. Rejected: a user with no orders locks nothing, which is precisely the
  case where two `CASH_IN`-funded buys race, and the lock set grows with the user's history.
- **A table lock, or an application-level mutex.** Rejected: the first serialises every user against
  every other; the second does not survive a second instance of the API.

## Consequences

- **One user's submissions are sequenced at the database.** The cost is explicit and accepted: a
  submission holds the row for the duration of one fold, one decision and two inserts.
- A submission that waits past the transaction deadline answers **`503`** through the unchanged
  handler (ADR-0031) with nothing written. That is the right answer — the request was valid — and it
  needed no new mapping.
- The guarantee is per user, so **throughput across users is unaffected**: a submission for one user
  does not wait on another user's lock, which is asserted directly by holding one user's row and
  submitting for another.
- The proof is automated, not argued: two-thread Testcontainers tests fire the real endpoint and
  assert that exactly one of two unaffordable orders fills, in every run, and that the position is
  never driven negative.
