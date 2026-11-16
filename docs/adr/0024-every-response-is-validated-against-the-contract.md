# ADR-0024: One living API contract, and every response in the web tests is validated against it

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

Each feature specified with Spec Kit carries a hand-written OpenAPI 3.1 contract in its spec,
`specs/NNN-feature/contracts/*.openapi.yaml`, written before the controller and meant to be what
the controller is checked against (research R-022 of the portfolio feature). Two problems with it:

- **Nothing checked it.** No test read the file. A field renamed in a DTO, a type changed, a status
  code added without a schema, and the contract would keep describing an API that no longer exists.
- **It belongs to the past.** A spec records what a feature designed at the time. When a later
  feature changes an endpoint, editing the old spec's contract rewrites that record, and leaving it
  alone leaves a contract that no longer describes the API.

## Decision

- **The API contract lives in `docs/api/openapi.yaml`**, one file for the whole API, next to the
  `trading-api.http` collection (research R-023). It started as a copy of the portfolio feature's
  contract.
- **A spec's `contracts/` is the design as that feature proposed it**, and is not edited after the
  feature is implemented. A feature that changes the API proposes the change in its own spec and,
  when implemented, applies it to `docs/api/openapi.yaml` and `trading-api.http`.
- **The `MockMvc` that every web test uses validates every response against
  `docs/api/openapi.yaml`**, through `alwaysExpect` on a `MockMvcBuilderCustomizer`
  (`ContractValidationConfiguration`, imported by `WebTestBase`). No test opts in, so none can
  forget to.

The validator is Atlassian's `openapi-request-validator-mockmvc` 3.0.0, which supports OpenAPI 3.1
(including `type: [number, "null"]`, how the contract marks the figures that can be unknown) and
Spring 7. It is not managed by the Spring Boot BOM, so its version is pinned in the pom.

What it checks:

- **Responses, fully**: status code declared for the path, content type, and the body against the
  schema, `additionalProperties: false` included. It checks shape, not values: the figures are
  asserted by the tests themselves, and the `examples` in the contract are documentation.
- **Requests, only their route**: a request to a path or method the contract does not declare
  fails. The rest of request validation is off, because some tests send invalid requests on
  purpose (`/users/abc/portfolio` is how the 400 is tested).

Checked to fail, on the portfolio tests, by changing the contract three ways: removing
`settledCash` from the schema (every test failed, `additionalProperties`), declaring
`totalAccountValue` a string (every test failed except the one where it is `null`, which is still
valid), and renaming the path (every test failed, `path.missing`).

## Alternatives considered

- **Validate against each spec's own contract.** It needs no copy, but it makes a historical
  document the live one: the first feature that changes an existing endpoint has to either edit an
  old spec or validate against a stale contract. It would also need each request routed to the
  contract that declares its path.
- **One living contract per resource** (`portfolio`, `instruments`, `orders`). Smaller files, but
  the same routing problem, and a client imports several files instead of one.
- **Generate the contract from the code with springdoc.** The document would follow the code and
  could never disagree with it, which is the opposite of what a contract is for (R-022). Already
  discarded for runtime documentation in the author's journal.
- **Contract tests opted into per test** (`.andExpect(openApi().isValid(...))` on each request).
  Same library, but a test that forgets the line is not validated, and nothing notices.
- **A Postman collection generated from the contract.** A third copy to regenerate on every change,
  and a JSON file whose diffs are mostly generated ids. The `.http` collection is plain text and
  runs from the IDE.

## Consequences

- A change to a response that the contract does not describe fails the build, and so does a
  contract change the code does not follow.
- An endpoint missing from `docs/api/openapi.yaml` fails its own web tests with `path.missing`. A
  feature cannot ship an endpoint without adding it to the contract. The constitution states the
  rule so that the agents implementing the next features follow it.
- The requirement ids quoted in the contract (`FR-`, `SC-`, research `R-`) refer to the spec of the
  feature that introduced each endpoint, which the contract's own description says.
- Request bodies are not validated against the contract. When order submission lands with a
  request schema, turning request body validation on for valid requests is worth reconsidering.
