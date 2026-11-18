---

description: "Task list for feature 002-instrument-search"
---

# Tasks: Instrument Search

**Input**: Design documents from `/specs/002-instrument-search/`

**Prerequisites**: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: REQUIRED (constitution V). Every behaviour is tested at the level where it lives, and in this feature that weight sits at the **persistence** level, because the matching rule lives in the query ([R-102](research.md#r-102-the-database-executes-the-search)). Three levels: plain Java for the validating value types and the daily return, Testcontainers (`postgres:18.7`) for folding, matching, tiering, the lateral join and paging, Testcontainers + MockMvc for the HTTP contract. Writing them first is allowed, not required; a story is not done without them.

**Open questions, answered by the author on 2026-11-16 before this phase**:

- **Q-101 — yes**: `id` is the final tie-break, `ORDER BY tier, ticker, id` (T041). This reverses the "not added" default recorded in [research.md](research.md#q-101-should-id-be-the-final-tie-break-in-the-ordering) and in the plan; T061 records the answer in both.
- **Q-102 — no**: no Micrometer counter for searches that page. The default stands; nothing is built.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1-US4)
- Every task carries its exact file path

## Path Conventions

Single Maven module in four layers (plan § Project Structure, constitution VIII):

- Production: `src/main/java/com/cocos/trading/{controller,service,model,persistence}/…`
- Tests: `src/test/java/com/cocos/trading/…`, mirroring the production packages
- Resources: `src/main/resources/`

All PostgreSQL identifiers are **lowercase** (`instrumentid`, `previousclose`) — `V1__given_schema.sql` writes them unquoted (data-model § 1).

## How the stories were sliced

Each story is a vertical slice that ships an endpoint a user can call. The response envelope and the paging fields exist from US1 (with the default page), so the shape does not churn; what each story adds is what the user observes:

| Story | The endpoint after it | What it adds to the query |
|---|---|---|
| US1 (P1) | ticker matches, default page, total reported | folded `strpos` on `ticker`, cash excluded, `ORDER BY ticker`, `LIMIT`/`OFFSET`, the count |
| US2 (P2) | name matches too, accents folded both ways | the same predicate over `name` |
| US3 (P3) | best matches first, any page, caller-chosen size | the relevance `CASE`, the `ticker, id` tie-break, validated `page`/`size` |
| US4 (P4) | each result carries its price and the day's move | `LEFT JOIN LATERAL` on `marketdata` |

`docs/api/openapi.yaml` is therefore extended in four steps, once per story — the live contract always describes what is actually served (ADR-0024), and the web tests validate against it, so it cannot lag.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: the project already builds and feature 001 ships. This phase adds only the two things this feature needs before any code.

- [X] T001 Create the test package directory `src/test/java/com/cocos/trading/model/instrument/` and confirm `src/test/java/com/cocos/trading/{persistence,controller,service}/` already exist; the production package `src/main/java/com/cocos/trading/model/instrument/` exists from feature 001 (`InstrumentRef`)
- [X] T002 [P] Add the `trading.search` block to `src/main/resources/application.yaml` with `default-page-size: 20`, `max-page-size: 100` and `max-query-length: 64`, placed beside the existing `spring` and `management` blocks and left out of the `staging` document so one set of defaults serves every profile (data-model § The configuration, [R-106](research.md#r-106-the-limits-are-configuration-not-constants))
- [X] T003 Run `./mvnw verify` and confirm the baseline is green before any feature code — `TradingApiApplicationTests` boots against Testcontainers, so this is the line that says a later failure is ours

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: the migration, the configured limits and the rejection shape that every story depends on. No story work can begin until this phase is complete.

**⚠️ CRITICAL**: `search_fold` is the single home of the folding rule (FR-004, FR-005). Both the stored columns and the query parameter pass through it, inside the same statement, so it is never written twice and never written in Java.

- [X] T004 Create `src/main/resources/db/migration/V3__instrument_search_fold.sql` with the `search_fold(TEXT)` function exactly as in data-model § 1 — `LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE`, `lower(translate(…))` in that order so the fold does not depend on the server collation. No table, no column, no index ([R-111](research.md#r-111-no-index-today-and-the-exact-reason-why)), no `CREATE EXTENSION` ([R-115](research.md#r-115-no-postgresql-extension-is-installed)); `V1` and `V2` are untouched (constitution VI). No SQL comments (constitution VII)
- [X] T005 [P] Create `SearchLimits` in `src/main/java/com/cocos/trading/model/instrument/SearchLimits.java` — a record carrying `maxQueryLength`, `defaultPageSize` and `maxPageSize` as values, with no Spring import and no constant defaults of its own (data-model § 3, FR-015c)
- [X] T006 [P] Create `InvalidSearchRequestException` in `src/main/java/com/cocos/trading/model/instrument/InvalidSearchRequestException.java` — one unchecked exception for the whole family of bad requests, carrying a message that names the rule broken and the limit in force ([R-113](research.md#r-113-rejections-are-rfc-9457-problemdetail-from-the-existing-handler))
- [X] T007 Create `SearchProperties` in `src/main/java/com/cocos/trading/service/SearchProperties.java` — `@ConfigurationProperties("trading.search")` with the three values and the defaults of T002, exposing the model's `SearchLimits`. It lives in `service` because the model may not import Spring and a new top-level package would sit outside the ArchUnit layer checks (data-model § The configuration)
- [X] T008 Enable the binding in `src/main/java/com/cocos/trading/TradingApiApplication.java` with `@ConfigurationPropertiesScan` (or `@EnableConfigurationProperties(SearchProperties.class)`), whichever leaves the application class with one annotation less to repeat per feature
- [X] T009 Add the two rejection mappings to `src/main/java/com/cocos/trading/controller/ApiExceptionHandler.java`, reusing its existing private `problem(status, detail)` helper: `InvalidSearchRequestException` → `400` with the exception's message, and `MissingServletRequestParameterException` → `400` naming the missing parameter. `MethodArgumentTypeMismatchException` is already mapped by feature 001 and is not touched ([R-113](research.md#r-113-rejections-are-rfc-9457-problemdetail-from-the-existing-handler))
- [X] T010 [P] Add the `INSTRUMENT_SEARCH = "/api/v1/instruments/search"` constant to `src/test/java/com/cocos/trading/controller/WebTestBase.java`, beside the existing `PORTFOLIO`, so every web test in this feature names the endpoint once
- [X] T011 [P] Create `src/test/java/com/cocos/trading/persistence/SearchFoldTest.java` (extends `PersistenceTestBase`, Testcontainers): `search_fold` lowercases, strips the diacritics of the three names in the dataset in both directions (`Río`→`rio`, `Compañía`→`compania`, `Zorraquín`→`zorraquin`), returns `NULL` for `NULL` (`STRICT`), and is `IMMUTABLE` — asserted by creating an expression index over it and dropping it again, which is how [R-103](research.md#r-103-one-folding-function-in-sql-applied-to-both-sides) verified it
- [X] T012 [P] Create `src/test/java/com/cocos/trading/service/SearchPropertiesTest.java`: the defaults bind to 20, 100 and 64 with nothing configured, and a `@SpringBootTest(properties = …)` override produces different `SearchLimits` — the binding half of FR-015c, with the HTTP half asserted in T039

**Checkpoint**: migration applied, limits configured and observable, rejections shaped. Story work can begin.

---

## Phase 3: User Story 1 - Find an instrument by ticker (Priority: P1) 🎯 MVP

**Goal**: `GET /api/v1/instruments/search?query=pam` returns the instruments whose ticker contains the query, in any case, each carrying the id the order endpoint consumes — one page of them, with the total stated.

**Independent Test**: search `PAMP`, `pamp` and `pam` against the provided dataset; all three return instrument 47. `ZZZZ` returns an empty list with `200`, a blank query returns `400`, and `ars` returns nothing because cash is never a result. No name matching and no prices are needed to judge any of it.

### Tests for User Story 1 (REQUIRED - constitution V)

- [X] T013 [P] [US1] Create `src/test/java/com/cocos/trading/model/instrument/SearchQueryTest.java` (plain Java, no Spring, no database): `"  pamp  "` becomes `pamp` (FR-007); `null`, `""` and `"   "` are rejected (FR-008); a query one character over the configured maximum is rejected and the message names the maximum, one character under it is accepted (FR-009); the text is left unfolded, because folding is SQL's job
- [X] T014 [P] [US1] Create `src/test/java/com/cocos/trading/persistence/InstrumentSearchRepositoryTest.java` (extends `PersistenceTestBase`, Testcontainers, provided seed): `pamp` and `PAMP` both return instrument 47 and only it (FR-004); `pam` returns it too (FR-003); `ZZZZ` returns no rows and a count of 0 (FR-011); `ars` and `pesos` return nothing because `i.type <> :cashType` excludes instrument 66 (FR-023); `%`, `_`, `'` and `\` are matched literally and return no rows, never the whole table (FR-010, SC-011); `bnaco` returns nothing (FR-024); the count query agrees with the number of rows the page query returns when everything fits on one page
- [X] T015 [P] [US1] Create `src/test/java/com/cocos/trading/model/instrument/InstrumentSearchTest.java` (plain Java): `totalPages()` is derived from the total and the page size — 59 over 20 is 3, 59 over 59 is 1, 0 over 20 is 0 — rather than carried as a second field that could disagree (data-model § 3)
- [X] T016 [P] [US1] Create `src/test/java/com/cocos/trading/controller/InstrumentSearchTickerTest.java` (extends `WebTestBase`, Testcontainers + MockMvc, every response validated against `docs/api/openapi.yaml`): quickstart scenario 1 — `PAMP`, `pamp` and `pam` return the same single result with `"query"` echoed in its trimmed form, and `query=%20%20pamp%20%20` returns the identical body; plus the `200`/`400` split of scenario 9 — `ZZZZ` is `200` with `results: []` and `totalElements: 0`, a whitespace-only query is `400` `application/problem+json`, a missing `query` parameter is `400`, a 65-character query is `400` and a 50-character one is `200` with an empty page

### Implementation for User Story 1

- [X] T017 [P] [US1] Create `SearchQuery` in `src/main/java/com/cocos/trading/model/instrument/SearchQuery.java` — a value built by a factory taking the raw text and the `SearchLimits`, which trims then throws `InvalidSearchRequestException` for null, empty, blank and over-long. An instance is always valid; no caller re-checks it ([R-107](research.md#r-107-the-query-is-a-model-value-type-that-rejects-at-construction))
- [X] T018 [P] [US1] Create `SearchPage` in `src/main/java/com/cocos/trading/model/instrument/SearchPage.java` with the `first(SearchLimits)` factory only — page 0 at the configured default size. The validating factory that accepts a caller's page and size arrives in US3 (T040), which is the story that lets a caller ask
- [X] T019 [P] [US1] Create `SearchResult` in `src/main/java/com/cocos/trading/model/instrument/SearchResult.java` carrying one `InstrumentRef` — the id, ticker, name and type of FR-012. It gains the `Quote` in US4 (T051)
- [X] T020 [US1] Create `InstrumentSearch` in `src/main/java/com/cocos/trading/model/instrument/InstrumentSearch.java` — the results of one page, the page asked for, the size in force and the total that matched, answering `totalPages()` (depends on T018, T019)
- [X] T021 [P] [US1] Create the projection `InstrumentMatchRow` in `src/main/java/com/cocos/trading/persistence/InstrumentMatchRow.java` with `instrumentId`, `ticker`, `name` and `type`; it gains `close` and `previousClose` in US4 (T052). It never leaves the persistence package (constitution VIII, `ArchitectureTest`)
- [X] T022 [US1] Create `InstrumentSearchRepository` in `src/main/java/com/cocos/trading/persistence/InstrumentSearchRepository.java` with two native queries over the same `WHERE`: the page — `WITH folded AS (SELECT search_fold(CAST(:query AS TEXT)) AS q)`, `strpos(search_fold(i.ticker), f.q) > 0`, `i.type <> :cashType`, `ORDER BY i.ticker`, `LIMIT`/`OFFSET` — and the count, with no join and no order. It takes the trimmed query text and folds it itself ([R-102](research.md#r-102-the-database-executes-the-search), data-model § 2)
- [X] T023 [US1] Create `InstrumentSearchMapper` in `src/main/java/com/cocos/trading/persistence/mapper/InstrumentSearchMapper.java` turning `InstrumentMatchRow`s into model `SearchResult`s by hand (ADR-0014), as `MarketDataMapper` does for feature 001 (depends on T019, T021)
- [X] T024 [US1] Create `InstrumentSearchService` in `src/main/java/com/cocos/trading/service/InstrumentSearchService.java` — one public method `search(SearchQuery, SearchPage)` reading as the summary of its three steps (constitution IX), inside one `@Transactional(isolation = REPEATABLE_READ, readOnly = true)` so the page and the total describe one instant ([R-112](research.md#r-112-one-read-only-repeatable_read-transaction-around-both-statements)). It binds the cash type from `InstrumentRef`'s constant, never a literal in the query ([R-110](research.md#r-110-cash-is-excluded-by-type-as-a-bound-parameter)), and logs nothing: an instrument with no price is an ordinary result, not an anomaly (depends on T022, T023)
- [X] T025 [P] [US1] Create `InstrumentMatchResponse` in `src/main/java/com/cocos/trading/controller/dto/InstrumentMatchResponse.java` — a record with `instrumentId`, `ticker`, `name` and `type`; it gains `close` and `dailyReturnPercent` in US4 (T055)
- [X] T026 [US1] Create `InstrumentSearchResponse` in `src/main/java/com/cocos/trading/controller/dto/InstrumentSearchResponse.java` — a record with `query`, `page`, `size`, `totalElements`, `totalPages` and `results`, built by a static factory from the `SearchQuery` and the `InstrumentSearch`, as `PortfolioResponse.of` is. Not Spring Data's `Page` ([R-116](research.md#r-116-the-response-carries-the-page-its-size-the-totals-and-the-results)) (depends on T025)
- [X] T027 [US1] Create `InstrumentSearchController` in `src/main/java/com/cocos/trading/controller/InstrumentSearchController.java` — `@RestController` on `/api/v1/instruments/search`, `query` required, building a `SearchQuery` and `SearchPage.first(…)` from the configured limits, one service call, one DTO out. No rule, no `try`/`catch`: the rejection travels as the exception T009 maps (depends on T024, T026)
- [X] T028 [US1] Add the `searchInstruments` operation, the `InstrumentSearch` and `InstrumentMatch` schemas (both `additionalProperties: false`) and the `query` parameter to `docs/api/openapi.yaml`, taking them from [contracts/instrument-search.openapi.yaml](contracts/instrument-search.openapi.yaml) and including only what this story serves — the `page` and `size` parameters arrive in T045, the price fields in T056 (ADR-0024, [R-114](research.md#r-114-the-contract-goes-straight-into-the-live-openapi-document-and-the-http-collection)). The design-time proposal under `contracts/` is never edited
- [X] T029 [P] [US1] Add an instrument search section to `docs/api/trading-api.http` with the ticker requests of quickstart scenario 1 and the two `400`s of scenario 9, beside the existing portfolio section
- [X] T030 [US1] Refactor: with this story's tests green, review the code it added against constitution IX (one thing per method, the public method as the summary of its steps, the steps below it in call order, no performance-driven structure without a measurement) and clean it; the tests stay green and no behaviour changes

**Checkpoint**: a working search endpoint. A user resolves a ticker to the id the order endpoint consumes, in one request, and the rejection paths ship with it.

---

## Phase 4: User Story 2 - Find an instrument by name (Priority: P2)

**Goal**: a user who does not remember the ticker types part of the company's name and finds it, regardless of case and of accents — in both directions.

**Independent Test**: `banco` returns exactly `BBAR`, `BHIP`, `BMA` and `BPAT`; `rio` returns four including `MOLI` (`Molinos Río de la Plata`); `compania` returns `INTR`; `metr` returns one result, not two.

### Tests for User Story 2 (REQUIRED - constitution V)

- [X] T031 [P] [US2] Extend `src/test/java/com/cocos/trading/persistence/InstrumentSearchRepositoryTest.java`: `banco` and `BANCO` both return the four banks (FR-002, FR-004); `rio` returns four rows including `MOLI` and `río` returns the same four, `compania` returns `INTR` and `zorraquin` returns `GARO` — folding applied to both sides (FR-005); `metr` returns exactly one row although the query matches both the ticker and the name of `METR` (FR-006); the count query carries the name predicate too, so it agrees with the rows
- [X] T032 [P] [US2] Create `src/test/java/com/cocos/trading/controller/InstrumentSearchNameTest.java` (extends `WebTestBase`): quickstart scenarios 2 and 3 — `banco` returns the four tickers, `BANCO` returns the identical list, `rio` and `río` return four including `MOLI`, `compania` returns `INTR`; and `bnaco` is `200` with an empty page, because matching is literal (FR-024)

### Implementation for User Story 2

- [X] T033 [US2] Extend the page query's predicate in `src/main/java/com/cocos/trading/persistence/InstrumentSearchRepository.java` to `strpos(search_fold(i.ticker), f.q) > 0 OR strpos(search_fold(i.name), f.q) > 0`, both sides through the same `search_fold` as the ticker already is — one row per instrument however many fields match (FR-002, FR-005, FR-006)
- [X] T034 [US2] Extend the count query in the same file with the identical predicate, so the total and the page can never describe different sets (FR-015)
- [X] T035 [US2] Add the `nameFamily` and accent-folding examples to the `searchInstruments` operation in `docs/api/openapi.yaml`, and the `banco`, `rio` and `compania` requests to `docs/api/trading-api.http`
- [X] T036 [US2] Refactor: with this story's tests green, review the code it added against constitution IX (one thing per method, the public method as the summary of its steps, the steps below in call order, no performance-driven structure without a measurement) and clean it; the tests stay green and no behaviour changes

**Checkpoint**: both halves of the statement's "por ticker y/o por nombre" work, and the three accented names are reachable from a keyboard-typed query.

---

## Phase 5: User Story 3 - Best matches first, one page at a time (Priority: P3)

**Goal**: the closest matches come first, the caller can ask for any page at a size of their choosing up to the configured maximum, and a request outside the limits is rejected rather than quietly adjusted.

**Independent Test**: `gr` returns `GRIM` above `MOLA`; `bma` returns `BMA` first; walking the pages of `a` returns all 59 matches exactly once with the fourth page empty and still reporting 59; `size=500` and `page=-1` are both `400`; two identical requests return identical bodies.

### Tests for User Story 3 (REQUIRED - constitution V)

- [X] T037 [P] [US3] Create `src/test/java/com/cocos/trading/model/instrument/SearchPageTest.java` (plain Java): a negative page, a size of 0 and a size one above the configured maximum are rejected and the message names the maximum in force (FR-015a); a size exactly at the maximum is accepted; **the factory never clamps** — the asserted outcome of an out-of-range request is an exception, never a different page; `first(limits)` is page 0 at the default size
- [X] T038 [P] [US3] Extend `src/test/java/com/cocos/trading/persistence/InstrumentSearchRepositoryTest.java` with the ordering and paging: `gr` returns the nine matches tier by tier — `GRIM`, then `GCLA`, `GGAL`, `SUPV`, then `AGRO`, `LOMA`, `MOLA`, `OEST`, `PGR` — each tier alphabetical (FR-013, FR-014); `bma` returns `BMA` first; the three pages of `a` at size 20 return 20, 20 and 19 rows with nothing repeated and nothing skipped and a total of 59 on every one of them, page 3 empty with the same total (FR-015b); the same query run twice returns the same rows in the same order (SC-007)
- [X] T039 [P] [US3] Create `src/test/java/com/cocos/trading/controller/InstrumentSearchPagingTest.java` (extends `WebTestBase`): quickstart scenarios 4, 5, 6, 7 and 11 — the tier order of `gr` and `bma`, `metr` reporting `totalElements: 1`, `a` reporting `size: 20`, `totalElements: 59`, `totalPages: 3` and 20 results, the four-page walk, `size=59` returning all 59 in one page, `size=500`/`size=0`/`page=-1` each `400` with the limit named, and two identical requests returning identical bodies. Include one `@SpringBootTest(properties = "trading.search.default-page-size=5")` case asserting the echoed `size` is 5 — the HTTP half of FR-015c, quickstart scenario 8
- [X] T040 [US3] Add the validating factory to `src/main/java/com/cocos/trading/model/instrument/SearchPage.java` — it takes the requested page, the requested size and the `SearchLimits`, and throws `InvalidSearchRequestException` naming the limit for a negative page, a size below 1 or a size above the maximum. It never clamps (FR-015a)
- [X] T041 [US3] Add the relevance ordering to the page query in `src/main/java/com/cocos/trading/persistence/InstrumentSearchRepository.java`: `ORDER BY CASE WHEN search_fold(i.ticker) = f.q THEN 1 WHEN starts_with(search_fold(i.ticker), f.q) THEN 2 WHEN starts_with(search_fold(i.name), f.q) THEN 3 ELSE 4 END, i.ticker, i.id` (FR-013, FR-014). The `i.id` is **Q-101, answered yes by the author on 2026-11-16**: the schema does not enforce ticker uniqueness and a partial order under `LIMIT`/`OFFSET` is how pages silently repeat or skip a row. The tier is an ordering, never a field in the response
- [X] T042 [US3] Make the page query take the page and size from the validated `SearchPage` in the same file, replacing the default-only paging of T022
- [X] T043 [US3] Add the optional `page` and `size` parameters to `src/main/java/com/cocos/trading/controller/InstrumentSearchController.java`, `size` defaulted through a `${trading.search.default-page-size}` placeholder so an absent `size` is filled in at the HTTP boundary where defaults belong, and build the `SearchPage` through the validating factory of T040 (data-model § The configuration)
- [X] T044 [US3] Thread the requested page through `InstrumentSearchService` and `InstrumentSearchResponse` so `page`, `size` and `totalPages` report what was served; the count query already runs under the same `REPEATABLE_READ` snapshot (T024), which is what makes the total and the page agree
- [X] T045 [US3] Add the `page` and `size` parameters, the `pagedBroadQuery` and `pagePastTheEnd` examples and the three rejection examples to the `searchInstruments` operation in `docs/api/openapi.yaml` — the maximum is described as the configured default rather than asserted as a fixed `maximum`, since a deployment may change it ([R-114](research.md#r-114-the-contract-goes-straight-into-the-live-openapi-document-and-the-http-collection)); add the paging requests to `docs/api/trading-api.http`
- [X] T046 [US3] Refactor: with this story's tests green, review the code it added against constitution IX (one thing per method, the public method as the summary of its steps, the steps below in call order, no performance-driven structure without a measurement) and clean it; the tests stay green and no behaviour changes

**Checkpoint**: a filter has become a search. The order is total, the pages fit together, and no caller is ever served a page they did not ask for.

---

## Phase 6: User Story 4 - See what each instrument is worth (Priority: P4)

**Goal**: each result carries the instrument's latest `close` in pesos and the day's move, so a user choosing what to trade reads the price in the same request — and an instrument with no market data is still returned, with both figures absent.

**Independent Test**: `pamp` reports `close: 925.85` and `dailyReturnPercent: 0.44`, computed by hand from instrument 47's latest row; `pgr` reports both as `null`; `agro` reports a genuine `0.00`, which is a different answer from unknown.

### Tests for User Story 4 (REQUIRED - constitution V)

- [X] T047 [P] [US4] Create `src/test/java/com/cocos/trading/model/instrument/QuoteTest.java` (plain Java): `(close − previousClose) / previousClose` in percent units at scale 2 `HALF_UP` via `DecimalPolicy.percent` (FR-017, FR-020); absent when `close` is absent, when `previousClose` is absent, and when `previousClose` is `0` **or** `0.00` — the case that proves the guard is `compareTo`, not `equals` (FR-019, constitution II); the price is still reported when only the return is absent; a flat day is `0.00`, not absent. No `double` anywhere in the test
- [X] T048 [P] [US4] Extend `src/test/java/com/cocos/trading/persistence/InstrumentSearchRepositoryTest.java`: the lateral join returns the **latest row per instrument**, asserted across two instruments whose latest rows carry different dates, never one shared date (FR-016); `PGR` and `IRCP` come back with `null` prices rather than being dropped, which is what the `LEFT` buys (FR-018); the dataset's two rows per instrument never produce two rows in the result (FR-006)
- [X] T049 [P] [US4] Create `src/test/java/com/cocos/trading/controller/InstrumentSearchPricesTest.java` (extends `WebTestBase`): quickstart scenarios 1 and 10 — `pamp` carries `close: 925.85` and `dailyReturnPercent: 0.44`, `pgr` carries `instrumentId: 3` with `close: null` and `dailyReturnPercent: null` and **never** `0` (ADR-0013, SC-009), and `agro` carries `0.00`, the contrast that distinguishes a known zero from an unknown figure. Both keys are always present in the JSON

### Implementation for User Story 4

- [X] T050 [P] [US4] Create `Quote` in `src/main/java/com/cocos/trading/model/instrument/Quote.java` holding `close` and `previousClose`, either possibly absent, answering `close()` and `dailyReturnPercent()`. The divisor is guarded with `compareTo(ZERO) == 0`, never `equals` (constitution II, FR-019), and the arithmetic goes through `DecimalPolicy.percent` (ADR-0016). No method takes an `Optional` (constitution IX)
- [X] T051 [US4] Add the `Quote` to `SearchResult` in `src/main/java/com/cocos/trading/model/instrument/SearchResult.java` — one `InstrumentRef` and its `Quote`, the identity and the two figures one row of the answer carries (depends on T050)
- [X] T052 [P] [US4] Add `close` and `previousClose` to the projection `src/main/java/com/cocos/trading/persistence/InstrumentMatchRow.java` as nullable `BigDecimal`s — `NULL` is how FR-018 arrives, rather than being special-cased
- [X] T053 [US4] Add `LEFT JOIN LATERAL (SELECT m.close, m.previousclose FROM marketdata m WHERE m.instrumentid = i.id ORDER BY m.date DESC LIMIT 1) p ON TRUE` and the two columns to the page query in `src/main/java/com/cocos/trading/persistence/InstrumentSearchRepository.java`. The count query is **not** touched — it needs no price and no join (data-model § 2). `marketdata.previousclose` is read here for the first time; feature 001's `findLatestCloses` stays untouched ([R-108](research.md#r-108-the-page-query-reads-the-price-the-portfolios-query-is-untouched))
- [X] T054 [US4] Map the two columns into a `Quote` in `src/main/java/com/cocos/trading/persistence/mapper/InstrumentSearchMapper.java` — a `null` price becomes an absent figure, never zero (ADR-0013) (depends on T050, T052)
- [X] T055 [US4] Add `close` and `dailyReturnPercent` to `src/main/java/com/cocos/trading/controller/dto/InstrumentMatchResponse.java`, serialising an absent figure as JSON `null` with the key always present ([R-116](research.md#r-116-the-response-carries-the-page-its-size-the-totals-and-the-results))
- [X] T056 [US4] Add the two fields to the `InstrumentMatch` schema in `docs/api/openapi.yaml` (nullable, `additionalProperties: false` kept) with the `unpricedInstrument` example, and add the `pgr` and `agro` requests to `docs/api/trading-api.http`
- [X] T057 [US4] Refactor: with this story's tests green, review the code it added against constitution IX (one thing per method, the public method as the summary of its steps, the steps below in call order, no performance-driven structure without a measurement) and clean it; the tests stay green and no behaviour changes

**Checkpoint**: all four stories work independently. The one rule in the statement that no other endpoint implements — the daily return from `close` and `previousClose` — now lives somewhere.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: the reasoning, the documentation and the end-to-end validation the constitution requires before this feature is done.

- [X] T058 [P] Write `docs/adr/0027-the-database-executes-the-instrument-search.md` from [R-102](research.md#r-102-the-database-executes-the-search), carrying the rejected alternative (matching in Java over the whole catalogue) next to the accepted one, the folding function of R-103, the no-index reasoning of R-111, the extensions rejected in R-115, and the tension with constitution III stated rather than argued away (constitution VII)
- [X] T059 [P] Write `docs/adr/0028-search-results-are-paginated.md` from [R-105](research.md#r-105-pagination-with-a-total-count), recording that it reverses the earlier cap-plus-`truncated` position, and the configurable limits of R-106
- [X] T060 Add both rows to the index in `docs/adr/README.md` — number, title and status — in the same change as T058 and T059, never after (constitution VII)
- [X] T061 Record the author's answers of 2026-11-16 in `specs/002-instrument-search/research.md` (Q-101 answered **yes**, Q-102 answered **no**) and in the "Open for the author" table of `specs/002-instrument-search/plan.md`, so neither document keeps presenting a settled question as open or states a default that was overridden
- [X] T062 Update `README.md` with the instrument search endpoint and the three `trading.search.*` settings with their defaults and environment variables, wherever it lists endpoints and configuration (plan § Deferred, by decision)
- [X] T063 Run `./mvnw verify` and confirm the whole suite is green, including `ArchitectureTest` (the model imports no Spring, Jakarta or JPA type and holds no repository; entities and projections do not leave `persistence`; DTOs do not appear in service or model signatures) and the formatting check (ADR-0019)
- [X] T064 Walk quickstart scenarios 1 to 11 against a running application (`docker compose up -d && ./mvnw spring-boot:run`) and confirm every figure matches [quickstart.md](quickstart.md), including the `diff` of scenario 11
- [X] T065 Run quickstart scenario 8 — `TRADING_SEARCH_DEFAULT_PAGE_SIZE=5 docker compose --profile app up -d --build` — and confirm the same image serves `size: 5` with no rebuild of the jar (FR-015c, ADR-0025)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies
- **Foundational (Phase 2)**: depends on Setup — **blocks every story**; nothing can match before `search_fold` exists and nothing can be rejected before the limits and the handler do
- **User stories (Phases 3-6)**: all depend on Foundational. Here they are **sequential, not parallel**: US2, US3 and US4 each extend the same two queries in `InstrumentSearchRepository`, which is the direct consequence of the matching rule living in SQL ([R-102](research.md#r-102-the-database-executes-the-search)). Each story is still independently testable and independently demonstrable
- **Polish (Phase 7)**: depends on every story that is being shipped

### User Story Dependencies

- **US1 (P1)**: after Foundational. The MVP, and the only story that creates the endpoint, the service and the DTOs
- **US2 (P2)**: after US1 — it widens US1's predicate. Independently testable: the `banco`, `rio` and `compania` assertions fail before it and pass after it, and US1's ticker assertions keep passing
- **US3 (P3)**: after US1. Independent of US2 in principle (relevance and paging apply to whichever predicate exists), but the measured fixtures — `gr` returning nine, `a` returning 59 — assume the name predicate, so building it after US2 is what makes its tests the ones in quickstart
- **US4 (P4)**: after US1. It adds columns and a join; it touches neither the predicate nor the ordering, so it genuinely does not depend on US2 or US3

### Within Each User Story

- Tests must exist and pass before the story is done; test-first is allowed, not required
- Model value types before the repository, repository before the service, service before the controller
- The contract (`docs/api/openapi.yaml`) before the web tests can pass — every response is validated against it (ADR-0024), so an unwritten schema fails the build
- Refactor last, with the story's tests green, before the story is marked done

### Parallel Opportunities

- T002 runs alongside T001
- **Phase 2**: T005, T006 in parallel; T010, T011 and T012 in parallel once T004 and T007 are in
- **Within each story**: all four US1 test tasks (T013-T016) are different files and can be written together; so can T017, T018, T019, T021 and T025; T031 and T032; T037, T038 and T039; T047, T048, T049 and, on the implementation side, T050 and T052
- **Phase 7**: T058 and T059 in parallel, then T060
- Across stories: none. The repository is one file and three stories extend it

---

## Parallel Example: User Story 1

```bash
# Launch all four test files for User Story 1 together:
Task: "SearchQueryTest in src/test/java/com/cocos/trading/model/instrument/SearchQueryTest.java"
Task: "InstrumentSearchRepositoryTest in src/test/java/com/cocos/trading/persistence/InstrumentSearchRepositoryTest.java"
Task: "InstrumentSearchTest in src/test/java/com/cocos/trading/model/instrument/InstrumentSearchTest.java"
Task: "InstrumentSearchTickerTest in src/test/java/com/cocos/trading/controller/InstrumentSearchTickerTest.java"

# Launch the independent value types together:
Task: "SearchQuery in src/main/java/com/cocos/trading/model/instrument/SearchQuery.java"
Task: "SearchPage in src/main/java/com/cocos/trading/model/instrument/SearchPage.java"
Task: "SearchResult in src/main/java/com/cocos/trading/model/instrument/SearchResult.java"
Task: "InstrumentMatchRow in src/main/java/com/cocos/trading/persistence/InstrumentMatchRow.java"
Task: "InstrumentMatchResponse in src/main/java/com/cocos/trading/controller/dto/InstrumentMatchResponse.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1: Setup
2. Phase 2: Foundational — blocks everything
3. Phase 3: User Story 1
4. **Stop and validate**: quickstart scenario 1 and the `400`/`200` split of scenario 9 against a running application
5. The endpoint already resolves a ticker to the id the order endpoint consumes, rejects a blank query and never returns cash

### Incremental Delivery

1. Setup + Foundational → the fold exists and the limits are configurable
2. + US1 → ticker search, paged by default, totals reported (**MVP**)
3. + US2 → name search, accents folded both ways
4. + US3 → best matches first, any page, caller-chosen size, bad requests rejected
5. + US4 → the price and the day's move on every result
6. Each step leaves the previous assertions green and extends the live contract in the same change

### Parallel Team Strategy

Limited by design, and the reason is worth stating: the matching rule lives in one SQL file ([R-102](research.md#r-102-the-database-executes-the-search)), so US2, US3 and US4 all edit `InstrumentSearchRepository`. One developer carries the query through the four stories; a second can usefully take the test files, the contract and the examples (T028, T029, T035, T045, T056) and the Phase 7 documents (T058-T062) alongside.

---

## Notes

- [P] = different files, no dependencies on incomplete tasks
- [Story] maps each task to the user story it serves, for traceability back to spec.md
- Every story is independently completable, testable and demonstrable
- No comments in any source file, SQL included — the reasoning lives in these documents (constitution VII)
- `BigDecimal` on every money path, compared with `compareTo`; no `double` or `float`, tests included (constitution II)
- The model imports no Spring, Jakarta, JPA or web type and holds no repository (constitution VIII, enforced by `ArchitectureTest`)
- Commit after each task or logical group; stop at any checkpoint to validate the story on its own
