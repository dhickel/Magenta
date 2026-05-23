# 2026-05-23 Assignment Work Area Metadata

## Summary

Added durable assignment metadata for selected Work Areas and output routing choices. This is the backend persistence/validation slice that submit forms and runtime output routing will build on.

## Changes

- Added `selected_work_area_id`, `output_route_type`, `output_work_area_id`, and `output_direct_relative_path` to `work_assignments`.
- Extended `AssignmentRequest` and `WorkAssignment` with Work Area/output route fields while preserving existing constructor call sites.
- Added assignment creation validation for:
  - default Home Work Area selection,
  - active same-owner selected Work Areas,
  - active same-owner output Work Areas,
  - direct output directories that already exist under the owner workspace root,
  - unsupported output route types.
- Added Work Area service helpers for same-owner validation and owner-root directory checks.
- Updated technical docs with the assignment metadata contract.

## Validation

- `mvn -Dtest='io.mindspice.magenta2.ai.orchestration.runtime.AssignmentContextServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepositorySchemaMigrationTest' test`

Result: 22 tests passed, 0 failures, 0 errors.

## Deferred

- Runtime alias changes that expose selected Work Area as `workspace/` and owner root as `root/`.
- Output directory resolver changes that apply `DEFAULT`, `WORK_AREA`, and `DIRECT_DIRECTORY` routes during execution.
- HTMX submit-form picker controls.
