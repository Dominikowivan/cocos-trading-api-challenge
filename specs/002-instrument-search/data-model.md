# Phase 1 Data Model: Instrument Search

**Feature**: `002-instrument-search` | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md) |
**Research**: [research.md](research.md) | **Design**: [ADR-0027](../../docs/adr/0027-instrument-search-is-one-short-jpql-query.md)

What this feature reads, what it maps it into, and which rule each piece owns. It describes
contracts and rules, not the shape of the code: the names below name the question each piece
answers, never how it answers it.

Rewritten on 2026-11-16 after the implementation was simplified (ADR-0027): the first version of
this document described a native query with a folding function, relevance tiers and a lateral
join. That design and why it was dropped are in the ADR and in [research.md](research.md).

## 1. Given schema (read as-is)

Two tables, read, never written (FR-022). Identifiers are lowercase in PostgreSQL because
`V1__given_schema.sql` writes them unquoted.

| Column | Type | Read as | Used for |
|---|---|---|---|
| `instruments.id` | `SERIAL` | `long` | FR-012 — the id the order endpoint consumes; the final tie-break (FR-014) |
| `instruments.ticker` | `VARCHAR(10)` | `String` | matched (FR-002) and the sort key (FR-013) |
| `instruments.name` | `VARCHAR(255)` | `String` | matched (FR-002) |
| `instruments.type` | `VARCHAR(10)` | `String` | reported; `MONEDA` excludes the row (FR-023) |
| `marketdata.instrumentid` | `INT` | `Integer` | which instrument a price row belongs to |
| `marketdata.close` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent | FR-016, FR-018 |
| `marketdata.previousclose` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent | FR-017, FR-019 |
| `marketdata.date` | `DATE` | not mapped | orders the rows so the latest one per instrument is picked (FR-016) |

`orders` and `users` are **not read** (FR-021). `marketdata.high`, `low` and `open` are not read.

### Change to the schema

None. This feature adds no migration, no function and no index. Why no index:
[R-111](research.md#r-111-no-index-today-and-the-exact-reason-why) — a btree cannot serve substring
containment, and the trigram index that could needs an extension this project does not install.

## 2. Persistence

Spring Data JPA repositories over JPA entities, mapped by hand (ADR-0014). Entities and
projections never leave the package (constitution VIII, enforced by `ArchitectureTest`).

| Element | Contract |
|---|---|
| `InstrumentRepository` | **new**. `search(query, cashType, pageable)`: one JPQL `@Query` returning a `Page` of `InstrumentEntity`; Spring Data derives the count from the same query. The query itself ends in `ORDER BY i.ticker, i.id`, so paging is stable by construction whoever calls it. Called through `searchNonCash(text, page, size)`, a default method that binds the cash type from `InstrumentRef.CASH`, so no caller repeats it |
| `InstrumentEntity` | **exists, unchanged** |
| `MarketDataRepository.findLatestMarketData` | **exists, extended**: the `DISTINCT ON (instrumentid) … ORDER BY instrumentid, date DESC` query the portfolio uses, now also returning `previousclose`. The portfolio ignores the new column |
| `LatestMarketData` | **exists, extended**: interface projection `instrumentId`, `close`, and now `previousClose` |
| `InstrumentSearchMapper` | **new**. Translates only a page of entities → model `InstrumentRef`s. The `LatestMarketData` rows → a model `DailyCloses` belong to `MarketDataMapper.toDailyCloses`, next to the portfolio's `toCloses`, and the ids go through its one `toInstrumentIdColumns`. What an instrument with no row gets is the model's decision, not the mapper's |

**What the search query guarantees**, each clause earning its place:

| Clause | Requirement |
|---|---|
| `locate(lower(:query), lower(column)) > 0`, for ticker **or** name | FR-002, FR-003, FR-004, FR-024 — literal, case-insensitive containment |
| `locate`, not `LIKE` | FR-010 — no wildcards, so `%`, `_`, quotes and backslashes need no escaping |
| no accent folding | FR-005 — accents must be typed as stored |
| `i.type <> :cashType`, the value bound from the model, inside the parentheses' `AND` | FR-023 |
| ticker, then id, fixed in the query's `ORDER BY` | FR-013, FR-014, and the total order FR-015b needs for paging |
| `Pageable` | FR-015 — the page, and the derived count for `totalElements` |

Prices are a second statement over the ids of the page only: two fixed statements per request,
never one per result.

## 3. Model

Plain Java. No Spring, no Jakarta, no JPA, no web type, no reference to a repository (constitution
VIII). Everything below is testable with no database and no test double (constitution V).

Package `model/instrument/`, which feature 001 created for `InstrumentRef`. It holds only what the
business knows about an instrument and its price.

| Type | Holds | Answers |
|---|---|---|
| `InstrumentRef` | **exists**: id, ticker, name, type | `isCash()` — type is `MONEDA`. Its constant `CASH` is public, and the repository binds it rather than repeating the value (FR-023, [R-110](research.md#r-110-cash-is-excluded-by-type-as-a-bound-parameter)) |
| `DailyClose` | `close` and `previousClose` of the latest market data row, either possibly absent | `close()`; `dailyReturnPercent()` — absent when `close` is absent, when `previousClose` is absent, or when `previousClose` compares equal to zero (FR-019); otherwise `(close − previousClose) / previousClose` in percent units, scale 2 `HALF_UP` via `DecimalPolicy.percent` (FR-017, FR-020, ADR-0016). Not called a quote: in trading a quote is a live bid/ask, and this is end-of-day data |
| `DailyCloses` | the `DailyClose` of each priced instrument | `of(instrument)` — its `DailyClose`, or `DailyClose.unknown()` when it has no market data (FR-018) |

## 4. Service

`InstrumentSearchService` and everything below sit together in `service/search/` (constitution
VIII: `service` grouped by use case). They are the use case's request and response models —
paging and validation, which belong to the API and depend on configuration, not to the business —
so they live with the service that applies them rather than in the model. The controller builds
only a `SearchRequest` from the raw parameters; the service validates it against the limits it
was configured with, so the controller never reads configuration.

| Type | Holds | Answers |
|---|---|---|
| `SearchRequest` | the raw `query`, `page` and `size` (absent when not sent), unvalidated | nothing: it is what the controller read, handed over as one value |
| `SearchQuery` | the trimmed query text | built by a factory that trims (FR-007) then rejects `null`, empty, blank (FR-008) and longer than the configured maximum (FR-009) by throwing. An instance is always valid |
| `SearchPage` | the requested page number and size | built by a factory that takes the configured default when no size was asked for, and rejects a negative page, a size below one and a size above the configured maximum (FR-015a) by throwing. Never clamps: a caller is never served a page they did not ask for |
| `SearchLimits` | maximum query length, default and maximum page size | the bounds the two factories validate against. **Values, not constants** — constructed from configuration (FR-015c, [R-106](research.md#r-106-the-limits-are-configuration-not-constants)) |
| `InvalidSearchRequestException` | which rule was broken, in its message | the one rejection this feature raises; the handler turns it into a `400` naming the limit in force |
| `InstrumentMatch` | one `InstrumentRef` and its `DailyClose` | the identity and the two figures one row of the answer carries (FR-012); `priced(instruments, dailyCloses)` — one per instrument, in the order given |
| `InstrumentMatches` | the query that was run, the results of one page, the page asked for, and the total that matched | `totalPages()`, derived from the total and the size rather than carried twice |
| `SearchProperties` | the three configured limits | `limits()`, the `SearchLimits` in force |

### The configuration

| Property | Default | Environment variable |
|---|---|---|
| `trading.search.default-page-size` | 20 | `TRADING_SEARCH_DEFAULT_PAGE_SIZE` |
| `trading.search.max-page-size` | 100 | `TRADING_SEARCH_MAX_PAGE_SIZE` |
| `trading.search.max-query-length` | 64 | `TRADING_SEARCH_MAX_QUERY_LENGTH` |

Bound into `SearchProperties`, whose `@DefaultValue`s are the only place the defaults are
written; `application.yaml` does not repeat them. An absent `size` reaches `SearchPage.of` as
`null` and becomes the configured default there, so the page-size policy lives in one place.

### The orchestration

`InstrumentSearchService.search(SearchRequest)` is the only method, inside one
`REPEATABLE_READ`, `readOnly` transaction
([R-112](research.md#r-112-one-read-only-repeatable_read-transaction-around-both-statements)) —
so the page, the total and the prices describe one instant and the response cannot contradict
itself:

1. build the `SearchQuery` and the `SearchPage` from the request against the configured limits,
   throwing `InvalidSearchRequestException` before anything is read;
2. ask `InstrumentRepository.search` for the page of matches, sorted by ticker then id, and map it
   to `InstrumentRef`s;
3. ask `MarketDataRepository.findLatestMarketData` for the latest row of those instruments only;
4. pair each instrument with its `DailyClose` through `InstrumentMatch.priced` and return the `InstrumentMatches` for the page asked for.

No logging and no diagnostics: unlike the portfolio, this feature detects no ledger anomaly. An
instrument with no price is an ordinary result, not an alarm.

## State transitions

None. The feature is read-only (FR-022) and holds no state between requests.

## Entity-to-requirement map

| Requirement | Owner |
|---|---|
| FR-001, FR-021, FR-022 | `InstrumentSearchService.search`, read-only, no user id, `orders` never read |
| FR-002, FR-003, FR-004, FR-024 | `InstrumentRepository.search`'s `locate(lower(…))` predicate over both columns |
| FR-005 | the absence of a fold: accents are compared as stored (ADR-0027) |
| FR-006 | one row per instrument in the result set; a match on both fields is still one row |
| FR-007, FR-008, FR-009 | `SearchQuery`'s factory, against `SearchLimits` |
| FR-010 | `locate`: no pattern syntax exists to escape |
| FR-011 | no match is an empty `results` with `200` and a total of zero |
| FR-012 | `InstrumentMatch`, carrying `InstrumentRef` |
| FR-013, FR-014 | ticker, then id, fixed in the `ORDER BY` of `InstrumentRepository.search` |
| FR-015, FR-015b | `Pageable` over that total order, with the derived count |
| FR-015a | `SearchPage`'s factory, which rejects rather than clamps |
| FR-015c | `SearchProperties`, with the page size echoed in the response and the maximum named in the rejection |
| FR-016 | `findLatestMarketData`, latest row per instrument |
| FR-017, FR-020 | `DailyClose.dailyReturnPercent()` via `DecimalPolicy.percent` |
| FR-018 | `DailyCloses.of` returns `DailyClose.unknown()` for an instrument with no market data |
| FR-019 | `DailyClose.dailyReturnPercent()` absent when the divisor is absent or `compareTo(ZERO) == 0` |
| FR-023 | `i.type <> :cashType`, bound by `InstrumentRepository.searchNonCash` |
