# Phase 02 Workspace Outputs Worker Handoff

## Owned Paths Changed

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputDirectoryKind.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputPublicationTarget.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/ResolvedOutputDirectory.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactServiceAttributionTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputDirectoryServiceTest.java`

## Implemented

- Added typed output-directory resolution for task, workflow, and job outputs.
- Resolution uses `EffectiveWorkspaceResolver` so project context wins over agent context.
- `workspaceId` on `OutputPublicationTarget` is compatibility metadata only and does not override effective workspace resolution.
- Added `OutputArtifactService.publishDirectoryContents(...)`.
- Temp publication copies confined regular files into `outputDir/copied-temp/`.
- Symlinks are skipped and never followed, including assignment temp `projects/<projectId>` links.
- Copied files are registered as `RunOutputArtifact` rows with `output_name` prefixed by `copied_temp/` and inferred artifact type.
- Source, output, and destination paths remain confined under the configured data root and output directory.

## Validation

- Passed: `mvn -Dtest=io.mindspice.magenta2.ai.orchestration.workspaces.OutputDirectoryServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactServiceAttributionTest,io.mindspice.magenta2.ai.orchestration.workspaces.OutputArtifactPathSemanticsTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkspacePathSegmentValidationTest,io.mindspice.magenta2.ai.orchestration.workspaces.RootRelativePathServiceTest test`
  - Result: 45 tests run, 0 failures, 0 errors.
- Passed startup smoke: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - Result: app started successfully on random port `42511`; command exited `124` from the expected timeout after graceful shutdown.
- Passed: `git diff --check`

## Requested Integration Edits Outside This Lane

These are required to expose `includeTempWithOutput` end to end but are outside this worker's ownership.

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/task/TaskTools.java`
  - Add optional `@ToolParam(required = false) Boolean includeTempWithOutput` to `task_complete`.
  - Call `taskService.completeRun(context.runId(), outputValues, finalMessage, evidence, Boolean.TRUE.equals(includeTempWithOutput))`.

- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`
  - Keep the existing `completeRun(String, Map<String,Object>, String, List<String>)` overload delegating with `false`.
  - Add `completeRun(String runId, Map<String,Object> outputValues, String finalMessage, List<String> evidence, boolean includeTempWithOutput)` and delegate to `PlanService.completeRun(...)`.

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
  - Inject `OutputDirectoryService`.
  - In `startRun`, replace direct `EffectiveWorkspaceResolver` plus `workspaceDirectoryService.taskOutput(...)` allocation with `outputDirectoryService.resolve(OutputPublicationTarget.task(definition.id(), runId, agentId, context == null ? null : context.projectId(), null))`.
  - Use `ResolvedOutputDirectory.outputDirectory()` for output path and `ResolvedOutputDirectory.workspaceId()` for `effectiveWorkspaceId`.
  - Add `completeRun(..., boolean includeTempWithOutput)` overloads. Existing overloads should delegate with `false`.
  - Completion order should be: validate required outputs, `materializeRunOutputs`, if requested call `outputArtifactService.publishDirectoryContents(run.id(), task.id(), resolveStoredPath(run.workspacePath()), resolveStoredPath(run.outputDir()), outputArtifactContextFor(run, task))`, then `discoverLooseArtifactsForRun`, then persist completed run, then cleanup temp.
  - Let `publishDirectoryContents` exceptions fail completion before terminal state is saved.

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
  - Inject/use `OutputDirectoryService`.
  - Replace `workspaceDirectoryService.workflowOutput(workspace.root(), workflowId, runId)` in `workflowOutputPath(...)` with `outputDirectoryService.resolve(OutputPublicationTarget.workflow(workflowId, runId, agentId, context == null ? null : context.projectId(), null)).outputDirectory()`.

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
  - Inject/use `OutputDirectoryService`.
  - Replace direct `jobAssignmentOutput(effectiveWorkspace.root(), assignmentKey, runId)` allocation with `outputDirectoryService.resolve(OutputPublicationTarget.job(def.id(), assignmentKey, runId, effectiveAgentId, effectiveProjectId, null)).outputDirectory()`.
  - Continue to use `jobAssignmentWorkspace(...)` separately for opt-in persistent job workspaces.

## Blockers

- End-to-end `includeTempWithOutput` behavior is not wired because it requires edits to `TaskTools`, `TaskService`, and `PlanService`, which are outside this lane's owned paths.
- Workflow and job callers still use direct directory helpers until the integration lane remaps those files.

## Notes

- During closeout, `git status --short --untracked-files=all` showed non-owned untracked Avatar files under `src/main/java/io/mindspice/magenta2/avatar/**`, `src/test/java/io/mindspice/magenta2/avatar/**`, and `src/main/resources/avatar-schema.sql`, plus another lane note at `.codex-orchestration/avatar-dashboard-sprint/lanes/phase-03-agent-tools-prep-worker.md`. This worker did not read or edit them.
