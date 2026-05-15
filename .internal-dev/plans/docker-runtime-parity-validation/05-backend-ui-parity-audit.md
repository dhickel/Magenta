# Phase 05: Backend/UI Parity Audit

## Context

The product is not complete if backend capabilities exist but the UI is missing, partial, misleading, or only operable through raw API knowledge.

## Goal

Create a backend capability inventory and compare it against what an operator can actually do through the UI.

## In Scope

- Docker/runtime controls.
- Agents and agent profiles.
- Plans/tasks.
- Workflows, gates, inbox, and resume.
- Jobs, projects, schedules, assignments, reactions.
- Workspaces, links, leases, outputs.
- Model overrides and agent chat.
- HTMX-vs-JavaScript policy adherence for UI flows.

## Out of Scope

- Visual polish with no contract consequence.

## Implementation Steps

1. Build a capability matrix with columns:
   - backend capability
   - backend entry point/service
   - UI route/control
   - reachable from browser
   - complete / partial / missing
   - HTMX / JavaScript / mixed
   - evidence
   - remediation note
2. Derive backend capabilities from controllers/services, not from assumptions.
3. Walk every operational page in Playwright and map visible controls back to the matrix.
4. For each backend capability, classify whether the UI:
   - fully supports it
   - supports only part of it
   - exposes it misleadingly
   - omits it entirely
5. Pay special attention to Docker/runtime status completeness and workspace/link/lease visibility.
6. Review JavaScript-backed flows and record whether JS is genuinely the path of least resistance or whether standard CRUD/fragment behavior should have been HTMX.
7. Turn every missing or partial alpha-relevant capability into a tracked issue, not just a prose observation.

## Validation

Required checks:
- Every backend domain named in scope appears in the matrix.
- Every missing or partial UI surface has explicit evidence.
- No backend-only feature is silently counted as complete.
- JavaScript usage is justified wherever standard HTMX could have handled the flow.

## Exit Criteria

- `.internal-dev/reviews/docker-runtime-parity-validation/05-backend-ui-parity-evidence.md` exists.
- A reusable parity matrix is attached or embedded.
