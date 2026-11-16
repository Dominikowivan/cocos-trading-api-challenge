# ADR-0032: Paging is Spring Data's `Pageable`, and out-of-range values are adjusted, not rejected

- **Status:** Accepted
- **Date:** 2026-11-16
- **Supersedes:** part of [ADR-0028](0028-search-results-are-paginated.md)
- **Journal:** [06Refactors](../journal/06Refactors.md)

## Context

ADR-0028 paginated instrument search with code of its own: a `SearchPage` that validated `page`
and `size`, rejected anything out of range with a `400`, took its limits from
`trading.search.default-page-size` and `max-page-size`, and computed the page count.

A retrospective on the author's question — *is this the industry standard, and is it code Spring
already provides?* — found:

- Spring Data Web resolves a `Pageable` straight from `?page=&size=&sort=`, with global defaults in
  `spring.data.web.pageable.*`, and `Page` already carries `getTotalPages()`.
- The custom code existed because of four choices Spring does not make: reject instead of adjust,
  limits per endpoint, no `sort`, and no Spring types outside persistence and service.
- Of those, rejection is the minority position. The common behaviour, Spring's included, is to lower
  an oversized page to the maximum.

The same retrospective revisited the request to paginate the portfolio's positions, which had just
been built the ADR-0028 way. It was rolled back before being committed: every page of a portfolio
costs the whole portfolio, because the account figures need the user's entire ledger, so paging
there only trims the response — for users who hold three positions in the dataset.

## Decision

**Instrument search takes a Spring Data `Pageable`, configured globally, with Spring's default
handling of out-of-range values. The portfolio is not paginated.** Author's decision, 2026-11-16.

- The controller receives `Pageable`; the service passes it to the repository; the `Page` that
  comes back is mapped to the same response as before (`query`, `page`, `size`, `totalElements`,
  `totalPages`, `results`). **The response shape does not change.**
- Limits, in `application.yaml` as `${ENVIRONMENT_VARIABLE:default}`:

  | Property | Default | Environment variable |
  |---|---|---|
  | `spring.data.web.pageable.default-page-size` | 20 | `SPRING_DATA_WEB_PAGEABLE_DEFAULT_PAGE_SIZE` |
  | `spring.data.web.pageable.max-page-size` | 100 | `SPRING_DATA_WEB_PAGEABLE_MAX_PAGE_SIZE` |

  The defaults are ADR-0028's, for ADR-0028's reasons. `trading.search.max-query-length` stays
  search's own: it is not a paging limit.
- Out-of-range paging, as Spring resolves it, measured by the web tests:

  | Request | Served |
  |---|---|
  | `size=500` | `size` 100 |
  | `size=0` | the default size |
  | `page=-1` | page 0 |

  The response echoes the `page` and `size` actually served, so the adjustment is visible.
- **`sort` is ignored.** `InstrumentRepository.searchNonCash` rebuilds the `Pageable` from the page
  number and size only. Otherwise Spring Data appends the caller's sort to the query's
  `ORDER BY i.ticker, i.id`, so a caller would reach the query with a field name of their choosing
  (what an unknown field does then was not measured; with the sort dropped it cannot happen). The order stays
  the one ADR-0027 fixes, and two tests assert it: `sort=name,desc` changes nothing, and
  `sort=nonsense` answers `200`.

Removed: `SearchPage` and its tests, the page fields of `SearchLimits` and `SearchProperties`, and
`InstrumentMatches.totalPages()` (it is `Page.getTotalPages()` now). `InstrumentMatches` carries a
`Page<InstrumentMatch>`.

## Consequences

- **Fewer rules of our own, one of them weaker.** A caller who asks for 500 gets 100 and has to read
  `size` to notice. ADR-0028 argued that this serves a page nobody asked for; the author weighed it
  against following the framework's and the industry's default, and chose the default.
- **The limits are global.** Any future endpoint that takes a `Pageable` shares them. A different
  maximum for one endpoint needs `@PageableDefault` (default only) or a customised resolver.
- **Spring types now reach the controller** (`Pageable`) and the service's result
  (`Page<InstrumentMatch>`). The model is untouched, and the response is still a record of ours,
  which is the line ADR-0028 drew for what a client sees.
- The portfolio's response is as it was in feature 001. If pagination is wanted there later, the
  cost argument above applies first.

## Alternatives considered

- **Keep ADR-0028's implementation.** About a hundred lines with explicit, tested rules. Rejected by
  the author in favour of the framework's default behaviour.
- **Spring's `PagedModel` as the response** (`content` plus a `page` object). The standard Spring
  envelope, but it renames `results` and nests the paging fields: a breaking change to the contract
  for no behaviour gained.
- **Rejecting through a customised `PageableHandlerMethodArgumentResolver`.** Possible, but it
  rebuilds the custom rule inside the framework's extension point, which is the cost this decision
  set out to remove.
