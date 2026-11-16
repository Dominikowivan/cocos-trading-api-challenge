# Feature Specification: Instrument Search

**Feature Branch**: `feature/instrument-search`

**Created**: 2026-11-16

**Status**: Draft

**Input**: Instrument search endpoint, as described in `docs/challenge/backend-challenge.md` (the source of requirements). Delivered as prioritized, independently testable user stories, one per matching capability.

## Source of Requirements

The behaviour this feature must deliver is defined in the challenge statement,
[`docs/challenge/backend-challenge.md`](../../docs/challenge/backend-challenge.md) — see its
*Buscar activos* bullet in **Resumen** ("el listado de activos similares a la busqueda realizada
dentro del mercado (tiene que soportar busqueda por ticker y/o por nombre)"), the
*Consideraciones funcionales* bullets on prices in pesos, on `close` and `previousClose` as the
inputs to the daily return, and on cash being modelled as a `MONEDA` instrument, plus the
**Base de datos** section. That document is kept verbatim and is not restated here; this spec only
adds what it leaves open.

Decisions already taken, cited rather than re-decided:

- [ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md) — no authentication, so
  there is no "current user" in this feature; search is the same for everybody.
- [ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md) — an unknown money figure
  is absent rather than `0`. An instrument with no market data reports absent price figures.
- [ADR-0016](../../docs/adr/0016-decimal-scale-and-rounding-policy.md) — the decimal scale and
  rounding policy every money figure follows.
- [ADR-0024](../../docs/adr/0024-every-response-is-validated-against-the-contract.md) — the
  endpoint is added to `docs/api/openapi.yaml` and `docs/api/trading-api.http` as part of its
  implementation.

Unlike the portfolio, this feature does not read the `orders` ledger at all: it reads
`instruments` and `marketdata` only, and writes nothing.

### Measurements this spec is built on

All figures below were measured on `src/main/resources/db/seed/V1000__given_dataset.sql`
(2026-11-16), not estimated:

- 66 instruments: 65 of type `ACCIONES` and exactly one of type `MONEDA` — `ARS` / `PESOS`, id 66.
- Tickers are unique, uppercase, 3 to 5 characters. Names are 5 to 42 characters.
- Three names carry diacritics: `Compañía Introductora de Buenos Aires S.A.`,
  `Garovaglio Y Zorraquín`, `Molinos Río de la Plata`.
- Three instruments have no market data row: `PGR` (id 3), `IRCP` (id 10) and `ARS` (id 66). The
  other 63 have two rows each, 126 in total. Since `ARS` is never searchable (FR-023), the two
  unpriced instruments this feature can return are `PGR` and `IRCP` — both reachable from the
  provided seed, so FR-018 needs no constructed fixture.
- Probes over the 65 searchable instruments, matching ticker or name. Case-insensitive substring:
  `banco` → 4, `pam` → 1, `metr` → 1, `molinos` → 3, `gr` → 9, `rio` → 3, `compania` → 0,
  `zorraquin` → 0, `a` → 59 of the 65, `ars` → 0, `pesos` → 0, `%` → 0. Adding accent folding
  changes exactly three of those: `rio` → 4 (adds `MOLI`), `compania` → 1 (`INTR`) and
  `zorraquin` → 1 (`GARO`).

## User Scenarios & Testing *(mandatory)*

Every story below is read-only and observed through one search request carrying a query string.
Each adds one capability to the answer, so each can be built, tested and demonstrated on its own:
a search that only matches tickers is already usable, and every later story strictly widens or
sharpens the result.

### User Story 1 - Find an instrument by ticker (Priority: P1)

A user types part or all of a ticker — `PAMP`, `pamp`, `pam` — and gets the instruments whose
ticker contains what they typed, regardless of case. Each result identifies the instrument well
enough to place an order against it: its id, its ticker, its name and its type.

**Why this priority**: the ticker is how a trader names an instrument, and the result's id is what
the order endpoint consumes. A search that resolves a ticker to an id is already a working feature,
and every other story is a widening of this one.

**Independent Test**: search for `pamp`, `PAMP` and `pam` against the provided dataset and check
all three return instrument 47, `PAMP`; no name matching and no prices are needed to judge the
result.

**Acceptance Scenarios**:

1. **Given** the provided dataset, **When** a user searches `PAMP`, **Then** the result contains
   exactly instrument 47 — ticker `PAMP`, name `Pampa Holding S.A.`, type `ACCIONES`.
2. **Given** the same dataset, **When** a user searches `pamp` in lower case, **Then** the result
   is identical to the one for `PAMP`.
3. **Given** the same dataset, **When** a user searches `pam`, a partial ticker, **Then**
   instrument 47 is returned.
4. **Given** the same dataset, **When** a user searches `ZZZZ`, which matches no ticker and no
   name, **Then** the response is an empty list and not an error.
5. **Given** the same dataset, **When** a user searches `ars` or `pesos`, **Then** the response is
   an empty list: the only `MONEDA` instrument is cash and is never a search result.
6. **Given** the same dataset, **When** a user searches `bnaco`, a transposition of `banco`,
   **Then** the response is an empty list — matching is literal, not fuzzy.

---

### User Story 2 - Find an instrument by name (Priority: P2)

A user who does not remember the ticker types part of the company's name — `banco`, `molinos`,
`rio` — and gets the instruments whose name contains what they typed, regardless of case and
regardless of accents. Searching `rio` finds `Molinos Río de la Plata`, and searching `compania`
finds `Compañía Introductora de Buenos Aires S.A.`.

**Why this priority**: it is the second half of the statement's "por ticker y/o por nombre", and it
is what makes the endpoint useful to someone who knows the company but not its symbol. It depends
on nothing in P1 beyond the shape of a result, and accent-insensitivity is what separates a search
that works on this dataset from one that silently hides three instruments.

**Independent Test**: search for `banco`, `rio` and `compania` against the provided dataset and
compare the returned sets against the names measured above.

**Acceptance Scenarios**:

1. **Given** the provided dataset, **When** a user searches `banco`, **Then** the result contains
   exactly the four banks — `BPAT`, `BBAR`, `BHIP` and `BMA`. This is what the statement's
   "activos similares" means here: one fragment returns the family that shares it.
2. **Given** the same dataset, **When** a user searches `BANCO` in upper case, **Then** the result
   is identical to the one for `banco`.
3. **Given** the same dataset, **When** a user searches `rio` without an accent, **Then** the
   result contains `MOLI` (`Molinos Río de la Plata`) alongside the three unaccented matches
   `BHIP`, `RICH` and `OEST` — four in total. A plain substring match returns only three, which is
   the failure this scenario exists to catch.
4. **Given** the same dataset, **When** a user searches `compania`, **Then** `INTR`
   (`Compañía Introductora de Buenos Aires S.A.`) is returned. A plain substring match returns
   nothing.
5. **Given** the same dataset, **When** a user searches `metr`, **Then** `METR` (`MetroGAS S.A.`)
   is returned once, even though the query matches both its ticker and its name — a match on both
   fields is still one result.

---

### User Story 3 - Best matches first, one page at a time (Priority: P3)

A user whose query is broad — `a` matches 59 of the 65 searchable instruments — gets a short,
ordered page rather than the whole market: the closest matches first, a bounded number of them,
and a statement of how many matched in total so they can ask for the next page or refine the
query. Within the same relevance tier results are ordered by ticker, so the same query always
returns the same page in the same order.

Relevance, strongest first: an exact ticker match, then a ticker that starts with the query, then a
name that starts with the query, then any other match on ticker or name.

**Why this priority**: it is what turns a filter into a search. Without it the answer to a
two-letter query is unusable and the response size is unbounded; without the tie-breaker the
same query can return different pages on different runs, which no test can pin down.

**Independent Test**: search for `a` and check the page is bounded and ordered as specified, and
that the response states 59 matched; ask for the second page and check it continues where the
first stopped; search twice and check the two responses are identical.

**Acceptance Scenarios**:

1. **Given** the provided dataset, **When** a user searches `a`, **Then** the number of results
   returned does not exceed the page size, even though 59 instruments match.
2. **Given** the same dataset, **When** a user searches `gr`, **Then** `GRIM` — whose ticker starts
   with `gr` — is ordered above `MOLA`, which matches only through its name (`Molinos Agro S.A.`).
3. **Given** the same dataset, **When** a user searches `bma`, **Then** `BMA` is the first result,
   because an exact ticker match outranks every partial one.
4. **Given** the same dataset, **When** the same query is sent twice with no data change,
   **Then** the two responses are byte-for-byte identical.
5. **Given** a query that matches more instruments than one page holds, **When** the response is
   read, **Then** it states how many matched in total and which page this is, so the user knows
   to ask for the next page or refine the query rather than believing they have seen the whole
   market.
6. **Given** the same dataset and a query of `a`, **When** the user asks for the second page,
   **Then** it continues exactly where the first stopped, with no instrument repeated and none
   skipped — which only holds because the order is total.
7. **Given** a request asking for a page beyond the last one, **When** the response is read,
   **Then** it is an empty page with the same total, not an error: the search ran and that page
   holds nothing.
8. **Given** a request asking for a page size above the stated maximum, or for a negative page,
   **When** it is received, **Then** it is rejected as a bad request rather than silently
   adjusted, so the caller is never served a page different from the one they asked for.

---

### User Story 4 - See what each instrument is worth (Priority: P4)

Each result carries the instrument's latest `close` in pesos and its daily return — `close`
against `previousClose`, as a percentage — so a user choosing what to trade sees the price and the
day's move without a second request. An instrument with no market data is still returned, with both
figures absent ([ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md)).

**Why this priority**: it is the only figure in the statement that no other endpoint produces — the
portfolio reports total return and explicitly never the daily one — so without this story the
statement's `previousClose` rule is implemented nowhere. It is last because the search is useful
without it and every other story is a precondition for reading it.

**Independent Test**: search for `pamp` and check the reported close and daily return equal the
latest market data row for instrument 47 computed by hand; search for `pgr` and check both figures
are absent.

**Acceptance Scenarios**:

1. **Given** the provided dataset, **When** a user searches `pamp`, **Then** the result carries the
   `close` of instrument 47's most recent market data row and a daily return of
   `(close − previousClose) / previousClose`.
2. **Given** two instruments whose most recent market data rows carry different dates, **When** both
   are returned, **Then** each uses its own instrument's latest row — "latest" is resolved per
   instrument, never at one shared date.
3. **Given** the provided dataset, **When** a user searches `pgr`, **Then** `PGR` (id 3) is
   returned with its ticker, name and type, and with its price and daily return absent (`null`),
   never `0`.
4. **Given** an instrument whose latest row has a `previousClose` of `0` or absent, **When** it is
   returned, **Then** its price is reported and its daily return is absent, never a division by
   zero.

---

### Edge Cases

- **Empty or blank query.** A request with no query, an empty query or one made only of whitespace
  is rejected as a bad request. It is not answered with the whole market: "search for nothing" has
  no useful answer, and returning the whole market on a missing parameter hides the mistake from the
  caller.
- **Query of one character.** `a` matches 59 of the 65 searchable instruments. It is a
  legitimate query and is answered, bounded by the page size of story 3, rather than rejected — a
  trader typing into a search box sees the first letter's results while they keep typing.
- **A page past the end.** Asking for page 9 of a query that fills two pages returns an empty
  page carrying the real total, not an error and not the last page. Asking for a negative page, or
  for a page larger than the maximum, is a bad request.
- **No match at all.** `ZZZZ` returns an empty list with a success status, not a not-found error:
  the search ran and its answer is "nothing matches". This is distinguishable from the rejection of
  a blank query.
- **Surrounding whitespace.** `"  pamp  "` is treated as `pamp`. Leading and trailing whitespace is
  not part of what the user meant to type.
- **Accented names.** Three instruments in the dataset carry diacritics. Both the query and the
  stored name are folded before matching, so `rio` finds `Río`, `compania` finds `Compañía`, and
  `Zorraquín` is found by `zorraquin`. The reverse also holds: typing `río` finds it too.
- **Characters that mean something to the search engine.** A query containing `%`, `_`, a quote or
  a backslash is matched as literal text. `%` returns the instruments whose ticker or name contains
  a per-cent sign — none in this dataset — not the whole market.
- **Very long query.** A query longer than any ticker or name cannot match anything; it is answered
  with an empty list, and a query beyond a sane maximum length is rejected as a bad request rather
  than passed to the database.
- **The pesos instrument.** `ARS` / `PESOS` (id 66) is cash, not a tradable asset: the portfolio
  excludes it from positions and an order against it is a transfer, not a trade. It is excluded
  from the search for the same reason (FR-023), so searching `ars` or `pesos` returns an empty
  list — the same answer as any other query that matches nothing, with no special explanation.
  The rule is written against the `MONEDA` **type**, not against the id or the ticker, so a second
  currency added later is excluded without changing anything.
- **A query that is close but not contained.** `bnaco` matches no ticker and no name, so it returns
  an empty list (FR-024). Searching is literal: the user refines what they typed, and the API does
  not guess what they meant.
- **Duplicate tickers.** None exist in the dataset and the ticker is treated as the user-facing
  identity of an instrument, but nothing in the given schema enforces uniqueness. If two
  instruments shared a ticker both would be returned, each with its own id; the search does not
  deduplicate and does not fail.
- **Instrument with several market data rows.** The dataset carries two days per instrument; only
  that instrument's latest row is read, for both `close` and `previousClose`.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a read-only search over the market's instruments, taking one
  free-text query and returning the instruments that match it.
- **FR-002**: The search MUST match the query against both the ticker and the name of an
  instrument, and MUST return an instrument that matches on either.
- **FR-003**: Matching MUST be partial: an instrument matches when its ticker or its name contains
  the query, not only when it equals it.
- **FR-004**: Matching MUST be case-insensitive in both directions, for ticker and for name.
- **FR-005**: Matching MUST be accent-insensitive: query and stored text are folded so that `rio`
  matches `Río`, `compania` matches `Compañía` and `zorraquin` matches `Zorraquín`, and the
  accented spellings match the unaccented stored text too.
- **FR-006**: An instrument matching on both its ticker and its name MUST be returned exactly once.
- **FR-007**: The query MUST be trimmed of leading and trailing whitespace before matching.
- **FR-008**: A missing, empty or whitespace-only query MUST be rejected as a bad request, and MUST
  NOT be answered with the full list of instruments.
- **FR-009**: A query longer than the stated maximum length MUST be rejected as a bad request.
- **FR-010**: Characters with special meaning to the underlying search mechanism (`%`, `_`, quotes,
  backslashes) MUST be matched as literal text, never interpreted as wildcards or as syntax.
- **FR-011**: A query that matches no instrument MUST return an empty list with a success status,
  distinguishable from the rejection of a blank query and from a not-found error.
- **FR-012**: Each result MUST carry the instrument's id, ticker, name and type. The id is what the
  order endpoint consumes.
- **FR-013**: Results MUST be ordered by relevance, strongest first: exact ticker match, then
  ticker starting with the query, then name starting with the query, then any other match.
- **FR-014**: Results within the same relevance tier MUST be ordered by ticker, so that the same
  query over unchanged data always returns the same list in the same order.
- **FR-015**: Results MUST be paginated. The response MUST carry one page of results plus the
  page requested, the page size in force, and the total number of instruments that matched, so a
  caller can tell whether more exist and ask for them.
- **FR-015a**: The caller MAY ask for a page size of their own, so a client that wants more than
  the default can have it in one request. The page size MUST still be bounded by a maximum the
  caller cannot exceed, and a request for a larger page, for a page size below one, or for a
  negative page number, MUST be rejected as a bad request rather than silently adjusted. A
  request that names neither page nor size MUST be served the default page size.
- **FR-015c**: The default page size MUST be configurable per deployment, without rebuilding the
  application, so an operator can tune it against real traffic. The maximum page size and the
  maximum query length MUST be configurable the same way. Each MUST have a stated value that
  applies when nothing is configured, and the values in force MUST be observable in the response
  (the page size is echoed) or in a rejection (the maximum is named).
- **FR-015b**: Paging MUST be consistent: over unchanged data, consecutive pages of the same query
  MUST together return every match exactly once, in the order of FR-013 and FR-014. A page beyond
  the last MUST be an empty page carrying the real total, not an error.
- **FR-016**: Each result MUST carry the latest `close` of that instrument in pesos, resolved per
  instrument rather than at one shared date.
- **FR-017**: Each result MUST carry the instrument's daily return as a percentage,
  `(close − previousClose) / previousClose`, taken from that same latest row.
- **FR-018**: An instrument with no market data MUST still be returned, with its price and daily
  return reported as absent rather than as `0`
  ([ADR-0013](../../docs/adr/0013-unknown-money-is-absent-never-zero.md)).
- **FR-019**: When `previousClose` is absent or zero, the daily return MUST be reported as absent;
  the price MUST still be reported, and no division by zero may occur.
- **FR-020**: Money and percentage figures MUST be exact decimals in pesos, with the scale and
  rounding of [ADR-0016](../../docs/adr/0016-decimal-scale-and-rounding-policy.md).
- **FR-021**: The search MUST NOT read the `orders` ledger and MUST NOT depend on a user id: it
  returns the same answer for every caller ([ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md)).
- **FR-022**: The search MUST NOT write anything.
- **FR-023**: Instruments of type `MONEDA` MUST NOT be returned by the search. The search answers
  with tradable assets only, so every result is something the order endpoint accepts as a trade.
  On the provided dataset this excludes exactly one instrument, `ARS` / `PESOS` (id 66), leaving 65
  searchable; `ars` and `pesos` therefore return an empty list.
- **FR-024**: Matching MUST be literal containment of the folded query (FR-003 to FR-005) and MUST
  NOT be tolerant to typos: a query that is not a substring of any ticker or name returns nothing.
  `bnaco` returns an empty list. The statement's "activos **similares** a la busqueda" is satisfied
  by the fact that one fragment returns the whole family of instruments that share it — `banco`
  returns the four banks — not by measuring the distance between two strings.

### Key Entities

- **Instrument**: a tradable asset with an id, a ticker, a name and a type. The ticker and the name
  are what the query is matched against; the id is what identifies it to the order endpoint. The
  type `MONEDA` marks the pesos instrument, which is cash rather than an asset.
- **Market data**: a daily price row per instrument, carrying `close` and `previousClose`. Only the
  instrument's latest row is read.
- **Search query (input)**: the free text a user typed. Trimmed, folded for case and accents, and
  matched as literal text.
- **Search result (derived)**: one matched instrument, carrying its identity plus its latest price
  and daily return. Not stored.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On the provided dataset, searching `PAMP`, `pamp` and `pam` each return instrument
  47, `Pampa Holding S.A.` — the ticker path works in any case and on a partial query.
- **SC-002**: On the same dataset, searching `banco` returns exactly four instruments: `BPAT`,
  `BBAR`, `BHIP` and `BMA`.
- **SC-003**: On the same dataset, searching `rio` returns four instruments including `MOLI`
  (`Molinos Río de la Plata`), and searching `compania` returns `INTR`. Both would return fewer
  without accent folding — three and zero respectively.
- **SC-004**: On the same dataset, searching `metr` returns exactly one result, not two, although
  the query matches both the ticker and the name of `METR`.
- **SC-005**: On the same dataset, searching `a` — which matches 59 of the 65 searchable
  instruments — returns no more than one page, and the response states that 59 matched. Walking
  the pages of that query returns all 59 instruments exactly once, in the specified order.
- **SC-006**: On the same dataset, searching `bma` returns `BMA` first, and searching `gr` returns
  `GRIM` above `MOLA`.
- **SC-007**: Two identical searches with no intervening data change return identical responses,
  including the order of the results.
- **SC-008**: Every instrument returned carries an id that the order endpoint accepts, so a user
  can go from a search to an order without a further lookup.
- **SC-009**: On the same dataset, searching `pgr` returns `PGR` with its price and daily return
  absent; no search response ever reports `0` for a price that is merely unknown.
- **SC-010**: A blank query is rejected, and that rejection is distinguishable from the empty list
  returned for `ZZZZ`.
- **SC-011**: A query of `%` returns the instruments whose ticker or name contains a per-cent sign
  — none on this dataset — and never the whole market.
- **SC-012**: A user who knows only a company's name reaches its ticker in a single request, and a
  user who knows only part of its ticker reaches its name in a single request.

## Assumptions

- The search is read-only and public: with no authentication there is no "my instruments" view, and
  every caller sees the same market ([ADR-0007](../../docs/adr/0007-out-of-scope-matching-engine-and-auth.md)).
- **"Activos similares" means a fragment returns a family, not that typos are forgiven.** Confirmed
  by the author on 2026-11-16: `banco` must return every bank, which partial, case- and
  accent-insensitive containment already delivers (FR-003 to FR-005). Typo tolerance is explicitly
  not part of it (FR-024), so no similarity measure, threshold or database extension enters this
  feature. If it is ever wanted it is a new feature with its own decision to record, not a tweak
  to these rules.
- **The daily return belongs here.** The statement gives a rule for computing it from `close` and
  `previousClose`; the portfolio explicitly does not report it
  (`specs/001-portfolio-endpoint/spec.md`, FR-009), and the order endpoints do not either. Search is
  the only place it can be surfaced, so this feature is where that rule is implemented. This is an
  interpretation of an unassigned requirement, not a new one.
- **The latest price ships with each result**, confirmed by the author on 2026-11-16: the statement
  requires prices in pesos and this is the endpoint a user reads before deciding what to trade.
  Story 4 is P4 and strictly additive only because it is the last thing to build, not because it
  is optional.
- **Results are paginated, not capped with a truncation flag**, decided by the author on
  2026-11-16. The earlier draft of this spec preferred a cap plus "there is more, refine your
  query", on the grounds that pages are ceremony over a market of 65 instruments. That reasoning
  was rejected: the endpoint is designed for a market that grows, a cap discards matches with no
  way to reach them, and a client that wants the rest should not have to guess at a narrower
  query to get it. The total match count that pagination publishes also tells the caller how much
  they have not seen, which the flag only hinted at.
- Accent folding is assumed to be the right default rather than exact matching, because the three
  accented names in the dataset are otherwise unreachable from a keyboard-typed query — measured:
  `rio` misses `Molinos Río de la Plata` and `compania` misses `Compañía Introductora` without it.
- Relevance tiers are a presentation decision made here (not in the statement) so that the ordering
  is testable; the tie-break on ticker exists so acceptance scenarios can assert an exact list.
- The exact maximum query length, the default page size and the maximum page size are not set by
  the statement; concrete values are chosen at plan time, and the requirements above only demand
  that they exist, are stated, and are observable. They are **configuration, not constants**
  (FR-015c), decided by the author on 2026-11-16: the right page size depends on traffic nobody
  has measured yet, so it is a dial an operator turns rather than a number a release changes.
- **The search itself is executed by the database**, decided by the author on 2026-11-16: the
  filtering, the relevance ordering and the paging happen in the query, so only one page of rows
  ever crosses the wire. This is a constraint on the implementation rather than on the behaviour
  — every requirement above is written in terms of what the caller observes and none of them
  changes — but it is recorded here because it was a deliberate choice between two designs, and
  because it is what the plan's ADR and its measurements have to justify.
- **Cash is not an asset, so the search never returns it** (FR-023), confirmed by the author on
  2026-11-16. The rule is written against the `MONEDA` type rather than against `ARS`, so it keeps
  holding if another currency is added. It is the same exclusion the portfolio applies to positions
  (`specs/001-portfolio-endpoint/spec.md`, FR-005), reached independently here: there it protects a
  money figure, here it keeps every result something an order can be placed against.
- The ticker is treated as the user-facing identity of an instrument. The given schema does not
  enforce its uniqueness and this feature does not add that constraint; it was measured to hold on
  the provided dataset.
- Prices in the dataset are already in pesos, as the statement specifies; no currency conversion
  takes place.
- Performance targets are not set by the statement. The search reads `instruments` and `marketdata`
  only — 66 and 126 rows on the provided dataset — and does not touch the ledger, so it is
  structurally cheaper than the portfolio. Any index added for it is justified at plan time by a
  measurement, per constitution principle IX.
- Order submission, order cancellation and the portfolio are separate features and out of scope
  here; this feature produces the instrument id those endpoints consume, and shares no computation
  with them.
