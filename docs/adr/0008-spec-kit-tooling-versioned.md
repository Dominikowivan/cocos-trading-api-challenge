# ADR-0008: The agent tooling is versioned with the project

- **Status:** Accepted
- **Date:** 2026-11-15
- **Journal:** [02Setup](../journal/02Setup.md)

## Context

This exercise is built with AI assistance, which the invitation encourages. That raises a question
every team using coding agents has to answer: where does the agent's configuration live? If it
lives on each developer's machine, the same prompt produces different behaviour depending on who
runs it, and nobody can review how the work was produced.

## Decision

Spec Driven Development with Spec Kit, and **the tooling is committed to the repository**: the
constitution, the commands and the scripts. It is part of the development contract, in the same
category as the linter, the pre-commit hooks or the CI pipeline — if it changes, it changes for
everyone, under review, with history.

The line that separates this from carelessness: **configuration is versioned, generated artifacts
are not.** Caches and anything carrying machine-local paths stay out, as does the agent's local
state directory.

The constitution is where the project's criteria stop being prose and become rules: `BigDecimal`
and never `double`, compare with `compareTo`, domain logic in Java rather than in SQL, tests
against Testcontainers and never against the provided database, and no comments in the code —
whatever needs explaining belongs in the README or in a spec.

## Alternatives considered

- **Agent configuration per developer machine.** The default, and the reason the same prompt
  behaves differently for two people on the same team. Discarded for exactly that.
- **No specs, prompt the agent directly.** Faster for a single session. Discarded because then
  the reasoning exists only in a chat log, and the reasoning is what this exercise evaluates.

## Consequences

- The repository holds more process files than source files. The README opens with a map so a
  reader is never hunting for the code.
- The agent runs with its own configuration directory, isolated from any personal setup, so
  nothing outside this project can leak into it — and so the repository is the whole story.
- Spec Kit was installed after the first commit, on purpose: initialising it over an existing
  tree merges files, and with a commit in place the diff shows exactly what it added.
