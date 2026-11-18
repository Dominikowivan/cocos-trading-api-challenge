# ADR-0023: Dependencies and secrets are scanned with Trivy in CI

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

SonarQube Cloud ([ADR-0020](0020-static-analysis-on-sonarqube-cloud.md)) covers this project's own
code, security rules and taint analysis included, and looks for secrets in the files it analyses.
It does not look at dependencies: software composition analysis is a separate paid product, not
part of the Free plan. Dependabot was not enabled on the repository. Nothing checked the
libraries the application ships with for known vulnerabilities.

Measured on 2026-11-16, before deciding:

| Surface | Tool | Result |
|---|---|---|
| Dependencies that ship (compile and runtime scope) | Trivy 0.76.1 | 84 packages, 0 vulnerabilities |
| Secrets in the working tree | Trivy 0.76.1 | None |
| Secrets in the whole git history | gitleaks 8.30.1 | None, over the 21 commits with a diff (the other 5 are merges) |

The only password in the history is `trading`, the one the local Docker Compose database uses on
purpose.

## Decision

Every CI run, on every branch and pull request, runs Trivy after `./mvnw verify`, in the same job:

- It scans **dependencies and secrets**, and fails CI on a **HIGH or CRITICAL** finding.
- Vulnerabilities **with no fixed version** are ignored (`--ignore-unfixed`): CI fails only on
  what an upgrade can resolve.
- It runs **offline over the `~/.m2` the build has just filled** (`--offline-scan`). Without it,
  Trivy fetches every POM from Maven Central to resolve the dependency tree, and while this was
  being measured Central answered `429 Too Many Requests` and blocked the IP for 30 minutes.
- It runs as the official Docker image pinned by version and digest
  (`aquasec/trivy:0.76.1@sha256:790f…`), not through a third-party action: a tag can be moved, a
  digest cannot.

Both halves were checked to fail. A planted `jackson-databind` 2.9.8 produced 53 findings (41 high,
12 critical) and exit code 1; a planted token in GitHub's format was reported as a critical secret
and failed the scan. With both removed, the scan passes.

The history check with gitleaks is not part of CI. It is a one-off, repeated before the repository
is made public: new commits are already covered by the secret scanners of Trivy and SonarQube Cloud.

## Alternatives considered

- **OWASP Dependency-Check.** The Maven plugin matches dependencies against NVD entries by CPE,
  which is known for false positives, and needs an NVD API key and a large first download.
- **Dependabot alerts.** It watches continuously, so a vulnerability published after a merge is
  reported without waiting for the next CI run. That is what protects code already running in
  production, and this project will not run in production: it is delivered and then archived, and
  alerts about it would keep arriving by mail with nothing left to act on. It also raises alerts
  and pull requests but blocks nothing, so it would not replace a gate. Not enabled.
- **Snyk.** Another account and another service with plan limits, for what Trivy does locally.
- **`aquasecurity/trivy-action`.** It wraps the same scanner, but pins by a tag of a third-party
  repository.
- **GitHub CodeQL and secret scanning.** On a private repository they need GitHub Advanced
  Security, which is paid; SonarQube Cloud already covers static analysis of the code.

## Consequences

- A dependency with a fixable high or critical vulnerability, or a committed secret, fails CI on
  the branch that introduces it, before any pull request.
- Test-scope dependencies are not scanned: they do not ship. A vulnerability that only reaches
  tests (for example through Testcontainers) does not fail CI.
- A vulnerability published after a merge is found on the next CI run, not before.
- The vulnerability database, about 134 MB, is downloaded on every run.
- An unfixed vulnerability does not fail CI and does not show up either; it has to be looked for
  with a scan without `--ignore-unfixed`.
