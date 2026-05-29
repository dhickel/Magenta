---
schema_version: 1
document_type: target-architecture
status: planning
owner: advanced-planner
created: 2026-05-29
---

# Target Architecture And Widget Contract

## Target Package Shape

- `io.mindspice.magenta2.avatar.dashboard`: widget registry, widget definition records, widget instance/settings service, binding model, renderer interfaces, validation helpers.
- `io.mindspice.magenta2.avatar.planner`: personal planner/task/routine/calendar/reminder/habit services and repositories that own `avatar.sqlite` organizer behavior.
- `io.mindspice.magenta2.api.web`: thin dashboard controllers/fragments that delegate to widget/planner/project/tool services and render SimplyPages components.
- Existing runtime packages remain owners for agents, assignments, jobs, projects, Work Areas, files, outputs, schedules, and inboxes.

## Widget Definition Contract

Each first-party widget definition must include:

- `type`: stable key such as `today-planner`, `tasks-routines`, `calendar-schedule`, `notes`, `projects`, `habits-progress`, `reminders-alerts`, `agent-status-queue`, `agent-outputs`, `agent-files-notes`, `project-activity`, `system-metrics`, `dashboard-context`.
- `title`, `description`, `category`, `dataOwner`.
- `defaultWidth` and `supportedWidths`.
- `instancePolicy`: `SINGLE_PER_DASHBOARD`, `MULTI_INSTANCE`, or `SINGLE_SYSTEM`.
- `bindingMode`: `NONE`, `OPTIONAL_AGENT`, `REQUIRED_AGENT`, `OPTIONAL_PROJECT`, `REQUIRED_PROJECT`, `OPTIONAL_WORK_AREA`, `REQUIRED_WORK_AREA`, `OUTPUT_SOURCE`, `SYSTEM`.
- `settingsSchema`: typed schema object with defaults, validation, display labels, selectors, and hidden compatibility fields.
- `summaryRenderer`, `detailRenderer`, `settingsRenderer` references or strategy names.
- `refreshPolicy`: manual, HTMX interval, event-driven candidate, or no-refresh.
- `emptyStatePolicy`: no-data, missing-binding, invalid-binding, disabled-source.
- `toolDescriptors`: exact read and mutation tool names, authorization class, destructive confirmation requirement, response limits.
- `fixtures`: seed expectations for tests and Playwright.

## Widget Instance Contract

- A widget instance is addressed by `widgetInstanceId`, not by type.
- `widgetType` is the registry type.
- `settingsJson` stores only per-instance settings, including binding fields and display preferences.
- `dashboardId`, `rowId`, `columnPosition`, `columnWidth`, `enabled`, `collapsed`, `createdAt`, and `updatedAt` remain layout/instance fields.
- Existing routes using `widgetKey` must be migrated or bridged so old rows still render while new routes operate on `widgetInstanceId`.

## Binding Contract

Binding settings are explicit and nullable according to widget definition:

- `agentId`: references runtime `AgentProfileService`; invalid/missing agent renders a recoverable settings prompt.
- `projectId`: references `ProjectService`; invalid/missing project renders recoverable prompt.
- `workAreaId`: references `WorkAreaService`; Work Area access and file operations stay service-confined.
- `jobId`, `runId`, `outputSourceMode`, `artifactType`: optional output filters.
- `noteSourceMode`: `PERSONAL`, `AGENT`, `PROJECT`, `WORK_AREA`, `MIXED`.
- `plannerScope`: personal by default, optional selected project where supported.

No binding creates dashboard ownership. Dashboards own layout only.

## Route Contract

New routes should use instance ids:

- `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}` summary fragment.
- `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}/detail` detail modal/drawer.
- `GET /dashboards/{dashboardId}/widgets/{widgetInstanceId}/settings` settings modal.
- `PUT /dashboards/{dashboardId}/widgets/{widgetInstanceId}/settings` validate/save settings, return OOB modal close plus widget summary refresh.
- Layout routes may remain under `/_dashboards/_layout/...` during compatibility but must resolve instance ids.
- Agent/Work Area file widgets must call existing agent-detail Work Area route families or service-backed equivalents guarded by owner, not legacy `/avatar/_work-areas` routes.

Compatibility routes under `/_dashboards/_widgets/{widgetKey}` may remain as bridges for old tests/routes but must not be the primary architecture.

## UI Layer Contract

- Summary cards are bounded and scannable. Direct actions are limited to high-value quick actions such as capture, mark done, snooze, open detail, open settings.
- Detail modals/drawers own full CRUD/filter/search/tab workflows.
- Settings modals own binding selectors, filters, density/default view, refresh/reminder settings, and tool visibility indicators.
- HTMX returns stable fragments and OOB updates for multi-target changes.
- JavaScript is allowed for narrow local interactions only: chat streaming, local resize, optional calendar drag/timeblock behavior if justified, and non-transport UI affordances.

## Widget Suite Scope

Foundation phase creates platform and migrates existing widgets. Feature phases deliver:

- Phase 02: Today Planner, Tasks/Routines, Calendar/Schedule.
- Phase 03: Notes, Projects, Contacts/Materials.
- Phase 04: Agent Status/Queue, Agent Outputs, Agent Files/Notes.
- Phase 05: Habits/Trackers, Reminders/Alerts, Dashboard Context Panel.

System Metrics is either part of Foundation as a migrated scoped widget or Phase 05 if service integration is required.
