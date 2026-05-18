# Workflow Authoring, Runtime, and JS Domain

## Objective

Make workflow authoring usable and correct while preserving HTMX-first CRUD and limiting JavaScript to graph interactions that genuinely need it.

## Branch

Implementation branch: `public-alpha-remediation/workflow-authoring-runtime-js`.

## Owned Findings

- bug-03, bug-04.
- ro-04 workflow JS island overreach.
- ro-05 graph network failure under-reporting.
- Coordinates with security for bug-11 and execution domain for workflow direct-run routes.

## Subplans

| Order | Subplan | Findings |
| --- | --- | --- |
| 1 | `subplan-01-draft-editing-validation.md` | bug-03 |
| 2 | `subplan-02-executable-workflow-validation.md` | bug-04 |
| 3 | `subplan-03-js-island-narrowing.md` | ro-04 |
| 4 | `subplan-04-graph-error-handling.md` | ro-05 |

## Context

Validators must read `domain-workflow.md`, `domain-frontend-static.md`, `horizontal-security-error-htmx.md`, `domain-api-web.md`, `remediation-handoff.md`, and bug reports 03, 04, and 11.
