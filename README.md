# Cocos Trading API

A broker API with five endpoints: the three the statement asks for (a user's portfolio, instrument search and order submission), plus cash transfers and cancellation.

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
| [`docs/api/cocos-trading-api.postman_collection.json`](docs/api/cocos-trading-api.postman_collection.json) | **A Postman collection that walks the whole API.** The fastest way to try it: 66 requests, happy paths first and then the edge cases, each one explaining what it shows and asserting the figures it expects |
| [`docs/api/trading-api.http`](docs/api/trading-api.http) | The same example requests with their expected figures, runnable from IntelliJ's HTTP Client or VS Code's REST Client |
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

### Trying it

Import [`docs/api/cocos-trading-api.postman_collection.json`](docs/api/cocos-trading-api.postman_collection.json)
into Postman and run the folders in order. Each request carries a note on what it is showing and a
few tests, so the Collection Runner gives a pass/fail sweep of the whole API in one go: the happy
path of every endpoint, then the edge cases worth seeing — a rejection that is *recorded* rather
than refused, `null` where a figure is unknown against `0` where it is genuinely zero, and a retry
under an `Idempotency-Key` that does not trade twice.

The first three folders only read and can be re-run freely. The last three write, in order, and
their figures assume the dataset as seeded; to get back to it, `docker compose down -v` and bring
the stack up again. The same requests are also in
[`docs/api/trading-api.http`](docs/api/trading-api.http), for IntelliJ's HTTP Client or VS Code's
REST Client.

### The two environments

There are two environments, because the provided database and a development database are not the
same thing. The `docker compose` command above starts the local one. The provided one is
**staging**: it is what the exercise is evaluated against, and nothing experimental runs on it.

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

## Tests

```bash
./mvnw test
```

The build fails on unformatted code. To format it:

```bash
./mvnw spring-javaformat:apply
```

**The functional test the statement asks for** — *"un test funcional sobre la función para enviar
una orden"* — **is the end-to-end suite.** The web tests under
`src/test/java/com/cocos/trading/controller/` submit orders over HTTP against a real PostgreSQL
through Testcontainers ([ADR-0010](docs/adr/0010-testcontainers-with-postgresql-18.md)), with every
response validated against `docs/api/openapi.yaml`
([ADR-0024](docs/adr/0024-every-response-is-validated-against-the-contract.md)). They cover all four
submission paths, every refusal and every rejection, the two-thread concurrency race and the
idempotent retry — the requested test, and more.

## Endpoints

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/v1/users/{userId}/portfolio` | Total account value, cash available to trade, and one entry per position with quantity, market value and total return |
| `GET` | `/api/v1/instruments/search?query={text}` | One page of the instruments matching the text by ticker or name — partial and case-insensitive, ordered by ticker, never pesos (cash is not a tradable asset), each carrying its latest close and the day's move. Accents must be typed as they are stored: `río` finds *Molinos Río de la Plata*, `rio` does not (3 of 66 names; their tickers always match; `unaccent` considered and deferred, ADR-0027). Optional `page` and `size` |
| `POST` | `/api/v1/users/{userId}/orders` | Submits one order, by an exact `size` or by a total `amount` in pesos — exactly one of the two — from which whole shares are derived by flooring, never a fraction. `MARKET` fills immediately at the last close; `LIMIT` is validated at the caller's own price and accepted as `NEW`, reserving the pesos or the shares it commits. An order the user cannot pay for, or whose shares they do not hold, is **stored** `REJECTED` with a machine-readable reason and answered `201`, like the other two: a rejection is a recorded outcome, a malformed request is not (ADR-0033). Submissions for one user are serialised on their `users` row, so two simultaneous orders cannot spend the same pesos ([ADR-0036](docs/adr/0036-submissions-are-serialised-per-user.md)). An optional `Idempotency-Key` header makes a retry of the same request return the first outcome instead of trading twice, without taking that lock at all; the same key with a different body is a `409` ([ADR-0037](docs/adr/0037-idempotent-submission-with-a-caller-key.md)) |
| `POST` | `/api/v1/users/{userId}/cash-transfers` | Deposits (`CASH_IN`) or withdraws (`CASH_OUT`) a whole number of pesos. **An addition to the statement, not a departure from it**: the statement says transfers *can* be modelled as `CASH_IN`/`CASH_OUT` orders, and each transfer is stored as exactly that — one `MARKET` order on the `MONEDA` instrument at price `1`, the shape the provided dataset already uses ([ADR-0039](docs/adr/0039-a-transfer-is-a-market-order-on-the-cash-instrument.md)), so the portfolio counts it with no change. A deposit is always `FILLED`; a withdrawal is `FILLED` when *available* cash covers it and otherwise stored `REJECTED` with `INSUFFICIENT_FUNDS` and answered `201`, as an order is ([ADR-0038](docs/adr/0038-the-cash-transfer-endpoint-shape.md)). Its body is **closed** — any field besides `side` and `amount` is refused with `400` — while order submission's body ignores unknown fields. Transfers share the order endpoint's per-user lock and its `Idempotency-Key` namespace, both implemented once ([ADR-0040](docs/adr/0040-per-user-write-protection-lives-once.md)) |
| `POST` | `/api/v1/users/{userId}/orders/{orderId}/cancel` | Cancels one of the user's orders that is still `NEW`, with no body, and answers `200` with the order as submission does, now `CANCELLED`; what it reserved is available again in the next portfolio read. **An addition the statement implies, not a departure from it**: the statement defines `CANCELLED` and says only `NEW` orders can be cancelled, but names no endpoint ([ADR-0041](docs/adr/0041-the-order-cancellation-endpoint-shape.md)). An order that is not `NEW` is a `409` and left as it was; an order of another user is the same `404` as one that does not exist. Cancellations share the per-user lock and the `Idempotency-Key` namespace of the other writes ([ADR-0040](docs/adr/0040-per-user-write-protection-lives-once.md)), and a keyed retry of any write answers its first outcome even if the order was cancelled since ([ADR-0043](docs/adr/0043-a-replay-answers-the-outcome-as-first-recorded.md)) |

## Two things this API deliberately does not do

- **No matching engine.** A `LIMIT` order is born `NEW` and stays `NEW`; the only path out of that
  state is the user cancelling it. The statement says not to simulate the market, and that is
  exactly what this means — there is no order book, no counterparty and no partial fills.
- **No authentication.** The statement does not ask for it, so the user is a path parameter.

## Attribution

The statement, the schema and the seed data are not mine: they were provided by Cocos Capital and
published at https://github.com/cocos-capital/cocos-challenge. `docs/challenge/backend-challenge.md`
is a verbatim copy of that statement, taken from commit `abae7701`, and the Flyway migrations under
`src/main/resources/db/migration` are a transcription of that `database.sql`.