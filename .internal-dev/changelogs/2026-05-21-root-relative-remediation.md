## Date

2026-05-21

## Change Summary

Remediated root-relative path migration validation failures in job run storage, workspace link listing, output/controller tests, and SQLite datasource parent directory initialization.

## Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootConfiguration.java`
- Focused controller, runtime, workspace path, and configuration tests
- `docs/technical/configuration-operations.md`
- `docs/technical/workspaces-tools-outputs.md`

## Behavioral Impact

- `JobService` no longer constructs a root-relative path resolver eagerly when tests provide neither a resolver nor workspace directories; production storage still uses root-relative values whenever the resolver exists.
- `OrchestrationRunnerService` now builds the same fallback resolver from workspace directories, so root-relative job workspace paths still resolve to host paths when the resolver bean is absent but workspace directories are available.
- Workspace `PATH` link listing normalizes current-root absolute legacy targets to root-relative values and omits stale absolute targets outside the current data root.
- SQLite parent directory creation now supports file-backed `jdbc:sqlite:file:` URI forms while continuing to ignore memory URI forms.

## Risks

Workspace links outside the current root are filtered from list responses instead of being shown as raw stale host paths.

## Follow-up Items

None.
