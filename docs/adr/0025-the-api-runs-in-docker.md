# ADR-0025: The API runs in Docker with one command

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

Running the API took a JDK 21 and Maven, through `./mvnw spring-boot:run`, with the database in
Docker. Someone who only wants to call the endpoints, such as a reviewer of this exercise, had to
install a Java toolchain first. Docker is already a requirement, for the local database and for
Testcontainers.

## Decision

- A **multi-stage `Dockerfile`**. The first stage builds the jar with the Maven wrapper on a JDK
  image; the second runs it on a JRE image, as a non-root user. Tests are skipped in the image
  build: they need Docker themselves (Testcontainers), and CI runs them on every push.
- Both base images are **Eclipse Temurin 21.0.12.1 on Ubuntu Noble, pinned by tag and digest**,
  the same JDK as local development. A tag can be moved; a digest cannot.
- The Maven repository is a **BuildKit cache mount**, so rebuilding after a code change does not
  download every dependency again.
- `.dockerignore` **allows only what the build needs** (`mvnw`, `.mvn`, `pom.xml`, `src`), so the
  build context carries no `.git`, IDE files, documents or `target/`.
- `compose.yaml` gets an `app` service behind the **`app` profile**, pointing the datasource at the
  `postgres` service. `docker compose --profile app up --build` starts both; plain
  `docker compose up -d` still starts only the database, so the development flow does not change and
  does not compete for port 8080 with `./mvnw spring-boot:run`.

## Alternatives considered

- **`spring-boot:build-image` (Cloud Native Buildpacks).** No Dockerfile to maintain, but it is run
  through Maven, so it needs the JDK this decision is meant to make unnecessary.
- **A Dockerfile that copies a jar built on the host.** Shorter, but it needs the JDK on the host
  again, and the image depends on whatever is in the local `target/`.
- **Alpine-based images.** Smaller, but musl instead of glibc, a difference nothing here needs to
  take on.
- **Spring Boot's layered jar.** Splitting dependencies and application into separate image layers
  makes rebuilds of the runtime image cheaper. It was not measured to matter for an image that is
  built locally and not pushed anywhere, so the single jar stays.

## Consequences

- The API can be tried with Docker alone.
- The image is not built in CI and not pushed to a registry. It is a way to run the exercise, not a
  deployment artifact; Trivy scans the dependencies the image contains, not the image's own
  operating system packages.
- The app container has no health check yet. One comes with the observability work.
