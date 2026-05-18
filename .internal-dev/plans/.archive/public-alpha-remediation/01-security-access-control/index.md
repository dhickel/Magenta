# Security and Access Control Domain

## Objective

Close public-alpha security blockers around unauthenticated mutation/control surfaces, filesystem path ids, stored workflow XSS, and cross-agent lifecycle controls.

## Branch

Implementation branch: `public-alpha-remediation/security-access-control`.

The implementing agent must create this branch before code work and update root `progress.md`.

## Owned Findings

- bug-01: unauthenticated public mutation/control surface.
- bug-02: agent ids can escape agent subtree inside data root.
- bug-11: workflow graph stored XSS risk, owned here for security validation and coordinated with workflow UI.
- bug-12: assignment lifecycle routes are not agent-scoped.

## Subplans

| Order | Subplan | Findings |
| --- | --- | --- |
| 1 | `subplan-01-auth-csrf-gate.md` | bug-01 |
| 2 | `subplan-02-id-segment-validation.md` | bug-02 |
| 3 | `subplan-03-workflow-xss-security.md` | bug-11 |
| 4 | `subplan-04-agent-scoped-lifecycle.md` | bug-12 |

## Context

Implementation agents may read `review-context-index.md` for source details as needed. Validation agents must read `domain-api-web.md`, `horizontal-security-error-htmx.md`, `domain-orchestration-runtime.md`, `domain-workflow.md`, `remediation-handoff.md`, and bug reports 01, 02, 11, and 12 before validating.

## Race Risks

Coordinate with `04-workflow-authoring-runtime-js` on workflow XSS so the same JS/HTML rendering code is not rewritten twice. Coordinate with `02-workspace-tools-outputs` on shared id/path segment validation helpers before tool confinement work starts.
