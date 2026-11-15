# ADR-0001: Java 21 and Spring Boot 4.1.2

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [01PrimeraLectura](../journal/01PrimeraLectura.md), *Mail en el cual se envió el challenge*

## Context

The language is not a decision: the invitation email asks for Java explicitly, even though the
public repository frames the exercise in Node.js. What is a decision is which Java and which
framework version to build on, and that choice is made before a single line is written.

## Decision

Java 21 on Spring Boot 4.1.2, built with the Maven wrapper so the build needs nothing installed
beyond a JDK.

## Alternatives considered

- **Java 17.** Still supported, and the safer-looking option. Discarded because 21 is the LTS this
  work already runs on, and nothing in the exercise needs the extra compatibility.
- **Java 25.** Newer, with no benefit here that would justify arguing for it.
- **Spring Boot 3.x.** Not offered: Spring Initializr serves 4.0.x and 4.1.x only, so 4.1.2 is the
  current stable release rather than an early adopter's choice.

## Consequences

- Records and pattern matching are available for DTOs, which removes most of the boilerplate that
  would otherwise justify an annotation processor.
- Boot 4 moved several starters: `spring-boot-starter-webmvc` replaces `spring-boot-starter-web`,
  and Flyway autoconfiguration now lives in `spring-boot-starter-flyway`. Documentation and
  examples written for Boot 3 do not transfer cleanly, and at least one of those differences fails
  silently (see [ADR-0009](0009-flyway-and-provided-database-as-staging.md)).
- Dependency versions come from the Boot BOM. Only what the BOM does not manage carries an
  explicit version, which is why JaCoCo does and nothing else does.
