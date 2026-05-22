# Phase 02 - Workspace Outputs And Temp Publishing

## Context

Magenta already has effective workspace resolution, output directories, temp directories, root-relative artifact storage, and path confinement. The Avatar sprint needs clearer assignable output directories and an explicit `includeTempWithOutput` path so retained run evidence can be copied into final outputs when requested.

Relevant anchors:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/EffectiveWorkspaceResolver.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/RootRelativePathService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/task/TaskTools.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`

## Goal

Make output publication predictable for tasks, workflows, jobs, project contexts, and job assignments. Add a safe temp-publication primitive and expose `includeTempWithOutput` through task completion without weakening path confinement or symlink protections.

## In Scope

- Output directory resolution service for task/workflow/job outputs.
- Additive output target request fields where APIs need them.
- Recursive temp/run file copy into final output publication.
- Artifact registration for copied expected outputs.
- Security tests for traversal and symlink escape.

## Out of Scope

- Renaming `run_output_artifacts.plan_id`.
- Broadening loose artifact discovery.
- Following symlinks from temp directories.
- Publishing outputs outside the effective workspace rule.
- UI controls unless another lane explicitly adds them.

## Implementation Steps

1. Add output directory resolution records under `ai.orchestration.workspaces`.
   - `OutputDirectoryKind`: `TASK`, `WORKFLOW`, `JOB`.
   - `OutputPublicationTarget`: kind, agent id, project id, job id, job assignment id, job run id, compatibility workspace id.
   - `ResolvedOutputDirectory`: workspace metadata, workspace root, output dir, and `OutputArtifactContext`.

2. Add `OutputDirectoryService`.
   - Resolve effective workspace through `EffectiveWorkspaceResolver`.
   - Preserve existing path layout:
     - `outputs/tasks/<taskId>/<runId>/`
     - `outputs/workflows/<workflowId>/<runId>/`
     - `outputs/jobs/<jobAssignmentId>/<jobRunId>/`
   - Treat `workspaceId` as compatibility metadata only; it must not override project-vs-agent resolution.

3. Refactor output directory callers where practical.
   - `PlanService.startRun`
   - `WorkflowRunner` output path allocation
   - `JobService.startRun`
   - Keep public path layout unchanged unless a request explicitly targets job output.

4. Add temp publication primitive to `OutputArtifactService`.
   - Add `publishDirectoryContents(...)`.
   - Copy recursively from a confined source dir into `outputDir/copied-temp/`.
   - Use `Files.walkFileTree` without `FOLLOW_LINKS`.
   - Skip symlinks, including task temp `projects/<projectId>` symlinks.
   - Reject source files whose `toRealPath()` escapes `dataRoot` or the real source root.
   - Reject normalized destination paths outside the real output directory.
   - Register each copied file as a `RunOutputArtifact`.
   - Use `output_name = "copied_temp/" + relativePathWithSlashes`.
   - Infer artifact type from filename.

5. Wire `includeTempWithOutput` into task completion.
   - Add optional `Boolean includeTempWithOutput` to `TaskTools.complete(...)`.
   - Add overloads through `TaskService.completeRun(...)` and `PlanService.completeRun(...)`.
   - Completion order:
     - validate required outputs;
     - materialize declared outputs;
     - copy temp contents if requested;
     - run existing loose artifact discovery;
     - persist completed run;
     - clean temp according to existing retention rules.
   - If requested temp publication fails, fail completion rather than completing without copied outputs.

6. Add additive request fields only where needed.
   - Candidate records: `TaskController.TaskRunRequest`, `WorkflowController.WorkflowRunRequest`, `JobController.JobRunRequest`, `AgentOrchestrationController.AssignmentSubmitRequest`.
   - Prefer existing `input_json` for additive options unless a query requirement justifies new columns.

7. Update docs and package guide if route payloads or package responsibilities change.

## Validation

Focused tests:

- Output directory resolver preserves task/workflow/job layouts.
- Project context publishes under project workspace.
- Agent context publishes under agent workspace.
- Compatibility workspace id does not override effective workspace.
- `includeTempWithOutput` copies before cleanup.
- Symlinked files and project workspace links are skipped.
- Source and destination traversal attempts are rejected.
- Temp publication failure does not mark the run completed.
- Output content/download behavior remains confined.

Commands:

- `mvn -Dtest=OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest,WorkspacePathSegmentValidationTest,RootRelativePathServiceTest test`
- `mvn -Dtest=PlanServiceTest,WorkflowRunnerTest,JobServiceTest,OutputControllerTest test`
- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Exit Criteria

- All copied temp files are registered artifacts.
- No symlink escape or project workspace duplication path exists.
- Existing output queries/read/download routes work with copied temp artifacts.
- Runtime validation includes real filesystem security proof, not unit-only substitutes.
