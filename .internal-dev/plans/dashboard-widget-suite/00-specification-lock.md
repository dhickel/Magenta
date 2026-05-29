---
schema_version: 1
document_type: specification-lock
status: planning
owner: advanced-planner
created: 2026-05-29
work_classification: large
source_handoff: .internal-dev/plans/dashboard-widget-suite-preplanning/00-brainstorm-and-handoff.md
---

# Dashboard Widget Suite Specification Lock

## Objective

Build a first-party dashboard widget suite for the Home/Assistant dashboard as a major end-user feature set. The result must turn `/` into a useful personal command center with fast capture, day planning, calendar/schedule planning, tasks/routines, notes, projects, habits/progress, reminders/alerts, and agent-bound operational widgets. This plan does not implement product code.

## Acceptance Criteria

- Dashboards remain agent-agnostic containers. Widgets may bind to agents, projects, Work Areas, jobs, outputs, or personal data through per-instance settings.
- Widget definitions are centralized in a Java registry with metadata for category, default/supported widths, single vs multi-instance policy, binding mode, settings schema/defaults, summary/detail/settings renderers, refresh policy, empty states, and declared tool contracts.
- Widget instances persist separately from widget type. Multiple instances of the same type are supported when the registry allows it.
- The current `unique(dashboard_id, widget_key)` limitation is removed or replaced with constraints that enforce only selected single-instance widget types.
- Widget settings persist, validate missing/deleted bound entities, and render clear empty/error states instead of failing fragments.
- Every proper widget has three layers: compact dashboard summary, rich detail modal/drawer, and settings modal.
- First-party organizer state that requires transactional planner behavior lives in `avatar.sqlite`. Runtime, agent, project, Work Area, job, schedule, output, and assignment state remains in existing Magenta services and `magenta.sqlite`.
- No cross-database foreign keys are introduced between `avatar.sqlite` and `magenta.sqlite`.
- Agent-accessible widgets declare exact read/mutation tool names. Tool names are validated through `ChatToolRegistry`; mutations call services, not repositories or raw SQL.
- `avatar_*` tools remain supervisor-gated. Normal `agent_*` tools remain current-context scoped and must not accept arbitrary agent ids.
- UI remains dense operational tooling aligned with `/manage`, `/agents`, and existing Avatar dashboard patterns. CRUD and fragment changes are HTMX-first.
- Arbitrary scripted/custom widget execution is out of this implementation plan. Manifest/schema hooks may be designed, but user plugin/scripting execution requires a separate trust/sandbox/security research phase.
- Implementation updates affected specs, docs, changelog, and archives finalized plan/bug artifacts per repo workflow.

## Validation Criteria

- Repository/service tests cover widget registry, instance persistence, settings validation, binding validation, migration/backfill, recurrence/projection, reminders, project artifact adapters, and tool-facing services.
- Controller/API tests cover summary/detail/settings routes, invalid settings, missing entities, HTMX targets, status mapping, OOB responses, and fragment-only rendering.
- Tool tests cover `ChatToolRegistry` exact names, authorization, context scoping, destructive confirmations, limits, compact JSON response shape, and service mutation behavior.
- Schema/migration tests prove legacy rows/widgets migrate without duplication and multi-instance widgets can coexist.
- Bounded startup smoke runs after wiring/schema/tool registration changes: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`.
- Playwright/browser validation is delegated to a browser-proof agent and reconciled by a code validator. It must include desktop `1440x900` and mobile `390x844` screenshots plus visual critique.
- The canonical evidence index is `artifacts/dashboard-widget-suite/validation-summary.json`; it must not claim `fully_validated` until unit validators, integration validation, startup smoke, and browser proof are reconciled.

## Negative Criteria

- Do not treat this as an MVP skinning pass.
- Do not keep widgets as static `WIDGETS` plus `widgetBody(...)` switches.
- Do not keep single-instance uniqueness for all widget types.
- Do not make dashboards agent-owned.
- Do not move runtime/project/Work Area data into `avatar.sqlite`.
- Do not bypass Work Area/file/output/project services with filesystem shortcuts.
- Do not leave outputs unscoped.
- Do not model calendar as a renamed list.
- Do not rely on raw HTML strings or broad JavaScript where SimplyPages/HTMX patterns fit.
- Do not add arbitrary script execution, plugin runtime, or browser-driven script trust prompts in this plan.

## Constraints

- Follow repo `AGENTS.md`, `.internal-dev/AGENTS.md`, and package guides.
- Read/update `.internal-dev/specifications/` and targeted `.internal-dev/knowledge/` only as needed.
- Controllers stay thin; services own use cases; repositories own persistence.
- Use Java records for request/response/domain carriers where practical.
- Use SimplyPages `Row`/`Column`, reusable components/modules, stable HTMX targets, and OOB swaps before custom markup.
- Use centralized workspace/path/service helpers for Work Area/project/output paths.
- User model constraints: implementation workers `gpt-5.5` high; code validators/red-team `gpt-5.5` xhigh; planning red-team after this output `gpt-5.3-codex` xhigh; Playwright/browser agents `gpt-5.5` high unless xhigh is selectable. If unavailable, record `TOOLING_CONSTRAINT` and stop before substituting.

## Assumptions

- Reminder automation accepted for this suite is limited to in-dashboard reminder records, alerts, snooze/reschedule/complete, and recurrence projection. External push/email/PWA notification delivery is deferred unless a separate notification contract is accepted.
- Project household data is hybrid: project identity/membership/workspace remains DB-backed in runtime services; household/project content such as goals, materials, contacts, measurements, blockers, and next actions is file-backed typed schema under project/Work Area roots, with service-owned indexes only when query performance or UI summary needs require it.
- Widget definitions use a static Java registry first. Dynamic widget manifests are a future extension point, not dynamic Spring AI tool registration.
- Old `avatar_*` names and routes are preserved for compatibility during this suite. Broad naming churn is out of scope unless a worker proves a narrow rename reduces confusion without breaking route/docs compatibility.

## User Decision Gates

- Gate A: Before implementing external notification delivery beyond in-dashboard reminders.
- Gate B: Before adding arbitrary user plugin/script execution or a widget scripting runtime.
- Gate C: Before changing public route families from `/dashboards`/`/_dashboards` compatibility to a new namespace.
- Gate D: Before replacing current planner tables with a destructive migration instead of additive migration/adapters.

## Stop Rules

- Stop and return to planning if workers discover current schema cannot be migrated additively without data loss.
- Stop and consult the user before any unapproved model/reasoning substitution.
- Stop before implementation if the planning red-team rejects the registry/instance/binding/security architecture.
- Stop implementation if real startup or browser validation is blocked by missing local services/secrets; record blocked state instead of signing off.
