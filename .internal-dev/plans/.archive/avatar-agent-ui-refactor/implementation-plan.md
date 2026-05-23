# Avatar Agent UI Refactor Implementation Plan

## 1. Objective

Redo `/avatar` as a compact operational console that matches the density and visual language of `/dashboard` and `/agents`, while turning file/workspace selection into first-class assignment controls. The implementation must replace the current flat `position + standard/wide/compact` layout editor with a SimplyPages-style edit/decorator model: rows, 12-column widths, movement controls, add-row, add-widget catalog, modal-first mutations, and per-action HTMX autosave with OOB refreshes.

The same pass adds durable Work Area records for confined directories under an agent/project owned root, file explorer modals, output routing choices, and richer Avatar planner data. Avatar remains a user-centric layer over existing chat, tool, assignment, workspace, job, and output services; it must not introduce a parallel runtime.

## 2. Inputs And Assumptions

### Confirmed Inputs

- Existing `/avatar` routes live in `AvatarDashboardController`; rendering lives in `AvatarDashboardComponents`; styling lives in `avatar-dashboard.css`.
- Avatar user-centric data is already in separate `avatar.sqlite`, initialized from `avatar-schema.sql`.
- Existing Avatar layout table is `avatar_dashboard_layout` keyed by `widget_id` with `widget_position`, `widget_size`, and enabled/collapsed flags.
- Existing workspace/runtime state is in `magenta.sqlite` with `workspaces`, `work_assignments`, job tables, and `run_output_artifacts`.
- Workspace architecture already exposes durable workspace roots with `work/`, `outputs/`, `runs/`, `scratch/`, and optional `jobs/`.
- `AssignmentRequest` and `WorkAssignment` already carry `workspaceId` and `projectId`; `AssignmentService` resolves an effective workspace from project or agent context.
- Current `/avatar` already uses HTMX fragments for widget refresh, layout save, organizer mutations, output preview, and alert dismissal.
- SimplyPages editing examples show the required pattern: single modal container, `EditableModule.wrap(...)`, OOB save responses, add-module modals, insert-row controls, and width choices.

### Assumptions To Verify Before Coding

- Breaking/replacing old Avatar organizer tables in `avatar.sqlite` is acceptable for this alpha database. If data migration matters, stop and ask before deleting or hard-replacing tables.
- Existing `workspaceId` in assignment APIs is a compatibility workspace identifier, not the new selected Work Area id. Add separate columns/fields for Work Area routing instead of overloading it.
- Work Area records belong in primary runtime persistence (`magenta.sqlite`) because they describe agent/project owned roots and are used by assignment runtime, not only Avatar preferences.
- Layout row/widget records belong in `avatar.sqlite` because dashboard layout is Avatar UI state.
- Direct output redirect directories must already exist and must remain under the owned root after `toRealPath()` resolution.
- File explorer safe edit v1 is text-only, size-limited, UTF-8, and rejects binaries, huge files, symlinks that escape, and protected paths.

## 3. Scope

### In Scope

- `/avatar` visual/layout refactor only.
- Reusable SimplyPages edit/decorator components for row/column widget layout.
- New Avatar layout row/widget schema and service methods.
- Work Area persistence and service layer over confined agent/project roots.
- Runtime assignment metadata for selected Work Area and output routing.
- Runtime alias behavior: selected Work Area as `workspace/`; broader owned root as `root/`.
- Output routing behavior:
  - default to `<selected-work-area>/outputs/...`;
  - redirect to another Work Area's `outputs/`;
  - redirect to an existing direct directory under the owned root.
- Work Areas widget and file explorer modal.
- Planner task model with recurrence, subtodos, project/work links, calendar projection, and linkable notes.
- Reusable compact recurrence input module with friendly fields first and cron as advanced mode.
- Submit form integrations for Work Area/output redirect pickers.
- Persistent job default Work Area contract under `<root>/ongoing_jobs/<job-slug-or-user-override>`.
- Docs, tests, browser validation, runtime validation, security red-team checks, and `.internal-dev` closeout.

### Out Of Scope

- Scheduler/contact-user/wait-for-input automation for planner tasks.
- New public email ingestion.
- A general-purpose file manager outside owned roots.
- Multiple instances of first-party widgets in v1.
- Work Area controls inside plan chats.
- Replacing `/dashboard`, `/agents`, or `/chat` shell architecture.
- A SimplyPages upstream PR unless implementation discovers a missing library feature that is genuinely needed.

## 4. Current-State Analysis

### Avatar UI

`AvatarDashboardController` exposes:

- `GET /avatar`
- `GET /avatar/_widgets`
- `GET /avatar/_widgets/{widgetKey}`
- `GET /avatar/_edit`
- `PUT /avatar/_layout`
- organizer mutations for todos, daily tasks, notes, calendar
- output preview and alert dismissal fragments

`AvatarDashboardComponents` currently renders a fixed CSS grid and an edit modal that iterates known widgets with enabled/size/position fields. This does not meet the new contract because it lacks rows, 12-column widths, widget movement, add-row, widget catalog, and decorator edit mode.

`avatar-dashboard.css` already uses the correct broad visual direction: light panel background, thin borders, low radius, compact typography, and a sticky chat rail. It needs to be tightened and extended rather than replaced with a consumer-card dashboard.

### Avatar Data

`avatar-schema.sql` contains `avatar_dashboard_layout`, `avatar_todos`, `avatar_daily_tasks`, `avatar_calendar_items`, and `avatar_notes`. These are adequate for the current simple organizer but not for planner task recurrence, subtodos, calendar projection, or linkable note relationships.

The Avatar package guide explicitly keeps Avatar persistence in `avatar.sqlite` and runtime state out of the Avatar package. Keep that boundary.

### Workspace And Runtime

Workspace services already enforce confinement under `dataRoot`. `WorkspaceDirectoryService` provides agent/project roots and standard subdirectories. `OutputArtifactService` validates output paths and persisted artifact reads.

`work_assignments` stores assignment runtime state and effective workspace ids, but it does not store selected Work Area or output redirect choice. Add these as explicit assignment metadata rather than smuggling them into `input_json`.

The current architecture focus says execution aliases are `workspace/`, `work/`, `outputs/`, `run/`, `scratch/`, and `job/`. This plan changes the meaning of `workspace/` for assignments with a selected Work Area: `workspace/` is the selected Work Area, while `root/` is the broader effective owned root. Record that as a deliberate compatibility-sensitive runtime change.

### Submit Surfaces

`OrchestrationController` owns `/agents`, `/agents/{id}`, `/agents/_submit-form/{agentId}`, `/agents/_submit/{agentId}`, and agent detail submit routes. These are the main web targets for Work Area/output routing controls. Plan chats remain excluded.

## 5. Target Design

### 5.1 Avatar Layout Model

Replace `avatar_dashboard_layout` with row-aware layout records. The simplest durable schema is:

```sql
create table if not exists avatar_dashboard_rows (
    id text primary key,
    row_position integer not null,
    collapsed integer not null default 0,
    settings_json text not null default '{}',
    updated_at text not null
);

create table if not exists avatar_dashboard_widgets (
    id text primary key,
    row_id text not null,
    widget_key text not null,
    column_position integer not null,
    column_width integer not null,
    enabled integer not null default 1,
    collapsed integer not null default 0,
    settings_json text not null default '{}',
    updated_at text not null,
    unique(widget_key),
    foreign key(row_id) references avatar_dashboard_rows(id) on delete cascade
);

create index if not exists idx_avatar_dashboard_widgets_row
    on avatar_dashboard_widgets(row_id, column_position);
```

Compatibility: during the first migration, transform existing `avatar_dashboard_layout` rows into one or more dashboard rows by mapping `wide -> 6`, `standard -> 4`, `compact -> 3` and wrapping at 12 columns. Keep a fallback if the old table is missing.

Widget catalog v1:

- `daily-tasks`
- `todos`
- `calendar`
- `notes`
- `work-areas`
- `outputs`
- `system`
- `alerts`
- `recent-work`

The old `files` widget becomes the new `work-areas` widget. Do not allow duplicate first-party widget keys in v1.

### 5.2 Layout Editing Contract

Create reusable web-layer components, for example:

- `DashboardLayoutEditorComponents`
- `EditableDashboardRow`
- `EditableDashboardWidgetFrame`
- `WidgetCatalogModal`

These should use SimplyPages `Row`, `Column`, `Button`, `Select`, `Modal`, `Form`, `EditableModule.wrap(...)` where it fits. Raw HTML is allowed only where SimplyPages cannot express an attribute or structure after docs/demo inspection; document any raw fallback in code comments or the implementation handoff.

Endpoints under `/avatar/_layout/**`:

- `GET /avatar/_edit` opens edit mode shell.
- `POST /avatar/_layout/rows` adds a row and returns OOB grid/editor update.
- `POST /avatar/_layout/rows/{rowId}/move` with `direction=up|down`.
- `DELETE /avatar/_layout/rows/{rowId}` only when row is empty or after explicit confirm.
- `GET /avatar/_layout/rows/{rowId}/catalog` opens add-widget modal.
- `POST /avatar/_layout/rows/{rowId}/widgets` adds catalog widget at width.
- `POST /avatar/_layout/widgets/{widgetId}/move` with `direction=left|right|up|down`.
- `PUT /avatar/_layout/widgets/{widgetId}/width` with preset `3|4|6|8|12`.
- `POST /avatar/_layout/widgets/{widgetId}/toggle`.
- `DELETE /avatar/_layout/widgets/{widgetId}` removes from layout but leaves widget data.

Every mutation persists immediately and returns:

- empty/closed modal container OOB when applicable;
- `#avatar-widget-grid` OOB;
- `#avatar-layout-editor` or `#avatar-edit-container` OOB;
- user-visible error fragment for validation failures.

Movement bounds:

- Row up/down disabled at first/last row.
- Widget left/right disabled when already at row edge or when width would overlap.
- Widget up/down moves to nearest valid slot in adjacent row if capacity exists; otherwise return a validation fragment.
- Width changes must keep row total at or below 12.

### 5.3 Visual UI Contract

The redesigned `/avatar` must:

- keep a dense operational shell, not a landing page;
- use white/near-white panels, thin blue-gray borders, low shadows, and radii at or below 8px;
- keep multiple useful widgets visible in the first desktop viewport;
- use compact headings, table/list rows, action strips, and semantic chips;
- use icon-triggered modal actions for mutations inside widgets;
- avoid nested decorative cards and oversized personal-product blocks;
- use HTMX for CRUD, modal open/save, row/column actions, autosave, refresh, search/filter, and OOB swaps;
- keep JavaScript limited to chat streaming and narrow local helpers such as text preview editor affordances if HTMX alone is materially awkward.

### 5.4 Organizer Planner Data

Hard-replace or migrate old simple organizer tables into planner-grade tables in `avatar.sqlite`:

```sql
create table if not exists avatar_planner_tasks (
    id text primary key,
    title text not null,
    notes text,
    status text not null,
    priority text not null,
    starts_at text,
    due_at text,
    timezone text,
    recurrence_json text,
    linked_project_id text,
    linked_assignment_id text,
    linked_job_id text,
    linked_output_id text,
    created_at text not null,
    updated_at text not null,
    completed_at text
);

create table if not exists avatar_planner_subtodos (
    id text primary key,
    task_id text not null,
    title text not null,
    status text not null,
    subtodo_position integer not null,
    created_at text not null,
    updated_at text not null,
    foreign key(task_id) references avatar_planner_tasks(id) on delete cascade
);

create table if not exists avatar_planner_task_notes (
    task_id text not null,
    note_id text not null,
    created_at text not null,
    primary key(task_id, note_id)
);

create table if not exists avatar_planner_calendar_projection (
    id text primary key,
    task_id text not null,
    occurrence_start text not null,
    occurrence_end text,
    status text not null,
    created_at text not null,
    updated_at text not null,
    foreign key(task_id) references avatar_planner_tasks(id) on delete cascade
);
```

Records to add:

- `PlannerTask`
- `PlannerSubtodo`
- `PlannerTaskStatus`
- `PlannerRecurrence`
- `PlannerCalendarProjection`
- `PlannerTaskLink`

Recurrence input module:

- friendly mode first: none, daily, weekly, monthly;
- fields: start date, optional end date, time, interval, weekday/month day;
- advanced cron field collapsed/back-seat;
- service validates recurrence and projects near-term occurrences for calendar widget.

Organizer widgets open tabbed modals:

- Planner tasks
- Todos/subtodos
- Calendar
- Notes

Planner tasks are distinct from Magenta executable task/plan definitions. Name UI copy carefully to avoid confusing planner tasks with work-unit tasks.

### 5.5 Work Area Persistence

Add runtime-owned Work Area records in `magenta.sqlite`:

```sql
create table if not exists work_areas (
    id text primary key,
    owner_type text not null, -- AGENT or PROJECT
    owner_id text not null,
    workspace_id text,
    root_relative_path text not null,
    area_relative_path text not null,
    display_name text not null,
    system_flag integer not null default 0,
    home_flag integer not null default 0,
    active_flag integer not null default 1,
    metadata_json text not null default '{}',
    created_at text not null,
    updated_at text not null,
    unique(owner_type, owner_id, area_relative_path)
);

create index if not exists idx_work_areas_owner
    on work_areas(owner_type, owner_id, active_flag);
```

Add records/services under `ai.orchestration.workspaces`:

- `WorkArea`
- `WorkAreaOwnerType` or reuse `WorkspaceOwnerType` if adequate
- `WorkAreaRepository`
- `WorkAreaService`
- `WorkAreaDirectoryService` or methods on `WorkspaceDirectoryService`
- `WorkAreaPathPolicy`

Rules:

- Any confined existing directory under an agent/project owned root can be marked as a Work Area.
- New default Home Work Area is `<root>/home`.
- Keep old folders browsable for compatibility; do not delete or hide legacy `work/`, `outputs/`, `runs/`, `scratch/`.
- Default assignment work uses Home Work Area when no selection is provided.
- Persistent jobs get a default Work Area under `<root>/ongoing_jobs/<job-slug-or-user-override>`.
- Work Area ids are stable metadata records; file paths are resolved and checked at use time.

### 5.6 Assignment And Runtime Routing

Add columns to `work_assignments`:

```sql
alter table work_assignments add column selected_work_area_id text;
alter table work_assignments add column output_route_mode text;
alter table work_assignments add column output_work_area_id text;
alter table work_assignments add column output_direct_relative_path text;
```

Update records:

- `AssignmentRequest` adds selected Work Area and output route fields.
- `WorkAssignment` adds the same fields.
- `AssignmentTemplateParser` preserves and validates these fields for templates.
- Repository row mapping persists them.
- Audit/transcript rendering shows selected Work Area and output routing.

Output route enum:

- `DEFAULT_SELECTED_WORK_AREA`
- `WORK_AREA`
- `DIRECT_DIRECTORY`

Runtime path resolution:

- Resolve effective owned root from existing project/agent rule.
- Resolve selected Work Area:
  - request value if supplied and active;
  - persistent job default if job policy requires it;
  - Home Work Area otherwise.
- Expose aliases to model/tool runtime:
  - `workspace/` -> selected Work Area real path;
  - `root/` -> broader owned root real path;
  - `work/`, `outputs/`, `scratch/` should resolve relative to selected Work Area for new assignments.
- Output directory resolver uses selected Work Area by default.
- Direct output directory must exist, be a directory, be under owned root by `toRealPath()`, and not be protected.

### 5.7 File Explorer

Add `/avatar` explorer fragments:

- `GET /avatar/_work-areas` rerenders Work Areas widget.
- `GET /avatar/_work-areas/modal?ownerType=&ownerId=` opens Work Areas modal.
- `GET /avatar/_explorer?ownerType=&ownerId=&path=` returns directory listing.
- `GET /avatar/_explorer/preview?ownerType=&ownerId=&path=` previews text or metadata.
- `GET /avatar/_explorer/download?ownerType=&ownerId=&path=` downloads file.
- `GET /avatar/_explorer/edit?ownerType=&ownerId=&path=` opens text edit modal.
- `PUT /avatar/_explorer/file` saves safe text edit.
- `POST /avatar/_explorer/directories` creates directory.
- `PUT /avatar/_explorer/rename` renames file/directory.
- `DELETE /avatar/_explorer` recursively deletes after typed confirm.
- `POST /avatar/_work-areas/mark` marks directory as Work Area.
- `POST /avatar/_work-areas/{workAreaId}/unmark` deactivates non-system Work Area.

Explorer v1 must reject:

- path traversal;
- symlink escapes;
- editing binary files;
- editing files over configured byte limit;
- recursive delete of owned root;
- recursive delete of current Home;
- recursive delete of active Work Areas in queued/running assignments;
- recursive delete of active output targets;
- recursive delete of protected system paths;
- unmarking Home/system Work Areas without a deliberate future migration.

### 5.8 Submit Forms And Pickers

Integrate Work Area and output routing controls into:

- `/agents/_submit-form/{agentId}`
- `/agents/_submit/{agentId}`
- agent detail submit panel;
- job/schedule/reaction assignment template forms that enqueue assignments;
- task/workflow/job submit forms where they create `AssignmentRequest`.

Do not add Work Area controls to plan chats.

Picker UX:

- compact current selection display;
- browse button opens explorer/picker modal;
- default to Home Work Area;
- output route segmented control: default, other Work Area, direct directory;
- direct directory picker allows existing directories only.

### 5.9 Compatibility And Migration

- Existing assignments with null Work Area fields resolve to Home Work Area at runtime.
- Existing output artifacts remain readable from stored artifact paths.
- Existing old Avatar widgets should be migrated best-effort into rows.
- If old organizer rows are hard-replaced, add a changelog and clear docs warning because `avatar.sqlite` alpha data is not preserved.
- Keep old folders browsable but mark new Home Work Area as default.

### 5.10 Observability And Errors

- Assignment audit should record selected Work Area id/path and output route.
- Explorer and layout endpoints return user-visible HTMX error fragments, not raw stack traces.
- Runtime validation should include audit evidence showing alias mapping and output path choice.
- Add structured events when Work Areas are marked/unmarked and when guarded delete is refused.

## 6. Implementation Plan

### Phase 01: Foundation Branch And Baseline

Files:

- `AGENTS.md`
- `.internal-dev/focus/AGENTS.md`
- package `AGENTS.md` for web, avatar, orchestration, workspace
- this plan suite

Steps:

1. Create or continue a dedicated branch such as `feature/avatar-agent-ui-refactor`.
2. Confirm `git status --short` is clean or only contains expected plan-suite files.
3. Run a baseline focused compile/test command if cheap:
   - `mvn -DskipTests compile`
   - or record why baseline was skipped.
4. Create `.codex-orchestration/avatar-agent-ui-refactor/notes.md`.

Exit: clean baseline known and shared notes initialized.

### Phase 02: Avatar Layout Schema And Services

Files:

- `src/main/resources/avatar-schema.sql`
- `src/main/java/io/mindspice/magenta2/avatar/**`
- `src/test/java/io/mindspice/magenta2/avatar/**`

Steps:

1. Add row/widget layout records.
2. Add repository methods:
   - list rows with widgets;
   - add row;
   - move row;
   - add widget;
   - move widget;
   - resize widget;
   - remove widget;
   - normalize/repair row positions and widths.
3. Add migration adapter from old `avatar_dashboard_layout`.
4. Enforce single widget key.
5. Add tests for migration, duplicate widget rejection, movement bounds, width bounds, and position normalization.

Gotchas:

- Do not put runtime Work Area data in Avatar package.
- Keep data carriers as records where practical.
- Preserve `AvatarSnapshot` compatibility or update tests and callers.

### Phase 03: SimplyPages Layout Editor Components

Files:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- new web components under `src/main/java/io/mindspice/magenta2/api/web/`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`

Steps:

1. Replace flat edit modal with edit mode shell and reusable decorator components.
2. Render rows using SimplyPages `Row`/`Column` with stable ids.
3. Wrap widget display modules or widget frames with edit controls inspired by `EditableModule.wrap(...)`.
4. Add icon/symbol controls for:
   - move row up/down;
   - move widget left/right/up/down;
   - width presets;
   - add row;
   - add widget from catalog;
   - remove/toggle widget.
5. Keep one modal container and use OOB responses after every mutation.
6. Tighten CSS to align with `/dashboard` and `/agents`.

Gotchas:

- Do not create a raw HTML string dashboard if SimplyPages components can express the structure.
- Keep stable ids for HTMX targets.
- Avoid nested decorative cards.

### Phase 04: Work Area Persistence

Files:

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/**`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/**`

Steps:

1. Add `work_areas` schema.
2. Add Work Area records, repository, service, and path policy.
3. Materialize Home Work Area for each agent/project root.
4. Support mark/unmark of existing confined directories.
5. Add persistent job Work Area resolver under `ongoing_jobs/<slug>`.
6. Add active-use queries for queued/running assignments and output targets.
7. Add tests for confinement, symlink escape, duplicate marks, Home creation, and protected-path checks.

Gotchas:

- Use `toRealPath()` where symlink escape matters.
- Do not broaden deletion permissions just because paths are under `dataRoot`.

### Phase 05: Assignment Runtime And Output Routing

Files:

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/**`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/**`
- selected task/workflow/job output resolver files
- runtime tests

Steps:

1. Add assignment metadata columns and record fields.
2. Extend `AssignmentRequest`, `WorkAssignment`, repository mappers, and template parser.
3. Add output route enum and resolver.
4. Modify assignment creation to default missing selection to Home Work Area.
5. Update runtime alias construction so `workspace/` maps to selected Work Area and `root/` maps to effective owned root.
6. Update output artifact materialization call sites to use routed output directory.
7. Add audit/transcript metadata.

Gotchas:

- Existing assignments with null fields must keep working.
- Project membership and lease rules still apply to the broader owned root.
- Direct output directory must be existing and confined under owned root, not merely string-prefixed.

### Phase 06: Explorer Routes And Components

Files:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- possible `AvatarExplorerComponents`
- `src/main/resources/static/css/avatar-dashboard.css`
- controller tests

Steps:

1. Add Work Areas widget.
2. Add explorer modal with tabs/regions for Work Areas, tree/list, preview, and actions.
3. Implement browse, preview/download, safe text edit, create directory, rename, recursive delete, mark/unmark.
4. Use typed confirm for recursive delete.
5. Return HTMX fragments for validation and authorization errors.

Gotchas:

- Browser downloads may use normal links; CRUD stays HTMX.
- Preview/download must share path policy with edit/delete.

### Phase 07: Planner Data And Modals

Files:

- `src/main/resources/avatar-schema.sql`
- `src/main/java/io/mindspice/magenta2/avatar/**`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- tests in Avatar and web packages

Steps:

1. Add planner task/subtodo/recurrence/link/projection schema and records.
2. Add recurrence parser/validator/projector.
3. Add reusable recurrence input component.
4. Replace inline organizer mutations with modal CRUD flows.
5. Use tabbed organizer modals for planner tasks, todos/subtodos, calendar, and notes.
6. Link planner tasks to existing project/assignment/job/output ids as metadata only.

Gotchas:

- Do not add automation that schedules or contacts the user.
- Keep planner task wording distinct from executable Magenta tasks.

### Phase 08: Submit Form Integration

Files:

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- possibly reusable picker components under web package
- `src/test/java/io/mindspice/magenta2/api/web/**`

Steps:

1. Add compact Work Area picker to assignment submit surfaces.
2. Add output route controls and picker modals.
3. Validate selected Work Area and output route before `AssignmentRequest`.
4. Include fields in schedule/reaction assignment templates where they enqueue runtime assignments.
5. Ensure plan-chat routes remain unchanged.

Gotchas:

- `OrchestrationController.java` is large and shared; serialize all edits.
- Keep existing project/workspace selectors working.

### Phase 09: Documentation And Closeout

Files:

- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- new technical docs for Work Areas/output routing if needed
- package `AGENTS.md` files if boundaries changed
- `.internal-dev/changelogs/**`
- `.internal-dev/knowledge/**`
- `.internal-dev/focus/**`

Steps:

1. Update end-user docs for Work Areas, explorer, layout edit mode, planner modals, and output routing.
2. Update technical route docs for new fragment endpoints.
3. Update package guides if Work Area ownership or route conventions changed.
4. Add changelog and reusable knowledge notes.
5. Update focus/unfinished-work/architecture/decisions as applicable.
6. Commit explicit paths only.

## 7. Validation Plan

See `validation-red-team.md` for the full gate. Minimum required commands/scenarios:

- Focused Avatar repository/service tests.
- Focused workspace/path policy tests.
- Focused assignment runtime tests.
- Focused controller fragment tests.
- `mvn test` scope chosen by the implementation coordinator after touched modules are known.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Playwright validation by `gpt-5.3-codex` medium subagent with screenshots for:
  - `/avatar` view mode desktop/mobile;
  - `/avatar` edit mode;
  - widget modals;
  - Work Area explorer;
  - submit pickers;
  - `/dashboard` and `/agents` comparison surfaces.
- Runtime proof:
  - queued assignment with selected Work Area sees it as `workspace/`;
  - broader owned root is visible as `root/`;
  - default output writes under selected Work Area outputs;
  - Work Area redirect writes under target Work Area outputs;
  - direct redirect writes to existing confined directory.

## 8. Handoff Checklist

- Read this suite and package guides.
- Keep implementation on a dedicated branch.
- Create shared orchestration notes before coding.
- Run only one code-editing lane at a time unless the coordinator explicitly authorizes disjoint docs/review work.
- Use SimplyPages components/modules first; document any raw HTML fallback.
- Keep all standard UI mutations HTMX-first.
- Preserve existing runtime/project/agent boundaries.
- Add tests before sign-off for every schema/API/runtime change.
- Run Playwright validation through a subagent.
- Complete docs, `.internal-dev`, and explicit-path commit workflow before final response.
