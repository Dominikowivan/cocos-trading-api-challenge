# Phase 0 Research: Instrument Search

**Feature**: `002-instrument-search` | **Date**: 2026-11-16 | **Spec**: [spec.md](spec.md)

This phase resolves what the spec deliberately left open — where matching happens, how text is
folded, how the answer is paged, what the configurable limits are, and how the daily return
reaches the response — and records the alternatives weighed for each.

The spec leaves three values to this phase ("concrete values are chosen at plan time"): the
default page size, the maximum page size and the maximum query length. They are
[R-105](#r-105-pagination-with-a-total-count) and [R-106](#r-106-the-limits-are-configuration-not-constants),
and all three are configuration rather than constants (FR-015c).

Nothing here restates the challenge statement (constitution, Technology and Scope Constraints) or
re-decides what an accepted ADR already settled; those are cited.

> **Two decisions were taken by the author on 2026-11-16, after a first version of this document
> proposed the opposite.** The search is executed by the database rather than in Java
> ([R-102](#r-102-the-database-executes-the-search)), and the answer is paginated rather than
> capped with a truncation flag ([R-105](#r-105-pagination-with-a-total-count)). Both earlier
> positions and why they were rejected are kept in those entries: the record of the exercise is
> the reasoning, and a decision reads better next to the argument it beat.

## Measured facts this phase relied on

Two sets of measurements, both taken on 2026-11-16, neither estimated.

**The dataset**, parsed from `src/main/resources/db/seed/V1000__given_dataset.sql`:

| Fact | Value |
|---|---|
| Instruments | 66 — 65 `ACCIONES`, 1 `MONEDA` (`ARS` / `PESOS`, id 66) |
| Tickers | unique, uppercase, 3 to 5 characters |
| Names | 5 to 42 characters; longest is `Compañía Introductora de Buenos Aires S.A.` (42) |
| Names with diacritics | 3: ids 13 (`INTR`), 16 (`GARO` — `Garovaglio Y Zorraquín`), 42 (`MOLI` — `Molinos Río de la Plata`) |
| Tickers or names containing `%` or `_` | none |
| `marketdata` | 126 rows, 63 instruments, exactly 2 rows each, dates `2023-07-13` and `2023-07-14` |
| Searchable instruments with no `marketdata` | 2: `PGR` (id 3) and `IRCP` (id 10) |
| `PAMP` (id 47) latest row | `2023-07-14`, `close` 925.85, `previousClose` 921.80 → daily return **0.44** |
| `BMA` (id 31) latest row | `close` 1502.80, `previousClose` 1520.25 → **-1.15** |
| `METR` (id 54) latest row | `close` 229.50, `previousClose` 232.00 → **-1.08** |
| `INTR` (id 13) latest row | `close` 77.00, `previousClose` 75.00 → **2.67** |
| `GRIM` (id 40) latest row | `close` 392.50, `previousClose` 397.50 → **-1.26** |
| `AGRO` (id 26) latest row | `close` 138.00, `previousClose` 138.00 → **0.00** |

The last line matters: a daily return of exactly zero exists in the dataset, so "absent" and
"zero" are distinguishable on real data with no constructed fixture (ADR-0013).

**The query**, run against a real `postgres:18.7-alpine` built from this project's own migrations
and seed — the design below is not sketched, it was executed:

| Probe | Result |
|---|---|
| `search_fold` is `IMMUTABLE` enough to index | yes — `CREATE INDEX … (search_fold(ticker) text_pattern_ops)` was accepted |
| Database collation of the image | `en_US.utf8`; the fold is written not to depend on it ([R-103](#r-103-one-folding-function-in-sql-applied-to-both-sides)) |
| Folding | `Compañía…` → `compania…`, `Molinos Río…` → `molinos rio…`, `Zorraquín` → `zorraquin`, `RÍO` → `rio` |
| Metacharacters | `strpos(…, '%') > 0` is **false** on every row — `%` matches nothing, not everything |
| `banco` | 4 rows: `BBAR`, `BHIP`, `BMA`, `BPAT`, all relevance tier 3, ticker order |
| `gr` | 9 rows: `GRIM` (tier 2), `GCLA` `GGAL` `SUPV` (tier 3), `AGRO` `LOMA` `MOLA` `OEST` `PGR` (tier 4) |
| `rio`, `compania`, `zorraquin`, `metr`, `pamp` | 4, 1, 1, 1, 1 |
| `ars`, `pesos`, `%`, `bnaco`, `ZZZZ` | 0 each |
| `a` | 59 |
| Paging `a` at size 20 | page 0 → 20, page 1 → 20, page 2 → 19, page 3 → empty. 59 total, no instrument repeated, none skipped |
| `pgr` | one row, `close` and `previousclose` both `NULL` — the unpriced instrument is returned, not dropped |
| Latest row per instrument | `PAMP` and `AGRO` resolve to `2023-07-14`; `PGR` and `IRCP` to no row at all |
| Plan for `a` | `Seq Scan on instruments` (59 of 66 rows pass the filter) + `top-N heapsort`, `shared hit=1` |

Every count matches the dataset measurements above, computed independently. The plan line is the
honest one: see [R-111](#r-111-no-index-today-and-the-exact-reason-why).

## Decisions

### R-101: The endpoint is `GET /api/v1/instruments/search?query=…`

**Decision**: one path, `/api/v1/instruments/search`, with the free text in a required `query`
parameter and the paging in optional `page` and `size` parameters. No path variable, no user id
(FR-021).

**Rationale**: the spec rejects the collection reading of this endpoint. FR-008 says a missing or
blank query is a `400` and explicitly **not** the whole market, so `GET /api/v1/instruments` —
which a client reads as "list the instruments" — would be a collection resource that refuses to
list its collection. A sub-resource named `search` says what it is and leaves
`GET /api/v1/instruments` and `GET /api/v1/instruments/{id}` free for later features that would
genuinely list or fetch.

**Alternatives considered**:

- `GET /api/v1/instruments?query=…` — the more conventional REST shape, and the one to prefer if
  the blank query returned the full list. It does not, so the shape would lie.
- `GET /api/v1/instruments?ticker=…&name=…` — two parameters matching the statement's "por ticker
  y/o por nombre" literally. Rejected: the statement describes what is matched, not what the user
  types; FR-002 is one query against both fields, and a user who types `banco` should not have to
  say which field they meant.
- `POST /api/v1/instruments/search` with a body. Rejected: the search writes nothing (FR-022) and a
  64-character query fits in a URL.

**Parameter names**: `query`, `page`, `size` — spelled out. `page` and `size` are what Spring Data
and most APIs call them, so a client guesses them correctly on the first try.

### R-102: The database executes the search

**Decision**: one native query filters, ranks and pages; the service receives one page of rows
already in order. SQL does the matching (`strpos` over folded text), the relevance tiering (a
`CASE` in the `ORDER BY`), the ticker tie-break and the `LIMIT`/`OFFSET`. Java keeps the
validation of the input and the money arithmetic of the daily return.

The shape, as verified against a real PostgreSQL 18.7:

```sql
WITH folded AS (SELECT search_fold(CAST(:query AS TEXT)) AS q)
SELECT i.id, i.ticker, i.name, i.type, p.close, p.previousclose
FROM instruments i
CROSS JOIN folded f
LEFT JOIN LATERAL (
    SELECT m.close, m.previousclose FROM marketdata m
    WHERE m.instrumentid = i.id ORDER BY m.date DESC LIMIT 1
) p ON TRUE
WHERE i.type <> :cashType
  AND (strpos(search_fold(i.ticker), f.q) > 0 OR strpos(search_fold(i.name), f.q) > 0)
ORDER BY CASE WHEN search_fold(i.ticker) = f.q             THEN 1
              WHEN starts_with(search_fold(i.ticker), f.q) THEN 2
              WHEN starts_with(search_fold(i.name), f.q)   THEN 3
              ELSE 4 END,
         i.ticker
```

**Rationale** — the author's, recorded as given on 2026-11-16: reading every instrument into the
JVM to filter it there does not scale. The work belongs where the data is, and only the rows of
one page should cross the wire. At 66 instruments the difference is invisible; at 66,000 it is the
difference between a page of 20 rows and a 66,000-row result set built on every keystroke, and an
endpoint fed by a search box is exactly the one that gets called on every keystroke.

Four consequences, each of which turned out better than the alternative design:

1. **The fold exists once.** The query text is folded by the same `search_fold` the stored columns
   go through, inside the same statement ([R-103](#r-103-one-folding-function-in-sql-applied-to-both-sides)).
   The objection that killed the earlier Java design — that the fold would exist twice, once in SQL
   for the stored text and once in Java for the query — does not apply, because Java never folds
   anything.
2. **There is no pattern syntax to escape.** `strpos(haystack, needle) > 0` is literal containment
   and `starts_with` is a literal prefix; neither interprets `%`, `_`, a quote or a backslash.
   FR-010 is satisfied by construction, exactly as `String.contains` would have, and it was
   measured: a query of `%` returns zero rows.
3. **The latest price comes along.** A `LEFT JOIN LATERAL … ORDER BY date DESC LIMIT 1` resolves
   the latest row **per instrument** (FR-016) in the same statement, so the page arrives complete.
   `LEFT` is what keeps `PGR` and `IRCP` in the result with `NULL` figures rather than dropping
   them (FR-018) — measured.
4. **Paging is the database's job and it does it exactly.** `LIMIT`/`OFFSET` over a total order —
   measured: 20 + 20 + 19 over 59 matches, nothing repeated, nothing skipped, and a page past the
   end comes back empty rather than wrong.

**What this costs, stated plainly**: the matching rule and the relevance order now live in SQL
rather than in Java. That is a real tension with constitution III, and it is argued in the plan's
Constitution Check and Complexity Tracking rather than waved through here. The practical price is
that the rule can only be tested against a running PostgreSQL — Testcontainers, not a plain-Java
unit test — so this feature's test weight sits at the persistence level instead of the model
level. The compensating fact is that those tests exercise the real engine, which constitution V
values over speed.

**Alternatives considered**:

- **Matching in Java over the whole catalogue** (`findAll()`, fold and rank in a model object).
  Proposed first, and rejected by the author for the scaling reason above. It would have put every
  rule in plain Java and made every acceptance scenario a millisecond unit test; it would also have
  made the endpoint's cost grow with the instrument table, with a `SELECT *` on every keystroke.
  The trade is real in both directions and the author's call is the one recorded.
- **Matching in SQL, ranking in Java** — filter with the query, then tier and sort the page in the
  service. Rejected as the worst of both: ranking after `LIMIT` ranks the wrong rows, and ranking
  before `LIMIT` means fetching every match anyway, which is the design that was just rejected.
- **Full-text search (`tsvector`)**: wrong tool. `to_tsquery` matches whole lexemes, so `pam` would
  not find `PAMP` and `metr` would not find `MetroGAS`. FR-003 is substring containment, which is
  what a trader typing into a box expects.

**ADR candidate 0027** — *The database executes the instrument search*. It has real alternatives,
it binds anything that later searches instruments, and it is the decision that most needs its
reasoning visible, both because it is a deliberate tension with principle III and because the
author chose it over a documented alternative.

### R-103: One folding function in SQL, applied to both sides

**Decision**: a migration creates one function, used on the stored columns **and** on the query
parameter:

```sql
CREATE FUNCTION search_fold(value TEXT) RETURNS TEXT
  LANGUAGE SQL IMMUTABLE STRICT PARALLEL SAFE
  RETURN lower(translate(value,
    'áàäâãåéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÅÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ',
    'aaaaaaeeeeiiiiooooouuuuncAAAAAAEEEEIIIIOOOOOUUUUNC'));
```

**Rationale**:

- **It is the single home of the fold.** Both sides of every comparison pass through it, in the
  same statement, so FR-004 and FR-005 hold in both directions — measured: `rio` finds `Río` and
  `RÍO` finds `rio`. There is no second spelling of the rule in Java to drift from it.
- **`translate` maps the accented capitals too, and `lower` runs after it.** That is deliberate:
  with a `C` collation, `lower('Á')` is `'Á'`, so a fold written as `translate(lower(…))` would
  silently stop working on a differently configured server. This image's collation was measured as
  `en_US.utf8`, where either order works; the order chosen is the one that does not depend on the
  measurement.
- **It is `IMMUTABLE`, so it can be indexed.** Verified by creating an expression index over
  `search_fold(ticker)` and dropping it again. The day the table outgrows a scan, the index has a
  function to be built on ([R-111](#r-111-no-index-today-and-the-exact-reason-why)).
- `STRICT` makes a `NULL` name fold to `NULL` rather than to the empty string, so a row with a
  `NULL` ticker or name cannot accidentally match every query.

**On constitution III**: this is a function in the database, which the principle treats with
suspicion. It carries no business rule — it normalises text, the way a collation does — and the
rule that *uses* it (what matches, what ranks above what) is in the query, not in the function.
It is recorded in the plan's Complexity Tracking rather than passed over.

**Alternatives considered**:

- **`unaccent`** — the obvious answer, and rejected twice over. `CREATE EXTENSION` generally needs
  superuser; the provided database is staging (ADR-0009) and we do not know that our credentials
  there have it, so the migration might pass on Testcontainers and fail on staging, which is the
  worst possible asymmetry. And `unaccent()` is not `IMMUTABLE` (it reads a dictionary), so it
  cannot appear in an index expression without an `IMMUTABLE` wrapper — which is the hand-written
  function above, with an extension added for nothing. **Author's decision, 2026-11-16: no
  extensions.**
- **Repeating the `translate(…)` expression inline** in the `WHERE` and the `ORDER BY` instead of
  naming it. Rejected: the character map would appear four times in one statement and again in
  any future query, which is the duplication a name exists to prevent.
- **A stored generated column** (`GENERATED ALWAYS AS (search_fold(ticker)) STORED`) plus an index.
  Viable and `IMMUTABLE`-compatible, and the natural companion to the trigram index of R-111.
  Deferred: it alters the provided table for a performance benefit nothing has measured yet, which
  is the structure-before-measurement constitution IX forbids.
- **Folding the query in Java, the columns in SQL** — the duplicated rule described in R-102.

### R-104: Relevance is a four-branch `CASE`, and the tie-break is the ticker

**Decision**: the `ORDER BY` ranks exact ticker (1), ticker prefix (2), name prefix (3), anything
else that matched (4), then orders by ticker ascending within a tier (FR-013, FR-014). The tier is
not returned to the client; it is an ordering, not a figure.

**Rationale**: the second key makes the order **total**, which is what SC-007 and every exact-list
assertion depends on — and, now that the answer is paged, what makes FR-015b possible at all. A
partial order plus `LIMIT`/`OFFSET` is how pages silently repeat and skip rows: without a unique
final key, PostgreSQL is free to return tied rows in any order on each execution, so page 0 and
page 1 could both contain the same instrument. The ticker is unique in the dataset, which makes
the order total in practice; see the honest caveat below.

**Measured, on the dataset** (the two scenarios that discriminate):

- `gr` → `GRIM` (ticker prefix) first, then `GCLA`, `GGAL`, `SUPV` (name prefix: all three names
  start with `Grupo`), then `AGRO`, `LOMA`, `MOLA`, `OEST`, `PGR`, each group alphabetical.
  Scenario 3.2 asserts `GRIM` above `MOLA`; this ordering delivers it.
- `banco` → all four are name-prefix matches, so the tier does not separate them and the ticker
  does: `BBAR`, `BHIP`, `BMA`, `BPAT`.

**The caveat, stated rather than hidden**: the schema does not enforce ticker uniqueness, and the
spec's edge case says two instruments could share one. If that ever happened, `(tier, ticker)`
would stop being a total order and paging could repeat a row. Adding `i.id` as a final tie-break
would close it for one extra sort key and no behaviour change. It is **not** added here, because
the spec says the search does not deduplicate and the ordering requirement is written in terms of
the ticker; it is flagged as the one-line change if ticker uniqueness is ever in doubt.

**Alternatives considered**: returning the tier as a field (rejected — it is a presentation
detail, and a client that sorts by it would be re-implementing the server's job); scoring by match
position (rejected — not asked for, and harder to predict from the response than four stated
tiers); sorting by name in the tie-break (rejected — the spec treats the ticker as the user-facing
identity, and feature 001 already orders positions by ticker).

### R-105: Pagination with a total count

**Decision**: `page` (zero-based) and `size` parameters; the response carries the page, the size in
force, the total number of matches and the number of pages, plus the results. Two statements per
request: the page query above and a count query over the same `WHERE`.

**Rationale** — the author's, recorded as given on 2026-11-16: a cap discards matches with no way
for the client to reach them, and telling a user "there is more, type something narrower" is
making them guess at a query to retrieve data the API already found. Pagination hands them the
rest. The total count is the part the truncation flag could only hint at: a client that sees
`totalElements: 59` knows exactly how much they have not seen.

**What it costs**: the `COUNT(*)`, a second scan of the same predicate. At 66 rows it is free
(measured: one shared buffer hit). At scale it is the standard cost of offset pagination, and the
standard escape is a cursor — deferred below rather than built.

**Rejected, and recorded because the spec originally said the opposite**: a cap of 20 results plus
a `truncated` boolean. The argument was that 65 instruments do not justify pages. It was rejected
on the grounds above, and the spec's assumption was rewritten in the same change rather than left
to contradict this document.

**Alternatives considered**:

- **Page/size without a total**, signalled by fetching `size + 1` rows and reporting "there is
  more". Cheaper — no `COUNT(*)` — but the client never learns how many matched, which is the
  property that made pagination worth the change.
- **Cursor pagination** over `(tier, ticker)`. Stable when data changes between pages and it does
  not degrade at high offsets, but it cannot jump to an arbitrary page and it is considerably more
  machinery for a market of 65 instruments. Recorded as the escape hatch if the table grows and
  deep offsets start to hurt.

**ADR candidate 0028** — *Search results are paginated, with a total count*. It shapes the API
contract, it binds the next feature that returns a list, and it reverses a documented position.

### R-106: The limits are configuration, not constants

**Decision**: three values, with defaults, bound from configuration and therefore settable by
environment variable without rebuilding (FR-015c):

| Property | Default | Environment variable | Why that default |
|---|---|---|---|
| `trading.search.default-page-size` | **20** | `TRADING_SEARCH_DEFAULT_PAGE_SIZE` | Comfortably above every meaningful family in the dataset (`gr` → 9, `banco` → 4), so no realistic search is paged at all, and below 59, so a one-letter query pages and SC-005 is observable on the provided data |
| `trading.search.max-page-size` | **100** | `TRADING_SEARCH_MAX_PAGE_SIZE` | Above the 65 searchable instruments, so a client that genuinely wants the whole market can ask for it in one request; low enough to be a real ceiling against `size=100000` |
| `trading.search.max-query-length` | **64** | `TRADING_SEARCH_MAX_QUERY_LENGTH` | The longest name is 42 characters, so 43 to 64 is a band where a query is accepted, matches nothing and returns an empty page — which is the behaviour the spec's "very long query" edge case distinguishes from rejection. Both halves stay observable |

**Rationale** — the author's, 2026-11-16: the right page size depends on traffic nobody has
measured, so it is a dial an operator turns, not a number a release changes. Spring Boot's relaxed
binding gives the environment-variable spelling for free, which is what makes it work in Docker
(ADR-0025) with one `-e` and no rebuild.

Extending the same treatment to the maximum page size and the maximum query length is **this
plan's call, not the author's request**: they are the same kind of number, read from the same
place, and leaving two of the three hard-coded would be the odd choice to explain. Flagged here so
it can be reverted cheaply if the intent was narrower.

**A request for a size above the maximum is rejected, not clamped** (FR-015a). Clamping serves a
page the caller did not ask for and then reports a `size` they did not choose; the caller cannot
tell whether they received page 3 of their paging or of ours. A `400` naming the maximum is one
round trip and no ambiguity.

**Alternatives considered**: constants in the code (rejected by FR-015c); a database-backed
setting (ceremony, and a read on every request); making the values per-client (nothing asks for
it, and it would let a caller raise their own ceiling, which FR-015a forbids).

### R-107: The query is a model value type that rejects at construction

**Decision**: `SearchQuery` is a model value type. A factory takes the raw text and the configured
maximum length, trims (FR-007), and rejects `null`, empty, blank (FR-008) and over-long (FR-009)
by throwing a model exception. `SearchPage` does the same for `page` and `size` against the
configured bounds (FR-015a). Nothing downstream can hold an invalid request. The controller builds
both, exactly as `PortfolioController` builds a `UserId`.

**Rationale**: constitution IX — a value type that names a domain concept needs no second use.
With the matching now in SQL, this validation is most of what is left in Java, and it is the part
that most deserves a plain-Java test: four rejections and a trim, asserted in milliseconds. It
also guards the boundary that matters: the trimmed text is what reaches the parameter binding, so
an untrimmed or unbounded string cannot reach the database.

The alternative — Jakarta `@NotBlank @Size(max = 64)` on the controller parameters — puts three
functional requirements in annotations on the web layer, where the model tests cannot reach them,
and cannot express FR-007's "trim, then check" without a custom validator. It also cannot read the
maximum from configuration: `@Size(max = …)` needs a compile-time constant.

### R-108: The page query reads the price; the portfolio's query is untouched

**Decision**: the `LEFT JOIN LATERAL` in R-102's statement carries `close` and `previousclose` for
the page's rows. `MarketDataRepository.findLatestCloses` and its `LatestClose` projection — the
portfolio's — are not changed.

**Rationale**: feature 001 states, in its spec (FR-009), its plan and the live OpenAPI document,
that the portfolio **never reads** `previousClose`. Widening its projection would make that
sentence false in the code while it stays true in the documents. The two statements resolve
"latest row per instrument" differently — `DISTINCT ON` for a set of ids, `LATERAL` for a row at a
time — because they are asked different questions: the portfolio has its ids first, the search
discovers them in the same pass. Duplicated *structure*, which constitution IX permits leaving
alone; the duplicated *rule* would be the fold, and that one exists once.

`MarketDataEntity` is untouched: both queries are native and map to projections, so the entity
that has no `previousClose` field is not involved.

**Alternatives considered**: a second `DISTINCT ON` query for the page's ids, keeping the search
to two statements plus a count (rejected — a third round trip for data the page query can carry,
and it reintroduces the id round-trip the `LATERAL` removes); one shared projection for both
features (rejected above).

### R-109: The daily return is computed in Java

**Decision**: `(close − previousClose) / previousClose × 100`, in percent units, scale 2,
`HALF_UP` — `DecimalPolicy.percent(part, whole)`, the method feature 001 already uses for the total
return, unchanged (ADR-0016, FR-020). Absent when the instrument has no market data row (FR-018),
when `close` is `NULL`, and when `previousClose` is `NULL` or compares equal to zero (FR-019).
`close` is reported whenever it is known, even when the return is not.

**Rationale**: this is the one piece that stays in Java even though the rest of the feature moved
to SQL, and deliberately. Constitution II governs money arithmetic — `BigDecimal`, a stated scale,
a stated `RoundingMode`, `compareTo` and never `equals` — and `DecimalPolicy` is where that policy
lives for the whole API. Computing it in SQL would put a second rounding policy in a second
language, and `NUMERIC` division would have to restate the scale at each call site. The division
by zero of FR-019 is also a branch, which is a rule, which belongs in Java.

**Measured check** (by hand, against the seed): `PAMP` 925.85 against 921.80 → `4.05 / 921.80 ×
100` = `0.4393…` → **0.44**. `AGRO` 138.00 against 138.00 → **0.00**, a known zero, which is not
the same answer as `PGR`'s absent return.

**Alternatives considered**: computing it in the query (above); a ratio rather than percent units
(rejected for consistency with `totalReturnPercent`, whose name says percent); scale 4 (rejected —
ADR-0016 is the policy, and a second scale is a second policy).

### R-110: Cash is excluded by type, as a bound parameter

**Decision**: the query carries `i.type <> :cashType`, and the value is bound from the model's
constant, not written as a literal in the SQL.

**Rationale**: FR-023 is written against the **type**, not the id or the ticker, so a second
currency added later is excluded with no change. Binding the value rather than inlining `'MONEDA'`
keeps the one definition of "what cash is" in the model, next to `InstrumentRef.isCash()` that
feature 001 wrote — the same pattern as 001's `OrderMapper.toStatusColumns(OrderStatus.contributing())`,
where SQL selects rows and Java says which rows mean what.

### R-111: No index today, and the exact reason why

**Decision**: the migration creates the folding function and **no index**. The feature ships with
a sequential scan over `instruments`.

**Rationale**, measured rather than assumed: `EXPLAIN ANALYZE` of the broadest query (`a`) is a
`Seq Scan` over 66 rows plus a top-N heapsort, touching **one** shared buffer. There is nothing to
accelerate. More importantly, an index would not help even if the table were large, and this is
the part worth being precise about:

- A btree index — including one over `search_fold(ticker)` — serves equality and **prefix**
  predicates. It does not serve substring containment, which is what FR-003 requires. Verified: a
  prefix query was planned as a `Seq Scan` on this table anyway, because 66 rows never justify an
  index scan, but the general rule stands regardless of the plan chosen here.
- The predicate that *would* be indexable is a trigram GIN index (`pg_trgm`), which needs an
  extension this project does not install (R-103's reasoning, author's decision).

So the honest statement is: **moving the search into the database buys locality, not an index.**
Only one page of rows crosses the wire instead of the whole table, the filter runs where the data
is, and the work is bounded by `LIMIT` — which is the scaling argument, and it holds. What it does
not buy, today, is sublinear matching.

**The upgrade path, so the next reader does not have to derive it**: install `pg_trgm`, add a
stored generated column holding `search_fold(ticker)` and `search_fold(name)`, build a GIN
`gin_trgm_ops` index over it, and change `strpos(…) > 0` to `LIKE '%' || … || '%'` — because a
trigram index serves `LIKE`, not `strpos`. That last step reintroduces the escaping FR-010 needs,
so it is a real change and not a config flip, which is why it is written down here instead of
being half-built now.

### R-112: One read-only `REPEATABLE_READ` transaction around both statements

**Decision**: the service method carries
`@Transactional(isolation = REPEATABLE_READ, readOnly = true)`, as `PortfolioService` does
(feature 001, R-012).

**Rationale**: the feature issues two statements — the page and the count — and a response whose
`totalElements` was computed against a different snapshot than its `results` is a response that
contradicts itself: 59 matches reported, 20 returned, and the two numbers describing different
instants. Under PostgreSQL's default `READ COMMITTED` each statement gets its own snapshot, so that
is not hypothetical. SC-007 wants the same answer twice; this is what makes one answer internally
consistent in the first place. The cost is nil: a read-only snapshot takes no locks and blocks no
writer. `readOnly = true` also states FR-022 to the framework rather than only in prose.

### R-113: Rejections are RFC 9457 `ProblemDetail`, from the existing handler

**Decision**: the existing `ApiExceptionHandler` gains the mappings; all are `400`
`application/problem+json`, reusing its `problem(status, detail)` helper and the `Problem` schema
already in the contract:

| Cause | Thrown by | Detail says |
|---|---|---|
| Missing `query` parameter | Spring (`MissingServletRequestParameterException`) | which parameter is missing |
| Empty or blank query (FR-008) | the model, from `SearchQuery` | that the query must not be blank |
| Query over the maximum (FR-009) | the model, from `SearchQuery` | the maximum in force |
| `size` above the maximum, or below 1 (FR-015a) | the model, from `SearchPage` | the maximum in force |
| Negative `page` (FR-015a) | the model, from `SearchPage` | that the page must not be negative |
| `page`/`size` not integers | Spring (`MethodArgumentTypeMismatchException`) | already handled by feature 001 |

**Rationale**: one error shape for the whole API (001's R-013). The model rejections are one family
— the caller sent a request that cannot be served — so one exception type carrying a message is
enough rather than five classes. Naming the limit in the message is what makes FR-015c's "the
values in force are observable" true for the maximum, as echoing `size` makes it true for the
default.

### R-114: The contract goes straight into the live OpenAPI document and the `.http` collection

**Decision**: `docs/api/openapi.yaml` gains the operation, the `InstrumentSearch` and
`InstrumentMatch` schemas and the worked examples; `docs/api/trading-api.http` gains an instrument
search section. `specs/002-instrument-search/contracts/instrument-search.openapi.yaml` is this
phase's design-time proposal and is not edited after implementation.

**Rationale**: ADR-0024 and the constitution's Technology and Scope Constraints, verbatim. The
practical consequence: because every response in the web tests is validated against the live
document, the contract is written *before* the functional tests pass, and
`additionalProperties: false` on both new schemas means an accidental extra field fails the build.

One contract detail the configurability forces: `size`'s maximum is a deployment setting, so the
document states the **default** maximum and says it is configurable, rather than asserting a
`maximum: 100` that a differently configured deployment would violate. Response validation is
unaffected — the limits shape requests, not responses.

### R-115: No PostgreSQL extension is installed

**Decision**: neither `unaccent` nor `pg_trgm`. No `CREATE EXTENSION` in any migration. **Author's
decision, 2026-11-16**, taken with the index consequence of R-111 on the table.

**Rationale** — the reasons, any one sufficient:

1. **Staging may refuse it.** `CREATE EXTENSION` generally requires superuser. The provided
   database is staging (ADR-0009) and we have no measurement that our credentials there allow it.
   A migration that passes on Testcontainers and fails on staging is the worst kind of difference
   between environments, and it would fail at deploy time, not at build time.
2. **`unaccent()` is not `IMMUTABLE`**, so it cannot be indexed without a wrapper function — which
   is the hand-written function of R-103, with an extension added for nothing.
3. **`pg_trgm` is the only one that would buy anything** (R-111), and what it buys is unmeasurable
   on a 66-row table.

`pg_trgm` is additionally the wrong feature for FR-024: it provides *similarity*, and the spec says
matching is literal and typo-intolerant. The day typo tolerance is wanted, it is a new feature with
its own decision, and that is the day `pg_trgm` becomes the right answer for two reasons at once.

### R-116: The response carries the page, its size, the totals and the results

**Decision**: a JSON object — `query` (the trimmed text, echoed), `page`, `size`, `totalElements`,
`totalPages`, `results` (array, possibly empty). Each result carries `instrumentId`, `ticker`,
`name`, `type`, `close` and `dailyReturnPercent` (FR-012, FR-016, FR-017).

**Rationale**: an envelope rather than a bare array, because FR-015 needs somewhere to put the
paging. The echoed `query` is the trimmed, canonical form, so a caller that sent `"  pamp  "` can
see what was actually searched (FR-007) — the cheapest possible proof that trimming happened. The
echoed `size` is what makes the configured default observable (FR-015c) without exposing a
settings endpoint. `type` is carried because FR-012 asks for it; on this dataset it is `ACCIONES`
on every result, since `MONEDA` is excluded, and it stops being constant the day a second tradable
type appears.

Keys are always present, including `close` and `dailyReturnPercent` as `null` (ADR-0013, 001's
R-010). An empty page is `results: []` with `200` (FR-011, FR-015b), which is what distinguishes
"nothing matched" and "that page is past the end" from the `400` of a blank query (SC-010).

**Not Spring Data's `Page` serialised directly**: its JSON shape is an implementation detail Spring
itself warns about pinning, it carries `pageable`, `sort` and `numberOfElements` nobody asked for,
and `additionalProperties: false` would then be a contract over a framework's internals. The
repository may return a `Page`; the response DTO is this feature's own.

## ADR candidates

Written at implementation time, each with `docs/adr/README.md` updated in the same change
(constitution VII).

| # | Title | From |
|---|---|---|
| 0027 | The database executes the instrument search | [R-102](#r-102-the-database-executes-the-search), with the folding function in [R-103](#r-103-one-folding-function-in-sql-applied-to-both-sides), the index reasoning in [R-111](#r-111-no-index-today-and-the-exact-reason-why) and the extensions rejected in [R-115](#r-115-no-postgresql-extension-is-installed) |
| 0028 | Search results are paginated, with a total count | [R-105](#r-105-pagination-with-a-total-count), with the configurable limits of [R-106](#r-106-the-limits-are-configuration-not-constants) |

Decisions that had alternatives but are **internal to this feature** and owe no ADR: the endpoint
shape (R-101), the relevance tiers (R-104), the validation value types (R-107), the `LATERAL`
rather than a second `DISTINCT ON` (R-108), the response envelope (R-116). Each is recorded here
with its alternatives, which is where a reviewer asking "why not `ILIKE`?" or "why not Spring's
`Page`?" will look.

Decisions already made elsewhere and merely applied: ADR-0007 (no auth, no user id), ADR-0009
(staging), ADR-0013 (absent, never zero), ADR-0014 (Spring Data JPA, mapped by hand), ADR-0016
(scale and rounding), ADR-0017 (layers), ADR-0024 (one living contract), ADR-0025 (Docker, which is
what makes R-106's environment variables worth having).

## Open questions for the author

Neither blocks implementation.

### Q-101: Should `id` be the final tie-break in the ordering?

One extra sort key, `ORDER BY tier, ticker, id`. It costs nothing measurable and it closes the
hole described in [R-104](#r-104-relevance-is-a-four-branch-case-and-the-tie-break-is-the-ticker):
the schema does not enforce ticker uniqueness, and with two instruments sharing a ticker the order
stops being total, which is how paged results silently repeat or skip a row.

**Against**: ticker uniqueness was measured to hold on the provided dataset, the spec defines the
ordering in terms of the ticker, and an extra key defends against a state the data does not have.
**For**: it is one word, and the failure it prevents is invisible — a user would see a missing
instrument, not an error.

**Default if unanswered**: not added, as the spec is written. It is the cheapest insurance in this
feature, so it is worth ten seconds of the author's attention.

### Q-102: Should a counter record searches that page?

A Micrometer counter when `totalElements` exceeds `size`. `spring-boot-starter-actuator` and
`micrometer-registry-prometheus` are already dependencies and the Prometheus endpoint is already
exposed (ADR-0026), so the cost is two lines in the service.

**For**: "how often do callers hit a second page" is the number that would say whether the default
page size of 20 is right, and R-106 picked it from a 66-row dataset — now that the value is a
runtime dial, the measurement that justifies turning it is worth something.
**Against**: ADR-0026 states that nothing collects these metrics in this exercise, so the counter
would be written and never read.

**Default if unanswered**: not shipped.

### Resolved, not open

Feature 001 left two questions for the author. Both are settled in the repository and are not
reopened here: `springdoc-openapi` was **not** added (the contract is hand-written and validated,
ADR-0024), and the actuator **was** added with Prometheus exposed and nothing scraping it
(ADR-0026). This feature follows both.
