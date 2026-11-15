<!--
Sync Impact Report
- Version change: 1.1.2 → 1.2.0 (MINOR: materially expanded guidance on the challenge statement)
- Modified sections:
  - Technology and Scope Constraints — adds the challenge statement
    (docs/challenge/backend-challenge.md) as the verbatim source of requirements; departures
    from it now require an ADR as well as a README note; requirements from the statement MUST
    NOT be restated in this constitution. Closes the gap in VII, which required departures to be
    stated in the README but did not say where the statement lives.
- Modified principles: none
- Added sections: none
- Removed sections: none
- Templates: not modified (they read this file at runtime)
- Deferred TODOs: none

Previous report (1.1.2, 2026-11-15)
- Version change: 1.1.1 → 1.1.2 (PATCH: clarifies the existing ADR workflow)
- Modified principles:
  - VII. Code Carries No Comments; Reasoning Lives in Documents — adds the rule that adding or
    superseding an ADR updates the index in docs/adr/README.md in the same change; rationale
    extended accordingly
- Added sections: none
- Removed sections: none
- Templates: not modified (they read this file at runtime)
- Deferred TODOs: none

Previous report (1.1.1, 2026-11-15)
- Version change: 1.1.0 → 1.1.1 (PATCH: rationale for versioning `.claude/` stated in
  Development Workflow; no rule changed)

Previous report (1.1.0, 2026-11-15)
- Version change: 1.0.0 → 1.1.0 (MINOR: two principles added)
- Added principles:
  - VIII. Ports and Adapters
  - IX. Design for Change
- Modified principles: none
- Removed sections: none
- Templates: not modified (they read this file at runtime)
- Deferred TODOs: none

Previous report (1.0.0, 2026-11-15)
- Version change: (unfilled template) → 1.0.0
- Principles defined (all new):
  - I. The Ledger Is the Single Source of Truth
  - II. Money Is Exact
  - III. Business Logic Lives in Java, Not in the Database
  - IV. Order State Is Auditable and Race-Free
  - V. Tests Run on the Real Engine, Never on Staging
  - VI. Schema Changes Only Through Migrations; Staging Is Protected
  - VII. Code Carries No Comments; Reasoning Lives in Documents
- Added sections: Technology and Scope Constraints; Development Workflow; Governance
- Removed sections: none
- Sources: README.md, docs/adr/, docs/journal/
- Templates: not modified (they read this file at runtime)
- Deferred TODOs: none
-->

# Cocos Trading API Constitution

## Core Principles

### I. The Ledger Is the Single Source of Truth

- `orders` is the ledger. Positions, cash balance and available cash MUST be computed from it
  when read; no positions table, balance column or other stored copy of derived state may be
  added.
- Exactly one domain service owns the fold over the ledger. The portfolio endpoint and order
  validation MUST both consume it; computing available cash a second way is forbidden.
- Available cash is the balance minus everything reserved by `NEW` orders (cash for `BUY`,
  shares for `SELL`). Order validation MUST check against available cash and available holdings,
  never against the raw balance.
- Cost basis is weighted average cost. Changing the method requires a new ADR.

Rationale: a second source of truth for money diverges silently. See ADR-0002, 0003, 0004.

### II. Money Is Exact

- Amounts, prices and quantities that take part in money arithmetic MUST be `BigDecimal`.
  `double` and `float` MUST NOT appear in any money path, including DTOs and tests.
- `BigDecimal` values MUST be compared with `compareTo`, never with `equals`.
- Every division and every rescale MUST state its scale and `RoundingMode` explicitly.

Rationale: binary floating point cannot represent decimal currency, and `equals` treats `1.0`
and `1.00` as different values.

### III. Business Logic Lives in Java, Not in the Database

- Business rules (validation, the ledger fold, reservations, returns, state transitions) MUST be
  implemented in Java domain code.
- Triggers, stored procedures and functions MUST NOT carry business rules. SQL is for reading,
  writing and aggregating data; schema constraints that protect integrity are allowed.

Rationale: logic in the database cannot be unit-tested in milliseconds, is invisible from the
code, and splits one rule across two languages.

### IV. Order State Is Auditable and Race-Free

- Every order state transition MUST insert a row into `order_status_history` in the same
  transaction that updates `orders.status`. History rows are never updated or deleted.
- A rejection MUST record a machine-readable reason (no funds, no holdings, no price).
- Only `NEW` orders can be cancelled; cancelling MUST NOT release a reservation twice.
- Reading available cash or holdings and inserting the order that consumes them MUST happen
  under a lock or a serialisable transaction, so two concurrent requests cannot both spend the
  same funds. Submission and cancellation are both covered by this rule.

Rationale: support and compliance need the path of an order, and validation without
serialisation protects nothing. See ADR-0004, 0005, 0006.

### V. Tests Run on the Real Engine, Never on Staging

- Domain logic MUST be testable as plain Java, with no database and no Spring context.
- Tests that need a database MUST use Testcontainers with `postgres:18.7`, built from the same
  Flyway migrations and seed as local development. H2, embedded databases and mocked repositories
  MUST NOT stand in for PostgreSQL in persistence or functional tests.
- No test may connect to the provided (staging) database.
- Every feature MUST ship with tests for its rejection paths, not only its happy path.

Rationale: a green suite on a different engine is evidence about the wrong system.
See ADR-0010.

### VI. Schema Changes Only Through Migrations; Staging Is Protected

- Every schema change MUST be a new Flyway migration under `db/migration`. `V1__given_schema.sql`
  is immutable.
- The provided dataset lives only under `db/seed` and MUST NOT be loaded in `staging`.
- The provided database is staging. It is reached only through the explicitly named `staging`
  profile; no default, test or CI configuration may point at it.
- Credentials MUST come from the environment and MUST NOT be committed.

Rationale: changes reach staging when they are ready, reproducibly, and never by accident.
See ADR-0009, 0011.

### VII. Code Carries No Comments; Reasoning Lives in Documents

- Source code MUST NOT contain comments, Javadoc included. Names and structure carry intent;
  whatever still needs explaining goes in the README, a spec, or an ADR.
- A decision that had alternatives worth weighing MUST be recorded as an ADR. Accepted ADRs are
  never edited; a change of decision is a new ADR marking the old one `Superseded by ADR-00XX`.
- Adding or superseding an ADR MUST update the index in `docs/adr/README.md` in the same change:
  its row, its title and its status.
- `docs/journal/` is append-only and written by the author. Agents MUST NOT rewrite it.
- Any deliberate departure from the literal challenge statement MUST be stated in the README.

Rationale: the exercise evaluates the reasoning, so it must live where reviewers read it and
stay true to the order in which it happened. The ADR index is how a reader finds the
decisions, so an ADR missing from it is an ADR nobody will read.

### VIII. Ports and Adapters

- The domain package MUST NOT import Spring, Jakarta, JPA or any web or persistence type. It
  depends on interfaces it declares itself.
- Persistence and HTTP are adapters: JPA entities and request/response DTOs live in the adapter
  layer and MUST NOT leak into domain signatures.
- A port is introduced when there is a real boundary (persistence, HTTP, time, external
  services), not for every class. An interface with exactly one implementation and no boundary
  behind it MUST NOT be created.
- Mapping between JPA entities and the domain model is written by hand in the adapter.

Rationale: principle V already requires the domain to be testable without Spring or a database;
this is the structure that makes it true instead of aspirational.

### IX. Design for Change

- Names state intent; a reader MUST NOT need a comment to know what something does (see VII).
- Objects expose behaviour, not state: callers ask an object to do something rather than pulling
  its data to decide for them.
- A rule exists in exactly one place. Duplicated business logic MUST be extracted; duplicated
  structure MAY be left alone.
- Each class has one reason to change, and dependencies point toward the domain.
- New abstraction is justified by a second real use, not by an anticipated one.

Rationale: the cost of this codebase is in the changes it will need later (a matching engine,
authentication, a snapshot of the ledger); code that keeps each rule in one place and each
dependency pointing inward absorbs them without rewrites.

## Technology and Scope Constraints

- Java 21, Spring Boot 4.1.2, Maven wrapper. Dependency versions come from the Spring Boot BOM;
  an explicit version is allowed only for what the BOM does not manage.
- PostgreSQL 18.7 in every environment: local, tests and staging.
- The API is versioned under `/api/v1`. Its endpoints are portfolio, instrument search, order
  submission and order cancellation. A new endpoint requires a spec and, if it had alternatives,
  an ADR.
- Out of scope by decision (ADR-0007): no matching engine and no authentication. A `LIMIT` order
  is born `NEW` and leaves it only by cancellation; a `MARKET` order fills at the last close.
  Features MUST NOT assume `LIMIT` orders stay `NEW` forever.
- Without authentication the user id is untrusted input: every query MUST be scoped by it.
- The challenge statement in `docs/challenge/backend-challenge.md` is the source of
  requirements, kept verbatim. Where this constitution or the implementation departs from it,
  the departure MUST be recorded in an ADR and stated in the README. This constitution MUST NOT
  restate requirements from the statement: the statement is input, not a rule, and a copy here
  would drift from it.

## Development Workflow

- Features go through Spec Kit: specify, plan, tasks, implement. Small changes that do not need
  an agent may be made by hand; for those, the code and git history are the documentation.
- Every plan MUST pass a Constitution Check against these principles before implementation, and
  any violation MUST be justified in the plan's complexity tracking or the plan changed.
- CI (GitHub Actions) MUST be green before merging: full build, full suite including
  Testcontainers tests. JaCoCo coverage is reported, not gated.
- Agent tooling under `.specify/` and `.claude/` is versioned configuration, so every developer
  works with the same skills and what they produce stays coherent. Machine-local agent state
  (`.agent-home/`) and generated artifacts are not committed.

## Governance

- This constitution supersedes any other practice in the repository. Where it conflicts with an
  accepted ADR, the conflict MUST be resolved in the same change: either the constitution is
  amended or a new ADR supersedes the old one.
- Amendments are made through `/speckit-constitution` in a reviewed commit, with a Sync Impact
  Report at the top of this file.
- Versioning is semantic: MAJOR for removing or redefining a principle, MINOR for a new principle
  or materially expanded guidance, PATCH for wording and clarifications.
- Compliance is checked at plan time (Constitution Check) and at review. Runtime guidance for
  developers lives in `README.md` and `docs/adr/`.

**Version**: 1.2.0 | **Ratified**: 2026-11-15 | **Last Amended**: 2026-11-15
