# ADR-0010: Tests and local development run on the same PostgreSQL as staging

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [02Setup](../journal/02Setup.md)

## Context

The provided database is the environment this solution is evaluated against
([ADR-0009](0009-flyway-and-provided-database-as-staging.md)). Everything that runs before that —
the test suite and local development — should run on the closest thing to it, so that what passes
here is what happens there.

It runs **PostgreSQL 18.7**, which was measured rather than assumed.

## Decision

Tests run against a disposable PostgreSQL container, created by Testcontainers from the same
migrations and the same dataset as the local database, and pinned to the same version as staging:
`postgres:18.7`. Local development runs that same image.

One engine and one version across development, tests and staging.

## Alternatives considered

- **H2 in memory.** Faster to start and the usual choice. H2 does have a PostgreSQL compatibility
  mode, so the objection is not that it cannot be configured to behave like one — it is that the
  compatibility is a partial emulation. It is a different engine, and what it does not cover
  diverges **silently**: rounding of `NUMERIC(10, 2)`, identifier folding and anything
  PostgreSQL-specific in a query produce a green suite here and different behaviour there. A test
  that passes against an engine other than the one in production is evidence about the wrong
  system.
- **Running the tests against the provided database.** It is the real engine, but it makes the
  suite slow and not reproducible, and it leaves test rows in the database the exercise is
  evaluated on.

## Consequences

- The suite needs Docker, and each run pays a few seconds to start a container.
- Something that matters more than the choice itself: **most tests should not touch a database at
  all.** The domain logic — the fold over the ledger, return, positions — is plain Java and is
  tested as such, in milliseconds. Only the functional test the statement asks for and the
  repository tests need an engine, and those get the real one.
- Pinning an exact version means it has to be revisited if staging is upgraded. That is the point:
  the alternative is a drift nobody notices.
