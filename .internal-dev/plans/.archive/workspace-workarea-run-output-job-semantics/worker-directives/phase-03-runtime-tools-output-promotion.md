# Phase 03 Worker Directive: Runtime, Tools, Output Promotion, And Job Semantics

## Objective

Move execution to run-local staging, preserve run staging for at least one day, promote declared outputs to final destinations after completion, and remove job-owned workspace behavior from active runtime semantics.

## Editable Files

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContext.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobDefinition.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRun.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobExecutionSummary.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputPublicationTarget.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/ResolvedOutputDirectory.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellTools.java`
- Runtime/tool/output tests matching these packages.

## Forbidden Scope

- Do not edit browser fragments/controllers except where compilation requires record signature updates; Phase 04 owns user-facing surfaces.
- Do not introduce configurable structural path names.
- Do not implement direct write-blocking to final outputs; record it as deferred if Phase 01 did not.
- Do not remove old DB columns destructively.

## Supporting Docs To Read

- Phase 02 worker report and validator result.
- This suite's `02-target-design.md`, `shared/senior-engineer-guidance.md`, and `shared/validation-matrix.md`.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
- `.internal-dev/knowledge/output-file-path-realpath-confinement.md`
- `.internal-dev/knowledge/output-artifact-attribution-query-and-backfill-pattern.md`
- `.internal-dev/knowledge/file-tool-workspace-scope-pattern.md`
- `.internal-dev/knowledge/agent-shell-workspace-alias-resolution.md`
- `.internal-dev/knowledge/project-workspace-lease-runtime-pattern.md`

## Implementation Steps

1. Allocate task/workflow/job execution staging under `runs/<runId>/` and run-local outputs under `runs/<runId>/outputs/`.
2. Set `OrchestrationTaskContext.hostOutputPath` to run-local outputs during execution.
3. Update prompt text so agents write deliverables to `outputs/` and understand those are run-local staging outputs.
4. Add backend promotion/copy behavior from run-local outputs/declared outputs to final destinations:
   - jobless task/workflow: agent final `outputs/`;
   - job-bound task/workflow/job: bound Work Area/project output destination.
5. Preserve output artifact attribution and realpath confinement during promotion.
6. Replace immediate temp deletion with retention-aware cleanup. Retain run staging at least one day.
7. Remove active job-owned workspace allocation and `job/` alias behavior. If compatibility fields remain, they must be inert/legacy.
8. Update file/shell alias parsing to use centralized alias constants and remove `scratch/` as a target-model alias.
9. Add tests covering run-local outputs alias, promotion routing, job-bound output destination, no job-owned workspace creation, retention behavior, and attribution.

## Acceptance Criteria

- Model-facing `outputs/` is always run-local during execution.
- Final output destinations are populated only by backend promotion/materialization.
- Job-bound output routing never creates a job-owned workspace directory.
- Staging remains after terminal completion until retention threshold.
- File/shell tools and prompts agree on alias semantics.

## Negative Checks

```bash
rg -n "jobWorkspace|persistentWorkspace|hostJobWorkspacePath|\"job/\"|\"scratch\"|runtime/task-runs|runtime/workflow-runs|outputs/jobs|outputs/tasks|outputs/workflows" src/main/java src/test/java
```

Every remaining hit must be removed, compatibility-only, or explicitly deferred.

## Validation Commands

```bash
mvn -Dtest='OutputDirectoryServiceTest,OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest,AgentFileToolServiceTest,AgentShellToolServiceTest' test
mvn -Dtest='JobServiceTest,JobRepositoryTest,AssignmentContextServiceTest,WorkflowRunnerTest' test
```

## Stop Conditions

- Stop if backend promotion would require the agent/model to write directly to final outputs.
- Stop if job-owned workspace behavior remains required by a current execution path.
- Stop if retention behavior would delete resumable workflow state or active run staging.

## Senior Guidance

Treat staging, promotion, and final output browsing as separate responsibilities. The model should see a simple `outputs/`; services decide where final artifacts belong.

## Do Not Close Unless

- Runtime and tool tests prove the new alias and promotion behavior.
- Job workspace behavior is gone from active semantics.
- Retention is tested.
- Worker report lists any legacy columns/methods intentionally left for compatibility.

