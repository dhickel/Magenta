# Project Workspace Materialization

## Date

2026-05-18

## Change Summary

Leased project workspaces are now materialized into active assignment workspaces at `projects/<projectId>` so task tools can use the filesystem path promised by runtime instructions.

## Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- Focused tests in `OrchestrationRuntimeTest`, `WorkspacePathSegmentValidationTest`, `PlanServiceTest`, `AgentFileToolServiceTest`, and `AgentShellToolServiceTest`.

## Behavioral Impact

- Project-backed task runs create an assignment-local `projects/<projectId>` symlink after temp workspace allocation.
- Active assignment file and shell project aliases now require the materialized link and verify it targets the current project workspace.
- Runner cleanup removes the assignment-local project link before releasing the project lease; terminal temp workspace cleanup also removes it.

## Risks

- The materialized view requires filesystem symlink support for the host data root.
- Existing assignment-context tests or callers that hand-build project contexts must now materialize the assignment project link before using `projects/<projectId>` aliases.

## Validation

- Focused validation passed with `mvn -Dtest=WorkspacePathSegmentValidationTest,AgentFileToolServiceTest,AgentShellToolServiceTest,PlanServiceTest,OrchestrationRuntimeTest test`.
- `git diff --check` passed.
- Bounded Spring Boot startup reached a healthy app on ephemeral port `45047`.

## Follow-up Items

- Domain 02 subplans 05-07 remain out of scope for this change.
