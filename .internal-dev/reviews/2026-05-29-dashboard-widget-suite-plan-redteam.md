---
schema_version: 1
document_type: review
status: complete
created: 2026-05-29
review_type: non-mutating planning red-team
verdict: PASS_FOR_IMPLEMENTATION
---

# Dashboard Widget Suite Plan Red-Team Review

## Scope

Reviewed the completed planning artifact suite only. No product code was changed.

Primary artifacts reviewed:

- `.internal-dev/plans/dashboard-widget-suite-preplanning/00-brainstorm-and-handoff.md`
- `.internal-dev/plans/dashboard-widget-suite/`
- `.internal-dev/plans/dashboard-widget-suite/final-orchestration-plan.md`
- `.internal-dev/plans/dashboard-widget-suite/shared/validation-matrix.md`
- `.internal-dev/plans/dashboard-widget-suite/worker-directives/`

Required repo context checked:

- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
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
- `.internal-dev/knowledge/entity-selector-htmx-pattern.md`
- package guides under `api/web`, `avatar`, `ai/chat/tool`, and `ai/chat/tool/orchestration`
- observed code/schema anchors in `AvatarDashboardController`, `AvatarDashboardComponents`, `AvatarService`, `AvatarRepository`, `avatar-schema.sql`, Avatar tool classes, agent operational tool classes, and workspace/project/output service files.

## Findings

Verdict: `PASS_FOR_IMPLEMENTATION`

### Blocking Findings

None.

### Non-Blocking Findings And Risks

1. Phase numbering drift exists in one architecture planning file.

   Evidence: `.internal-dev/plans/dashboard-widget-suite/03-target-architecture-and-widget-contract.md` lists Today/Tasks/Calendar as Phase 03, Notes/Projects as Phase 04, Agent Ops as Phase 05, and Habits/Reminders/Context as Phase 06. The final orchestration plan and worker directives correctly define those as Phase 02, Phase 03, Phase 04, and Phase 05 respectively.

   Risk: low. The executable dispatch plan and directives are internally consistent, but the stale numbering can create worker confusion when agents cite architecture notes.

   Recommended correction before dispatch if the planner is resumed: update that section to remove phase numbers or align it to the final phase sequence.

2. Model policy should explicitly state the user-specific override relationship to repo testing defaults.

   Evidence: the validation matrix requires implementation workers `gpt-5.5` high, code validators `gpt-5.5` xhigh, and browser proof `gpt-5.5` high/xhigh if selectable. The user request requires implementation readiness for `gpt-5.5-high` workers and `gpt-5.5-xhigh` validators. The repo `AGENTS.md` text supplied in the prompt has a default testing/Playwright model policy of `gpt-5.3-codex` medium.

   Risk: low-to-medium operational routing ambiguity, not a product-plan defect. The plan correctly follows the user's explicit model readiness request and includes `TOOLING_CONSTRAINT` stop behavior. A one-line note that this suite intentionally uses the user's explicit model override would reduce dispatch friction.

3. Feature-phase directives could be made more mechanical by naming the expected new route families per phase.

   Evidence: Phase 01 names concrete files/routes and migration behavior. Phases 02-05 are correctly scoped to coherent 2-3 widget groups, but their editable targets are necessarily broader, such as dashboard widget renderers/routes under `avatar/dashboard` and `api/web`. The shared architecture document supplies the instance-id route contract, but per-phase route lists are not repeated in each directive.

   Risk: low. The shared route contract and validation matrix are strong enough for implementation, but repeating expected summary/detail/settings route families in each feature directive would reduce interpretation churn.

## Criteria Evaluation

| # | Criterion | Result | Evidence |
| --- | --- | --- | --- |
| 1 | Phase 01 removes/replaces `unique(dashboard_id, widget_key)` and supports multi-instance widgets without breaking compatibility. | PASS | Plan explicitly replaces global uniqueness with widget type/instance semantics and single-instance-only constraints. Current code/schema limitation was correctly identified in `avatar-schema.sql`, `AvatarRepository.addDashboardWidget`, and static catalog logic. |
| 2 | Registry covers settings schema, binding schema, refresh behavior, empty/error/loading states, renderer contract, tool descriptors, authorization, and migrations. | PASS | Registry contract includes settings schema, binding mode, renderers, refresh, empty-state, tool descriptors, authorization metadata, fixtures, and migration design. Loading-state language is implicit through refresh/empty/error and should be watched during implementation, but not blocking. |
| 3 | Dashboards are agent-agnostic while widgets bind to agents/projects/Work Areas/files with clear UI/settings/data boundaries. | PASS | Specs and plan consistently preserve dashboard layout ownership and per-widget binding. Settings and UI criteria require binding chips, selectors, missing-binding recovery, and no dashboard ownership confusion. |
| 4 | Tool definitions make practical sense, distinguish user vs agent-bound widgets, and avoid dynamic Spring AI registration unless accepted. | PASS | Tool design uses static `@Tool` classes plus registry descriptors, validates exact names through `ChatToolRegistry`, preserves supervisor-gated `avatar_*` tools, and forbids arbitrary-agent-id normal tools. |
| 5 | Notes, todos/tasks, schedules/calendar, projects/household materials, routines, reminders, contacts/materials, trackers, outputs/files/agent ops are real product widgets. | PASS | The suite covers Today Planner, Tasks/Routines, Calendar/Schedule, Notes, Projects, Contacts/Materials, Habits/Trackers, Reminders/Alerts, Agent Status/Queue, Agent Outputs, Agent Files/Notes, and Dashboard Context. UI criteria require rich summaries/detail/settings rather than placeholder lists. |
| 6 | File-backed artifacts and schemas are handled without pushing everything into DB, while QOL widgets stay service-backed. | PASS | Personal organizer state remains Avatar/service-backed; project materials/goals/contacts/blockers/next-actions use typed files under project/Work Area roots with service adapters and optional indexes. |
| 7 | Workspace/output/project/Work Area path confinement and architecture contracts are preserved. | PASS | Plan forbids bypassing Work Area/file/output/project services, prohibits cross-database FKs, keeps runtime state in existing services, and requires confinement tests. |
| 8 | UI expectations are concrete enough for consistency with agent dashboard, file browser/viewer, chat endpoint, and SimplyPages HTMX-first patterns. | PASS | UI contract references `/`, `/dashboard`, `/agents`, Work Area explorer/viewer, SimplyPages editing demo, compact operational style, HTMX-first fragments, modal safety, mobile checks, and visual failure examples. |
| 9 | Worker directives are scoped to 2-3-ish widgets/coherent phases with exact target files/modules/routes/tests and no broad handwaving. | PASS WITH RISK | Phase grouping is coherent and test targets are named. Phase 01 is exact. Later phases rely more on shared route contracts and broad module paths; this is acceptable for implementation but should be tightened if planner is resumed. |
| 10 | Validation criteria cover API, persistence/migration, tool contracts, startup smoke, docs/spec updates, and Playwright visual-quality scenarios. | PASS | Validation matrix includes focused Maven commands, startup smoke, phase-specific validator checks, evidence index rules, browser requirements, final `mvn test`, docs/spec/changelog/archive, stale-reference sweep, and visual critique. |
| 11 | Model/reasoning requirements and `TOOLING_CONSTRAINT` stop behavior are captured accurately. | PASS WITH RISK | User-requested `gpt-5.5` implementation/validation posture and stop-before-substitution behavior are captured. Add explicit override wording against repo default testing model policy to reduce dispatch ambiguity. |
| 12 | Open decisions are gated instead of hidden as assumptions. | PASS | External notifications, third-party calendar library, dynamic Spring AI tool registration, route renames, widget scripting/plugin execution, and destructive planner migration are explicitly gated or out of scope. |

## Risk Assessment

Implementation risk is high because this is a broad product suite touching persistence, UI, tools, and multiple service boundaries. The plan reduces that risk with a correct foundation-first sequence, clear database ownership, explicit widget registry/instance contracts, and strong browser-validation gates.

The main residual risk is execution discipline: later workers must not treat the feature directives as permission for broad rewrites of Avatar, Work Area, project, or tool services. Validators should enforce the service-boundary and visual-quality checks exactly as written.

## Positive Coverage Notes

- The plan correctly identifies the actual current limitation: `user_dashboard_widgets` has `unique(dashboard_id, widget_key)`, `AvatarRepository` checks `findDashboardWidgetByKey`, and `AvatarDashboardComponents` uses static `WIDGETS` plus `widgetBody(...)`.
- The migration design is compatible with SQLite constraints by creating/copying into a new table instead of relying on impossible in-place constraint drops.
- The tool design matches observed code: current tool registration is static `MethodToolCallbackProvider`, and exact tool names are already validated through `ChatToolRegistry`.
- The plan preserves the split between `avatar.sqlite` for Avatar/user organizer state and `magenta.sqlite` runtime/project/Work Area/output ownership.
- The Playwright criteria require visual critique and mobile/desktop proof, not just route loads.

## Recommendations

Proceed to implementation dispatch after the main thread creates the dedicated `dashboard-widget-suite` branch.

Exact next orchestration step recommended:

1. Create a dedicated git branch for `dashboard-widget-suite`.
2. Dispatch Phase 01 Platform Foundation to a `gpt-5.5-high` implementation worker using `.internal-dev/plans/dashboard-widget-suite/worker-directives/phase-01-platform-foundation.md`.
3. After Phase 01 implementation, dispatch a fresh `gpt-5.5-xhigh` code validator, then delegated Playwright proof, and reconcile evidence before Phase 02.

## Follow-ups

- Optional planner cleanup before dispatch: fix the stale phase numbers in `03-target-architecture-and-widget-contract.md`.
- Optional planner cleanup before dispatch: add a sentence to the validation matrix that `gpt-5.5` worker/validator/browser routing is a user-specific override for this suite, and fallback still requires `TOOLING_CONSTRAINT` plus approval.
- Optional directive cleanup: repeat the instance-id summary/detail/settings route family in each feature directive so workers do not need to cross-reference the shared architecture file for route shape.

## Checks Run

No automated product tests were run because this was a planning-artifact review only.

Inspection commands included:

- `rg --files .internal-dev/plans/dashboard-widget-suite .internal-dev/plans/dashboard-widget-suite-preplanning .internal-dev`
- `sed -n` reads of all primary planning artifacts, worker directives, shared implementation notes, evidence contract, validation matrix, and final plan.
- `find .internal-dev/knowledge -maxdepth 1 -type f` followed by targeted reads of dashboard, Avatar, Work Area, SimplyPages, and entity-selector knowledge.
- `sed -n` reads of relevant specification files and package `AGENTS.md` guides.
- `rg -n` checks against `AvatarDashboardController`, `AvatarDashboardComponents`, `AvatarService`, `AvatarRepository`, `avatar-schema.sql`, tool classes, and workspace/project/output service anchors.
