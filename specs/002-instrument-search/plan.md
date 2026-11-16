# Implementation Plan: Instrument Search

**Branch**: `feature/instrument-search` (feature directory `002-instrument-search`) | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-instrument-search/spec.md`

## Summary

One read-only endpoint, `GET /api/v1/instruments/search?query=…&page=…&size=…`, answering with one
page of the instruments whose ticker or name contains what the user typed — partial,
case-insensitive and accent-insensitive — best matches first, each carrying its latest `close` in
pesos and the day's move, plus how many matched in total (FR-001).

**The database executes the search** ([research R-102](research.md#r-102-the-database-executes-the-search),
ADR candidate 0027, author's decision): one native query filters, ranks, joins the latest price and
pages, so only the rows of one page ever cross the wire. A second query counts the matches. Java
keeps two jobs: validating the request, and the money arithmetic of the daily return.

The controller turns the request parameters into **`SearchQuery`** and **`SearchPage`**, model
value types that trim and reject at construction, so nothing downstream can hold an invalid
request and no unbounded string reaches the database. **`InstrumentSearchService`** runs both
statements inside one `REPEATABLE_READ` read-only transaction — so the page and the total describe
one instant and the response cannot contradict itself — maps the rows into model `SearchResult`s,
and returns the page. **`Quote.dailyReturnPercent()`** applies `DecimalPolicy`, reporting absent
instead of zero where a figure is unknown.

One migration, `V3__instrument_search_fold.sql`, creating **one function and nothing else**:
`search_fold`, which both the stored columns and the query parameter pass through, so the folding
rule exists exactly once and in one language. **No index, no new dependency, no PostgreSQL
extension** — and [R-111](research.md#r-111-no-index-today-and-the-exact-reason-why) states
precisely why an index would not help today and what the upgrade path is, rather than implying one
was forgotten.

The three limits — default page size, maximum page size, maximum query length — are
**configuration, not constants** (FR-015c): `trading.search.*` properties with defaults of 20, 100
and 64, settable per deployment through environment variables with no rebuild.

This feature reads `instruments` and `marketdata` only: it never touches the `orders` ledger, takes
no user id, and writes nothing (FR-021, FR-022). It also lands the one rule in the statement that
no other endpoint implements — the daily return from `close` and `previousClose` — which the
portfolio explicitly does not report (001's FR-009).

**The design was executed before it was written down.** The query, the folding function, the tier
ordering, the lateral join and the paging were all run against a real `postgres:18.7-alpine` built
from this project's migrations and seed; every count in the acceptance scenarios was reproduced
twice, once from the SQL seed and once from the database. See
[research.md](research.md#measured-facts-this-phase-relied-on).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.2 (`webmvc`, `data-jpa`, `flyway`), PostgreSQL JDBC
driver. **Nothing is added** — `pom.xml` already carries everything, and no PostgreSQL extension is
installed ([R-115](research.md#r-115-no-postgresql-extension-is-installed)).

**Storage**: PostgreSQL 18.7, schema as provided plus one function. Tables read: `instruments` and
`marketdata`, in one statement. Tables **not** read: `orders`, `users`.

**Schema facts that drive the mapping** (identifiers lowercase; `V1__given_schema.sql` writes them
unquoted):

| Column | Type | Maps to |
|---|---|---|
| `instruments.id` | `SERIAL` | `long` — the id FR-012 publishes and the order endpoint will consume |
| `instruments.ticker` | `VARCHAR(10)` | `String`; 3 to 5 characters and unique in the dataset, **not enforced by the schema** — see the tie-break caveat in [R-104](research.md#r-104-relevance-is-a-four-branch-case-and-the-tie-break-is-the-ticker) and Q-101 |
| `instruments.name` | `VARCHAR(255)` | `String`; 5 to 42 characters, three carrying diacritics |
| `instruments.type` | `VARCHAR(10)` | `String`; `MONEDA` excludes the row, bound as a parameter from the model (FR-023) |
| `marketdata.close` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent (FR-018) |
| `marketdata.previousclose` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent — **read here for the first time** (FR-017, [R-108](research.md#r-108-the-page-query-reads-the-price-the-portfolios-query-is-untouched)) |
| `marketdata.date` | `DATE` | not mapped; orders the lateral join that picks the latest row per instrument |

**Migration**: `V3__instrument_search_fold.sql` creates `search_fold(TEXT)`, `IMMUTABLE STRICT
PARALLEL SAFE`. No table, column or index. `V1` and `V2` untouched (constitution VI).

**Configuration** (FR-015c, [R-106](research.md#r-106-the-limits-are-configuration-not-constants)):

| Property | Default | Environment variable |
|---|---|---|
| `trading.search.default-page-size` | 20 | `TRADING_SEARCH_DEFAULT_PAGE_SIZE` |
| `trading.search.max-page-size` | 100 | `TRADING_SEARCH_MAX_PAGE_SIZE` |
| `trading.search.max-query-length` | 64 | `TRADING_SEARCH_MAX_QUERY_LENGTH` |

**Testing**: JUnit 5. Request validation and the daily return as plain Java with no Spring and no
database; **the matching rule on Testcontainers**, because it lives in the query — folding,
tiering, the lateral join and paging are asserted against `postgres:18.7`, built from the same
migrations and seed as local development (constitution V, ADR-0010); MockMvc for the HTTP layer,
with every response validated against `docs/api/openapi.yaml` (ADR-0024), including one test that
overrides the configured default page size. `./mvnw verify`.

**Target Platform**: Linux server / JVM 21; local development on Docker Compose (PostgreSQL on port
5433), the API itself runnable in Docker (ADR-0025) — which is what makes the environment-variable
configuration worth having.

**Project Type**: Single-module Maven web service in layers: controller → service → model and
persistence (constitution VIII).

**Performance Goals**: none set by the statement. Measured characteristic, not estimated: the
broadest query (`a`, 59 matches of 66 rows) plans as a sequential scan plus a top-N heapsort
touching **one** shared buffer, and returns one page. The scaling property the design buys is
**locality, not an index**: the filter runs where the data is and the result set is bounded by
`LIMIT`, instead of the whole table crossing into the JVM on every keystroke. What it does not buy
is sublinear matching — stated, with the upgrade path, in
[R-111](research.md#r-111-no-index-today-and-the-exact-reason-why).

**Constraints**: `BigDecimal` on every money path, `compareTo` never `equals` — the zero-divisor
check of FR-019 is exactly where `equals` would be wrong (constitution II). One scale and rounding
policy, `DecimalPolicy`, unchanged (ADR-0016). No comments in source (VII). Model free of Spring,
Jakarta, JPA and web types, and of any reference to persistence (VIII). Read-only (FR-022),
user-agnostic (FR-021).

**Scale/Scope**: one endpoint; 66 instruments and 126 price rows in the seed. Roughly 12 production
types, of which 7 are model types, 3 persistence, 2 controller, plus one properties record and one
migration. Two existing files gain a mapping; none changes behaviour.

## Constitution Check

*GATE: passed before Phase 0; re-evaluated after Phase 1 design — see the second verdict column.*

| Principle | How this feature complies | Pre-Phase 0 | Post-Phase 1 |
|---|---|---|---|
| **I. The ledger is the single source of truth** | Not applicable, and deliberately so: this feature never reads `orders` and never derives a money figure from it (FR-021). The two figures it reports come from `marketdata`, which is market fact, not account state. Nothing here creates a second source of truth for cash or positions. | N/A | N/A |
| **II. Money is exact** | `BigDecimal` for `close` and for the daily return, computed in Java with `DecimalPolicy`, unchanged (ADR-0016, [R-109](research.md#r-109-the-daily-return-is-computed-in-java)). `compareTo(ZERO)` guards the division of FR-019 — `0.00` and `0` are the same divisor, which `equals` would deny. This is the one piece that stayed in Java when the rest moved to SQL, and that is the reason: a second rounding policy in a second language is exactly what this principle forbids. No `double` or `float` anywhere. | PASS | PASS |
| **III. Business logic in Java, not in the database** | **The judgement call of this feature; see [Complexity Tracking](#complexity-tracking).** The principle's own wording allows SQL to do "reading, writing and aggregating data", and selecting rows, ordering them and paging them is that. What sits closer to the line is the relevance `CASE` — a presentation rule — and the `search_fold` function, which normalises text the way a collation does and carries no business rule. No trigger, no procedure, no rule about money or state in the database. Every figure the API computes is computed in Java. | PASS, with two items recorded | PASS, with two items recorded |
| **IV. Order state is auditable and race-free** | Not applicable: nothing here transitions an order or inserts history. The read runs `REPEATABLE_READ` and `readOnly` and takes no locks, because it writes nothing. | N/A | N/A |
| **V. Tests run on the real engine** | Three levels, with the weight at the persistence level precisely because the matching rule lives in the query: plain-Java tests for the two validating value types and the daily return; Testcontainers tests for folding, tiering, the lateral join and paging; Testcontainers + MockMvc for the eleven quickstart scenarios. No H2, no mocked repository, no test pointed at staging. The rejection paths — blank, missing, over-long, bad paging, no match, no price — ship with the feature. The principle is satisfied more literally than in feature 001: the rule is tested where it actually runs. | PASS | PASS |
| **VI. Schema changes only through migrations** | One new migration, `V3__instrument_search_fold.sql`, creating one function; `V1` and `V2` untouched. The seed stays the provided dataset: every scenario, including the two unpriced instruments, the flat `0.00` return and the three-page walk of `a`, is reachable from it with no fixture ([measurements](research.md#measured-facts-this-phase-relied-on)). Staging reached only through the `staging` profile; no credential committed. The decision to install **no extension** is partly a reading of this principle: a `CREATE EXTENSION` that passes on Testcontainers and fails on staging breaks the guarantee that migrations are reproducible everywhere ([R-115](research.md#r-115-no-postgresql-extension-is-installed)). | PASS | PASS |
| **VII. No comments; reasoning in documents** | No comments in the code this feature adds — including in the SQL, where the clause-by-clause justification lives in [data-model.md](data-model.md) instead. Two decisions that had real alternatives *and* bind work beyond this feature are ADR candidates — 0027 the database executes the search, 0028 pagination with a total count — each written at implementation time with `docs/adr/README.md` updated in the same change. Both reverse a position an earlier draft of this plan held, and research.md keeps the rejected argument next to the accepted one. No departure from the challenge statement is introduced; the README gains this endpoint and the new configuration. | PASS | PASS |
| **VIII. Layers** | `InstrumentSearchController` translates HTTP — it builds a `SearchQuery` and a `SearchPage`, calls one service method, maps the result to DTOs — as `PortfolioController` builds a `UserId`. `InstrumentSearchService` orchestrates: one repository, a hand-written mapper, and the model's arithmetic. The model imports nothing but the JDK, holds no repository and is not a Spring bean. The properties record lives in `service`, not in the model (which may not import Spring) and not in a new top-level package (which would sit outside the layers ArchUnit checks). Entities and projections stay in `persistence`; DTOs stay in `controller`, and the response DTO is this feature's own rather than Spring Data's `Page` ([R-116](research.md#r-116-the-response-carries-the-page-its-size-the-totals-and-the-results)). | PASS | PASS |
| **IX. Design for change** | Each rule in one place: the fold is one function applied to both sides of every comparison — the property that made this design defensible at all; the relevance order exists once, in one `ORDER BY`; the three limits live together in one properties record. Objects expose behaviour: `SearchQuery` and `SearchPage` are valid by construction rather than checked by their callers; `Quote` answers "what was the day's move" instead of handing out two numbers. No extension point is invented — no `SearchStrategy`, no pluggable matcher. The structures chosen for cost (paging in SQL, the lateral join instead of a second round trip) cite measurements, and the structure **not** chosen — the index — cites the measurement that says it would not help. | PASS | PASS |

**Gate verdict**: PASS, before and after design. The tension with principle III is real and is
recorded in [Complexity Tracking](#complexity-tracking) rather than waved through; it is the one
place a reviewer should push back if they disagree with the author's decision.

## Project Structure

### Documentation (this feature)

```text
specs/002-instrument-search/
├── spec.md                                   # Input (amended 2026-11-16: pagination, configurable limits)
├── plan.md                                   # This file
├── research.md                               # Phase 0: 16 decisions, 2 ADR candidates, 2 open questions
├── data-model.md                             # Phase 1: schema, migration, persistence, model, requirement map
├── quickstart.md                             # Phase 1: eleven runnable validation scenarios
├── contracts/
│   └── instrument-search.openapi.yaml        # Phase 1: HTTP contract, hand-written design-time proposal
└── tasks.md                                  # Phase 2 (/speckit-tasks — not created here)
```

### Source Code (repository root)

New unless marked. Nothing moves.

```text
src/main/java/com/cocos/trading/
├── controller/
│   ├── InstrumentSearchController.java       # HTTP only: parameters in, one service call, DTO out
│   ├── ApiExceptionHandler.java              # EXISTS — gains the invalid-request and missing-parameter 400s
│   └── dto/
│       ├── InstrumentSearchResponse.java     # record; query, page, size, totalElements, totalPages, results
│       └── InstrumentMatchResponse.java      # record; absent -> JSON null
├── service/
│   ├── InstrumentSearchService.java          # @Transactional(REPEATABLE_READ, readOnly); page + count, mapped
│   └── SearchProperties.java                 # @ConfigurationProperties("trading.search") -> model SearchLimits
├── model/instrument/
│   ├── InstrumentRef.java                    # EXISTS — isCash() and the cash type reused unchanged (FR-023)
│   ├── SearchQuery.java                      # trims, validates; always valid once built
│   ├── SearchPage.java                       # page and size, validated against the limits; never clamps
│   ├── SearchLimits.java                     # the three configured bounds, as values
│   ├── InvalidSearchRequestException.java    # the one rejection, naming the rule and the limit in force
│   ├── Quote.java                            # close, previousClose -> dailyReturnPercent (FR-017, FR-019)
│   ├── SearchResult.java                     # one instrument + its two figures
│   └── InstrumentSearch.java                 # the page: results, page asked for, total, derived totalPages
└── persistence/
    ├── InstrumentSearchRepository.java       # the page query (fold, match, tier, lateral, limit) + the count
    ├── InstrumentMatchRow.java               # projection: id, ticker, name, type, close, previousClose
    └── mapper/
        └── InstrumentSearchMapper.java       # rows -> SearchResults; NULL price -> absent, never zero

src/main/resources/
├── application.yaml                          # EXISTS — gains the three trading.search properties
└── db/migration/V3__instrument_search_fold.sql   # one IMMUTABLE function, no table, no index

docs/api/
├── openapi.yaml                              # EXISTS — gains the operation and the two schemas (ADR-0024)
└── trading-api.http                          # EXISTS — gains the instrument search section

docs/adr/
├── 0027-the-database-executes-the-instrument-search.md
├── 0028-search-results-are-paginated.md
└── README.md                                 # EXISTS — gains both rows in the same change (VII)

src/test/java/com/cocos/trading/
├── model/instrument/                         # SearchQuery, SearchPage, Quote — plain Java
├── persistence/                              # Testcontainers: folding, tiers, lateral join, paging, metacharacters
└── controller/                               # Testcontainers + MockMvc: quickstart scenarios 1-11
```

**Structure Decision**: the single Maven module and the four top-level packages of feature 001,
unchanged. This feature grows `model/instrument` — the package 001 created for `InstrumentRef` —
and adds one repository, one projection, one mapper, one service with its properties, and one
controller. No existing class changes behaviour; two gain a member.

The shape inside those packages is the one unusual thing, and it is a direct consequence of the
author's decision: the model is thinner than feature 001's and the persistence layer carries the
behaviour. That is visible in the test weight too (see the Constitution Check, V). Nothing in
`model/instrument` depends on `model/ledger` or `model/portfolio`; search and portfolio share
`InstrumentRef` and `DecimalPolicy` and nothing else.

## Phase 0 and Phase 1 outputs

| Artifact | Contents |
|---|---|
| [research.md](research.md) | 16 decisions with alternatives and rationale (R-101 to R-116); 2 marked as ADR candidates (0027, 0028); the dataset measurements **and** the probes run against a real PostgreSQL; **two questions left open for the author** (Q-101 `id` as a final tie-break, Q-102 a paging counter) |
| [data-model.md](data-model.md) | The two tables as read, the one-function migration, the page and count queries clause by clause against the requirements they serve, the model, the configuration, and a requirement-to-owner map |
| [contracts/instrument-search.openapi.yaml](contracts/instrument-search.openapi.yaml) | OpenAPI 3.1: the operation with its three parameters, the 200/400 responses, seven worked examples (exact ticker, the bank family, accent folding, unpriced, no match, a paged broad query, a page past the end) and five rejection examples |
| [quickstart.md](quickstart.md) | Eleven validation scenarios with expected figures, all reachable from the provided seed with no fixture, including the three-page walk of `a` and the environment-variable override |

## Requirement coverage

| Requirement | Where it is satisfied |
|---|---|
| FR-001, FR-021, FR-022 | `InstrumentSearchController` + `InstrumentSearchService`, read-only, no user id, `orders` never read |
| FR-002, FR-003, FR-024 | the page query's `strpos` predicate over both folded columns |
| FR-004, FR-005 | `search_fold`, applied to both sides inside one statement |
| FR-006 | one row per instrument in the result set |
| FR-007, FR-008, FR-009 | `SearchQuery`'s factory, against `SearchLimits` |
| FR-010 | `strpos` and `starts_with`: no pattern syntax exists to escape (measured: `%` → 0 rows) |
| FR-011 | no match is an empty `results` with `200` and a total of zero |
| FR-012 | `SearchResult`, carrying the whole `InstrumentRef` |
| FR-013, FR-014 | the relevance `CASE` and `ORDER BY tier, ticker` |
| FR-015, FR-015b | `LIMIT`/`OFFSET` over that total order, plus the count query under the same snapshot |
| FR-015a | `SearchPage`'s factory, which rejects rather than clamps |
| FR-015c | `SearchProperties` and the three environment variables; the size echoed, the maximum named in rejections |
| FR-016 | the `LEFT JOIN LATERAL`, latest row per instrument |
| FR-017, FR-020 | `Quote.dailyReturnPercent()` via `DecimalPolicy.percent` (ADR-0016) |
| FR-018 | the `LEFT` join plus `NULL` prices mapped to absent figures |
| FR-019 | `Quote.dailyReturnPercent()` absent when the divisor is absent or `compareTo(ZERO) == 0` |
| FR-023 | `i.type <> :cashType`, bound from the model |

## Complexity Tracking

Three judgement calls, recorded rather than hidden. The first two are where this feature stands
closest to constitution III.

| Decision | Why needed | Simpler alternative rejected because |
|---|---|---|
| **The matching predicate and the relevance ordering live in SQL** ([R-102](research.md#r-102-the-database-executes-the-search), ADR candidate 0027) | The author's decision, 2026-11-16: filtering a table by reading all of it into the JVM does not scale, and a search box calls this endpoint on every keystroke. Keeping the filter in the database means one page of rows crosses the wire instead of the whole table. | Matching in Java over the loaded catalogue was the first proposal and it is the one constitution III points at: it would have put every rule in plain Java and made each acceptance scenario a millisecond unit test. It was rejected on cost at scale. The principle is not violated — selecting, ordering and paging rows is the "reading data" it allows, and every computed figure is still Java — but the relevance `CASE` is a presentation rule expressed in SQL, and that is stated here rather than argued away. The compensation is that the rule is tested on the real engine (V), not on a stand-in. |
| **A `search_fold` function in the database** ([R-103](research.md#r-103-one-folding-function-in-sql-applied-to-both-sides)) | It is what makes the SQL design defensible: the query text and the stored text pass through the *same* function in the same statement, so the folding rule exists once. Writing it inline would repeat a 50-character map four times per statement; folding the query in Java would put the rule in two languages, which is the duplication IX forbids. | `unaccent` is the standard answer and was rejected for two independent reasons — `CREATE EXTENSION` may fail on the provided staging database, and `unaccent()` is not `IMMUTABLE` so it cannot be indexed without a wrapper, which is this function with an extension added for nothing ([R-115](research.md#r-115-no-postgresql-extension-is-installed)). Constitution III's concern is functions carrying *business rules*; this one normalises text, like a collation, and the rules that use it are in the query. Verified `IMMUTABLE` by building an expression index over it. |
| **Two statements per request, under one `REPEATABLE_READ` snapshot** ([R-112](research.md#r-112-one-read-only-repeatable_read-transaction-around-both-statements)) | `totalElements` and `results` must describe one instant, or a response says "59 matched" while returning a page drawn from a different snapshot — a self-contradicting answer, intermittent and only under load. | A single statement with a window function (`count(*) OVER ()`) would avoid the second query and the isolation level both. Rejected: it computes the total on every row of the page, it makes the one query harder to read, and the count query is free here (measured: one shared buffer hit). `READ COMMITTED` was rejected because it gives each statement its own snapshot, which is the defect described. |

## Open for the author

Two items are **not decided**; neither blocks implementation. Full arguments in
[research.md](research.md#open-questions-for-the-author).

| # | Question | If yes |
|---|---|---|
| Q-101 | Add `id` as the final tie-break — `ORDER BY tier, ticker, id`? | one extra sort key, no behaviour change on this dataset. It closes a real hole: the schema does not enforce ticker uniqueness, and with a duplicate ticker the order stops being total, which is how paged results silently repeat or skip a row. **This is the cheapest insurance in the feature and the one worth ten seconds of attention.** |
| Q-102 | A Micrometer counter for searches that span more than one page? | two lines in the service, with the actuator and the Prometheus registry already in place (ADR-0026) — against the fact that the same ADR states nothing collects these metrics here. Now that the page size is a runtime dial, the number that would justify turning it is worth slightly more than it was |

Both default to **not shipped** if unanswered.

## Deferred, by decision

- **A trigram index (`pg_trgm`) over a stored folded column** — the real fix for sublinear
  matching, with the exact steps in [R-111](research.md#r-111-no-index-today-and-the-exact-reason-why).
  It needs an extension the author declined, and it would change `strpos` back to `LIKE`, which
  reintroduces the escaping FR-010 needs — a real change, not a config flip, which is why it is
  written down instead of half-built.
- **Typo tolerance and similarity ranking** — forbidden in this feature by FR-024, and a new
  feature with its own decision if ever wanted. That is also the day `pg_trgm` becomes the right
  answer for two reasons at once.
- **Cursor pagination** over `(tier, ticker)` — the escape hatch if the table grows and deep
  offsets start to hurt. Offset paging is the right answer for a market of 65 instruments.
- **A stored generated column for the folded text** — viable today and the companion to the index
  above, deferred because it alters the provided table for a benefit nothing has measured (IX).
- **README housekeeping** — the README lists the endpoints that exist and where things are, and
  now also needs the three `trading.search.*` settings in whatever it says about configuration.
  Part of the implementation, not of the delivery sweep.

## A note on the constitution

Principle III is written as "business rules in Java, not in the database", with SQL allowed for
"reading, writing and aggregating data". This feature sits on that boundary deliberately, and the
Constitution Check calls it PASS on the reading that row selection, ordering and paging are what
the principle permits. If the author disagrees on re-reading, the honest fix is not to re-argue
this plan but to amend III so it says explicitly where a search's predicate and ranking may live —
in which case ADR-0027 becomes the decision that prompted the amendment. Flagged, not acted on:
amending the constitution is `/speckit-constitution`'s job and the author's call.
