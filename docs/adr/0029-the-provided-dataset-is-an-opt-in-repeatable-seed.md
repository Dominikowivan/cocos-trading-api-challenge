# ADR-0029: The provided dataset is an opt-in repeatable seed, not a versioned migration

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

The provided dataset lived in `src/main/resources/db/seed/V1000__given_dataset.sql`: a **versioned**
Flyway migration, numbered 1000 so it would sort after every schema migration. The default profile
loaded `classpath:db/migration,classpath:db/seed`; the `staging` profile overrode `locations` to
exclude the seed.

Two problems with that, both found while adding `V3` for instrument search.

**1. The seed occupied the version space.** Because `V1000` is versioned, Flyway records it with a
version and treats it as the schema's high-water mark. Adding any new migration afterwards is
therefore *out of order* on a database that already has the seed. Measured: an existing development
database refused to start with

```
Validate failed: Migrations have failed validation
Detected resolved migration not applied to database: 3.
```

This is not specific to `V3`. Every future migration — order submission's `order_status_history`
included — would hit it, on every developer's machine.

**2. The seed was opt-out, not opt-in.** Loading fixture data was the *default*, and safety came
from `staging` remembering to override `locations`. Any new environment, profile or deployment that
did not repeat that override would load the provided dataset into a real database. The guarantee ran
the wrong way round: the dangerous thing was the default, and every environment had to opt out of it.

## Decision

**The dataset is a repeatable migration, and loading it is opt-in through a named `local` profile.**

1. **`R__given_dataset.sql`** instead of `V1000__given_dataset.sql`. A repeatable migration carries
   no version, and Flyway runs repeatables after every pending versioned migration — which is the
   ordering the `1000` was faking, obtained as a property rather than a convention.

2. **It begins with a reset**, so it is genuinely re-runnable:

   ```sql
   TRUNCATE orders, marketdata, instruments, users RESTART IDENTITY CASCADE;
   ```

   Flyway re-runs a repeatable whenever its checksum changes. Without this line the re-run fails on
   `uq_marketdata_instrumentid_date` (measured: the migration rolls back and the application does
   not start). With it, a development database converges on the provided dataset whenever the file
   changes, which is what a fixture should do.

3. **The default profile loads schema only.** `spring.flyway.locations` is
   `classpath:db/migration` in the default document; a `local` profile document adds
   `classpath:db/seed`. The `staging` profile no longer overrides `locations` at all — it inherits
   the safe default, so a new schema location added later reaches staging automatically instead of
   being silently dropped by a stale override.

4. **`local` is activated where, and only where, a disposable database is wanted:**

   | Where | How |
   |---|---|
   | `./mvnw spring-boot:run` | `spring-boot-maven-plugin` `<profiles>` |
   | `docker compose --profile app` | `SPRING_PROFILES_ACTIVE=local` in `compose.yaml` |
   | the test suite | `spring.profiles.active=local` in `maven-surefire-plugin` |

   Each is a context that owns a throwaway database. Nothing else activates it.

### What was measured

- **The original defect is fixed.** With the seed as a repeatable, a database already carrying the
  dataset accepted a newly added `V3`: `Migrating to version "3"`, `now at version v3`, the seed did
  not re-run, data intact (66 instruments, 126 market data rows).
- **The seed sorts last and holds no version.** Fresh database: `V1`, `V2`, `V3`, then
  `repeatable migration "given dataset"`, with `version` `NULL` in `flyway_schema_history`.
- **No other environment loads it.** The packaged jar started with no profile applied only the
  schema migrations; the seed was absent from `flyway_schema_history` and the tables held
  **0 instruments, 0 users, 0 market data rows**.
- **An explicit profile overrides the plugin's `local`.** `-Dspring-boot.run.profiles=foo` activated
  only `foo` and loaded no data, so the README's
  `./mvnw spring-boot:run -Dspring-boot.run.profiles=staging` cannot drag the dataset into the
  provided database. This was the highest-stakes path and it is the one most worth having measured.
- **The suite still gets its data** through the surefire profile: the feature 001 persistence and
  web tests, which assert on seeded figures, pass unchanged.

## Alternatives considered

- **`spring.flyway.out-of-order: true`.** Proposed first, and rejected: it silences the symptom
  while leaving the seed in the version space, and it disables the check for *every* migration — so
  a genuinely out-of-order schema migration would also slip through. It treats a validation that is
  doing its job as the problem.
- **Keep `V1000` and override `locations` per environment.** The status quo. It is what made the
  dataset opt-out, and it is the arrangement that produced both defects.
- **`spring.sql.init.data-locations` instead of Flyway.** Separates data from migrations, which is
  conceptually right, but it re-runs on every start (so the inserts need the same reset anyway), and
  its ordering against Flyway and Hibernate needs `defer-datasource-initialization` to be reasoned
  about. More moving parts for the same outcome.
- **A PostgreSQL container init script** (`/docker-entrypoint-initdb.d`). Runs once per fresh data
  directory, which fits a fixture — but it runs *before* Flyway creates the tables, so it would have
  to carry the schema too, duplicating `V1__given_schema.sql`.
- **Seeding per test instead of per database** (`@Sql` on the test bases). The Testcontainers
  database is shared across the suite, so a per-class seed would re-insert; the current arrangement
  gets "seeded exactly once per fresh database" for free.
- **Activating the profile with `@ActiveProfiles("local")` on each test base** rather than in
  surefire. More explicit per test, but it has to be repeated on every `@SpringBootTest` — there are
  several, including nested ones — and a future test that forgets it fails confusingly. One line in
  the build cannot be forgotten.

## Consequences

- **Every existing development database must be recreated once** (`docker compose down -v`). The
  rename makes Flyway re-run the repeatable against a database that already holds the data, which
  fails on the unique constraint — loudly, with a rollback, not silently. This is unavoidable in any
  option, because `V1000` is already recorded in those histories.
- **Running the application with no profile gives an empty database.** That is the point, but it is
  a change in behaviour: `java -jar` against a fresh database no longer has the dataset. Local work
  goes through `./mvnw spring-boot:run` or `docker compose --profile app`, both of which set the
  profile.
- **The test suite runs under the `local` profile, including in CI.** Read `local` as "a disposable
  database this process owns, preloaded with the provided dataset" — true of a developer's Docker
  database and of a Testcontainers database alike. If `local` ever acquires settings that are about
  a developer's machine rather than about the data, the seed location should move to a profile of
  its own.
- **The dataset still ships inside the production jar** (16,428 bytes at
  `BOOT-INF/classes/db/seed/R__given_dataset.sql`). It can no longer be loaded by accident, but the
  guarantee is now configuration *plus* an explicit profile rather than the artifact not containing
  the file. Moving the seed to `src/test/resources` would make it structural, at the cost of local
  development losing its automatic seed; that is left open deliberately.
- Constitution VI still holds — the dataset lives only under `db/seed` and is not loaded in
  `staging` — and is now stricter than its wording requires. Tightening the wording to "loaded only
  under the `local` profile" is an amendment for `/speckit-constitution`, not a change made here.
