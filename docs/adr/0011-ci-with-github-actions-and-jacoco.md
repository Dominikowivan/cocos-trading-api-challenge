# ADR-0011: CI on GitHub Actions, with coverage reported

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [02Setup](../journal/02Setup.md)

## Context

A build that only runs on the author's machine proves nothing about the repository. That matters
more than usual here, because the tests depend on Docker and on migrations rather than on
application code alone.

## Decision

GitHub Actions on every push and pull request: build, run the full suite including the
Testcontainers tests, and publish the JaCoCo coverage report as an artifact.

JaCoCo carries an explicit version because the Spring Boot BOM does not manage that plugin.
Nothing else in the POM declares one.

## Alternatives considered

- **No CI at all.** Defensible for an exercise this size. Discarded because proving the suite
  passes somewhere other than the author's laptop is exactly what the tests depend on being able
  to show.
- **Mocking the database in CI** to avoid Docker. Discarded for the reasons in
  [ADR-0010](0010-testcontainers-with-postgresql-18.md); GitHub runners provide Docker, so there
  is nothing to work around.

## Consequences

- Each run pays the container startup time. At this size, seconds.
- **CI never uses the `staging` profile and holds no credentials for the provided database.** A
  pipeline able to reach that database would undo the protection in ADR-0009.
- Coverage is reported, not enforced. No threshold gate is configured: on a codebase this small a
  percentage is easy to game and says little about whether the risky logic is covered.
