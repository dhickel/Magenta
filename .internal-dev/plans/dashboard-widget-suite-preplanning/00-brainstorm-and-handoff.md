# Dashboard Widget Suite Brainstorm And Planning Handoff

Date: 2026-05-29
Status: preplanning handoff, not yet dispatched
Work classification: large mixed research, product design, architecture, API/tooling, persistence, UI, docs, and validation plan

## Objective

Replace the current bare-bones Assistant dashboard widgets with a proper first-party widget suite that supports personal ADHD-friendly planning, household/project tracking, agent-bound operational views, and agent-accessible tools.

The planning agent must produce a full orchestrated plan suite for implementation, not a thin MVP pass. The plan should assume this is a major end-user product feature, second only to automation in user-facing importance.

## User-Visible Outcome

The user should be able to open the Home dashboard and get a useful personal command center:

- Fast capture for tasks, notes, reminders, and project ideas.
- A day planner that can map today, now/next/later, top priorities, time blocks, routines, and overdue items.
- Feature-rich calendar and schedule views with recurrence, reminder states, and task projections.
- Tasks and routines that support chores, household projects, weekly/monthly ranges, repeatable work, and skip/snooze/restart behavior without guilt.
- Notes that can be personal notes, agent-bound notes, project notes, or Work Area file notes.
- Projects that work for both code projects and household projects, including goals, materials, blockers, contacts, next actions, and progress.
- Agent-bound widgets that select an agent in settings and show the selected agent clearly in the widget.
- Agent tools that can read and mutate the same user-facing widget state through controlled service APIs.
- Widget summaries that are compact, useful, and polished, with rich modal/detail views for real work.

## Current Source Context

Read these before detailed planning:

- `.internal-dev/specifications/AGENTS.md`
- `.internal-dev/specifications/index.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/specifications/deferred-features.md`
- `.internal-dev/knowledge/dashboard-api-contract.md`
- `.internal-dev/knowledge/dashboard-fragment-navigation.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AGENTS.md`

Observed current implementation anchors:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/resources/avatar-schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalTools.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-chat.js`
- `src/main/resources/static/js/avatar-layout-edit.js`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/avatar-dashboard-layout-persistence.md`
- `docs/technical/avatar-planner-organizer.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/api/00-index.md`
- `docs/technical/api-reference.md`

SimplyPages references to inspect:

- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/core/02-layout-page-row-column-grid.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/getting-started/03-editing-system-first-implementation.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/reference/editing-api-reference.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`

## Existing Contract To Preserve

- Dashboards are agent-agnostic widget containers. Agents do not own dashboards.
- Widgets can be bound to agents, projects, Work Areas, or personal data through widget settings.
- Avatar/user organizer data currently lives in `avatar.sqlite`.
- Runtime state, agent profiles, assignments, jobs, projects, Work Areas, schedules, reactions, and outputs stay in existing Magenta runtime services and `magenta.sqlite`.
- Do not create cross-database foreign keys between `avatar.sqlite` and `magenta.sqlite`.
- Work Areas use service-owned path confinement and existing Work Area/file explorer services.
- Controllers stay thin. Services own use cases. Repositories own persistence.
- UI is dense operational UI: compact panels, thin blue-gray borders, small radii, semantic chips, restrained controls, no landing-page styling.
- HTMX is the default for CRUD, forms, filtering, row actions, modal swaps, and partial refreshes.
- JavaScript is allowed only for narrow local behavior such as chat streaming, local resize, calendar drag behavior if justified, and interactions where JS is clearly the simpler path.
- Layout editing stays in-place on the real dashboard surface. Detail/settings flows may use modals or drawers.
- Standard SimplyPages row/column composition, HTMX fragment swaps, OOB responses, and stable target IDs should be used before raw strings or bespoke JS.

## Brainstorm: Recommended Product Shape

### 1. Widget Registry Instead Of Static Widget Switches

Current widget behavior is mostly a static `WIDGETS` list and a `widgetBody(...)` switch. That was enough for a layout refactor but is too weak for this feature set.

Plan a first-party widget registry with a Java domain shape such as:

- `widgetType` or `key`
- title, description, category
- default width and supported widths
- allows multiple instances or single-instance only
- data owner: personal/avatar, agent, project, Work Area, system, output, external/file-backed
- binding mode: none, optional agent, required agent, required project, required Work Area, locked/system
- settings schema and defaults
- summary renderer
- detail modal renderer
- settings modal renderer
- refresh policy
- empty-state policy
- declared tool contract names
- validation/test fixture expectations

Important schema issue: the current `user_dashboard_widgets` uniqueness is `unique(dashboard_id, widget_key)`. Multiple instances of the same widget type will matter for agent-bound widgets, project widgets, and filtered output widgets. The plan must explicitly decide whether to change this to `widget_type + instance id/settings` or to keep uniqueness only for selected single-instance widgets.

### 2. First-Party Widgets Are DB/Service Backed, Custom Widgets Are Schema/File Backed

The user explicitly wants future scripted/custom widgets, but the immediate product should not wait on a safe scripting runtime.

Recommended boundary:

- First-party organizer widgets use `avatar.sqlite` and service APIs where querying, recurrence, reminders, ordering, undo, and tool access need transactional behavior.
- Runtime/agent/project/output widgets reuse existing Magenta services and tables.
- Custom or scripted widget support starts with manifests and file-backed schemas, not arbitrary script execution.
- Project-specific household data such as materials, goals, measurements, contacts, and checklists should be represented as file-backed project artifacts or typed schemas under project/Work Area roots, with optional indexes in services where needed.
- Tools and widgets should read/write these through service-owned adapters, not direct filesystem hacks.

This gives the user reliable first-party behavior while still creating a path for end-user/custom widgets later.

### 3. Agent Tools Should Be A Widget Feature Contract

Each first-party widget should declare the tools agents can use for that widget. Existing `AvatarAssistantTools` already includes todo, daily task, calendar, note, submit-task, research-assignment, and output tools. This should be formalized instead of left implicit.

Tool contract requirements:

- Tool names are exact Spring AI tool names, tested through `ChatToolRegistry`.
- Each widget definition references read tools and mutation tools separately.
- Mutating tools require service validation and explicit confirmation where destructive or high-impact.
- Normal `agent_*` tools stay scoped to the current orchestration context.
- `avatar_*` tools remain supervisor-only and gated by the Avatar profile/tool approval rules.
- Agent-bound widgets may display a selected agent, but normal-agent tools must not accept arbitrary `agentId` arguments that bypass context boundaries.
- Dashboard chat should eventually be dashboard-aware, but the plan must distinguish read-only context injection from action tools.

Research required: if the planner proposes dynamic widget tool registration instead of annotated static tools, it must inspect current Spring AI `ToolCallbackProvider` behavior from official documentation and compare it to the existing static `@Tool` implementation.

### 4. Widget UI Pattern

Every proper widget should have three layers:

- Compact dashboard summary: bounded height, direct high-value actions, readable empty states, selected binding chip when applicable.
- Detail modal/drawer: feature-rich operational surface for real work, using HTMX tab/fragment swaps where appropriate.
- Settings modal: source selection, agent/project/Work Area selectors, filters, display density, default view, refresh/reminder settings, and data/tool visibility.

Do not make widget cards into giant forms. The dashboard card is for scanning and a few common actions. Deeper mutation happens in detail/settings surfaces.

Agent-bound widgets must show:

- selected agent chip/name/status in the header or settings summary;
- change-agent affordance in settings, using existing selector patterns;
- clear empty/error state when no agent is selected or the agent has no matching data;
- no confusion between dashboard ownership and widget binding.

### 5. Notes Source Model

Recommended notes design:

- Personal notes remain in `avatar_notes` for fast capture and assistant-organizer use.
- Agent/project/Work Area notes use the existing file explorer and label/tag system, especially files/directories tagged as notes.
- Notes widget settings choose source mode: personal, selected agent, selected project, selected Work Area, or mixed.
- The widget remembers last opened note/file in settings where useful.
- Detail view gives list/search/filter plus a viewer/editor that borrows from the good file viewer/browser UI.
- Tools can search personal notes and, when context allows, search/read tagged file notes through Work Area/file services.

### 6. Project And Household Project Model

Current projects are shared workspace/visibility records, not executable work units. Keep that.

Recommended project widget direction:

- Project database record remains the anchor for project identity, membership, workspace, jobs, and outputs.
- Household project content should be typed project artifacts: goals, materials, contacts, measurements, decisions, errands, blockers, and next actions.
- The planner should decide whether those artifacts live as structured JSON/YAML/Markdown files in the project workspace, `avatar.sqlite` tables, or a hybrid. Default recommendation is file-backed project schemas with service indexes only where query/performance requires it.
- Project widget should support code projects and household projects without pretending every project is a code repo.
- Agent tools should be able to inspect project goals/materials/next actions and propose updates through controlled file/schema tools or service adapters.

### 7. Calendar, Tasks, Routines, And Reminders

The existing planner data has tasks, subtodos, recurrence JSON, and projection rows, but the UI is too shallow.

Recommended product model:

- Separate "task due date" from "scheduled time block" and "reminder."
- Support daily/weekly/monthly/custom recurrence with skip/snooze/restart semantics.
- Support date ranges like "this week" or "sometime this month" for ADHD-friendly planning.
- Support chores and repeating household tasks without making missed occurrences feel like broken state.
- Support a day map that can turn projects/tasks into now/next/later or time-boxed blocks.
- Support reminder records and an in-dashboard reminder/alert surface. External notification channels can be gated if the current app has no accepted PWA/push/email delivery contract.
- The planner must explicitly decide whether this work accepts `DEFERRED-20260525-05` around reminder automation. If yes, update specs and include a scheduler/reminder design. If not, scope reminders to local in-dashboard state and make the limitation explicit.

External feature patterns to consider:

- CHADD guidance emphasizes breaking projects into actionable tasks and choosing top daily priorities.
- Todoist and Microsoft To Do both expose due dates, reminders, and recurring tasks as core task behavior.
- Sunsama emphasizes daily planning and timeboxing tasks onto a calendar.
- TickTick combines tasks, calendar, Eisenhower matrix, Pomodoro, and habit tracker modules.
- Amazing Marvin exposes daily planning, time estimates, recurring tasks, habits, anti-overwhelm, task breakdown, dopamine menu, and procrastination tools.
- Notion Calendar/database workflows show the value of linking docs/pages to calendar/task records.
- Neurodivergent planner products such as Tiimo, Structured, Thruday, TidalTask, Tempus, and Unstuck highlight quick capture, visual timelines, flexible routines, task breakdown, timers/focus, and gentle restart paths.

Sources for planning research:

- CHADD to-do list guidance: https://chadd.org/adhd-weekly/the-art-of-the-to-do-list/
- Todoist features: https://www.todoist.com/features
- Todoist reminders: https://get.todoist.help/hc/en-us/articles/205348301-Introduction-to-Reminders
- Microsoft To Do due dates/reminders/repeats: https://support.microsoft.com/en-us/office/add-due-dates-and-reminders-in-microsoft-to-do-064d9696-08d1-4433-bfdd-f661dc97491f
- Sunsama timeboxing: https://help.sunsama.com/docs/timeboxing
- Sunsama usage guide: https://help.sunsama.com/docs/usage-guides/
- TickTick beginner guide: https://help.ticktick.com/articles/7054286604315131904
- Amazing Marvin features: https://amazingmarvin.com/features/
- Amazing Marvin habits: https://help.amazingmarvin.com/en/articles/4835241-habits
- Notion recurring database templates: https://www.notion.com/en-gb/help/guides/automate-work-repeating-database-templates
- Notion Calendar getting started: https://www.notion.com/en-gb/help/guides/getting-started-with-notion-calendar

## Candidate First-Party Widget Suite

The planning agent should research and refine this suite, then sequence it 2-3 widgets per implementation phase after foundation work.

### Personal Organizer Widgets

1. Today Planner
   - Daily map, top priorities, now/next/later, time blocks, overdue, unscheduled, restart day, and quick capture.
   - Should pull from planner tasks, routines, calendar items, reminders, and optionally selected project tasks.
   - Detail modal should support drag/reorder or HTMX order controls, planned vs actual time, and daily review.

2. Tasks And Routines
   - Combines todos, recurring chores, subtasks, ranges, skip/snooze, priority, tags, project links, and status.
   - Avoids one-off task lists that silently become graveyards.
   - Detail modal should include filters, recurring rules, projected occurrences, and backlog cleanup.

3. Calendar And Schedule
   - Actual calendar view: day/week/month, agenda, projected planner occurrences, task timeboxing, reminders.
   - Must be visually calendar-like, not a short list of events.
   - Research whether a proven Java/browser calendar rendering helper is appropriate or if server-rendered HTMX is enough for v1.

4. Notes
   - Personal notes plus agent/project/Work Area note files.
   - Quick capture, search, last-opened note, tags, Markdown view/edit, source selector.
   - Agent-bound settings can lock notes to an agent or Work Area.

5. Projects
   - Code and household projects.
   - Goals, materials, contacts, blockers, next actions, outputs, project notes, active work, progress.
   - Should not duplicate full `/projects`, but should provide enough operational context to be useful.

6. Habits, Trackers, And Progress
   - Chores/routines, health/cleaning/maintenance tracks, streaks only if non-punitive, completion history, trend chips.
   - Should support "missed/skip/restart" without shame UX or broken recurrence.

7. Contacts And Follow-Ups
   - People, roles, project association, next contact, notes, reminders.
   - Likely file-backed or avatar-owned depending on planner research.

8. Reminders And Alerts
   - In-app reminder inbox, snooze, complete, reschedule, link to task/calendar/project/contact.
   - External notification channels should be separately gated if no current accepted route exists.

### Agent-Bound And System Widgets

1. Agent Status And Queue
   - Selected agent, status, model, queue, inbox, running/waiting work, health.
   - Settings choose agent and display filters.

2. Agent Outputs
   - Selected agent/project/job filters, recent outputs, preview/download, text/json/user-message inline read.
   - The current generic Outputs widget must become explicitly tied to dashboard-wide recent outputs, selected agent, selected project, selected job, or selected Work Area through settings.

3. Agent Files And Notes
   - Selected agent Work Area browser mini-view and tagged-note view.
   - Uses the existing file browser/viewer visual language and service confinement.

4. Project Activity
   - Project membership, active jobs, recent outputs, workspace lease state, blockers, and events.

5. System Metrics
   - Runtime counts, failed items, active agents, scheduler state, model/config warnings.
   - Should be operational and explainable, not random counters.

6. Chat/Assistant Context Panel
   - If dashboard-aware chat is accepted, expose a read-only summary of selected dashboard/widget state and action tools behind approvals.
   - This should not replace the main `/chat` endpoint or import the full browser chat client.

## Recommended Planning Work Units

The advanced planning agent should produce a large plan suite under:

`.internal-dev/plans/dashboard-widget-suite/`

Recommended work-unit grouping:

1. Research and specification lock
   - Survey current code, local docs, SimplyPages demos, ADHD/planner app patterns, Spring AI tool registration if needed, and recurrence/calendar options.
   - Produce a specific feature contract and phase map before implementation directives.

2. Widget platform foundation
   - Widget registry, widget instance/settings model, multi-instance policy, settings schema, binding model, route naming, settings/detail modal patterns, tool descriptor mapping.
   - Include migration from existing rows/widgets and `unique(dashboard_id, widget_key)` constraint resolution.

3. Personal planning core, 2-3 widgets
   - Today Planner, Tasks/Routines, Calendar/Schedule.
   - This group owns recurrence/reminder decisions and day mapping.

4. Notes and project context, 2-3 widgets
   - Notes, Projects, Contacts/Materials or Project Materials.
   - This group owns file-backed schema decisions for household/project data.

5. Agent-bound operational widgets, 2-3 widgets
   - Agent Status/Queue, Agent Outputs, Agent Files/Notes or Project Activity.
   - This group owns selected-agent settings UI and tool/display boundary.

6. Tracking, alerts, and dashboard-aware assistant context
   - Habits/Trackers, Reminders/Alerts, dashboard-aware chat context if accepted.
   - If external notification delivery is not accepted, limit to in-app reminder state and document the boundary.

7. Integration, polish, docs, specs, and final validation
   - End-user docs, technical docs, API docs, `.internal-dev` specs, changelog, evidence index, stale-reference sweep, browser proof reconciliation.

## Required Planning Deliverables

Planning model override:

- Planning/research agent: `gpt-5.5`, reasoning `xhigh`.
- Implementation workers: `gpt-5.5`, reasoning `high`.
- Code validators/red-team agents: `gpt-5.5`, reasoning `xhigh`.
- Playwright/browser validation agents: `gpt-5.5`, reasoning `high` unless the current tool route can select `xhigh`; if model/reasoning selection is unavailable, stop and record `TOOLING_CONSTRAINT` before substituting.

The advanced planning agent must produce:

- `00-specification-lock.md`
- `01-current-state-analysis.md`
- `02-external-research-and-product-patterns.md`
- `03-target-architecture-and-widget-contract.md`
- `04-data-model-and-migration-design.md`
- `05-tooling-and-agent-access-design.md`
- `06-ui-ux-contract-and-visual-validation-criteria.md`
- `shared/senior-engineer-guidance.md`
- `shared/implementation-notes.md`
- `shared/validation-matrix.md`
- `work-units/README.md`
- `worker-directives/phase-XX-<role>.md` for each work unit
- `final-orchestration-plan.md`
- `artifacts/dashboard-widget-suite/validation-summary.json` schema or planned evidence index contract

The plan must use `.internal-dev/plans/dashboard-widget-suite/` and should not produce a vague single markdown file.

## Acceptance Criteria For The Future Implementation

Platform criteria:

- Widget definitions are centralized and include UI, settings, binding, refresh, and tool metadata.
- Widget instances support settings and agent/project/Work Area binding without making dashboards agent-owned.
- Multi-instance widget needs are handled explicitly.
- Widget settings persist and validate invalid/missing bound entities.
- Summary/detail/settings routes use stable HTMX targets and OOB updates where needed.
- Existing dashboard layout editing remains in-place and visually compact.

Personal widget criteria:

- Today Planner gives a useful daily map, not just a list.
- Tasks/Routines handles recurring chores, ranges, subtasks, skip/snooze/restart, project links, and status.
- Calendar has real calendar/agenda views, recurrence projection, and task timeboxing/reminder affordances.
- Notes support quick capture, search, last opened note, tags, and personal/file-backed source modes.
- Projects support household and code projects with goals, materials, blockers, contacts, notes, and next actions.
- Habits/trackers avoid punitive streak-only design.

Agent/system widget criteria:

- Agent-bound widgets have settings to select the agent and a clear selected-agent UI chip.
- Agent-bound widgets use existing orchestration, Work Area, project, and output services.
- Outputs widget has explicit source binding and does not remain an unscoped "recent stuff" list unless that mode is selected.
- Agent/file widgets use existing file browser/viewer patterns and path confinement.
- System metrics explain their source and avoid placeholder counters.

Tool criteria:

- Every widget that should be agent-accessible declares matching read/mutation tools.
- Tool names are registered and validated through `ChatToolRegistry`.
- Mutating tools call services, not repositories or raw SQL.
- Supervisor-only tools remain gated by Avatar authorization.
- Normal-agent tools remain current-context scoped.
- Destructive/high-impact tools require confirmation where appropriate.
- Tool response records are compact JSON, bounded, and testable.

Docs/spec criteria:

- Update `.internal-dev/specifications/web.md`, `simplypages.md`, `architecture.md`, `service-graph.md`, `services.md`, `api.md`, `decisions.md`, and deferred/horizon files where the accepted scope changes.
- Update end-user docs for dashboard widgets and planner/calendar/project behavior.
- Update technical docs for widget registry, routes, persistence, tools, and validation.
- Update API docs for new or changed routes/payloads.
- Add changelog when implementation completes.

## Negative Criteria

The plan must fail review if it:

- Treats this as an MVP skinning pass.
- Leaves widgets as shallow forms with no detail/settings/tool contract.
- Adds arbitrary scripted widget execution without a trust/sandbox/security design.
- Uses raw HTML strings or JS-heavy client rendering when SimplyPages/HTMX fits.
- Lets dashboards become agent-owned.
- Moves runtime/project/Work Area data into `avatar.sqlite`.
- Creates cross-database foreign keys.
- Lets widgets bypass services and write directly to runtime repositories/files.
- Leaves output widgets unscoped.
- Keeps notes only in DB when agent/project file notes are part of the required workflow.
- Ignores recurrence/reminder ambiguity.
- Omits migration/backfill strategy for current dashboard rows/widgets.
- Omits Playwright visual-quality criteria.

## Validation Expectations For The Plan

Every mutating implementation phase needs:

- Focused service/repository tests for persistence, recurrence, settings, binding, and migration.
- Controller/API tests for new routes, invalid settings, missing entities, status mapping, and fragment rendering.
- Tool tests for registration, authorization, context scoping, limit bounding, mutation behavior, and compact response shape.
- Bounded Spring startup smoke when wiring/schema/tool registration changes.
- Docs/spec review for drift after each phase.

Playwright/browser validation must be delegated to a separate browser-proof agent and reconciled by a validator. It must include screenshots and visual critique, not just route load checks.

Focused browser targets should include:

- `/` with Assistant dashboard normal mode.
- `/dashboards/assistant?edit=true`.
- Dashboard selector switching and create-dashboard modal.
- Widget settings modal with agent/project/Work Area selector states.
- Today Planner detail modal with seeded tasks/routines/calendar data.
- Tasks/Routines detail modal with recurrence, skip/snooze, subtasks, and filters.
- Calendar day/week/month or chosen calendar views.
- Notes widget personal mode and agent/Work Area file-note mode.
- Projects widget with a household project seeded with goals/materials/contacts.
- Agent Status/Queue widget with selected agent and empty/no-agent states.
- Agent Outputs widget with selected-agent and dashboard-wide modes.
- Agent Files/Notes widget or equivalent Work Area-backed widget.
- `/agents` and `/agents/{agentId}` for style consistency.
- Work Area file browser/viewer surfaces when reused.

Viewport minimums:

- Desktop around 1440x900.
- Mobile around 390x844.

Visual pass/fail criteria:

- No stranded columns, oversized empty space, clipped controls, overlapping text, weak hierarchy, or card-inside-card clutter.
- Widgets use bounded bodies with useful summaries and clear "open detail/settings" affordances.
- Modals are scroll-safe on mobile.
- Calendar looks like an actual calendar or agenda, not just a list renamed calendar.
- Agent-bound widgets make selected agent/source obvious.
- File/note/project widgets borrow the good file browser, file viewer, agent dashboard, and chat visual language.
- HTMX interactions refresh only intended fragments and do not duplicate shell/nav roots.

Evidence expectations:

- A canonical validation summary JSON under `artifacts/dashboard-widget-suite/validation-summary.json`.
- Evidence status must not claim `fully_validated` until unit validators, integration validator, startup smoke, and browser proof are reconciled.
- Include commands run, test results, browser artifact paths, residual risks, and any `TOOLING_CONSTRAINT`.
- Perform a stale-reference sweep over docs and `.internal-dev`.

## Open Decisions For The Planning Agent

1. Should reminder automation be accepted now, and if yes, what is the minimum delivery channel?
   - Recommended default: in-dashboard reminders and alert records are in scope; external push/email/PWA channels are separate unless existing notification contracts are ready.

2. Should project household data be file-backed, DB-backed, or hybrid?
   - Recommended default: project identity stays DB-backed; project content schemas are file-backed under project/Work Area roots with service indexing only where needed.

3. Should arbitrary scripted widgets be implemented in this plan?
   - Recommended default: no arbitrary script execution in this plan. Design manifest/schema hooks and first-party extension points only.

4. Should widget definitions use static Java registration or dynamic metadata?
   - Recommended default: Java registry first, dynamic manifest later. Research Spring AI tool callback constraints before promising dynamic agent tools.

5. Should tasks, todos, daily tasks, planner tasks, and routines be unified or remain separate tables?
   - Recommended default: plan a clean domain model for tasks/routines/reminders, then migrate or adapt existing tables deliberately. Do not keep multiple shallow lists forever.

6. Should current old `avatar_*` names be renamed to `dashboard` or `assistant`?
   - Recommended default: avoid broad rename churn during feature delivery unless the planner proves it reduces confusion and keeps route/docs migrations controlled.

## Main-Thread Red-Team Checklist After Planning Agent Returns

Before dispatching implementation, the main thread should reject or send back the plan if:

- It does not name exact target files and route families.
- It lacks a real current-state analysis of existing `AvatarDashboard*`, `AvatarService`, `AvatarRepository`, and tool classes.
- It fails to resolve the widget instance/unique-key schema issue.
- It does not include widget settings and tool contracts.
- It does not research external planner/ADHD patterns enough to justify feature choices.
- It treats calendar as a list.
- It treats project/household data as an afterthought.
- It ignores file-backed agent/project notes.
- It lacks migration/backfill/error handling.
- It lacks exact Playwright scenarios and visual criteria.
- It violates model/reasoning overrides without recording `TOOLING_CONSTRAINT` and stopping.

## Dispatch Instruction Draft

Use this if the user confirms dispatch:

> Launch the advanced planning agent for `/home/hickelpickle/Code/Java/magenta2` using model `gpt-5.5` with reasoning `xhigh`. Produce a large plan suite under `.internal-dev/plans/dashboard-widget-suite/`. Use `.internal-dev/plans/dashboard-widget-suite-preplanning/00-brainstorm-and-handoff.md` as the preplanning handoff. Research current local code, local `.internal-dev` specs/knowledge, SimplyPages docs/demos, official external ADHD/planner app sources, and Spring AI tool registration constraints if needed. Do not implement. Return a complete orchestrated implementation plan with domain work units of 2-3 widgets at a time, worker directives for `gpt-5.5-high`, validator directives for `gpt-5.5-xhigh`, Playwright/browser proof instructions for `gpt-5.5-high` unless unavailable, and a validation matrix with exact API, service, tool, startup, and browser/visual checks. If any required model/reasoning route cannot be selected, stop and report `TOOLING_CONSTRAINT` before substituting.
