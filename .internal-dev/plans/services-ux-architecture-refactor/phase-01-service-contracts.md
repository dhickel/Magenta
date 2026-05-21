# Phase 01: Service Contracts And Assignment Context

## Context

Assignments are the execution substrate, but project context currently lives mainly in assignment input JSON while `workspaceId` remains a compatibility field. UI/API consumers need first-class assignment context before project/job/workspace controls can be made reliable.

## Goal

Make assignment project and effective workspace context durable, queryable, and safe to display. Establish the mutation and requeue service policies that later phases depend on.

## In Scope

- Add first-class assignment fields for `projectId`, effective workspace id, and effective workspace kind.
- Backfill legacy assignment `input.projectId`.
- Preserve compatibility `workspaceId`.
- Add assignment context/read-summary DTOs and repository/service queries.
- Add `projectId`/`workspaceId` validation rules.
- Add active project/job mutation policy checks.
- Add workspace-blocked assignment requeue service operations.
- Add focused service/controller tests.

## Out of Scope

- Job execution summary UI.
- Output table redesign.
- Full optimistic editor revision control.
- Subtree/read workspace locks.
- Removing loose artifact discovery.

## Implementation Steps

1. Inspect package guides under touched Java packages before edits.
2. Extend `work_assignments` bootstrap/migration with nullable:
   - `project_id`
   - `effective_workspace_id`
   - `effective_workspace_kind`
3. Backfill `project_id` from `input_json.projectId` when the new column is null.
4. Extend `WorkAssignment`, repository mappers, inserts, updates, retained history reads, diagnostics, and tests.
5. Keep writing `projectId` into assignment input JSON for compatibility, but make services read the first-class column first.
6. Resolve effective workspace id/kind at assignment creation:
   - project present: project workspace.
   - no project: agent workspace.
   - do not acquire write lease at creation.
7. Re-resolve and repair missing effective workspace fields at execution start for legacy rows.
8. Add `AssignmentSummary` or equivalent read model exposing assignment id, agent, type, status, job/job item, `projectId`, compatibility `workspaceId`, effective workspace id/kind/display path, lease/blocker details, timestamps, and output/run linkage where already available.
9. Add repository/service queries for active assignments by project, job, effective workspace, type, status, and non-terminal states.
10. Add validation helper for submission requests:
    - `projectId` is the only project-scoping field.
    - `workspaceId` alone remains compatibility metadata.
    - reject `projectId` plus unrelated project-owned `workspaceId`.
11. Add service checks for active project/job mutation policy:
    - block project delete with active assignments/leases/runs.
    - block membership removal for agents active on that project.
    - block job delete and execution-affecting job edits with active assignments/runs.
    - allow label-only project/job edits.
12. Add requeue operations for workspace-blocked `WAITING` assignments after the blocking lease is gone.
13. Wire controller/API errors to clear 409-style responses or HTMX fragments without changing unrelated UI yet.

## Validation

Run:

```bash
mvn test -Dtest=PublicRunSubmissionControllerTest,OperationalUiContractControllerTest,AgentOrchestrationControllerTest,ProjectServiceTest,OrchestrationRuntimeTest
mvn test -Dtest=WorkspaceLeaseServiceTest,WorkspaceRepositoryAttributionTest,WorkspaceRepositorySchemaMigrationTest,WorkspacePathSegmentValidationTest
git diff --check
```

Add/extend tests for:

- new assignment columns bootstrap on clean and migrated SQLite databases.
- legacy `input.projectId` backfill.
- assignment creation persists first-class `projectId`.
- assignment summaries expose effective workspace id/kind/path.
- `workspaceId`-only submission remains non-project-scoped.
- mismatched project-owned `workspaceId` is rejected.
- two same-project assignments serialize through project workspace lease conflict.
- workspace-blocked assignment can be requeued after lease release.
- active project/job mutation policy returns the expected conflict.

## Exit Criteria

- No new UI control depends on JSON parsing for assignment project context.
- Existing assignment routes remain compatible while exposing first-class context.
- `projectId` and `workspaceId` semantics are enforced by service tests.
- Direct execution paths have not been expanded.
- Active mutation and workspace-blocked requeue helpers are available for later phases.
