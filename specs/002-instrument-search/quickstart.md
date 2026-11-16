# Quickstart: Validating Instrument Search

**Feature**: `002-instrument-search` | **Plan**: [plan.md](plan.md) | **Contract**: [instrument-search.openapi.yaml](contracts/instrument-search.openapi.yaml)

How to run the feature and prove it works end to end. Scenarios only — no implementation code; the
response shape lives in the contract and the rules in [data-model.md](data-model.md).

Every figure below was reproduced before implementation, twice: from
`src/main/resources/db/seed/V1000__given_dataset.sql`, and by running the designed query against a
real `postgres:18.7-alpine` built from this project's migrations and seed. See
[research.md](research.md#measured-facts-this-phase-relied-on).

## Prerequisites

- JDK 21
- Docker (local database and Testcontainers)

## Run it

```bash
docker compose up -d
./mvnw spring-boot:run
```

PostgreSQL 18.7 on port **5433**; Flyway applies `db/migration` — including this feature's
`V3__instrument_search_fold.sql` — and loads `db/seed` (the provided dataset). The app is on
`http://localhost:8080`.

Every request below is also in [`docs/api/trading-api.http`](../../docs/api/trading-api.http),
runnable from IntelliJ's HTTP Client or VS Code's REST Client — the `curl` lines here are for
readers without either.

## Scenario 1 — a ticker, in any case and partial (SC-001, FR-003, FR-004)

```bash
curl -s 'http://localhost:8080/api/v1/instruments/search?query=PAMP' | jq
curl -s 'http://localhost:8080/api/v1/instruments/search?query=pamp' | jq
curl -s 'http://localhost:8080/api/v1/instruments/search?query=pam'  | jq
```

All three return the same single result, and it carries its price and the day's move:

```json
{
  "query": "pam",
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "results": [
    {
      "instrumentId": 47,
      "ticker": "PAMP",
      "name": "Pampa Holding S.A.",
      "type": "ACCIONES",
      "close": 925.85,
      "dailyReturnPercent": 0.44
    }
  ]
}
```

`query` echoes what was searched, so the three responses differ in that field alone. `close` and
`dailyReturnPercent` come from instrument 47's `2023-07-14` row — `925.85` against a
`previousClose` of `921.80`, which is `4.05 / 921.80 × 100 = 0.44` at scale 2 (FR-017, SC-001).

The same request with surrounding whitespace proves the trim (FR-007): `query=%20%20pamp%20%20`
returns the identical body, with `"query": "pamp"`.

## Scenario 2 — a name returns the whole family (SC-002, FR-002)

```bash
curl -s 'http://localhost:8080/api/v1/instruments/search?query=banco' | jq '.results[].ticker'
```

Exactly four, in this order:

```json
"BBAR"
"BHIP"
"BMA"
"BPAT"
```

All four are name-prefix matches, so relevance does not separate them and the ticker does
(FR-013, FR-014). This is the statement's "activos similares": one fragment, the family that
shares it — not a tolerance for typos. `query=BANCO` returns the identical list (FR-004).

## Scenario 3 — accents are folded, both ways (SC-003, FR-005)

```bash
curl -s 'http://localhost:8080/api/v1/instruments/search?query=rio'      | jq '.results[].ticker'
curl -s 'http://localhost:8080/api/v1/instruments/search?query=compania' | jq '.results[].ticker'
```

`rio` returns **four** — `BHIP`, `MOLI`, `OEST`, `RICH` — where a plain substring match returns
three: `MOLI` is `Molinos Río de la Plata`, reachable only because both sides are folded.
`compania` returns `INTR` (`Compañía Introductora de Buenos Aires S.A.`), where a plain match
returns nothing.

The reverse holds too, which is the half a one-sided implementation gets wrong: `query=río`
(`query=r%C3%ADo`) also returns those four, and `query=zorraquin` returns `GARO`.

## Scenario 4 — relevance, and one result per instrument (SC-004, SC-006, FR-006)

```bash
curl -s 'http://localhost:8080/api/v1/instruments/search?query=gr'   | jq '.results[].ticker'
curl -s 'http://localhost:8080/api/v1/instruments/search?query=bma'  | jq '.results[].ticker'
curl -s 'http://localhost:8080/api/v1/instruments/search?query=metr' | jq '.totalElements'
```

`gr` returns nine, tier by tier and alphabetical inside each:

```text
GRIM                              ticker starts with the query
GCLA  GGAL  SUPV                  name starts with it: all three are "Grupo …"
AGRO  LOMA  MOLA  OEST  PGR       contained
```

`GRIM` above `MOLA` is the assertion that fails if the tiers are not applied (SC-006). `bma`
returns `BMA` alone and first, because an exact ticker match outranks every partial one. `metr`
reports a total of **1**, not 2, although the query matches both the ticker and the name of
`METR` (SC-004).

## Scenario 5 — a broad query is paged, and says how much there is (SC-005, FR-015)

```bash
curl -s 'http://localhost:8080/api/v1/instruments/search?query=a' | jq '{page, size, totalElements, totalPages, shown: (.results | length)}'
```

```json
{ "page": 0, "size": 20, "totalElements": 59, "totalPages": 3, "shown": 20 }
```

59 of the 65 searchable instruments match; 20 come back and the response says exactly how many
the caller has not seen. The first three are `AGRO`, `ALUA` and `AUSO` — the only ticker-prefix
matches — and the rest are contained matches in ticker order.

## Scenario 6 — the pages fit together (FR-015b, story 3.6)

```bash
for p in 0 1 2 3; do
  curl -s "http://localhost:8080/api/v1/instruments/search?query=a&page=$p" | jq -c '{page, n: (.results|length)}'
done
```

```json
{"page":0,"n":20}
{"page":1,"n":20}
{"page":2,"n":19}
{"page":3,"n":0}
```

20 + 20 + 19 = 59: every match exactly once, none repeated, none skipped, and the fourth page is
empty rather than wrong. That only holds because the order is total — relevance tier, then ticker
— so the database has no freedom to return tied rows differently between pages.

The boundary is checkable by eye: page 0 ends at `CVH`, page 1 runs `DGCU2 … MOLA`, page 2 runs
`MOLI … YPFD`. Page 3 keeps reporting `totalElements: 59`, so an overshooting client learns the
real total rather than an error.

## Scenario 7 — the caller can ask for a bigger page (FR-015a)

```bash
curl -s 'http://localhost:8080/api/v1/instruments/search?query=a&size=59' | jq '{size, totalPages, shown: (.results|length)}'
curl -s -o /dev/null -w '%{http_code}\n' 'http://localhost:8080/api/v1/instruments/search?query=a&size=500'
curl -s -o /dev/null -w '%{http_code}\n' 'http://localhost:8080/api/v1/instruments/search?query=a&page=-1'
```

```json
{ "size": 59, "totalPages": 1, "shown": 59 }
```

then `400`, then `400`. A client that wants the whole result set in one request can have it, up
to the configured maximum; above it the request is **rejected, not quietly clamped**, and the
problem detail names the limit in force. The same holds for a negative page and for `size=0`.

## Scenario 8 — the default page size is a deployment setting (FR-015c)

```bash
docker compose down
TRADING_SEARCH_DEFAULT_PAGE_SIZE=5 docker compose --profile app up -d --build
curl -s 'http://localhost:8080/api/v1/instruments/search?query=a' | jq '{size, totalPages, shown: (.results|length)}'
```

```json
{ "size": 5, "totalPages": 12, "shown": 5 }
```

No rebuild of the jar, no code change: the same image serves a different default because the
environment says so. `TRADING_SEARCH_MAX_PAGE_SIZE` and `TRADING_SEARCH_MAX_QUERY_LENGTH` work
the same way. The echoed `size` is how a caller sees which value is in force.

## Scenario 9 — nothing matched is not an error, and a blank query is (SC-010, FR-011, FR-008)

```bash
curl -s -o /dev/null -w '%{http_code}\n' 'http://localhost:8080/api/v1/instruments/search?query=ZZZZ'
curl -s -o /dev/null -w '%{http_code}\n' 'http://localhost:8080/api/v1/instruments/search?query=%20%20'
curl -s -o /dev/null -w '%{http_code}\n' 'http://localhost:8080/api/v1/instruments/search'
```

`200`, then `400`, then `400`. The first body is `"results": []` with `"totalElements": 0`: the
search ran and nothing matched. The other two are `application/problem+json` — the search never
ran. Three more queries that are `200` with an empty page, each for its own reason:

| Query | Why it is empty |
|---|---|
| `bnaco` | a transposition of `banco`. Matching is literal; the API does not guess (FR-024) |
| `ars`, `pesos` | the only `MONEDA` instrument is cash and is never a result (FR-023) |
| `%` | matched as a per-cent sign, not as a wildcard. No ticker or name contains one (SC-011) |

A 65-character query is a `400` (FR-009); a 50-character one is a `200` with an empty page, since
the longest name in the market is 42 characters. Both halves of that rule are observable.

## Scenario 10 — an instrument with no price is still an instrument (SC-009, FR-018)

```bash
curl -s 'http://localhost:8080/api/v1/instruments/search?query=pgr' | jq '.results[0]'
```

```json
{
  "instrumentId": 3,
  "ticker": "PGR",
  "name": "Phoenix Global Resources",
  "type": "ACCIONES",
  "close": null,
  "dailyReturnPercent": null
}
```

`null`, never `0` (ADR-0013): the price is unknown, not zero. This is the one place the join
being a `LEFT` join is visible — an inner join would drop the instrument instead. `PGR` and
`IRCP` are the two searchable instruments with no `marketdata` row in the provided dataset, so no
fixture is needed to reach this. The contrast is `query=agro`, whose `dailyReturnPercent` is
`0.00` — `AGRO` closed flat on `2023-07-14`, and a known zero is a different answer from an
unknown one.

## Scenario 11 — two identical searches agree (SC-007, FR-014)

```bash
curl -s 'http://localhost:8080/api/v1/instruments/search?query=a' > one.json
curl -s 'http://localhost:8080/api/v1/instruments/search?query=a' > two.json
diff one.json two.json && echo identical
```

Byte-for-byte identical, including the order and the total. The order is total — relevance tier,
then ticker — so nothing is left to the database's discretion, and the page and the count are
read under one snapshot, so they cannot describe different instants.

## The automated suite

```bash
./mvnw verify
```

What proves what:

| Level | Runs on | Covers |
|---|---|---|
| Unit, plain Java | no Spring, no database | `SearchQuery`'s trim and its rejections (null, empty, blank, over-long); `SearchPage`'s rejections (negative page, size below 1, size above the maximum) and that it never clamps; the daily return, including absent `close`, absent and zero `previousClose`, and a genuine `0.00` |
| Persistence, Testcontainers | `postgres:18.7`, same migrations and seed | the matching rule, which lives in the query: folding both ways, partial and case-insensitive containment, one row per instrument, cash excluded, the four relevance tiers and the ticker tie-break, literal metacharacters, the lateral join resolving the latest row **per instrument** and keeping unpriced instruments, and paging that neither repeats nor skips |
| Functional, Testcontainers + MockMvc | full context | Scenarios 1 to 11 above as assertions, the `400`s among them, the configured-default override, with every response validated against `docs/api/openapi.yaml` (ADR-0024) |

The weight sits at the persistence level, which is the direct consequence of the matching rule
living in SQL (research R-102) — on the real engine, never on a stand-in. No test touches the
provided (staging) database, and no test stands PostgreSQL in with H2 (constitution V, ADR-0010).
The rejection paths — blank, missing, over-long, bad paging, no match, no price — ship with the
feature, not after it.
