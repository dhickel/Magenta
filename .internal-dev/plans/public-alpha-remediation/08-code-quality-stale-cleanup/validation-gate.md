# Code Quality Cleanup Validation Gate

## Validator Instructions

Read cleanup-relevant review files before validating and verify current code references rather than trusting older review assumptions.

## Required Checks

- Legacy workflow package is removed or documented as intentionally retained.
- Stale static modules are removed/quarantined without asset 404s.
- Active stale Docker/direct-run/comment residue is cleaned.
- Final sweep maps every review-only concern to completed, retained-with-reason, or explicitly user-deferred.
- Maven tests, compile, and focused public page asset check pass.
