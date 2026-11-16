# ADR-0020: Static analysis on SonarQube Cloud, gating develop

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

ArchUnit ([ADR-0018](0018-architecture-rules-as-archunit-tests.md)) checks the layering and the
formatter ([ADR-0019](0019-spring-java-format-checked-in-the-build.md)) the layout. Neither looks
at what the code does: a possible null dereference, a resource left open, a duplicated block, a
security hotspot, test coverage on what changed. The aim is that issues of that kind are caught when
they are introduced, not accumulated and cleaned up later.

The repository is private until delivery. On SonarQube Cloud's Free plan, as documented on
2026-11-16, a private project of up to 50k lines (this one has about 1,850) gets analysis of its
main branch only, and of pull requests only when they target the main branch. Custom quality gates
are not available, and dependency vulnerability scanning (SCA) is a separate paid product.

The branch model is `feature/*` → `develop`, with `main` kept empty until delivery.

## Decision

- The project is analysed on **SonarQube Cloud**, and its main branch there is **`develop`**, the
  branch features are merged into. Every pull request into `develop` and every push to it is
  analysed.
- The analysis is a step of the existing CI job, after `./mvnw verify`, using the
  `sonar-maven-plugin` (5.8.0, pinned in the pom because the Spring Boot BOM does not manage it).
  It reads sources, compiled classes and the JaCoCo report the build has already produced, so
  nothing is built twice.
- It runs with `sonar.qualitygate.wait=true`: if the quality gate fails, CI fails. The gate is the
  built-in *Sonar way*, which judges new code (no new issues, at least 80% coverage, at most 3%
  duplication, every security hotspot reviewed).
- Organization and project keys live in the pom; the token is the `SONAR_TOKEN` repository secret.

## Alternatives considered

- **Error Prone, or Checkstyle, in the build.** They run inside `./mvnw verify` with no account
  and no service, so a reviewer gets the same result on a clone. Error Prone works at compile time
  and catches bug patterns; Checkstyle checks conventions. Neither tracks coverage or duplication
  on new code, and neither gives a gate over what a change introduced.
- **SonarQube Cloud's automatic analysis.** It needs no CI configuration, but it does not import
  coverage, and without compiled classes its Java analysis is less precise.
- **A separate workflow for the analysis.** Common in examples, it builds and runs the tests a
  second time to produce what the CI job already has.
- **The generic `sonarqube-scan-action` with a `sonar-project.properties`.** For Maven, the
  plugin takes paths from the pom; a properties file repeats them by hand and drifts.
- **A self-hosted SonarQube Community Build.** No plan limits, but it also analyses one branch only,
  and a reviewer could not see its results.

## Consequences

- A pull request into `develop` that introduces an issue, leaves new code under 80% coverage or
  adds duplication fails CI, and the issues show up in the pull request.
- Pushes to `feature/*` branches are built and tested but not analysed: the Free plan rejects
  branch analysis for private projects. The pull request is where a feature gets analysed.
- **Vulnerable dependencies are not covered.** SCA is not part of the Free plan; that needs its own
  tool.
- When the repository becomes public the plan limits no longer apply: every branch can be
  analysed, and the dashboard can be linked from the README.
- The quality gate cannot be tuned on this plan. Where *Sonar way* disagrees with a decision of this
  project, the issue is marked in SonarQube Cloud with the reason, not suppressed in code
  (constitution VII rules out suppression comments).
