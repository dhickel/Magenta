# Phase 02: Effective Workspace Resolver And Run Metadata

## Context

Workspace decisions are currently fragmented across directory helpers, plan/task services, workflow code, and file/shell tools. The target architecture requires one effective durable workspace for every run.

## Goal

Introduce a central resolver and shared layout helpers, then persist effective workspace/output metadata when runs start.

## In Scope

- Effective workspace resolver service and data carrier.
- Shared workspace layout helpers for agent and project workspaces.
- Work-unit output path helpers.
- Plan/task run metadata compatibility updates.
- Schema/repository updates only where needed for persisted metadata.

## Out of Scope

- Project owner-agent migration.
- Job persistent workspace policy.
- Hard removal of loose artifact discovery.
- Full UI update.

## Implementation Steps

1. Add an `EffectiveWorkspaceResolver` under the workspace/orchestration package using existing path validation.
2. Extend `WorkspaceDirectoryService` with shared layout methods for `work`, `outputs`, `runs`, `scratch`, and work-unit output paths.
3. Update plan/task run creation to persist effective `workspaceId`, `projectId` where available, output directory, and run temp directory consistently.
4. Keep old payloads and fields readable.
5. Add/update schema migration tests for additive compatibility.
6. Append phase notes and validation results.

## Validation

- Workspace path segment and confinement tests.
- Workspace repository/schema migration tests.
- Plan repository/service tests covering run metadata.
- Spring context smoke.

## Exit Criteria

- A single resolver owns effective workspace selection.
- Run metadata no longer depends on late output-path inference for the common path.
- Existing `workspaceId` compatibility is preserved.
- Phase validation passes and the phase is committed.
