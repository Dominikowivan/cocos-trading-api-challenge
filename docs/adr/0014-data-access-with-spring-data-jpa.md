# ADR-0014: Data access with Spring Data JPA, with hand-written mapping into the model

- **Status:** Accepted
- **Date:** 2026-11-15
- **Research:** [R-001](../../specs/001-portfolio-endpoint/research.md), feature `001-portfolio-endpoint`

## Context

The challenge statement asks explicitly for the data-access strategy to be chosen and justified.
Nothing in the repository recorded one: `spring-boot-starter-data-jpa` arrived with the skeleton
commit and was never argued for, which is an inherited default rather than a decision.

The portfolio endpoint is the first feature that reads anything. It needs two queries — a user's
orders joined to their instrument, and the latest `close` per instrument — and the second cannot be
expressed in JPQL without loading rows that are then discarded
([ADR-0002](0002-orders-as-ledger.md) made every figure a read over the ledger, so this is the
shape of every future read as well). The write features that follow need an insert, an append to
`order_status_history` ([ADR-0005](0005-order-status-history.md)) and a locking read
([constitution IV](../../.specify/memory/constitution.md)), so whatever is chosen here is what they
will use.

## Decision

**Spring Data JPA (Hibernate)** entities for `users`, `instruments`, `orders` and `marketdata`,
with Spring Data repository interfaces in the `persistence` package and **hand-written
mappers** into model types. Reads that JPQL cannot express use `@Query(nativeQuery = true)` on the
same repositories. `spring.jpa.hibernate.ddl-auto: validate` stays on.

Four consequences of the decision are part of it:

- Entities live in `persistence/entity` and **never cross into the model**
  ([constitution VIII](../../.specify/memory/constitution.md),
  [ADR-0017](0017-layers-with-a-service-that-orchestrates.md)). The service calls a repository
  and hands the result to a mapper in `persistence/mapper`; the model receives `Order`,
  `InstrumentRef` and `BigDecimal`.
- The ids on the entities are `Integer`, because the given schema's keys are `SERIAL` and `INT`.
  The model speaks `long`; the narrowing is the mapper's job (`UserMapper`), and a user id outside
  the `INT` range is simply a user that cannot exist in this schema.
- Enumerated string columns (`side`, `status`, `type`) are mapped by `valueOf`, so an unrecognised
  value fails loudly at mapping time instead of being folded with a guessed sign.
- **A native query returns a typed interface projection**, never `Object[]`: the latest-close query
  returns `List<LatestClose>` with `getInstrumentId()` and `getClose()`. Its column aliases are
  double-quoted (`AS "instrumentId"`), because PostgreSQL folds unquoted identifiers to lowercase
  and the projection is matched by name. Rejected: `List<Object[]>`, which was the first
  implementation — positional indexes and casts the compiler cannot check;
  `jakarta.persistence.Tuple`, the same problem by name instead of position; `@SqlResultSetMapping` onto a DTO, the same result
  with more annotation; and `JdbcClient` for that one query, which would add a second data-access
  style for a single read.

## Alternatives considered

- **`JdbcClient` with explicit SQL and row mappers.** The most direct expression of *this*
  feature: two queries, two mappers, no session, no proxies. Rejected because the economy does not
  survive the write path — order submission would hand-write its insert, its history insert and its
  locking read — and because the mapping layer would then exist twice, once per access style. The
  wrong place to economise.
- **Spring Data JDBC.** Lighter than JPA, still a repository abstraction, and its aggregate model
  (an order root with its status history as a child collection) fits ADR-0005 well. It supports
  pessimistic locking through `@Lock` on a derived query method, so locking is not the
  discriminator. Rejected on two narrower points: `@Lock` is documented as ignored on string-based
  `@Query` methods, which is the shape a `SELECT … FOR UPDATE` over a user's ledger takes, and it
  offers no startup validation of the mapping against the schema. The closest call of the four, and
  the one to revisit if JPA's session semantics cause trouble on the write path.
- **jOOQ.** Type-safe SQL and the best fit for query-heavy work; it is managed by the Spring Boot
  BOM, so an unmanaged dependency is not an argument against it. Rejected on build shape: the
  type-safe API comes from code generation, which needs the schema available during the build, so
  CI must apply the migrations to a database before compiling. That is real upkeep for four tables.
  Its compile-time schema check is *stronger* than `ddl-auto: validate`, which makes this a trade
  rather than a dismissal.
- **JPA with derived-query methods only, no native SQL.** Rejected because the latest-close
  resolution ([R-002](../../specs/001-portfolio-endpoint/research.md)) cannot be expressed in JPQL
  without reading price history that is immediately thrown away.

## Consequences

- **The mapping is checked against the real schema at startup**, in local runs, in every
  Testcontainers test and on staging. It earned its keep immediately: `Long` ids were rejected
  against `SERIAL` columns before a single query ran, which a row-mapper approach would have
  discovered at the first query, or never.
- One abstraction covers both query shapes. The awkward query is native SQL on the same repository,
  so there is no second mapping layer for it.
- The cost usually levelled at JPA — entities leaking into business logic, lazy loading surprising
  a caller — is bounded by constitution VIII and by the hand-written mappers: every model object
  is fully built, inside the service's transaction, before the model sees it, so no model code can
  touch a proxy.
- **N+1 is now a property to test, not to hope for.** The ledger query fetch-joins the instrument,
  and the persistence test asserts one prepared statement per read rather than inspecting the SQL
  by eye.
- For this feature alone the choice barely matters — it is two queries and a hand-written mapping
  either way. It is recorded because it binds the write features, and because the alternatives are
  strong enough that a reviewer deserves the reason rather than the default.
