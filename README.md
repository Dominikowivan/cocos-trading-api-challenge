# Cocos Trading API

A broker API with four endpoints: a user's portfolio, instrument search, order submission and cancellation.

Java 21 · Spring Boot 4.1.2 · PostgreSQL · Maven wrapper · Testcontainers

## Where things are

This repository carries more process than code on purpose: the exercise asks for the reasoning
behind the solution, not only the solution. So here is the map.

| Path | What it holds |
|---|---|
| [`docs/challenge/`](docs/challenge) | The statement this solution answers, kept verbatim so requirements can be cited instead of paraphrased |
| [`docs/journal/`](docs/journal) | **The intent journal. Start here.** Every decision, in the order it was taken, with what was known at the time |
| [`docs/adr/`](docs/adr) | Architecture Decision Records: one file per decision, with the alternatives that were discarded |
| [`docs/api/openapi.yaml`](docs/api/openapi.yaml) | The API contract as it is today. Every response in the web tests is validated against it (ADR-0024) |
| [`docs/api/trading-api.http`](docs/api/trading-api.http) | Example requests with their expected figures, runnable from IntelliJ's HTTP Client or VS Code's REST Client |
| [`specs/`](specs) | One folder per feature, written with Spec Kit before the code. Its `contracts/` keeps the API as that feature designed it, and is not edited afterwards |
| `src/main/java` | Application code |
| `src/test/java` | Tests |
| `src/main/resources/db/migration` | Flyway migrations: the schema as it was provided, plus every change made to it |
| `src/main/resources/db/seed` | The provided dataset, loaded only by the `local` profile and by the tests |
| `.specify/` | Spec Driven Development tooling (see ADR-0008): versioned because it is part of the development contract, like the linter or the CI pipeline. The project's rules live in [`.specify/memory/constitution.md`](.specify/memory/constitution.md) |
| `.claude/` | The agent skills that drive that workflow, versioned so every developer works with the same ones |

> The journal is the only document written in Spanish. It is addressed to the people reviewing
> this exercise, and what it communicates is intent, not documentation.

### How the journal and the ADRs relate

The two documents are not the same content in two registers, and neither replaces the other.

- The **journal** states intent and reasoning, in the order things were thought. It is
  append-only and never rewritten backwards, so it still holds decisions that were later
  reversed, with what was known at the time. A journal edited to look consistent stops being
  evidence of the path taken.
- An **ADR** is the condensed, formal form of a single decision: context, alternatives
  discarded, decision, consequences. No voice, no narrative. There is one ADR per decision that
  had alternatives worth weighing, not one per journal entry: reading the schema or finding an
  anomaly in the data is a finding, not a decision.
- When a decision changes, the old ADR is marked `Superseded by ADR-00XX` instead of being
  edited. **The ADRs are the authority on what currently holds**; the journal is the authority
  on how it was reached.
- Each ADR links to the section of the journal where the decision was thought through, and the
  journal links forward to the ADR that formalises it.

## Requirements

- Docker — to run the API, and for the local database and Testcontainers
- JDK 21 — only to build, test or run it outside Docker

## Running it

Only Docker is needed. This builds the API inside a container and starts it on
<http://localhost:8080>, with a local **PostgreSQL 18.7** (the same version as the provided
database) holding the provided dataset:

```bash
docker compose --profile app up --build
```

There are two environments, because the provided database and a development database are not the
same thing. The one above is local. The provided one is **staging**: it is what the exercise is
evaluated against, and nothing experimental runs on it.

**The provided dataset is loaded only under the `local` profile** (ADR-0029). The default profile
applies the schema migrations and nothing else, so an environment has to ask for the fixture data
deliberately rather than remember to exclude it. `local` is set for you by the command above, by
`./mvnw spring-boot:run`, and by the test suite; `staging` never activates it.

**Staging: the provided database.** Credentials are never stored in this repository; they are
read from the environment:

```bash
export COCOS_DB_URL="jdbc:postgresql://<host>/<database>"
export COCOS_DB_USER="<user>"
export COCOS_DB_PASSWORD="<password>"

./mvnw spring-boot:run -Dspring-boot.run.profiles=staging
```

## Development

To work on the code, run only the database in Docker and the API from the IDE or the Maven wrapper:
it restarts in seconds after a change and takes a debugger with no extra setup.

```bash
docker compose up -d
./mvnw spring-boot:run
```

Both commands run under the `local` profile, so Flyway applies the schema migrations and then the
provided dataset (`db/seed/R__given_dataset.sql`, a repeatable migration that resets the four tables
before loading them). If you have a database from before ADR-0029, recreate it once with
`docker compose down -v`.

## Configuration

Every setting below is declared in `application.yaml` as `${ENVIRONMENT_VARIABLE:default}`, so the
same image serves a different value with one `-e` and no rebuild.

Paging is Spring Data's (ADR-0032): a `size` above the maximum is lowered to it, a `size` below 1
falls back to the default, and a negative `page` is read as `0`. The limits are global — they apply
to every endpoint that takes a `Pageable` — and the response echoes the `page` and `size` actually
served:

| Property | Default | Environment variable | What it does |
|---|---|---|---|
| `spring.data.web.pageable.default-page-size` | `20` | `SPRING_DATA_WEB_PAGEABLE_DEFAULT_PAGE_SIZE` | The page size applied when a request sends no `size` |
| `spring.data.web.pageable.max-page-size` | `100` | `SPRING_DATA_WEB_PAGEABLE_MAX_PAGE_SIZE` | The largest page served. A larger `size` is **lowered to it** |
| `trading.search.max-query-length` | `64` | `TRADING_SEARCH_MAX_QUERY_LENGTH` | The longest accepted `query`, measured after trimming. Above it the search is a `400` naming the limit |

```bash
SPRING_DATA_WEB_PAGEABLE_DEFAULT_PAGE_SIZE=5 docker compose --profile app up -d --build
```

The database access has a deadline and a bounded pool (ADR-0031). When either runs out the API
answers `503` with a problem detail, instead of holding the request open:

| Property | Default | Environment variable | What it does |
|---|---|---|---|
| `spring.transaction.default-timeout` | `10s` | `SPRING_TRANSACTION_DEFAULT_TIMEOUT` | The longest a request's transaction may run. Past it, the running query is cancelled |
| `spring.datasource.hikari.maximum-pool-size` | `10` | `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | Connections per instance. Times the number of instances, it must stay below the database's `max_connections` |
| `spring.datasource.hikari.connection-timeout` | `5000` (ms) | `SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT` | The longest a request waits for a free connection. A plain number of milliseconds: Hikari does not read `5s` |

## Tests

```bash
./mvnw test
```

The build fails on unformatted code. To format it:

```bash
./mvnw spring-javaformat:apply
```

## Endpoints

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/v1/users/{userId}/portfolio` | Total account value, cash available to trade, and one entry per position with quantity, market value and total return |
| `GET` | `/api/v1/instruments/search?query={text}` | One page of the instruments matching the text by ticker or name — partial and case-insensitive, ordered by ticker, never pesos (cash is not a tradable asset), each carrying its latest close and the day's move. Accents must be typed as they are stored: `río` finds *Molinos Río de la Plata*, `rio` does not (3 of 66 names; their tickers always match; `unaccent` considered and deferred, ADR-0027). Optional `page` and `size` |
| `POST` | `/api/v1/users/{userId}/orders` | Submits one order. `MARKET` fills immediately at the last close; `LIMIT` is accepted as `NEW`. An order the user cannot pay for, or whose shares they do not hold, is **stored** `REJECTED` with a machine-readable reason and answered `201`, like the other two: a rejection is a recorded outcome, a malformed request is not (ADR-0033) |
| `POST` | `/api/v1/users/{userId}/orders/{orderId}/cancel` | Cancels an order that is still `NEW` |

## One departure from the statement

**A `MARKET` order for an instrument with no published price is refused with `400` and nothing is
written, instead of being stored as a `REJECTED` order** ([ADR-0034](docs/adr/0034-an-unpriced-instrument-refuses-the-submission.md)).
Three instruments in the provided dataset have no `marketdata` row (`PGR`, `IRCP`, and `ARS`, which
is cash and refused anyway), and a `MARKET` order for one of them has no execution price to record.
An instrument that does not quote is a property of the request, knowable before any of the user's
money is considered — so refusing it keeps every row in `orders` priced and never writes a money
figure as `0` when it is unknown ([ADR-0013](docs/adr/0013-unknown-money-is-absent-never-zero.md)).
Every other rejection the statement asks for is stored. A `LIMIT` order for the same instrument is
accepted: its price came in the request.

## Two things this API deliberately does not do

- **No matching engine.** A `LIMIT` order is born `NEW` and stays `NEW`; the only path out of that
  state is the user cancelling it. The statement says not to simulate the market, and that is
  exactly what this means — there is no order book, no counterparty and no partial fills.
- **No authentication.** The statement does not ask for it, so the user is a path parameter. This
  is the one decision in the repository that would not survive contact with production, and the
  journal says what it would look like if it had to.

## Attribution

The statement, the schema and the seed data are not mine: they were provided by Cocos Capital and
published at https://github.com/cocos-capital/cocos-challenge. `docs/challenge/backend-challenge.md`
is a verbatim copy of that statement, taken from commit `abae7701`, and the Flyway migrations under
`src/main/resources/db/migration` are a transcription of that `database.sql`.

<!-- ───────────── DELETE BEFORE DELIVERY ─────────────
Pending, so the README never claims something that is not true yet:
 - the portfolio endpoint ships (feature 001-portfolio-endpoint) and instrument search ships
   (feature 002-instrument-search); order submission and order cancellation are still a proposal
   to be confirmed
 - `V2__portfolio_read_indexes.sql` adds the two `orders` indexes and the
   `UNIQUE (instrumentid, date)` constraint on `marketdata`; instrument search needs no migration
   (ADR-0027); there is still no migration for `order_status_history`
──────────────────────────────────────────────────── -->
