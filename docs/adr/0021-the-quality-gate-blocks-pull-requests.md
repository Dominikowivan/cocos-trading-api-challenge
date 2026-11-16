# ADR-0021: The quality gate blocks pull requests, not pushes to develop

- **Status:** Accepted
- **Date:** 2026-11-16
- **Supersedes:** the part of [ADR-0020](0020-static-analysis-on-sonarqube-cloud.md) that made every
  analysis wait for the quality gate

## Context

ADR-0020 ran the SonarQube Cloud analysis with `sonar.qualitygate.wait=true` on both pull requests
into `develop` and pushes to `develop`, so a failed gate failed CI in either case.

The push that merged the first pull request failed CI twice, both times after the analysis had
been uploaded and with the code itself clean. First the scanner could not read the gate status
(`Organization is not allowed to access data from non main branches`), because the project's main
branch in SonarQube Cloud was still `main`. Once it was renamed to `develop`, the re-run analysed
`develop` as the main branch for the first time; SonarQube Cloud computes new-code measures, and
the gate that depends on them, only from the second analysis, so the gate was *Not computed*, and
the scanner reported that as `FAILED`. The same analysis found no issues, 93% coverage and no
duplication.

Neither failure was about the code. Both showed something about the design: a push to `develop`
arrives after the merge, so a red CI there blocks nothing. It only reports late.

## Decision

The scanner waits for the quality gate only on pull requests
(`sonar.qualitygate.wait=${{ github.event_name == 'pull_request' }}`). A push to `develop` is
still analysed, without waiting for the gate.

## Alternatives considered

- **Keep waiting on both.** A gate that fails after the merge turns `develop` red with nothing
  left to stop, and makes CI depend on SonarQube Cloud being reachable on every merge.
- **Stop analysing pushes to `develop`.** That analysis is what moves the baseline that new code is
  measured against, and what the dashboard shows. It stays.

## Consequences

- The pull request is where the gate decides. A failed gate on `develop` is still visible in
  SonarQube Cloud, and the next pull request starts from it.
- The pull request check only stops a merge if `develop` is protected with a rule that requires it.
  Without that rule, the gate is a visible signal on the pull request, not a lock.
