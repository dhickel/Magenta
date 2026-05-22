# Workspace Output Temp Publishing

## Date

2026-05-22

## Change Summary

Implemented Phase 02 workspace/output publication support for the Avatar sprint. Added typed output directory resolution for tasks, workflows, and jobs; added safe temp/run directory publication into final outputs; and wired `includeTempWithOutput` through task completion.

## Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/**`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/**`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/task/TaskTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
- `docs/technical/workspaces-tools-outputs.md`

## Behavioral Impact

Task, workflow, and job output directories resolve through a shared service using the effective workspace rule: project workspace when a project is present, otherwise the executing agent workspace. `task_complete` now accepts optional `includeTempWithOutput`; when true, retained temp files are copied to `copied-temp/` under the final output directory and registered as output artifacts.

## Risks

Temp publication intentionally skips symlinks and rejects escaped source/destination paths. Requested temp publication failures fail task completion before a terminal completed state is saved. Callers should use the flag only when retained temp evidence should become part of published outputs.

## Follow-up Items

- UI lanes may add controls for `includeTempWithOutput` where useful.
- Phase 03/04 tools should expose output targets through bounded service APIs rather than constructing filesystem paths directly.
