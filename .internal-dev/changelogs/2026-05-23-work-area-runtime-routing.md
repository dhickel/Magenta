# 2026-05-23 Work Area Runtime Routing

## Summary

Applied selected Work Area and output redirect metadata during task, workflow, and job runtime path allocation.

## Changes

- Extended `OutputPublicationTarget` with selected Work Area and output route fields.
- Extended `ResolvedOutputDirectory` with both owner root and execution workspace root.
- Updated `OutputDirectoryService` so:
  - default outputs write below `<selected-work-area>/outputs/...`,
  - Work Area redirects write below `<output-work-area>/outputs/...`,
  - direct-directory redirects write directly to the selected existing owner-root-relative directory.
- Extended `OrchestrationTaskContext` with owner root and routing metadata.
- Propagated routing metadata from assignments into task, workflow, and job run allocation.
- Added `root/` alias support to file and shell tools while preserving `workspace/`, `outputs/`, `run/`, `work/`, `scratch/`, and `job/`.
- Updated docs and workspace package guidance.

## Validation

- `mvn -Dtest='io.mindspice.magenta2.ai.orchestration.workspaces.OutputDirectoryServiceTest,io.mindspice.magenta2.ai.chat.plan.PlanServiceTest,io.mindspice.magenta2.ai.chat.tool.file.AgentFileToolServiceTest,io.mindspice.magenta2.ai.chat.tool.shell.AgentShellToolServiceTest' test`

Result: 103 tests passed, 0 failures, 0 errors.

## Deferred

- HTMX submit-form picker controls.
- Browser validation for submit surfaces once controls are added.
