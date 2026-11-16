# ADR-0031: Database access has a deadline and a bounded pool, and running out of either is a 503

- **Status:** Accepted
- **Date:** 2026-11-16
- **Journal:** [06Refactors](../journal/06Refactors.md)

## Context

No request had a time limit. Measured on the local database (2026-11-16): `statement_timeout` and
`idle_in_transaction_session_timeout` are both `0`, unlimited; on staging they are not measured. A
query that blocks — on a lock, on a slow plan, on a database under load — holds its connection
for as long as it blocks, and the pool behind it has ten. Instrument search is called on every
keystroke, so ten stuck requests are enough to make every other one wait on `getConnection()` for
Hikari's default 30 seconds, and then fail as a `500`.

The pool's settings were already bindable from the environment through Spring Boot, but nothing in
the repository said they existed or what they were set to.

## Decision

**Every request's transaction has a deadline; the pool's size and wait are declared; running out of
either answers `503` with a problem detail.**

| Property | Default | Environment variable |
|---|---|---|
| `spring.transaction.default-timeout` | `10s` | `SPRING_TRANSACTION_DEFAULT_TIMEOUT` |
| `spring.datasource.hikari.maximum-pool-size` | `10` | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` |
| `spring.datasource.hikari.connection-timeout` | `5000` ms | `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` |

- **10 seconds** for the transaction: the author's figure, 2026-11-16. Both endpoints are reads
  that take milliseconds today, so the deadline only ever fires on something abnormal.
- **5 seconds** for the connection wait, down from Hikari's 30: a caller waiting longer than that
  for a search box is better served by an answer saying "retry".
- **10 connections**, which is Hikari's default, now written down. It is the number that has to be
  checked against the database: pool size times instances must stay below `max_connections` (100
  on the local database; staging not measured).
- `connection-timeout` is a plain number of milliseconds. Hikari binds it to a `long`, so `5s`
  fails at startup — found that way.

### Why the transaction timeout and not `statement_timeout`

Both services already run each request as one `@Transactional` read, so Spring's transaction
timeout covers the whole request — the two statements of a search, the three of a portfolio — as
one deadline. Spring hands the time remaining to each JDBC statement as its query timeout, and the
driver cancels it in the database: the test log shows
`ERROR: canceling statement due to user request`, so the query really stops, rather than the
application merely giving up on it.

`statement_timeout` set on the connection would apply to **Flyway** too, which uses the same data
source. A migration that validates a constraint over a large table on staging would be cut off in
the middle of a deploy. The transaction timeout never touches Flyway.

### Why 503

The request was valid and nothing was changed; the server could not serve it now. That is what
`503` says, and it tells a client the same request may succeed if retried — which a `500` does
not. Three exceptions map to it in `ApiExceptionHandler`:

| Exception | When |
|---|---|
| `QueryTimeoutException` | a statement was cancelled at the deadline |
| `TransactionTimedOutException` | the deadline had already passed before the next statement |
| `CannotCreateTransactionException` | no connection came free within `connection-timeout`, or the database is unreachable |

Each is logged at `WARN`, because a 503 is an operational event someone should see. The response
detail says nothing about the cause: a caller does not need to know about pools.

## Tested

`DatabaseUnavailableTest`, against a real PostgreSQL:

- **Deadline** (`default-timeout=1s`): a second connection takes an `ACCESS EXCLUSIVE` lock on
  `instruments` (search) or `orders` (portfolio); the request blocks on it, is cancelled after one
  second, and answers `503`.
- **Pool** (`maximum-pool-size=1`, `connection-timeout=250`): the test holds the only connection;
  the request cannot get one and answers `503`.

Every response is validated against the contract (ADR-0024), so `503` is declared on both
endpoints as a shared `ServiceUnavailable` response.

## Alternatives considered

- **`statement_timeout` on the connection** (through `options=-c statement_timeout=…`). Rejected
  for Flyway, above.
- **`jakarta.persistence.query.timeout`.** Per statement, not per request: a request of three
  statements could take three times the limit.
- **Keeping the pool undeclared.** It already worked from the environment, but an operator cannot
  turn a dial nobody told them about.
- **`Retry-After` on the 503.** Nothing measures how long an overload lasts, so any value would be
  invented.
