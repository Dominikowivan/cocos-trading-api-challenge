# ADR-0022: Java is analysed with the Sonar way comprehensive profile

- **Status:** Accepted
- **Date:** 2026-11-16

## Context

The first full analysis on SonarQube Cloud ([ADR-0020](0020-static-analysis-on-sonarqube-cloud.md))
reported no issues at all. The analysis was real: the Java analyser parsed all 32 main and 25 test
files. What explained the zero was the profile. New organizations default to **Sonar way core**
(313 Java rules), which activates the security rules, reliability rules of medium severity and
above, and maintainability rules of blocker severity only. Almost no code smell is in it.

SonarQube Cloud ships three built-in profiles with the same security rules and increasing
reliability and maintainability coverage: core (313 rules), extended (384) and comprehensive
(670). The Free plan allows choosing among them, not creating a custom one.

## Decision

The organization's default profile for Java is **Sonar way comprehensive**. Other languages in the
repository (XML, YAML) stay on core.

Measured with it on 2026-11-16, the codebase had four issues:

| Issue | Severity | Verdict |
|---|---|---|
| `allSatisfy` on a list that was not checked to be non-empty (`OrderRepositoryTest`) | Reliability, low | **A real defect.** The test passed vacuously if the query returned no orders, and its other assertion (one prepared statement) also held with none. Fixed with `isNotEmpty()` before the predicate |
| `Portfolio` could be a record | Maintainability, medium | Fixed. It was the only value type in the model that was not one; the sorting moved to a compact constructor |
| Several assertions on the same subject not chained (two tests) | Maintainability, low | Fixed. No change to what is asserted |

The first one was invisible under core, which leaves out low-severity reliability rules.

## Alternatives considered

- **Sonar way core, the default.** It reported nothing, including a test that could pass without
  testing anything.
- **Sonar way extended.** Adds low-severity reliability and high-severity maintainability rules.
  It would have caught the vacuous `allSatisfy`, a low-severity reliability issue, but none of the
  other three, which are medium and low maintainability. Comprehensive raised only four issues on
  this codebase: it was not noisy enough to need the middle option.
- **A custom profile.** Not available on the Free plan.

## Consequences

- New issues are measured against 670 rules. With four issues on about 970 lines, all fixed, the
  baseline is zero.
- A rule that disagrees with a decision recorded here is marked in SonarQube Cloud with the reason,
  not suppressed in code (constitution VII).
- The profile is a setting of the SonarQube Cloud organization, not of the repository. This ADR is
  where it is recorded.
