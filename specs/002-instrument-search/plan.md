# Implementation Plan: Instrument Search

**Branch**: `feature/instrument-search` (feature directory `002-instrument-search`) | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/002-instrument-search/spec.md`

Rewritten on 2026-11-16 after implementation. The first version of this plan specified one native
query with a folding function (`search_fold`, migration `V3`), four relevance tiers, a lateral join
for the price and a hand-written count. The author judged it too complex for the requirement and
replaced it ([ADR-0027](../../docs/adr/0027-instrument-search-is-one-short-jpql-query.md)); the
reasoning behind the first design is kept in [research.md](research.md).

## Summary

One read-only endpoint, `GET /api/v1/instruments/search?query=…&page=…&size=…`, answering with one
page of the instruments whose ticker or name contains what the user typed — partial and
case-insensitive, in ticker order — each carrying its latest `close` in pesos and the day's move,
plus how many matched in total (FR-001). Pesos (`MONEDA`) are never a result (FR-023).

**One short JPQL query** filters and excludes cash; `Pageable` pages and sorts it and gives the
total. A second query — the `findLatestMarketData` the portfolio already uses — brings the latest price
of the instruments on that page only. Java keeps two jobs: validating the request, and the money
arithmetic of the daily return.

The controller turns the request parameters into **`SearchQuery`** and **`SearchPage`**, value
types that trim and reject at construction, so nothing downstream can hold an invalid request and
no unbounded string reaches the database. **`InstrumentSearchService`** runs both statements inside
one `REPEATABLE_READ` read-only transaction — so the page, the total and the prices describe one
instant — lets the model's `DailyCloses` price the page into `InstrumentMatch`es and returns it. **`DailyClose.dailyReturnPercent()`**
applies `DecimalPolicy`, reporting absent instead of zero where a figure is unknown.

**No migration, no index, no new dependency, no PostgreSQL extension.** Accents are not folded:
`río` finds *Molinos Río de la Plata*, `rio` does not; `unaccent` was considered and deferred
(ADR-0027).

The three limits — default page size, maximum page size, maximum query length — are
**configuration, not constants** (FR-015c): `trading.search.*` properties with defaults of 20, 100
and 64, written once in `SearchProperties`, settable per deployment through environment variables with no rebuild.

This feature reads `instruments` and `marketdata` only: it never touches the `orders` ledger, takes
no user id, and writes nothing (FR-021, FR-022). It also lands the one rule in the statement that
no other endpoint implements — the daily return from `close` and `previousClose` — which the
portfolio explicitly does not report (001's FR-009).

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.2 (`webmvc`, `data-jpa`, `flyway`), PostgreSQL JDBC
driver. **Nothing is added**, and no PostgreSQL extension is installed
([R-115](research.md#r-115-no-postgresql-extension-is-installed)).

**Storage**: PostgreSQL 18.7, schema as provided. Tables read: `instruments` and `marketdata`, in
two statements. Tables **not** read: `orders`, `users`.

**Schema facts that drive the mapping** (identifiers lowercase; `V1__given_schema.sql` writes them
unquoted):

| Column | Type | Maps to |
|---|---|---|
| `instruments.id` | `SERIAL` | `long` — the id FR-012 publishes and the order endpoint will consume; the final tie-break |
| `instruments.ticker` | `VARCHAR(10)` | `String`; 3 to 5 characters and unique in the dataset, **not enforced by the schema** — which is why `id` is the final tie-break (Q-101) |
| `instruments.name` | `VARCHAR(255)` | `String`; 5 to 42 characters, three carrying diacritics |
| `instruments.type` | `VARCHAR(10)` | `String`; `MONEDA` excludes the row, bound by `InstrumentRepository.searchNonCash` (FR-023) |
| `marketdata.close` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent (FR-018) |
| `marketdata.previousclose` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent — added to the projection `findLatestMarketData` already returns (FR-017) |
| `marketdata.date` | `DATE` | not mapped; picks the latest row per instrument |

**Migration**: none. `V1` and `V2` untouched (constitution VI).

**Configuration** (FR-015c, [R-106](research.md#r-106-the-limits-are-configuration-not-constants)):

| Property | Default | Environment variable |
|---|---|---|
| `trading.search.default-page-size` | 20 | `TRADING_SEARCH_DEFAULT_PAGE_SIZE` |
| `trading.search.max-page-size` | 100 | `TRADING_SEARCH_MAX_PAGE_SIZE` |
| `trading.search.max-query-length` | 64 | `TRADING_SEARCH_MAX_QUERY_LENGTH` |

**Testing**: JUnit 5. Request validation and the daily return as plain Java with no Spring and no
database; the search query and the latest-price query on Testcontainers against `postgres:18.7`,
built from the same migrations and seed as local development (constitution V, ADR-0010); MockMvc
for the HTTP layer, with every response validated against `docs/api/openapi.yaml` (ADR-0024),
including one test that overrides the configured default page size. `./mvnw verify`.

**Target Platform**: Linux server / JVM 21; local development on Docker Compose (PostgreSQL on port
5433), the API itself runnable in Docker (ADR-0025) — which is what makes the environment-variable
configuration worth having.

**Project Type**: Single-module Maven web service in layers: controller → service → model and
persistence (constitution VIII).

**Performance Goals**: none set by the statement. The filter runs in the database and only one page
of rows crosses the wire, instead of the whole table on every keystroke. A sequential scan over 66
rows needs no index, and a btree index could not serve substring containment anyway — stated, with
the upgrade path, in [R-111](research.md#r-111-no-index-today-and-the-exact-reason-why).

**Constraints**: `BigDecimal` on every money path, `compareTo` never `equals` — the zero-divisor
check of FR-019 is exactly where `equals` would be wrong (constitution II). One scale and rounding
policy, `DecimalPolicy`, unchanged (ADR-0016). No comments in source (VII). Model free of Spring,
Jakarta, JPA and web types, and of any reference to persistence (VIII). Read-only (FR-022),
user-agnostic (FR-021).

**Scale/Scope**: one endpoint; 66 instruments and 126 price rows in the seed.

## Constitution Check

*Re-evaluated against constitution 2.3.0 after the simplification.*

| Principle | How this feature complies | Verdict |
|---|---|---|
| **I. The ledger is the single source of truth** | Not applicable: this feature never reads `orders` and never derives a money figure from it (FR-021). The two figures it reports come from `marketdata`, which is market fact, not account state. | N/A |
| **II. Money is exact** | `BigDecimal` for `close` and for the daily return, computed in Java with `DecimalPolicy` (ADR-0016, [R-109](research.md#r-109-the-daily-return-is-computed-in-java)). `compareTo(ZERO)` guards the division of FR-019. No `double` or `float` anywhere. | PASS |
| **III. Business logic in Java, not in the database** | The query selects, filters and pages rows — the "reading data" the principle allows. No function, trigger or procedure in the database; every figure the API computes is computed in Java. The tension the first design had with this principle (a relevance `CASE` and a folding function in SQL) is gone. | PASS |
| **IV. Order state is auditable and race-free** | Not applicable: nothing here transitions an order. The read runs `REPEATABLE_READ` and `readOnly` and takes no locks. | N/A |
| **V. Tests run on the real engine** | Plain-Java tests for the validating value types and the daily return; Testcontainers tests for the search query (containment, accents typed and not typed, cash excluded, ticker order, literal metacharacters, paging) and for the latest row per instrument; Testcontainers + MockMvc for the quickstart scenarios. No H2, no mocked repository, no test pointed at staging. | PASS |
| **VI. Schema changes only through migrations** | No schema change. Every scenario, including the two unpriced instruments, the flat `0.00` return and the three-page walk of `a`, is reachable from the provided seed with no fixture. No extension installed ([R-115](research.md#r-115-no-postgresql-extension-is-installed)). | PASS |
| **VII. No comments; reasoning in documents** | No comments in the code. Two decisions with real alternatives are ADRs — 0027 the search query, 0028 pagination with a total count — with `docs/adr/README.md` updated in the same change. | PASS |
| **VIII. Layers** | `InstrumentSearchController` translates HTTP — it builds a raw `SearchRequest`, calls one service method, maps the result to DTOs, and never reads configuration. `InstrumentSearchService` and its request and response models (`SearchRequest`, `SearchQuery`, `SearchPage`, `SearchLimits`, `SearchProperties`, `InstrumentMatch`, `InstrumentMatches`, `InvalidSearchRequestException`) sit together in `service/search`, because paging and validation belong to the use case, not to the business. The model holds only `InstrumentRef`, `DailyClose` and `DailyCloses`. Entities stay in `persistence`; Spring Data's `Page` is used between repository and service only, and the response DTO is this feature's own ([R-116](research.md#r-116-the-response-carries-the-page-its-size-the-totals-and-the-results)). | PASS |
| **IX. Design for change** | Each rule in one place: the match predicate is written once (the count is derived from it); "latest price per instrument" is one query shared with the portfolio; the three limits live together in one properties record. Objects expose behaviour: `SearchQuery` and `SearchPage` are valid by construction; `DailyClose` answers "what was the day's move" instead of handing out two numbers. No extension point is invented. | PASS |

**Gate verdict**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/002-instrument-search/
├── spec.md                                   # Input (amended 2026-11-16: pagination, configurable limits, simplification)
├── plan.md                                   # This file
├── research.md                               # Phase 0: decisions and their alternatives; four superseded by ADR-0027
├── data-model.md                             # Phase 1: schema, persistence, model, service, requirement map
├── quickstart.md                             # Phase 1: eleven runnable validation scenarios
├── contracts/
│   └── instrument-search.openapi.yaml        # Phase 1 design-time proposal; the live contract is docs/api/openapi.yaml
└── tasks.md                                  # Phase 2, historical record of the first implementation
```

### Source Code (repository root)

```text
src/main/java/com/cocos/trading/
├── controller/
│   ├── InstrumentSearchController.java       # HTTP only: parameters in, one service call, DTO out
│   ├── ApiExceptionHandler.java              # EXISTS — gains the invalid-request and missing-parameter 400s
│   └── dto/
│       ├── InstrumentSearchResponse.java     # record; query, page, size, totalElements, totalPages, results
│       └── InstrumentMatchResponse.java      # record; absent -> JSON null
├── service/search/                           # the use case and its own request and response models
│   ├── InstrumentSearchService.java          # @Transactional(REPEATABLE_READ, readOnly); validate, page, then prices of the page
│   ├── SearchRequest.java                    # the raw query, page and size the controller read
│   ├── SearchQuery.java                      # trims, validates; always valid once built
│   ├── SearchPage.java                       # page and size, validated against the limits; never clamps
│   ├── SearchLimits.java                     # the three configured bounds, as values
│   ├── SearchProperties.java                 # @ConfigurationProperties("trading.search") -> SearchLimits
│   ├── InvalidSearchRequestException.java    # the one rejection, naming the rule and the limit in force
│   ├── InstrumentMatch.java                  # one instrument + its DailyClose
│   └── InstrumentMatches.java                # the page: query, matches, page asked for, total, derived totalPages
├── model/instrument/
│   ├── InstrumentRef.java                    # EXISTS — isCash() and the cash type reused unchanged (FR-023)
│   ├── DailyClose.java                       # close, previousClose -> dailyReturnPercent (FR-017, FR-019)
│   └── DailyCloses.java                      # DailyClose per instrument; unknown() when there is none (FR-018)
└── persistence/
    ├── InstrumentRepository.java             # search: one JPQL @Query ending in ORDER BY ticker, id; searchNonCash binds the cash type
    ├── MarketDataRepository.java             # EXISTS — findLatestMarketData now also returns previousclose
    ├── LatestMarketData.java                      # EXISTS — gains previousClose
    └── mapper/
        └── InstrumentSearchMapper.java       # entities -> InstrumentRefs (rows -> DailyCloses is MarketDataMapper's)

docs/api/
├── openapi.yaml                              # EXISTS — gains the operation and the two schemas (ADR-0024)
└── trading-api.http                          # EXISTS — gains the instrument search section

docs/adr/
├── 0027-instrument-search-is-one-short-jpql-query.md
├── 0028-search-results-are-paginated.md
└── README.md                                 # EXISTS — gains both rows in the same change (VII)

src/test/java/com/cocos/trading/
├── model/instrument/                         # DailyClose, DailyCloses — plain Java
├── service/search/                           # SearchQuery, SearchPage, InstrumentMatch, InstrumentMatches, SearchProperties
├── persistence/                              # Testcontainers: InstrumentRepository, MarketDataRepository
└── controller/                               # Testcontainers + MockMvc: quickstart scenarios 1-11
```

**Structure Decision**: the single Maven module and the four layers of feature 001. `service` grouped
by use case, each with its own request and response models (constitution VIII, enforced by
ArchUnit); `model` by concept; `controller` and `persistence` by role. Nothing in `model/instrument` depends on `model/ledger` or
`model/portfolio`; search and portfolio share `InstrumentRef`, `DecimalPolicy` and the
latest-price query, and nothing else.

## Phase 0 and Phase 1 outputs

| Artifact | Contents |
|---|---|
| [research.md](research.md) | 16 decisions with alternatives and rationale (R-101 to R-116); R-103, R-104 and R-108 superseded by ADR-0027; the dataset measurements; two questions put to the author and answered on 2026-11-16 (Q-101 `id` as a final tie-break — yes; Q-102 a paging counter — no) |
| [data-model.md](data-model.md) | The two tables as read, the search query clause by clause against the requirements it serves, the model, the service and its collaborators, the configuration, and a requirement-to-owner map |
| [contracts/instrument-search.openapi.yaml](contracts/instrument-search.openapi.yaml) | The design-time proposal, kept as it was; the live contract is `docs/api/openapi.yaml` |
| [quickstart.md](quickstart.md) | Eleven validation scenarios with expected figures, all reachable from the provided seed with no fixture |

## Requirement coverage

| Requirement | Where it is satisfied |
|---|---|
| FR-001, FR-021, FR-022 | `InstrumentSearchController` + `InstrumentSearchService`, read-only, no user id, `orders` never read |
| FR-002, FR-003, FR-004, FR-024 | `InstrumentRepository.search`: `locate(lower(:query), lower(column)) > 0` over ticker or name |
| FR-005 | accents compared as stored; `unaccent` deferred (ADR-0027) |
| FR-006 | one row per instrument in the result set |
| FR-007, FR-008, FR-009 | `SearchQuery`'s factory, against `SearchLimits` |
| FR-010 | `locate` has no wildcards: nothing to escape (asserted: `%`, `_`, `'`, `\` → 0 rows) |
| FR-011 | no match is an empty `results` with `200` and a total of zero |
| FR-012 | `InstrumentMatch`, carrying the whole `InstrumentRef` |
| FR-013, FR-014 | ticker, then id, fixed in the `ORDER BY` of `InstrumentRepository.search` |
| FR-015, FR-015b | `Pageable` over that total order, with the count derived from the same query under the same snapshot |
| FR-015a | `SearchPage`'s factory, which rejects rather than clamps |
| FR-015c | `SearchProperties` and the three environment variables; the size echoed, the maximum named in rejections |
| FR-016 | `MarketDataRepository.findLatestMarketData`, latest row per instrument |
| FR-017, FR-020 | `DailyClose.dailyReturnPercent()` via `DecimalPolicy.percent` (ADR-0016) |
| FR-018 | `DailyCloses.of` returns `DailyClose.unknown()` for an instrument with no market data |
| FR-019 | `DailyClose.dailyReturnPercent()` absent when the divisor is absent or `compareTo(ZERO) == 0` |
| FR-023 | `i.type <> :cashType`, bound by `InstrumentRepository.searchNonCash` |

## Complexity Tracking

| Decision | Why needed | Simpler alternative rejected because |
|---|---|---|
| **Three statements per request — page, count, prices — under one `REPEATABLE_READ` snapshot** ([R-112](research.md#r-112-one-read-only-repeatable_read-transaction-around-both-statements)) | `totalElements`, `results` and the prices must describe one instant, or a response says "59 matched" while returning a page drawn from a different snapshot. | One statement with a lateral join and a window count was the first design; it is what made the query indefensible (ADR-0027). `READ COMMITTED` gives each statement its own snapshot, which is the defect described. |

## Answered by the author

| # | Question | Answer | What shipped |
|---|---|---|---|
| Q-101 | Add `id` as the final tie-break? | **yes** | ticker then id, in the query's `ORDER BY`: the schema does not enforce ticker uniqueness, and a partial order under `LIMIT`/`OFFSET` is how paged results silently repeat or skip a row |
| Q-102 | A Micrometer counter for searches that span more than one page? | **no** | Nothing: ADR-0026 states nothing collects these metrics in this exercise |
| — | Fold accents? | **no, deferred** (2026-11-16) | Accents compared as stored; `unaccent` written down as considered (ADR-0027) |
| — | Rank by relevance? | **no** (2026-11-16) | Ticker order |
| — | Are pesos a result? | **no** (2026-11-16) | `MONEDA` excluded: pesos are the currency assets are paid in, not a tradable asset (ADR-0027) |

## Deferred, by decision

- **Accent-insensitive matching** — `unaccent`, once staging is measured to allow the extension;
  the steps are in ADR-0027.
- **A trigram index (`pg_trgm`)** — the real fix for sublinear matching, with the steps in
  [R-111](research.md#r-111-no-index-today-and-the-exact-reason-why). Irrelevant at 66 rows.
- **Typo tolerance and similarity ranking** — forbidden in this feature by FR-024, and a new
  feature with its own decision if ever wanted.
- **Cursor pagination** over `(ticker, id)` — the escape hatch if the table grows and deep offsets
  start to hurt. Offset paging is the right answer for a market of 65 instruments.
