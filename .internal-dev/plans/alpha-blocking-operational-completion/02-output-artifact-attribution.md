# Phase 02: First-Class Output Artifact Attribution

## Context

Output artifacts currently store `runId`, `planId`, output name, type, file path, and content JSON. Agent output filtering is indirect: the UI walks agent-owned jobs, then their runs, then artifacts. This is fragile as ownership semantics evolve and does not satisfy the operational contract that outputs can be directed to agents, jobs, projects, and users.

Relevant files:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/RunOutputArtifact.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunContext.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OperationalUiContractControllerTest.java`

## Goal

Persist direct attribution metadata on each output artifact so output browsing and agent output tabs do not depend on slow or imprecise job/run traversal. Filtering by agent, job, project, workspace, run, plan, and type should be service/repository-level behavior.

## In Scope

- Add nullable artifact fields: `agentId`, `jobId`, `projectId`, `workspaceId`, and `runType` if useful for distinguishing plan/workflow/job output.
- Add migration-safe schema updates for existing SQLite databases.
- Extend output materialization APIs to accept attribution context.
- Update artifact query APIs and UI filters to use direct metadata.
- Backfill direct metadata opportunistically when materializing from known run contexts.

## Out of Scope

- Retrospective backfill of old artifact rows from historical jobs unless it is trivial and safe.
- User inbox delivery redesign.
- Changing output file materialization rules.

## Implementation Steps

1. Extend `RunOutputArtifact`.
   - Add fields after `planId` or before `createdAt`:
     - `String agentId`
     - `String jobId`
     - `String projectId`
     - `String workspaceId`
     - `String runType`
   - Keep constructor updates mechanical and explicit.

2. Update `WorkspaceRepository` schema.
   - Add columns with `alter table` guarded by an existing local helper if present, or implement a small `addColumnIfMissing(table, column, ddl)` helper using `pragma table_info`.
   - Add indexes:
     - `idx_run_output_artifacts_agent`
     - `idx_run_output_artifacts_job`
     - `idx_run_output_artifacts_project`
     - `idx_run_output_artifacts_workspace`
   - Update `saveArtifact`, `toArtifact`, and query SQL.

3. Add a typed query record.
   - Prefer `OutputArtifactQuery(String agentId, String jobId, String projectId, String workspaceId, String runId, String planId, String artifactType, int limit)`.
   - Place it in the workspace package.
   - Add `OutputArtifactService.query(OutputArtifactQuery query)`.
   - Keep the legacy `query(runId, planId, artifactType, limit)` as a compatibility wrapper.

4. Extend materialization with attribution.
   - Add `OutputArtifactContext` or similar record with the attribution fields.
   - Add overloads:
     - `materialize(..., Path outputDir, OutputArtifactContext context)`
     - `materializeAll(..., Path outputDir, OutputArtifactContext context)`
   - Existing callers can pass an empty context until updated.

5. Wire known contexts.
   - In plan/task execution paths, pass `agentId`, `jobId`, `projectId`, and `workspaceId` from `OrchestrationRunContext` where available.
   - In workflow and job runners, preserve child run IDs and pass the parent context into artifact materialization.
   - Do not invent ownership if the caller does not know it; store null instead.

6. Update UI filtering.
   - Replace `OrchestrationController.queryOutputs(...)` job traversal with `outputArtifactService.query(new OutputArtifactQuery(...))`.
   - Keep fallback traversal only for old rows with null attribution if needed, and mark it as compatibility fallback.
   - Agent output tab should filter by `agentId` directly.

7. Update tests.
   - Add repository tests proving old DBs get new columns and queries by each attribution field work.
   - Add service tests proving artifact save preserves attribution.
   - Update controller tests to assert agent output tab uses direct query behavior through stubs where practical.

## Validation

Required commands:

```bash
mvn -q -Dtest=OperationalUiContractControllerTest,OrchestrationControllerTest test
mvn -q -Dtest=WorkspaceLeaseServiceTest,OrchestrationRuntimeTest test
mvn -q test
timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Manual validation:

- Create or seed artifacts with `agentId`, `jobId`, and `projectId`.
- Open `/outputs`.
- Filter by agent, job, project, run, and type.
- Open an agent detail outputs tab and verify it shows only that agent's directly attributed outputs.

Negative validation:

- Existing artifacts without new columns or null attribution must still render.
- Unknown agent/job/project filters must return an empty state, not an error.

## Exit Criteria

- Output artifact rows can be queried directly by agent/job/project/workspace/run/type.
- UI no longer depends on job traversal as the primary filtering path.
- Existing artifact rows remain readable.
- Tests and startup smoke pass or blockers are documented with exact failure output.
