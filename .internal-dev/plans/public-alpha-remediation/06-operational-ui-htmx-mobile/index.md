# Operational UI, HTMX, and Mobile Domain

## Objective

Fix operational UI usability and HTMX behavior issues that make public alpha confusing or unusable, especially mobile layout and stale runtime UI references.

## Branch

Implementation branch: `public-alpha-remediation/operational-ui-htmx-mobile`.

## Owned Findings

- bug-16, bug-18, bug-20.
- ro-09 stale Docker/Podman UI/resource/docs labels.
- ro-10 static placeholder agent events.
- ro-11 shallow workspace health display.

## Subplans

| Order | Subplan | Findings |
| --- | --- | --- |
| 1 | `subplan-01-mobile-shell-layout.md` | bug-16 |
| 2 | `subplan-02-agent-lifecycle-htmx-targets.md` | bug-18 |
| 3 | `subplan-03-htmx-error-statuses.md` | bug-20 |
| 4 | `subplan-04-stale-runtime-labels.md` | ro-09 |
| 5 | `subplan-05-agent-detail-quality.md` | ro-10, ro-11 |

## Context

Validators must read frontend/API/workspace reviews, Playwright evidence, and bug reports 16, 18, and 20.
