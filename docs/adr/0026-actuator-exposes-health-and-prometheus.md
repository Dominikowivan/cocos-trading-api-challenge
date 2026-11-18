# ADR-0026: Actuator exposes health and Prometheus metrics, and nothing collects them here

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

The application exposed nothing about its own state. Whoever runs it could not ask whether it can
serve requests, nor read how many it serves, how long they take, how many fail, or how close the
database connection pool is to running out: the four golden signals (latency, traffic, errors,
saturation).

Spring Boot measures all four without application code once Actuator is present. Actuator brings
Micrometer, which records HTTP requests through a servlet filter in front of the dispatcher, the
JVM and the Hikari pool into an in-memory registry. What remains to decide is what is exposed,
in which format, and whether this repository also runs the system that collects it.

## Decision

- **Actuator, exposing exactly two endpoints over HTTP**: `/actuator/health`, which reports `UP`
  only if the database answers, and `/actuator/prometheus`, the registry's metrics in Prometheus's
  text format (`micrometer-registry-prometheus`). Both dependencies are managed by the Spring Boot
  BOM. Every other Actuator endpoint stays closed: the API has no authentication (ADR-0007), and
  some of them disclose configuration (`env`) or memory (`heapdump`).
- **No Prometheus server, dashboard or alerting in this repository.** Collecting metrics is shared
  infrastructure that serves every service of whoever operates them, not part of one API; and what
  watches a service should not share its failure domain, or a lost host takes the alarm down with
  the thing it should alarm about. What this API owes that infrastructure is the endpoint.
- **No container health check.** ADR-0025 expected one to come with this work, and the runtime
  image has `curl` to call `/actuator/health`. But Docker and Compose only mark a container
  unhealthy; acting on it is an orchestrator's job, and nothing here runs one. The check would
  have no consumer, so it is not added.
- **No domain metric yet.** A counter of ledger anomalies (research Q-002 of the portfolio
  feature) is only worth having when an alert reads it; the error-level log already reports each
  one with the user and the ticker. It is added together with the alert, if one is ever added.
- **The tests run on the application's own configuration.** `src/test/resources/application.yaml`
  was a hand-made copy of the main one and, sharing its name, replaced it entirely in tests: the
  Actuator exposure was missing there, and so would have been any later property. It was removed;
  its only real difference, Hibernate statistics for `OrderRepositoryTest`, moved to
  `PersistenceTestBase`.

`ActuatorEndpointsTest` checks that health is `UP` with the database reachable, that the
Prometheus output carries the portfolio's requests under the route template
(`/api/v1/users/{userId}/portfolio`, not one series per user), and that seven other endpoints
answer 404. It enables metrics export with `@AutoConfigureMetrics`, since Spring Boot disables it
in tests by default.

## Alternatives considered

- **Prometheus, and Grafana, in `compose.yaml`.** Cheap to add behind a profile, and it would show
  history instead of a snapshot. Without load it would plot flat lines, and it is infrastructure a
  reviewer has to start to see nothing happen. Kept for the load tests, where it has something to
  show.
- **Actuator without a registry.** Metrics would still be measured and readable as JSON at
  `/actuator/metrics`, but in no format a monitoring system scrapes. The Prometheus format is the
  de facto standard, and switching to another backend is a dependency change, not a code change.
- **Exposing every Actuator endpoint.** Convenient while developing, and a disclosure of
  configuration and memory on an API without authentication.

## Consequences

- Anyone running the API can read its golden signals with one request, and point a Prometheus at
  it with one scrape target.
- Nothing keeps history and nothing alerts. A latency percentile alert would also need the
  `http.server.requests` histogram enabled.
- A property added to `application.yaml` now reaches the tests without being copied.
