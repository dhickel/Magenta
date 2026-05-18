# Validation Harness and Regression Domain

## Objective

Raise confidence by adding Spring web/context, REST/SSE, Playwright, and fixture parity coverage that the public-alpha review showed was missing.

## Branch

Implementation branch: `public-alpha-remediation/validation-harness-regression`.

## Owned Findings

- bug-17.
- ro-14 reusable Playwright harness.
- ro-15 production/test schedule/reaction config mismatch.
- ro-16 SQLite fixture `foreign_keys=true` gaps.
- ro-17 green tests missed core blockers.

## Subplans

| Order | Subplan | Findings |
| --- | --- | --- |
| 1 | `subplan-01-spring-web-route-coverage.md` | bug-17 |
| 2 | `subplan-02-playwright-harness.md` | ro-14 |
| 3 | `subplan-03-fixture-parity.md` | ro-15, ro-16 |
| 4 | `subplan-04-regression-gap-tests.md` | ro-17 |

## Context

Validators must read `domain-test-harness.md`, `automated-validation-evidence.md`, `playwright-public-pages-evidence.md`, `final-readiness-review.md`, `remediation-handoff.md`, and bug-17.
