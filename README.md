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
| [`docs/api/trading-api.http`](docs/api/trading-api.http) | Example requests with their expected figures, runnable from IntelliJ's HTTP Client or VS Code's REST Client |
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

- JDK 21
- Docker — for the local database and for Testcontainers

## Running it

There are two environments, because the provided database and a development database are not the
same thing. The provided one is **staging**: it is what the exercise is evaluated against, and
nothing experimental runs on it.

**Default: a local database.** Brings up PostgreSQL in Docker, applies the migrations and loads
the provided dataset:

```bash
docker compose up -d
./mvnw spring-boot:run
```

The container publishes port **5433**, not 5432, so it does not collide with a PostgreSQL already
installed on the machine. It runs **PostgreSQL 18.7**, the same version as the provided database.

**Staging: the provided database.** Credentials are never stored in this repository; they are
read from the environment:

```bash
export COCOS_DB_URL="jdbc:postgresql://<host>/<database>"
export COCOS_DB_USER="<user>"
export COCOS_DB_PASSWORD="<password>"

./mvnw spring-boot:run -Dspring-boot.run.profiles=staging
```

## Tests

```bash
./mvnw test
```

## Endpoints

| Method | Path | What it does |
|---|---|---|
| `GET` | `/api/v1/users/{userId}/portfolio` | Total account value, cash available to trade, and one entry per position with quantity, market value and total return |
| `GET` | `/api/v1/instruments?query={text}` | Instruments matching the text by ticker or name |
| `POST` | `/api/v1/orders` | Submits an order. `MARKET` fills immediately at the last close; `LIMIT` is accepted as `NEW` |
| `POST` | `/api/v1/orders/{orderId}/cancel` | Cancels an order that is still `NEW` |

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
 - the portfolio endpoint ships (feature 001-portfolio-endpoint); instrument search, order
   submission and order cancellation are still a proposal to be confirmed
 - `V2__portfolio_read_indexes.sql` adds the two `orders` indexes and the
   `UNIQUE (instrumentid, date)` constraint on `marketdata`; there is still no migration for
   `order_status_history`
──────────────────────────────────────────────────── -->
