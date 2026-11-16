# ADR-0027: Instrument search is one short JPQL query

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

Instrument search (`GET /api/v1/instruments/search`) has to find the instruments "similar" to a
free-text query by ticker and/or name, page them and carry each one's latest price.

The first implementation did everything in one native query: a folding function of its own
(`search_fold`, created by a migration) for case and accents, a four-tier relevance `CASE` in the
`ORDER BY`, a `LEFT JOIN LATERAL` for the latest price, and a second hand-written query repeating
the predicate to count the matches. Each piece had a reason, and together they made a query the
author judged indefensible for what the statement asks: *"similar by ticker and/or name"*.

Calibration against the public submissions of the same challenge (64 repositories with a search,
measured 2026-11-16): the common answer is a case-insensitive `LIKE '%…%'` over ticker or name,
ordered by ticker and paged. Eight fold accents, all of them with the `unaccent` extension; about
nine rank by relevance. The previous design sat at the far end of the field.

## Decision

**One JPQL `@Query`, paged by `Pageable` and ordered by the query itself; the latest prices of that page in a second
query that already existed.**

```java
@Query("""
        SELECT i FROM InstrumentEntity i
        WHERE (locate(lower(:query), lower(i.ticker)) > 0
            OR locate(lower(:query), lower(i.name)) > 0)
          AND i.type <> :cashType
        ORDER BY i.ticker, i.id
        """)
Page<InstrumentEntity> search(String query, String cashType, Pageable pageable);
```

- **Matching:** partial and case-insensitive, on the ticker or on the name. An instrument whose
  ticker and name both match is one row.
- **`locate`, not `LIKE`.** `locate` returns the position of one string inside another, `0` when it
  is absent, so `> 0` reads "contains". It has no wildcards, so `%`, `_`, quotes and backslashes
  are ordinary characters with nothing to escape. Hibernate renders it as PostgreSQL's
  `position(lower(?) in lower(ticker)) > 0` (seen in the SQL log of the test run, 2026-11-16), and
  the persistence test searches `%`, `_`, `'` and `\` and gets nothing back.
- **Cash is excluded, by type.** See below.
- **Order:** ticker, then id, fixed in the query's own `ORDER BY i.ticker, i.id`, so no caller can page in a
  different order; `searchNonCash` binds the cash type from `InstrumentRef.CASH`. `id` makes the order total, because the
  schema does not enforce ticker uniqueness and a partial order under `LIMIT`/`OFFSET` is how pages
  silently repeat or skip a row.
- **Count:** `Page` carries it; Spring Data derives the `COUNT` from the same `@Query`, so the
  predicate is written once.
- **Prices:** `MarketDataRepository.findLatestMarketData` — the `DISTINCT ON (instrumentid) … ORDER BY
  instrumentid, date DESC` query the portfolio already uses — is called with the ids of the page,
  now also returning `previousclose`. Two fixed statements per request, never one per result. An
  instrument with no market data simply has no row there and is returned with both figures `null`.

### Why pesos are not a result

`ARS` (type `MONEDA`) is a row of `instruments` because the schema records money movements as
orders, but at the business level it is not an asset that is traded: it is the currency every
asset is paid in. It has no market data because it cannot be priced against itself, and it moves
through `CASH_IN`/`CASH_OUT`, which are transfers, not purchases. The search answers "what can I
trade", and its result id is what order submission consumes, so returning pesos would offer
something nothing can be done with. It would also contradict the portfolio, which treats `ARS` as
cash and never as a position. Author's decision, 2026-11-16. The rule is written against the
`MONEDA` type, not the ticker, so it holds if another currency is added.

## Consequences, and what was given up

- **Accents are not folded.** `río` finds *Molinos Río de la Plata* and `rio` does not; the same for
  `compañía` and `zorraquín`. The tests assert both halves. It affects 3 of the 66 names and only
  the accented word: tickers are ASCII, so `MOLI`, `INTR` and `GARO` always find their instrument,
  and so does any unaccented fragment of the name (`molinos`, `introductora`). Upper/lower case does
  work across accents (`RÍO` finds `Río`) under the database's `en_US.utf8` collation.
- **No relevance ranking.** Results come in ticker order. For the queries a user would actually
  type — a ticker, or a word of a name — the result set is small enough that order hardly matters
  (`pamp` → 1, `banco` → 4, `gr` → 9); the cost shows on one-letter queries, which page.
- **No migration, no database function, no native SQL** for this feature.

## Considered and deferred: accent-insensitive matching

Recorded so the next reader does not have to rediscover it.

- **`unaccent`** is the standard PostgreSQL extension for it. Not installed: `CREATE EXTENSION`
  generally needs superuser, there is no measurement that our credentials on the staging database
  (ADR-0009) allow it, and a migration that passes on Testcontainers and fails on staging fails at
  deploy time. It is not part of JPQL, so it also turns this query native, and `unaccent()` is
  `STABLE`, so indexing it needs an `IMMUTABLE` wrapper function.
- **A hand-written folding function** (`translate` + `lower`, `IMMUTABLE`) needs no extension and
  was the first implementation. It brings back the native query and a migration for three names.
- **If it is ever wanted**, the order is: measure that staging allows the extension, then a native
  query with `unaccent` on both sides, then — only with a large catalogue — a trigram GIN index
  over the folded text.

## Alternatives considered

- **The native query described in the context.** Rejected by the author for its complexity
  relative to the requirement.
- **A Spring Data derived query**, `findByTickerContainingIgnoreCaseOrNameContainingIgnoreCase`. No
  query text at all, and Spring escapes `%` and `_` in `Containing`. It was implemented and passed,
  then dropped when the author decided pesos must not be a result: a derived query gives `And`
  precedence over `Or`, so `…OrNameContainingIgnoreCaseAndTypeNot` reads as
  `ticker OR (name AND type)` and `ARS` still matches by its ticker. The only correct spelling
  repeats the type condition on both sides of the `Or`, which nobody can read.
- **`LIKE` instead of `locate`**, with `lower(…) LIKE lower(concat('%', :query, '%'))`. More
  familiar, but the query's `%` and `_` become wildcards and have to be escaped before the call — a
  helper that `locate` does not need.
- **Matching in Java over the whole catalogue** (`findAll()` and filter). Rejected: an endpoint fed
  by a search box is called on every keystroke, and reading the whole table per call is the wrong
  shape at any size the table could grow to.
- **Full-text search (`tsvector`).** Wrong tool: it matches whole lexemes, so `pam` would not find
  `PAMP`.
