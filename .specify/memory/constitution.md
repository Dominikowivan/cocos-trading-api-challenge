<!--
Sync Impact Report
- Version change: 2.2.0 → 2.3.0 (MINOR: materially expanded guidance)
- Modified principles:
  - VIII. Layers — no package becomes a catch-all: `service` is grouped by use case, each
    `@Service` in its own subpackage with its request and response models, exceptions and
    configuration, nothing at the root, use cases independent of each other, enforced by three
    ArchUnit rules; the controller hands the service a raw request and never reads configuration;
    `model` by concept, `controller` and `persistence` by role. Rationale extended
- Modified sections: none
- Added sections: none
- Removed sections: none
- Templates: not modified (they read this file at runtime)
- Origin: instrument search moved its request value types out of `model/instrument` (they are
  paging and validation, not business) and they were about to pile up flat in `service` next to
  every service; the author reorganised by hand and asked for the rule. Revised the same day,
  before release, after review: the first draft kept each `@Service` at the root of `service`
  and its models in a sibling subpackage, which split every use case from its own request and
  response, and left the controller reading configuration to validate the request
- Deferred TODOs: none

Previous report (2.2.0, 2026-11-16)
- Version change: 2.1.0 → 2.2.0 (MINOR: materially expanded guidance)
- Modified principles: none
- Modified sections:
  - Technology and Scope Constraints — the API contract lives in `docs/api/openapi.yaml`, one file
    for the whole API, and every response in the web tests validates against it (ADR-0024). A
    feature's `specs/NNN/contracts/` is its design-time proposal, not edited once implemented; a
    feature that changes the API applies the change to `docs/api/openapi.yaml` and
    `docs/api/trading-api.http`
  - Development Workflow — the CI rule lists what a green build now includes (ArchUnit,
    formatting, Trivy) and the SonarQube Cloud gate on pull requests. Replaces "JaCoCo coverage is
    reported, not gated", which ADR-0020 had contradicted: the gate requires 80% coverage on new
    code
- Added sections: none
- Removed sections: none
- Templates: not modified (they read this file at runtime)
- Origin: the portfolio's contract lived only in its spec, where nothing validated it and where a
  later feature changing the endpoint would have had to rewrite a historical document
- Deferred TODOs: none

Previous report (2.1.0, 2026-11-15)
- Version change: 2.0.0 → 2.1.0 (MINOR: materially expanded guidance)
- Modified principles:
  - V. Tests Run on the Real Engine — every behaviour a feature adds MUST be tested at the level
    where it lives; tests are part of the definition of done, never optional; test-first is
    allowed, not required. Closes the gap between "testable" and "tested", which let Spec Kit's
    default ("tests are optional unless requested") apply
  - IX. Design for Change — "new abstraction needs a second real use" narrowed to extension points
    (a value type naming a domain concept, such as `Closes`, needs none); a method never takes an
    `Optional`. Also adds the function-level rule (one thing, one level of abstraction, a
    public method reads as the summary of its steps, steps below in call order) and the rule that a
    structure chosen for performance must cite a measurement; rationale extended
- Modified sections:
  - Development Workflow — design documents specify contracts and rules, not the shape of the
    code; every user story ends with a refactor task reviewed against IX
- Added sections: none
- Removed sections: none
- Templates: the project override `.specify/templates/overrides/tasks-template.md` adds a refactor
  task per story and marks tests as required; the `speckit-tasks` skill's "tests are optional"
  rule is replaced with a pointer to principle V. Both are re-applied by hand after a Spec Kit
  upgrade
- Origin: the first `AccountLedger` was one interleaved loop because research R-004, ADR-0015 and
  data-model prescribed "one pass"; it passed the Constitution Check because nothing in IX spoke
  at method level, and no refactor step existed without TDD
- Deferred TODOs: none

Previous report (2.0.0, 2026-11-15)
- Version change: 1.2.0 → 2.0.0 (MAJOR: principle VIII redefined)
- Modified principles:
  - VIII. Ports and Adapters → VIII. Layers: Controller, Service, Model, Persistence. The service
    orchestrates persistence and model; the model holds no reference to persistence (no
    repositories, no ports) and returns what it detects as values. Persistence is Spring Data
    repositories over JPA entities with hand-written mappers. Decision recorded in ADR-0017.
  - I. The Ledger Is the Single Source of Truth — "one domain service owns the fold" becomes "one
    model object owns the fold", fed by the service
  - III, V — "domain code/logic" reads "model code/logic"
  - IX. Design for Change — dependency direction restated for layers; rationale adjusted
- Added sections: none
- Removed sections: none
- Templates: not modified (they read this file at runtime)
- Follow-up: feature 001 plan, research and data-model, and ADR-0015, cite the old shape and are
  updated in the same change
- Deferred TODOs: none

Previous report (1.2.0, 2026-11-15)
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
- Exactly one model object owns the fold over the ledger: the service reads the entries and hands
  them to it. The portfolio endpoint and order validation MUST both obtain their figures from it;
  computing available cash a second way is forbidden.
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
  implemented in Java model code.
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

- Model logic MUST be testable as plain Java, with no database, no Spring context and no test
  doubles: a model test builds its input data and asserts on the returned value.
- Tests that need a database MUST use Testcontainers with `postgres:18.7`, built from the same
  Flyway migrations and seed as local development. H2, embedded databases and mocked repositories
  MUST NOT stand in for PostgreSQL in persistence or functional tests.
- No test may connect to the provided (staging) database.
- Every behaviour a feature adds MUST be covered by tests at the level where it lives: model
  rules in plain Java, persistence and HTTP on Testcontainers. Tests are part of a story's
  definition of done and are never optional; writing them first is allowed, not required.
- Every feature MUST ship with tests for its rejection paths, not only its happy path.

Rationale: a green suite on a different engine is evidence about the wrong system, and code with
no test is a claim nobody checked.
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

### VIII. Layers: Controller, Service, Model, Persistence

- A request flows controller → service. The service is the only layer that orchestrates: it reads
  through persistence, maps what it read into model objects, asks the model to compute, performs
  the side effects (writing, logging) and returns model objects to the controller.
- The model is plain Java. It MUST NOT import Spring, Jakarta, JPA or any web or persistence type,
  and MUST NOT hold a reference to a repository, a service or any interface standing in for one. It
  receives data and returns data; what it detects (an anomaly, a rejection reason) it returns as
  a value for the service to act on.
- Persistence is Spring Data repositories over JPA entities. Entities leave the persistence
  package only through a mapper; mapping between entities and the model is written by hand.
- The controller only translates HTTP: it reads the request, calls one service method and maps
  the returned model into a response DTO. Request and response DTOs MUST NOT appear in service or
  model signatures.
- An interface is created only when the framework needs one (a Spring Data repository) or there
  are two real implementations. A service with one implementation is a class.
- No package becomes a catch-all. `service` is grouped by use case: each `@Service` lives in its
  own subpackage (`service/portfolio`, `service/search`) together with its request and response
  models, its exceptions and its configuration; nothing sits at the root of `service`, and one
  use case does not depend on another — enforced by ArchUnit. The controller hands the service a
  raw request model and gets a response model back: validating the request against configured
  limits is the use case's job, so the controller never reads configuration. `model` groups by
  concept (`model/portfolio`), keeping at its root only what every concept shares
  (`DecimalPolicy`). `controller` and `persistence` group by role (`dto`, `entity`, `mapper`). A
  subpackage that starts holding a second concept is split in the same change that adds it.

Rationale: the path of a request reads top to bottom in one service method, and principle V holds
without test doubles because the model never reaches out. In 1.x (ports and adapters) model
objects fetched their own data through ports, which scattered the orchestration across them.
See ADR-0017. Grouping a use case with its own request and response models means a reader opens
one package and sees the whole use case, and the package list says what the system does rather
than what kinds of classes it has; grouping by concept keeps a growing layer from turning into a
bag of unrelated types that a reader has to sort before they can read it.

### IX. Design for Change

- Names state intent; a reader MUST NOT need a comment to know what something does (see VII).
- Objects expose behaviour, not state: callers ask an object to do something rather than pulling
  its data to decide for them.
- A rule exists in exactly one place. Duplicated business logic MUST be extracted; duplicated
  structure MAY be left alone.
- Each class has one reason to change. Dependencies go controller → service → model and
  persistence; the model depends on nothing in the application.
- New extension points (interfaces, hooks, configuration) are justified by a second real use, not
  by an anticipated one. A value type that names a domain concept needs no second use.
- A method never takes an `Optional`: absence is resolved by the caller (`map`, `flatMap`) or
  returned.
- A method does one thing at one level of abstraction. A public method reads as the summary of
  its steps; the steps are private methods that follow it in the order they are called.
- Structure chosen for performance (a single pass, a cache, a denormalisation) MUST cite a
  measurement that justifies it. Without one, the more readable structure wins.

Rationale: the cost of this codebase is in the changes it will need later (a matching engine,
authentication, a snapshot of the ledger); code that keeps each rule in one place and a model
that depends on nothing absorbs them without rewrites. Code is read far more often than it is
written, and the evaluation of this exercise is a reading of it: a rule that can only be found by
untangling four interleaved accumulators is a rule a reviewer will not find. An optimisation
nobody measured costs that readability and buys nothing anyone can show.

## Technology and Scope Constraints

- Java 21, Spring Boot 4.1.2, Maven wrapper. Dependency versions come from the Spring Boot BOM;
  an explicit version is allowed only for what the BOM does not manage.
- PostgreSQL 18.7 in every environment: local, tests and staging.
- The API is versioned under `/api/v1`. Its endpoints are portfolio, instrument search, order
  submission and order cancellation. A new endpoint requires a spec and, if it had alternatives,
  an ADR.
- The API contract is `docs/api/openapi.yaml`, one file for the whole API, and every response in
  the web tests MUST validate against it (ADR-0024). A feature's `specs/NNN/contracts/` is its
  design-time proposal and MUST NOT be edited once the feature is implemented; a feature that
  changes the API applies the change to `docs/api/openapi.yaml` and `docs/api/trading-api.http`
  as part of its implementation.
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
- Design documents (spec, plan, data-model, ADRs) specify contracts, rules and figures, not the
  shape of the code: they say what a method returns and which rule it applies, never "one loop
  accumulating four sums". An agent implements a plan literally, so a shape written there is a
  shape nobody chose.
- Every user story ends with a refactor task: with its tests green, the code it added is reviewed
  against principle IX and cleaned before the story is marked done. This is the refactor step of
  red-green-refactor, kept even where the tests were not written first.
- CI (GitHub Actions) MUST be green before merging. Every run builds and runs the full suite,
  Testcontainers tests included, with the architecture rules (ArchUnit, ADR-0018) and the
  formatting check (ADR-0019) inside the build, and scans dependencies and secrets with Trivy
  (ADR-0023). Pull requests into `develop` also pass the SonarQube Cloud quality gate (ADR-0020,
  ADR-0021), which requires at least 80% coverage on new code: coverage is gated there, on new
  code only. JaCoCo produces the report on every run and gates nothing by itself.
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

**Version**: 2.3.0 | **Ratified**: 2026-11-15 | **Last Amended**: 2026-11-16
