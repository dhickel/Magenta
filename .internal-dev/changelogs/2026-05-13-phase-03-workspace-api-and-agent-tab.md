# Phase 03 Workspace API And Agent Tab

## Date
- 2026-05-13

## Change Summary
- Implemented workspace operational API completion under `/api/workspaces` with list, read, and active lease endpoints.
- Added service/repository support for filtered workspace listing and active lease lookup by workspace id with bounded list limits.
- Upgraded agent workspace tab rendering to show workspace metadata, active leases, link table, output-path hint, and clear empty states.
- Added controller/repository/runtime tests covering list/read/leases behaviors and workspace tab rendering.

## Files
- `src/main/java/io/mindspice/magenta2/api/web/WorkspaceController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/test/java/io/mindspice/magenta2/api/web/WorkspaceControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepositoryAttributionTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`

## Behavioral Impact
- `/api/workspaces` now supports operational read/list usage and no longer only exposes link mutation endpoints.
- Agent workspace dashboard view now surfaces ownership, pathing, and lease/link operational state directly.
- Invalid `ownerType` requests now return `400` via explicit validation.
- Missing workspace id on read/lease routes now returns `404`.

## Risks
- Workspace lease visibility depends on records sharing the same `workspace_id` convention as workspace rows; mismatched ids still correctly render empty lease state.
- Output directory hint is derived from owner type/id conventions (`agents/<id>/outputs`, `jobs/<id>/outputs`) rather than direct filesystem resolution.

## Follow-up Items
- If needed, add a dedicated workspace index surface in dashboard navigation that consumes the new list API.
- If runtime id conventions diverge, add normalization between workspace rows and lease rows.
