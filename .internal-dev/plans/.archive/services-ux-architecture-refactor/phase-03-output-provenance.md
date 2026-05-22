# Phase 03: Output Provenance

## Context

Output artifacts already store rich attribution, but API/UI filters and display surfaces expose only part of it. Compatibility fallback can make job output pages appear correct while direct artifact attribution remains incomplete.

## Goal

Make direct output artifact attribution the primary query/display contract for project, agent, job, assignment, run, workspace, and work-unit context.

## In Scope

- Ensure plan/task/workflow/job output materialization receives first-class assignment context.
- Ensure workflow output attribution includes project/effective workspace context.
- Expand output query parameters for stored attribution fields.
- Update output repository/service tests for direct attribution.
- Keep compatibility fallback explicit and tested.
- Address deterministic output filename collision behavior for concurrent workflow outputs if needed.

## Out of Scope

- Output authorization or project membership enforcement.
- Merging chat files into orchestration output artifacts.
- Broad removal of loose artifact discovery unless done with full compatibility tests and docs.
- UI table redesign beyond data contracts needed by phase 4.

## Implementation Steps

1. Trace output materialization for plan/task, workflow, and job execution.
2. Ensure output context receives:
   - agent id.
   - project id.
   - effective workspace id.
   - compatibility workspace id only where explicitly needed.
   - job id.
   - job assignment id.
   - job run id.
   - run type.
3. Update workflow run persistence or a companion read model so workflow run/output detail can expose agent/project/effective workspace context.
4. Ensure `OrchestrationTaskContext.workspaceId` used for artifact attribution means effective workspace id. Keep compatibility workspace metadata separate.
5. Expand `OutputArtifactQuery`, repository SQL, `OutputController`, and HTMX output fragments to accept:
   - `workspaceId`
   - `planId`
   - `jobAssignmentId`
   - `jobRunId`
   - `runType`
6. Keep existing query params working:
   - `agentId`
   - `jobId`
   - `projectId`
   - `runId`
   - `type`
   - `limit`
7. Split tests for direct attribution from compatibility fallback so fallback cannot hide missing metadata.
8. Add or update tests for output content and download confinement after the query expansion.
9. Review parallel workflow output materialization. If duplicate names can overwrite files, either:
   - make generated filenames unique with a stable suffix, or
   - reject duplicates deterministically and surface a clear validation/execution error.
10. Leave loose artifact discovery as compatibility unless changed with explicit docs and regression tests.

## Validation

Run:

```bash
mvn test -Dtest=OutputArtifactServiceAttributionTest,WorkflowStreamSupportTest,TaskStreamSupportTest,PublicApiRouteBindingTest,OperationalUiContractControllerTest
git diff --check
```

Add/extend tests for:

- query by `projectId`.
- query by `agentId`.
- query by `workspaceId`.
- query by `jobId`.
- query by `jobAssignmentId`.
- query by `jobRunId`.
- query by `planId`.
- query by `runId`.
- query by `runType`.
- workflow outputs carry project/effective workspace attribution.
- job outputs carry job assignment/run attribution.
- fallback query behavior is explicitly compatibility-only.
- content/download routes still reject paths outside data root.
- duplicate output filename behavior under parallel workflow execution is deterministic.

## Exit Criteria

- New project/job/output UI can use direct artifact attribution.
- Output filters cover stored attribution fields needed for operator debugging.
- Compatibility fallback remains present only as a documented bridge.
- Chat files remain separate from output artifacts.
