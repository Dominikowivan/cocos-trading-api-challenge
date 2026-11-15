# Specification Quality Checklist: Portfolio Endpoint

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-15
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

All 16 items pass. The three clarifications are resolved (2026-11-15):

1. **Negative position** → reported as-is, no total return, error-level diagnostic with user,
   instrument and quantity (FR-017, FR-018, FR-019). The rule it violates is real, so the
   response stays honest to the user and loud to the technical team.
2. **Held instrument with no market data** → position listed, market value and return absent,
   account total absent, warning-level diagnostic (FR-020, FR-021, FR-022). Measured: the three
   unpriced instruments are `PGR`, `IRCP` and `ARS`; `ARS` **is** held (user `1` has `CASH_IN` and
   `CASH_OUT` rows in it, settled cash `753,000.00`)
   but is excluded as cash, so no *position* in the seed lacks market data. The exclusion must run
   first or user `1`'s total goes `null` — pinned by FR-023 and SC-012. The path itself needs a
   constructed fixture; it is defensive and unreachable through the API.
3. **Unknown user id** → rejected as not found (FR-024).

Two gaps found in review on 2026-11-15 and closed, both of the kind an implementing agent would
have silently invented an answer for:

- **A position taken *through* zero** had no rule. The seed already does it (BMA goes
  `0 → +20 → -10`), and a reviewer buying 30 BMA for user `1` would reopen it long. FR-008 now
  says the crossing resets the basis and the remainder takes the crossing order's price; US3
  scenarios 4-5 and SC-013 pin it.
- **Settled cash** was described as the fold of "pesos movements", readable as the `ARS` rows
  alone, which yields `900,000.00` rather than `753,000.00`. FR-010 now states that every `FILLED`
  order has a pesos effect, not just the rows of the pesos instrument.

Both resolutions that had real alternatives are now recorded as ADRs, with the index in
`docs/adr/README.md` updated in the same change (constitution principle VII):

- [ADR-0012](../../../docs/adr/0012-ledger-anomalies-are-reported-not-hidden.md) — report-vs-reject
  a short position.
- [ADR-0013](../../../docs/adr/0013-unknown-money-is-absent-never-zero.md) — absent-vs-partial
  account total, and absent-not-zero as a general rule.

Notes for the next phase:

- "Fail with an error log" means *the request succeeds and an error-level record is written*, not
  that the request is rejected. Raised as an interpretation and confirmed by the author on
  2026-11-15; recorded in Assumptions and in ADR-0012.
- Both behaviours are recorded in ADR-0012 and ADR-0013 and nowhere else. Principle VII's README
  obligation covers departures from the challenge statement; the statement does not consider
  either case, so neither is a departure and no README note is owed.
- Requirements reference the challenge statement and ADRs 0002/0003/0004/0007 instead of
  restating them, per constitution principle VII and the Technology and Scope Constraints rule
  that the statement is the verbatim source of requirements.
- Column and table names (`orders`, `size`, `close`, `previousClose`, `MONEDA`) appear because
  they are the vocabulary of the given dataset in the statement, not implementation choices.
  Diagnostic *levels* appear because the anomalies are only ever noticed through them; their
  destination and format are left to plan time.
