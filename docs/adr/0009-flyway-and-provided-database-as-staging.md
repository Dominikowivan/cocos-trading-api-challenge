# ADR-0009: Flyway migrations, and the provided database treated as staging

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [02Setup](../journal/02Setup.md)

## Context

The schema is given and already populated, and this solution needs to change it: it adds
`order_status_history` ([ADR-0005](0005-order-status-history.md)) and indexes. So changes have to
reach a database that already exists, without being applied by hand and without being applied by
accident.

The provided database is not a development environment. It is where the exercise is evaluated —
the equivalent of staging or pre-production: changes arrive there when they are ready, not while
they are being tried out.

## Decision

Flyway, with the given DDL transcribed as `V1__given_schema.sql` and every change after it as a
new migration. Two environments:

| | Database | Flyway | Seed data |
|---|---|---|---|
| Default | local PostgreSQL in Docker | runs | loaded |
| `staging` | the provided database | runs, with `baseline-on-migrate` | never loaded |

Nothing reaches the provided database unless the `staging` profile is named explicitly. Forgetting
a flag is not what should protect someone else's data.

`baseline-on-migrate` with `baseline-version: 1` is what makes this work: that database already
contains the original schema, which is exactly what `V1` describes, so Flyway records it as
version 1 and applies only what came after.

**`V1` is immutable from here on.** The baseline assumes it still describes what is in staging;
editing it would break that assumption silently.

The provided dataset lives in a separate Flyway location (`db/seed`), so it is a fixture rather
than part of the schema.

## Alternatives considered

- **Applying the changes by hand on the provided database.** Fast, unrepeatable, and impossible to
  review. Discarded: the whole point is that the change is documented and applied the same way
  everywhere.
- **Developing directly against the provided database.** Discarded: experiments would leave rows
  behind in the database the exercise is evaluated on.
- **Liquibase.** Equivalent for this purpose; Flyway's plain-SQL migrations are closer to the DDL
  that was handed over, so the transcription is literal and reviewable.

## Consequences

- The database can be rebuilt from zero, which is what makes the test suite reproducible
  ([ADR-0010](0010-testcontainers-with-postgresql-18.md)).
- A migration history table is created in staging the first time the profile is used. That run is
  done deliberately and watched, not discovered by a reviewer.
- Boot 4 note, learned the expensive way: Flyway's autoconfiguration lives in
  `spring-boot-starter-flyway`. With a bare `flyway-core` dependency the application starts
  normally, migrates nothing, and reports no error at all.
