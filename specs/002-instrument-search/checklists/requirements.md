# Specification Quality Checklist: Instrument Search

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-16
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain — all three answered by the author on 2026-11-16
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

- Every acceptance number was measured on `src/main/resources/db/seed/V1000__given_dataset.sql`
  on 2026-11-16, never estimated: 66 instruments (65 `ACCIONES`, one `MONEDA`), three names with
  diacritics, three instruments without market data, and the probe counts quoted in the spec. The
  counts were re-measured over the 65 searchable instruments after `MONEDA` was excluded.
- The three clarifications were answered by the author on 2026-11-16 and are recorded in
  Assumptions, with the requirements they settle:
  - **Q1 — price and daily return are in scope** (user story 4, FR-016 to FR-019). The statement's
    `close`/`previousClose` rule is implemented nowhere else in the API.
  - **Q2 — `MONEDA` instruments are excluded** (FR-023), so every result is something an order can
    be placed against.
  - **Q3 — matching is literal containment, not typo-tolerant** (FR-024). "Activos similares"
    means one fragment returns the family that shares it (`banco` → four banks), not that `bnaco`
    is forgiven. No similarity measure or database extension enters the feature, so no ADR is owed
    for it.
- Column names (`close`, `previousClose`, `MONEDA`) appear in the spec because they are the
  statement's own vocabulary for the data, not an implementation choice made here.
- Left to plan time by design: the concrete result cap, the maximum query length, the endpoint's
  path and parameter names, and any index. The spec only requires that each exists and is
  observable.
