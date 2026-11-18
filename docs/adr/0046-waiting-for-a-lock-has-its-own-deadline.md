# ADR-0046: Waiting for a lock has its own deadline, narrower than the transaction's

- **Status:** Accepted
- **Date:** 2026-11-17
- **Journal:** [10Refactors](../journal/10Refactors.md)
- **Refines:** [ADR-0031](0031-database-access-has-a-deadline-and-a-bounded-pool.md)

## Context

[ADR-0031](0031-database-access-has-a-deadline-and-a-bounded-pool.md) bounds database access with one
instrument: `spring.transaction.default-timeout`, ten seconds, which cancels the running statement
and answers `503`. It covers every way a request can hang, including waiting for the per-user lock of
[ADR-0036](0036-submissions-are-serialised-per-user.md).

Covering both with one number means the two cases cannot be told apart, and the number has to be
sized for the slower of them. A query that is genuinely slow and a request queued behind another
write for the same user are different events: the first is a problem with the query or the data, the
second is the lock doing exactly what it was added to do. Ten seconds is a reasonable ceiling for a
statement. It is a long time to hold a client waiting to learn that someone else is writing.

PostgreSQL has a narrower instrument, `lock_timeout`, which bounds **only** the wait to acquire a
lock and leaves the duration of statements alone.

## Decision

**A connection sets `lock_timeout` to three seconds, below the transaction's ten.**

```yaml
spring:
  datasource:
    hikari:
      connection-init-sql: SET lock_timeout = '${TRADING_DB_LOCK_TIMEOUT:3s}'
```

Three seconds because the write it waits for is sub-millisecond work: three seconds of queue is
already evidence that something is wrong, not that traffic is high. Below the transaction's deadline
so that, when a request is waiting for a lock, **this is the limit that fires** — the request fails
for the reason that is true.

Exhausting it answers the same `503` as the other two limits of ADR-0031: PostgreSQL raises
`55P03`, Spring translates it to `PessimisticLockingFailureException`, and `ApiExceptionHandler`
answers it beside `QueryTimeoutException`. Nothing is written, as the two numbers of ADR-0031 already
promise, and the client is told to retry.

## Rationale

The limit belongs to the thing it bounds. A transaction deadline says "this request took too long";
a lock timeout says "another write for this user is in flight". Keeping them separate means each can
be sized for its own event, and the narrower one is set tighter than the general one so that the
specific failure is not reported as the generic one.

It is also the limit that protects the pool. Requests waiting on a lock hold a connection while they
wait, so the pool of ten (ADR-0031) can be consumed by a queue on a single user's row. A shorter
deadline on exactly that wait returns those connections sooner, which is a stronger argument for the
narrower limit than the error message is.

`connection-init-sql` applies it to every connection Hikari opens, so it holds for the application,
for Flyway and for the tests, with no code and no per-use-case call. A `lock_timeout` set per
transaction would have been one more statement in each write and one more thing to forget.

## Alternatives considered

- **Keep the transaction deadline as the only limit.** What ADR-0031 does, and it is not wrong: a
  request waiting for a lock does fail, write nothing and answer `503`. It fails after ten seconds
  and reports the generic reason, and it holds a pooled connection for all ten.
- **`SELECT … FOR UPDATE NOWAIT`**, failing immediately instead of queueing. It makes the lock
  useless for its purpose: two submissions arriving together are expected to queue, and one of them
  would be refused for being simultaneous rather than for being wrong.
- **A `lock_timeout` set inside each use case**, as a statement before the lock. Equivalent in
  effect, and it puts an infrastructure concern into every write. Reviewing other public solutions to
  this challenge showed the per-use-case shape; the connection-level setting achieves the same with
  nothing to call.
- **A shorter value, below a second.** Would reject legitimate queues under real concurrent load from
  one account. Three seconds is a deadline for a stuck lock, not a concurrency limiter.

## Consequences

- A request queued behind another write for the same user now fails after three seconds, not ten,
  and releases its pooled connection then.
- `SubmissionLockTimeoutTest` proves it: with a thirty-second transaction deadline and a 300 ms
  `lock_timeout`, a submission blocked by a held lock answers `503` well inside the transaction's
  deadline, and writes nothing. The three `*LockDeadlineTest` classes still cover the other limit,
  with a one-second transaction deadline that fires first.
- The value is one environment variable, `TRADING_DB_LOCK_TIMEOUT`, in the units PostgreSQL accepts.
