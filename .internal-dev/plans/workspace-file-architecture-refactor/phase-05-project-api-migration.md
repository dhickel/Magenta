# Phase 05: Project API And Owner-Agent Migration

## Context

Projects are intended to be shared durable workspace and visibility abstractions, not executable work units and not permanently owned by a single agent. Current project API, schema, services, and UI still require or display owner-agent semantics.

## Goal

Remove required project owner-agent behavior through a compatibility-preserving migration and introduce explicit `projectId` submission context where missing.

## In Scope

- Project model, repository, service, and controller changes.
- Additive schema migration for nullable/compatibility owner fields if needed.
- Explicit `projectId` in task/workflow/plan/job submission records where missing.
- Compatibility handling for existing `workspaceId` payloads.
- Operational UI labels/forms affected by owner-agent semantics.
- Route, schema, service, and focused UI tests.

## Out of Scope

- Making projects executable.
- Removing all legacy response fields immediately.
- Broad UI redesign.
- Job persistent workspace policy.

## Implementation Steps

1. Make `ownerAgentId` nullable or compatibility-only at schema/repository boundaries.
2. Update project creation so owner agent is optional; legacy supplied owner creates or preserves membership.
3. Update project update/delete/member logic so ownership no longer blocks membership removal unless a replacement rule is explicitly required.
4. Add explicit `projectId` to submission APIs while preserving `workspaceId`.
5. Prefer `projectId` when both fields are present.
6. Update UI labels from owner-agent language to membership/context language.
7. Add tests for legacy payload compatibility and new `projectId` behavior.
8. Append phase notes and validation results.

## Validation

- `ProjectServiceTest`.
- `ProjectRepositoryTest`.
- `WorkspaceRepositorySchemaMigrationTest`.
- `PublicApiRouteBindingTest`.
- `OrchestrationControllerTest`.
- `OperationalUiContractControllerTest`.
- Spring context smoke.
- Focused Playwright MCP validation for changed UI interactions.

## Exit Criteria

- Project creation no longer requires owner agent.
- Existing owner fields remain compatible where exposed.
- Work submission can explicitly attach `projectId`.
- `workspaceId` compatibility is preserved.
- Phase validation passes and the phase is committed.
