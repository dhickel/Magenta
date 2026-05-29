---
schema_version: 1
document_type: current-state-analysis
status: planning
owner: advanced-planner
created: 2026-05-29
---

# Current State Analysis

## Verified Local Contracts

- `.internal-dev/specifications/web.md` defines `/` as the Assistant dashboard home with a compact chat rail and configurable rows/widgets. Dashboards are agent-agnostic containers.
- `.internal-dev/specifications/simplypages.md` requires SimplyPages-native composition, HTMX-first CRUD/fragments, and in-place dashboard layout editing.
- `.internal-dev/specifications/architecture.md` keeps Avatar profile/preferences/organizer/dashboard layout in `avatar.sqlite` and runtime state in `magenta.sqlite`.
- `.internal-dev/specifications/service-graph.md` allows Avatar dashboard to reuse chat/tool/runtime/workspace/output services but forbids a second runtime.
- `.internal-dev/specifications/services.md` still marks planner recurrence automation as deferred; this plan accepts only in-dashboard reminder/alert state.
- `.internal-dev/specifications/api.md` owns route/payload compatibility. New routes need controller/API tests and docs updates.
- `.internal-dev/specifications/deferred-features.md` includes `DEFERRED-20260525-05` for planner automation and `DEFERRED-20260525-11` for dashboard-aware system chat.

## Dashboard Code Anchors

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java` owns `/`, `/dashboards/{dashboardId}`, `/dashboards/{dashboardId}/_page`, layout mutation endpoints, current widget CRUD fragments, organizer modal routes, output preview, and Work Area explorer fragments.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java` owns rendering. It currently has `static final List<WidgetDefinition> WIDGETS` with `daily-tasks`, `todos`, `calendar`, `notes`, `outputs`, `system`, `alerts`, and `recent-work`.
- Current rendering is a switch in `widgetBody(data, widgetId)`. This is the main architecture gap for a major widget suite.
- Current widget detail modal calls the same `widgetBody(...)` as summary, so detail views are not rich operational surfaces.
- Current catalog disables already-used widget keys globally for a dashboard, matching the schema limitation and preventing multi-instance agent/project-bound widgets.
- Current `/dashboards/_create` form targets `body`; knowledge notes call out this as an open question because dashboard switching otherwise uses stable `#dashboard-home` fragment swaps.

## Persistence Anchors

- `src/main/resources/avatar-schema.sql` contains legacy `avatar_dashboard_layout` and `avatar_dashboard_widgets`, plus current `user_dashboards`, `user_dashboard_rows`, and `user_dashboard_widgets`.
- `user_dashboard_widgets` currently stores `id`, `dashboard_id`, `row_id`, `widget_key`, `column_position`, `column_width`, flags, `settings_json`, and `updated_at`.
- `user_dashboard_widgets` has `unique(dashboard_id, widget_key)`. This blocks multiple instances of the same widget type on one dashboard.
- `AvatarRepository.addDashboardWidget(...)` checks `findDashboardWidgetByKey(dashboardId, widgetKey)` and throws `dashboard widget already exists`.
- `AvatarRepository.ensureAssistantDashboard()` seeds Assistant with the eight static widgets and empty settings JSON.
- Planner/user organizer tables already exist: `avatar_todos`, `avatar_daily_tasks`, `avatar_calendar_items`, `avatar_notes`, `avatar_planner_tasks`, `avatar_planner_subtodos`, `avatar_planner_task_notes`, `avatar_planner_calendar_projection`, `avatar_events`.
- Existing planner recurrence stores `recurrence_json` and projection rows, but current UI is shallow and automation is not accepted.

## Tooling Anchors

- `AvatarAssistantTools` exposes static Spring AI `@Tool` methods such as `avatar_todo_list`, `avatar_todo_upsert`, `avatar_daily_task_list`, `avatar_calendar_list`, `avatar_calendar_upsert`, `avatar_note_append`, `avatar_note_search`, `avatar_submit_task`, `avatar_list_outputs`, and `avatar_read_output`.
- `AvatarAssistantToolService` gates these through `AvatarAssistantToolAuthorizationService` and delegates to `AvatarService`, `AssignmentService`, `TaskService`, and `OutputArtifactService`.
- `AgentOperationalTools` exposes normal `agent_*` tools scoped to current orchestration context plus `avatar_*` supervisor tools for system/project/job/assignment/schedule/output state.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java` and tests already validate `ToolCallbackProvider` names. Tool contract expansion should reuse this path.
- Local tool configuration uses `MethodToolCallbackProvider.builder().toolObjects(...).build()` for static annotated tool classes. This plan does not require dynamic Spring AI registration.

## SimplyPages/HTMX Anchors

- SimplyPages layout docs say use `Row` and `Column.create().withWidth(1..12)` for explicit 12-column layouts, and keep module sizing in layout.
- SimplyPages editing docs and demo show single modal containers, edit modal endpoints, save endpoints returning OOB swaps, add-module controls attached to actual row structures, and insert-row controls as quiet separators.
- HTMX pattern docs require one primary target contract per endpoint, stable IDs, `outerHTML` for replacing a whole module, `innerHTML` for modal containers, and OOB multi-target updates for save flows.
- Current Avatar dashboard already uses `#dashboard-home`, `#avatar-edit-container`, `#avatar-widget-grid`, and row/widget ids. The plan should preserve those targets or deliberately migrate with compatibility tests.

## Existing Docs/Test Anchors

- End-user docs: `docs/end-user/avatar-dashboard.md`, `docs/end-user/projects-and-workspaces.md`.
- Technical docs: `docs/technical/avatar-dashboard-fragments.md`, `docs/technical/avatar-dashboard-layout-persistence.md`, `docs/technical/avatar-planner-organizer.md`, `docs/technical/api-reference.md`.
- API docs: `docs/api/00-index.md`.
- Live docs state Work Areas are no longer dashboard widgets and newly rendered Work Area UI must call the agent-detail Work Area route family, not legacy `/avatar/_work-areas` fragments.
- Tests already present: `AvatarRepositoryTest`, `AvatarServiceTest`, `AvatarDashboardControllerTest`, `AvatarToolsTest`, `ChatToolRegistryTest`, `AgentOperationalToolConfigurationTest`, `AgentOperationalToolServiceTest`, `ProjectServiceTest`, `WorkAreaServiceTest`, `WorkAreaExplorerServiceTest`, `OutputArtifactServiceAttributionTest`, `OutputArtifactPathSemanticsTest`, and web controller tests.

## Architecture Gaps To Resolve

- No widget registry beyond static display metadata.
- No widget instance type/settings/binding contract.
- No multi-instance policy.
- No settings modal for agent/project/Work Area/source binding.
- No per-widget tool descriptor contract.
- Planner tasks/todos/daily tasks are fragmented and UI exposes only partial create/complete flows.
- Calendar summary is a list, not a calendar/agenda.
- Notes are DB-only in the current widget, despite Work Area/file note requirements.
- Project/household data has no typed artifact model.
- Outputs widget is unscoped recent output display unless callers infer filters from page data.
- Browser validation must cover both function and visual quality because route tests can pass shallow UI.
