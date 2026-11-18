# ADR-0028: Search results are paginated, with a total count

- **Status:** Accepted; partly superseded by [ADR-0032](0032-paging-is-spring-datas-pageable.md)
- **Date:** 2026-11-16

> **Superseded in part (2026-11-16).** The response envelope, the total count and the shared
> snapshot below still hold. Two sections do not: *"The limits are configuration, not constants"*
> (the page limits are now Spring Data's `spring.data.web.pageable.*`, global, and only
> `max-query-length` remains search's own) and *"A bad paging request is rejected, never
> clamped"* (out-of-range paging is now adjusted, not rejected). See ADR-0032.

## Context

A one-letter query matches 59 of the 65 searchable instruments in the provided dataset. Something
has to decide what the endpoint does when a query matches more than a caller can usefully read.

The feature was specified the other way first: **a cap of 20 results plus a `truncated` boolean**,
on the argument that 65 instruments do not justify pages. This ADR records the reversal, because
the earlier position is written down in the feature's own history and a reader who finds it should
find the reason it changed next to it.

## Decision

**`page` (zero-based) and `size` parameters; the response carries the page, the size in force, the
total number of matches, the number of pages and the results.** Two statements per request: the
page query and the count query Spring Data derives from the same `@Query` (ADR-0027).

```json
{ "query": "a", "page": 0, "size": 20, "totalElements": 59, "totalPages": 3, "results": [] }
```

Author's reasoning, recorded as given on 2026-11-16: a cap discards matches with no way for the
client to reach them, and telling a user "there is more, type something narrower" is making them
guess at a query to retrieve data the API already found. Pagination hands them the rest. The total
count is the part a truncation flag could only hint at: a client that sees `totalElements: 59`
knows exactly how much they have not seen.

`totalPages` is derived from the total and the size rather than carried as a second field that
could disagree with them.

### Both statements read one snapshot

The service method carries `@Transactional(isolation = REPEATABLE_READ, readOnly = true)`, as
`PortfolioService` does. A response whose `totalElements` was computed against a different snapshot
than its `results` is a response that contradicts itself — 59 matches reported, 20 returned, the
two numbers describing different instants. Under PostgreSQL's default `READ COMMITTED` each
statement gets its own snapshot, so that is not hypothetical. The cost is nil: a read-only snapshot
takes no locks and blocks no writer, and `readOnly = true` states the feature's read-only nature to
the framework rather than only in prose.

### The limits are configuration, not constants

| Property | Default | Environment variable | Why that default |
|---|---|---|---|
| `trading.search.default-page-size` | **20** | `TRADING_SEARCH_DEFAULT_PAGE_SIZE` | Comfortably above every meaningful family in the dataset (`gr` → 9, `banco` → 4), so no realistic search is paged at all, and below 59, so a one-letter query pages and the behaviour is observable on the provided data |
| `trading.search.max-page-size` | **100** | `TRADING_SEARCH_MAX_PAGE_SIZE` | Above the 65 searchable instruments, so a client that genuinely wants the whole market can ask for it in one request; low enough to be a real ceiling against `size=100000` |
| `trading.search.max-query-length` | **64** | `TRADING_SEARCH_MAX_QUERY_LENGTH` | The longest name is 42 characters, so 43 to 64 is a band where a query is accepted, matches nothing and returns an empty page — distinct from rejection. Both halves stay observable |

Author's reasoning, 2026-11-16: the right page size depends on traffic nobody has measured, so it
is a dial an operator turns, not a number a release changes. Spring Boot's relaxed binding gives
the environment-variable spelling for free, which is what makes it work in Docker (ADR-0025) with
one `-e` and no rebuild of the jar.

Extending the same treatment to the maximum page size and the maximum query length was **this
feature's call, not the author's request**: they are the same kind of number, read from the same
place, and leaving two of the three hard-coded would be the odd choice to explain.

The values in force stay observable without a settings endpoint: the response **echoes** `size`,
and a rejection **names** the maximum it enforced.

### A bad paging request is rejected, never clamped

A `size` above the maximum, a `size` below 1 and a negative `page` are each a `400` naming the
limit in force. Clamping would serve a page the caller did not ask for and then report a `size`
they did not choose; the caller cannot tell whether they received page 3 of their paging or of
ours. A `400` is one round trip and no ambiguity. The model factories that enforce this are written
so that the asserted outcome of an out-of-range request is an exception — there is no code path that
returns a different page.

A page **past the last one** is not in this category: it is a valid request that returns an empty
list carrying the real total, so an overshooting client learns how far it overshot.

## Alternatives considered

- **A cap of 20 plus a `truncated` boolean.** The specified design, rejected on the grounds above.
  The spec's assumption was rewritten in the same change rather than left to contradict this
  decision.
- **Page/size without a total**, signalled by fetching `size + 1` rows and reporting "there is
  more". Cheaper — no `COUNT(*)` — but the client never learns how many matched, which is the
  property that made pagination worth the change.
- **Cursor pagination** over the ordering keys. Stable when data changes between pages and it does
  not degrade at high offsets, but it cannot jump to an arbitrary page and it is considerably more
  machinery for a market of 65 instruments. Recorded as the escape hatch if the table grows and
  deep offsets start to hurt.
- **Spring Data's `Page` and `Pageable`** as the model and response types. Rejected: it would put
  a Spring type in the model layer, which principle VIII forbids, and it would serialise a larger
  envelope than the contract describes. They are used where they belong — between the repository
  and the service, which is what gives the count for free (ADR-0027) — and the model and the
  response carry records this feature owns.
- **Constants in the code** for the three limits (rejected: a deployment cannot change them); a
  database-backed setting (ceremony, and a read on every request); per-client limits (nothing asks
  for it, and it would let a caller raise their own ceiling, which the rejection rule forbids).

## Consequences

- Every search costs a `COUNT(*)` as well as the page query — a second scan of the same predicate.
  At 66 rows it is free (measured: one shared buffer hit). At scale it is the standard cost of
  offset pagination, and the standard escape is the cursor deferred above.
- The response envelope exists from the first story, so its shape does not churn as paging and
  prices are added.
- The next feature that returns a list is bound by this: it paginates the same way, with the same
  field names, rather than inventing a second envelope.
- The contract states `size`'s maximum as the **default** maximum and says it is configurable,
  rather than asserting `maximum: 100` that a differently configured deployment would violate.
- `TRADING_SEARCH_DEFAULT_PAGE_SIZE=5` on the Docker image serves `size: 5` from the same jar, and
  a web test asserts the same override through `@SpringBootTest(properties = …)`.
