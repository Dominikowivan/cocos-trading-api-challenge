# Phase 1 Data Model: Instrument Search

**Feature**: `002-instrument-search` | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md) |
**Research**: [research.md](research.md)

What this feature reads, what it maps it into, and which rule each piece owns. It describes
contracts and rules, not the shape of the code: the names below name the question each piece
answers, never how it answers it.

The shape of this feature is unusual for this codebase and worth stating once: **the matching rule
lives in the query** ([R-102](research.md#r-102-the-database-executes-the-search)), so the model is
small — input validation and money arithmetic — and the persistence layer is where the behaviour
is. Everything else about the layering is unchanged.

## 1. Given schema (read as-is)

Two tables, read, never written (FR-022). Identifiers are lowercase in PostgreSQL because
`V1__given_schema.sql` writes them unquoted.

| Column | Type | Read as | Used for |
|---|---|---|---|
| `instruments.id` | `SERIAL` | `long` | FR-012 — the id the order endpoint consumes |
| `instruments.ticker` | `VARCHAR(10)` | `String` | matched and ranked (FR-002, FR-013); the tie-break key (FR-014) |
| `instruments.name` | `VARCHAR(255)` | `String` | matched and ranked (FR-002, FR-013) |
| `instruments.type` | `VARCHAR(10)` | `String` | reported; `MONEDA` excludes the row (FR-023) |
| `marketdata.instrumentid` | `INT` | — | correlates the lateral join |
| `marketdata.close` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent | FR-016, FR-018 |
| `marketdata.previousclose` | `NUMERIC(10,2)`, nullable | `BigDecimal` or absent | FR-017, FR-019 |
| `marketdata.date` | `DATE` | not mapped | orders the lateral join that picks the latest row per instrument (FR-016) |

`orders` and `users` are **not read** (FR-021). `marketdata.high`, `low` and `open` are not read.

### Change to the schema

One migration, `V3__instrument_search_fold.sql`, creating one function and nothing else — no
table, no column, no index:

```sql
CREATE FUNCTION search_fold(value TEXT) RETURNS TEXT
  LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE
  RETURN lower(translate(value,
    'áàäâãåéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÅÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ',
    'aaaaaaeeeeiiiiooooouuuuncAAAAAAEEEEIIIIOOOOOUUUUNC'));
```

It is the single home of the fold: both the stored text and the query parameter pass through it
(FR-004, FR-005). It carries no business rule — it normalises text, the way a collation does — and
it is `IMMUTABLE`, which was verified by building an expression index over it and dropping it
again ([R-103](research.md#r-103-one-folding-function-in-sql-applied-to-both-sides)).

Why `translate` before `lower` and not the other way round: with a `C` collation `lower('Á')` is
`'Á'`, so the fold is written not to depend on the server's collation. Why no index:
[R-111](research.md#r-111-no-index-today-and-the-exact-reason-why) — a btree cannot serve substring
containment, and the trigram index that could needs an extension this project does not install.

`V1__given_schema.sql` and `V2__portfolio_read_indexes.sql` are untouched.

## 2. Persistence

Spring Data JPA repositories over JPA entities, mapped by hand (ADR-0014). Entities and
projections never leave the package (constitution VIII, enforced by `ArchitectureTest`).

| Element | Contract |
|---|---|
| `InstrumentSearchRepository` | **new**. Two statements over the same predicate: one page of matches, ordered by relevance then ticker, and the count of all matches. Takes the raw trimmed query text — it folds it itself, in SQL — plus the value that means cash and the paging. Returns a page of `InstrumentMatchRow` and a total |
| `InstrumentMatchRow` | **new** interface projection: `instrumentId`, `ticker`, `name`, `type`, `close`, `previousClose`. The last two are `null` for an instrument with no market data row, which is how FR-018 arrives rather than being special-cased |
| `InstrumentEntity` | **exists, unchanged**. Not read by this feature — the page query is native and projects directly |
| `MarketDataEntity`, `LatestClose`, `findLatestCloses` | **unchanged**. The portfolio still never reads `previousClose` ([R-108](research.md#r-108-the-page-query-reads-the-price-the-portfolios-query-is-untouched)) |
| `InstrumentSearchMapper` | **new**. `InstrumentMatchRow` rows → model `SearchResult`s: the identity into an `InstrumentRef`, the two prices into a `Quote`. A `null` price becomes an absent figure, never zero (ADR-0013) |

**What the page query guarantees**, each clause earning its place:

| Clause | Requirement |
|---|---|
| `search_fold(:query)` computed once in a `WITH`, compared against `search_fold` of each column | FR-004, FR-005 — both sides folded by one function |
| `strpos(folded_column, folded_query) > 0` | FR-003, FR-010, FR-024 — literal containment, no pattern syntax to escape |
| `i.type <> :cashType`, the value bound from the model | FR-023 |
| `CASE` over exact / `starts_with` ticker / `starts_with` name / else | FR-013 |
| `ORDER BY tier, i.ticker` | FR-014, and the total order FR-015b needs for paging |
| `LEFT JOIN LATERAL (… ORDER BY m.date DESC LIMIT 1)` | FR-016 — latest row **per instrument**; `LEFT` is what keeps an unpriced instrument in the page (FR-018) |
| `LIMIT`/`OFFSET` from the validated page | FR-015 |
| the count query, same `WHERE`, no join and no order | FR-015 — `totalElements` |

## 3. Model

Plain Java. No Spring, no Jakarta, no JPA, no web type, no reference to a repository (constitution
VIII). Everything below is testable with no database and no test double (constitution V).

Package `model/instrument/`, which feature 001 created for `InstrumentRef`.

| Type | Holds | Answers |
|---|---|---|
| `InstrumentRef` | **exists**: id, ticker, name, type | `isCash()` — type is `MONEDA`. Its constant is what the query binds as `:cashType` (FR-023, [R-110](research.md#r-110-cash-is-excluded-by-type-as-a-bound-parameter)) |
| `SearchQuery` | the trimmed query text | built by a factory that trims (FR-007) then rejects `null`, empty, blank (FR-008) and longer than the configured maximum (FR-009) by throwing. An instance is always valid. The text is passed to the query unfolded — folding is SQL's ([R-103](research.md#r-103-one-folding-function-in-sql-applied-to-both-sides)) |
| `SearchPage` | the requested page number and size | built by a factory that rejects a negative page, a size below one and a size above the configured maximum (FR-015a) by throwing. Never clamps: a caller is never served a page they did not ask for |
| `SearchLimits` | maximum query length, default and maximum page size | the bounds the two factories validate against. **Values, not constants** — constructed from configuration, so a deployment can change them without a rebuild (FR-015c, [R-106](research.md#r-106-the-limits-are-configuration-not-constants)) |
| `InvalidSearchRequestException` | which rule was broken, in its message | the one rejection this feature raises; the handler turns it into a `400` naming the limit in force |
| `Quote` | `close` and `previousClose`, either possibly absent | `close()`; `dailyReturnPercent()` — absent when `close` is absent, when `previousClose` is absent, or when `previousClose` compares equal to zero (FR-019); otherwise `(close − previousClose) / previousClose` in percent units, scale 2 `HALF_UP` via `DecimalPolicy.percent` (FR-017, FR-020, ADR-0016) |
| `SearchResult` | one `InstrumentRef` and its `Quote` | the identity and the two figures one row of the answer carries (FR-012) |
| `InstrumentSearch` | the results of one page, the page asked for, and the total that matched | `totalPages()`, derived from the total and the size rather than carried twice; the page is a value, not a cursor into anything |

The model does **not** contain the matching rule, the relevance tiers or the fold. Those are in
the query ([R-102](research.md#r-102-the-database-executes-the-search)), which is this feature's
one deliberate departure from how feature 001 is built, argued in the plan's Constitution Check.

### The configuration

| Property | Default | Environment variable |
|---|---|---|
| `trading.search.default-page-size` | 20 | `TRADING_SEARCH_DEFAULT_PAGE_SIZE` |
| `trading.search.max-page-size` | 100 | `TRADING_SEARCH_MAX_PAGE_SIZE` |
| `trading.search.max-query-length` | 64 | `TRADING_SEARCH_MAX_QUERY_LENGTH` |

Bound into a properties record in the `service` package — not the model, which may not import
Spring, and not a new top-level package, which would sit outside the layers ArchUnit checks. The
service turns it into the model's `SearchLimits`; the controller reads the default page size
through a property placeholder on its `size` parameter, so an absent `size` is filled in at the
HTTP boundary where defaults belong. One value in `application.yaml`, two readers, no second
definition.

### The service (orchestration)

`InstrumentSearchService.search(SearchQuery, SearchPage)` is the only method, inside one
`REPEATABLE_READ`, `readOnly` transaction
([R-112](research.md#r-112-one-read-only-repeatable_read-transaction-around-both-statements)) —
so the page and the total describe one instant and the response cannot contradict itself. It
reads, maps and returns:

1. ask the repository for the page of matches and the total;
2. map the rows into `SearchResult`s;
3. return the `InstrumentSearch` for the page that was asked for.

No logging and no diagnostics: unlike the portfolio, this feature detects no ledger anomaly. An
instrument with no price is an ordinary result, not an alarm.

## State transitions

None. The feature is read-only (FR-022) and holds no state between requests.

## Entity-to-requirement map

| Requirement | Owner |
|---|---|
| FR-001, FR-021, FR-022 | `InstrumentSearchService.search`, read-only, no user id, `orders` never read |
| FR-002, FR-003, FR-024 | the page query's `strpos` predicate over both columns |
| FR-004, FR-005 | `search_fold`, applied to both sides inside the query |
| FR-006 | one row per instrument in the result set; a match on both fields is still one row |
| FR-007, FR-008, FR-009 | `SearchQuery`'s factory, against `SearchLimits` |
| FR-010 | `strpos` and `starts_with`: no pattern syntax exists to escape |
| FR-011 | no match is an empty `results` with `200` and a total of zero |
| FR-012 | `SearchResult`, carrying `InstrumentRef` |
| FR-013, FR-014 | the `CASE` tier and `ORDER BY tier, ticker` |
| FR-015, FR-015b | `LIMIT`/`OFFSET` over that total order, plus the count query |
| FR-015a | `SearchPage`'s factory, which rejects rather than clamps |
| FR-015c | the properties above, with the page size echoed in the response and the maximum named in the rejection |
| FR-016 | the `LEFT JOIN LATERAL`, latest row per instrument |
| FR-017, FR-020 | `Quote.dailyReturnPercent()` via `DecimalPolicy.percent` |
| FR-018 | `LEFT` join plus `null` prices mapped to absent figures |
| FR-019 | `Quote.dailyReturnPercent()` absent when the divisor is absent or `compareTo(ZERO) == 0` |
| FR-023 | `i.type <> :cashType`, bound from the model |
