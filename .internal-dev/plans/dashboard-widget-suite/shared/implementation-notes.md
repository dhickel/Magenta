---
schema_version: 1
document_type: implementation-notes
status: planning
owner: advanced-planner
created: 2026-05-29
---

# Implementation Notes

## Files To Inspect Before Mutation

- `.internal-dev/specifications/{web,simplypages,architecture,service-graph,services,api,decisions,deferred-features}.md`
- `.internal-dev/knowledge/{dashboard-api-contract,dashboard-fragment-navigation,simplypages-avatar-layout-and-editing,avatar-work-area-ui-refactor,workspace-file-explorer-details-list-rewrite,entity-selector-htmx-pattern}.md` when relevant.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/resources/avatar-schema.sql`
- Tool classes under `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/` and `.../orchestration/`
- Existing services for projects, Work Areas, outputs, assignments, inbox, schedules, and agent profiles.

## Likely New/Changed Production Files

- New dashboard widget registry/service/rendering classes under `src/main/java/io/mindspice/magenta2/avatar/dashboard/`.
- New planner/reminder/habit/project-artifact classes under `src/main/java/io/mindspice/magenta2/avatar/` or subpackages.
- `AvatarDashboardController.java` and `AvatarDashboardComponents.java` should be decomposed only as needed; avoid unrelated broad controller rewrites.
- `avatar-schema.sql` and repository bootstrap/migration helpers.
- CSS in `src/main/resources/static/css/avatar-dashboard.css`; cache-bust asset version after changes.
- Narrow JS only in `avatar-layout-edit.js` or new small files when justified.
- Docs under `docs/end-user/`, `docs/technical/`, `docs/api/`.

## Test Targets

- `src/test/java/io/mindspice/magenta2/avatar/AvatarRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/avatar/AvatarServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarToolsTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistryTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalTool*.java`
- Project/Work Area/output service tests when those services are touched.

## Naming Guidance

- Use `widgetType` for registry type.
- Use `widgetInstanceId` or `instanceId` for persisted instance id.
- Avoid `widgetKey` in new APIs except compatibility adapters.
- Use `task` for user-facing executable/planner work where practical, preserving compatibility with existing names.

## Data Compatibility

- Old rows using static widget keys must render after migration.
- Old `/dashboards/assistant` URLs and dashboard selector behavior must continue.
- Current `avatar_*` tool names can remain while richer names are added; tests must document exact supported names.

## Docs Closeout Expected In Implementation

- Update `.internal-dev/specifications/*` for accepted architecture/API/web/service changes.
- Add decisions for registry/instance model, in-dashboard reminder boundary, and project artifact storage.
- Update end-user docs for dashboard widgets/planner/calendar/projects.
- Update technical/API docs for widget routes, persistence, and tools.
- Add changelog and archive completed plan artifacts per repo workflow.
