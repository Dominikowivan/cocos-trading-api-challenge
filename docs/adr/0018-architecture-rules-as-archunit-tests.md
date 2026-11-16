# ADR-0018: The layering rules are enforced by ArchUnit tests

- **Status:** Accepted
- **Date:** 2026-11-16
- **Constitution:** principle VIII; ADR-0017

## Context

Constitution VIII and [ADR-0017](0017-layers-with-a-service-that-orchestrates.md) fix the shape of the
code: calls go controller → service → persistence, the model is plain Java, entities leave
persistence only through a mapper, the service is the transaction boundary, and request and
response DTOs stay in the controller. Until now nothing checked any of it. A rule that is only
written down holds as long as every change is reviewed against it, and most of the code in this
repository is written by agents, which follow what they read in the code around them more reliably
than what they read in a document.

The portfolio endpoint was the first code those rules could be checked against. Measured on
2026-11-16, it already complied with all of them.

## Decision

`ArchitectureTest` holds the rules as ArchUnit tests, analysing the production classes only:

| Rule | Source |
|---|---|
| The controller layer is accessed by no other layer; the service layer only by the controller; persistence only by the service; the model by the other three | VIII, IX |
| Classes in the model depend only on `java..` and on the model | VIII |
| No class outside persistence depends on a JPA `@Entity` | VIII |
| `@Transactional` appears only on classes and methods in the service layer | ADR-0017 |
| Service and model do not depend on the controller's DTOs | VIII |

The last rule is implied by the first. It is kept on its own so that a DTO leaking into a service
fails with a message naming the DTO rule rather than a generic layer violation.

They run inside `./mvnw verify`, so CI enforces them without changes. ArchUnit is not managed by
the Spring Boot BOM, so its version (1.5.1) is pinned in the pom, as the constitution allows.

Each rule was checked to fail: a planted model class that imported Spring, a DTO and an entity,
and a planted controller class annotated with `@Transactional`, failed all six tests.

## Alternatives considered

- **A Maven module per layer.** The compiler would enforce the direction of calls with no test at
  all. Rejected: four poms and a parent for an exercise with four endpoints, and it only covers
  the direction of calls. That the model is free of Spring, or that `@Transactional` stays in the
  service, would still need a check.
- **Spring Modulith.** It verifies boundaries between business modules (orders, portfolio,
  instruments), not between technical layers inside one. That is a different decision, and this
  codebase has no modules to separate yet.
- **Leave it to code review.** It is what held until now. Rejected for the reason in the context:
  the reviewer would have to read every change against the constitution, and the agents that write
  most of the code would not be stopped by anything.

## Consequences

- A change that breaks the layering fails the build with the name of the rule it broke.
- Some rules of the constitution cannot be checked from bytecode and stay with review: that the
  code carries no comments (VII), that private methods follow the order in which they are called,
  and that an interface has two real implementations (VIII).
- The rules are written against today's packages. A new top-level package (a scheduler, a
  messaging adapter) belongs to no layer and is ignored by the first rule until it is added to it.
