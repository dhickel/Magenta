# Phase 02 Worker Directive: Static Layout, Schema Fields, And Work Areas

## Objective

Introduce the application-owned structural path source of truth, migrate core workspace directory construction to it, add required DB fields, and make new Work Areas ID-backed on disk.

## Editable Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspacePathLayout.java` (new)
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/EffectiveWorkspaceResolver.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkArea.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentRequest.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/WorkAssignment.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanRun.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRun.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- Tests under `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
- Relevant runtime/repository tests under `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/`, `src/test/java/io/mindspice/magenta2/ai/chat/plan/`, and `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/`

## Forbidden Scope

- Do not rewrite task/workflow/job execution behavior beyond schema/data-carrier fields needed by this phase.
- Do not update UI/browser surfaces; Phase 04 owns controllers/fragments.
- Do not delete development filesystem data; Phase 05 owns reset/migration.

## Supporting Docs To Read

- This suite's `00-specification-lock.md`, `02-target-design.md`, and `shared/implementation-notes.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `.internal-dev/knowledge/plain-path-segment-id-validation.md`
- `.internal-dev/knowledge/root-relative-workspace-storage.md`
- `.internal-dev/knowledge/root-relative-path-remediation.md`

## Implementation Steps

1. Add `WorkspacePathLayout` with static structural segment constants and relative path helper methods. Keep it application-owned, not config-backed.
2. Refactor `WorkspaceDirectoryService` and `WorkspaceService` to use `WorkspacePathLayout` for structural paths.
3. Change target workspace construction to `workspace/<agentWorkspaceId>/...`, `chats/<conversationId>/files`, `agents/<agentId>/...` for metadata/internal structures, and `projects/<projectId>/...`.
4. Add `run_display_name` to `work_assignments`, `plan_runs`, and `workflow_runs` with guarded SQLite migrations.
5. Add `runDisplayName` to request and record carriers needed by assignment/task/workflow submissions. Do not require it for job-bound assignments in this phase.
6. Make new Work Areas create/resolve as stable ID-backed directories under `workareas/<workAreaId>` while keeping Home at `home/`.
7. Preserve compatibility reads for existing `area_relative_path` rows and root-relative stored paths.
8. Update focused tests for layout helper output, id validation, additive schema migration, Work Area creation/resolution, Home, duplicate handling, and active-use guards.

## Acceptance Criteria

- New structural path construction goes through `WorkspacePathLayout` or `WorkspaceDirectoryService`.
- New Work Areas use `work_areas.id` as disk segment and `display_name` as label.
- Required `run_display_name` fields exist and are mapped through repositories/records.
- No new public/operator configuration controls structural directory names.
- Existing root-relative path compatibility remains intact.

## Negative Checks

```bash
rg -n "\"workspace\"|\"agents\"|\"projects\"|\"chats\"|\"workareas\"|\"runs\"|\"outputs\"|runtime/task-runs|runtime/workflow-runs|outputs/jobs|jobs/.*/workspace" src/main/java/io/mindspice/magenta2/ai/orchestration src/test/java/io/mindspice/magenta2/ai/orchestration
```

Review every hit. Structural active-code strings should be constants/helper names or explicit compatibility/test assertions.

## Validation Commands

```bash
mvn -Dtest='*Workspace*Test,*WorkArea*Test,*RootRelative*Test,*RepositorySchema*Test' test
mvn -Dtest='AssignmentContextServiceTest,WorkflowRepositoryTest' test
```

## Stop Conditions

- Stop if using `work_areas.id` as the disk segment breaks a current DB invariant that cannot be migrated compatibly.
- Stop if schema changes require destructive table rebuilds not covered by the development reset scope.

## Senior Guidance

Do not solve all execution semantics here. Build the reliable layout/schema foundation and prove it.

## Do Not Close Unless

- New layout helper is tested.
- New DB fields are schema-guarded and mapped.
- Work Area ID-backed behavior is tested.
- Worker report identifies remaining legacy references intentionally left for later phases.
