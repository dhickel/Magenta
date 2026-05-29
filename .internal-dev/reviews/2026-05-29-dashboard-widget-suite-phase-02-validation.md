# Scope

Phase 02 code validation/red-team for the Magenta dashboard widget suite on branch `feature/dashboard-widget-suite`.

Validated against:

- `.internal-dev/plans/dashboard-widget-suite/worker-directives/phase-02-personal-planning-core.md`
- `.internal-dev/plans/dashboard-widget-suite/00-specification-lock.md`
- `.internal-dev/plans/dashboard-widget-suite/03-target-architecture-and-widget-contract.md`
- `.internal-dev/plans/dashboard-widget-suite/04-data-model-and-migration-design.md`
- `.internal-dev/plans/dashboard-widget-suite/05-tooling-and-agent-access-design.md`
- `.internal-dev/plans/dashboard-widget-suite/06-ui-ux-contract-and-visual-validation-criteria.md`
- `.internal-dev/plans/dashboard-widget-suite/shared/implementation-notes.md`
- `.internal-dev/plans/dashboard-widget-suite/shared/validation-matrix.md`
- Current `artifacts/dashboard-widget-suite/validation-summary.json`
- Repo governance in `AGENTS.md`, `.internal-dev/AGENTS.md`, `.internal-dev/specifications/AGENTS.md`
- Package guides for `avatar`, `api/web`, and `ai/chat/tool`
- Relevant specs: `architecture.md`, `service-graph.md`, `services.md`, `api.md`, `web.md`, `simplypages.md`, `decisions.md`, `deferred-features.md`
- Relevant knowledge: `simplypages-avatar-layout-and-editing.md`, `dashboard-api-contract.md`, `htmx-route-render-contract-validation.md`

Unrelated pre-existing `.gitignore` and `.internal-dev/reviews/2026-05-28-model-alias-internal-review.md` changes were ignored as instructed.

# Findings

## Browser-Findings Repair Revalidation - 2026-05-29

Verdict after scoped browser-findings repair: `PASS_CODE_VALIDATION_BROWSER_RERUN_READY`.

Focused code/criteria revalidation of the four Phase 02 browser findings passed. Browser/Playwright proof remains pending rerun and was not run in this pass.

- `phase-02-today-overdue-unscheduled-hidden`: PASSED_CODE_REVALIDATION. Today Planner now renders bounded `Overdue` and `Unscheduled` sections in the planner body, and controller coverage asserts both seeded sections render.
- `phase-02-quick-capture-no-visible-confirmation`: PASSED_CODE_REVALIDATION. Quick-captured unscheduled work now has a visible refreshed-state path through the `Unscheduled` section, with controller coverage asserting the captured title appears.
- `phase-02-tasks-subtodos-hidden`: PASSED_CODE_REVALIDATION. Tasks/Routines rows now render compact subtodo entries when subtodos exist, with controller coverage for a seeded subtodo.
- `phase-02-calendar-reminder-affordance-missing`: PASSED_CODE_REVALIDATION. Calendar/Schedule now renders a visible HTMX reminder creation form targeting the widget root, with controller coverage for the form and CSS cache bump coverage for `avatar-dashboard.css?v=9`.

## Revalidation Update - 2026-05-29

Verdict after scoped repair: `PASS_CODE_VALIDATION`.

Focused revalidation of the three failed findings passed. Browser/Playwright proof remains pending and was not run in this pass.

- `phase-02-calendar-occurrence-status-overlay`: PASSED. `AvatarService.calendarSchedule(...)` now overlays `PlannerOccurrence` rows by `taskId` plus `occurrenceStart`, uses occurrence status and metadata when present, and `AvatarServiceTest.calendarScheduleOverlaysOccurrenceStatusWithoutChangingParentTask` proves a snoozed occurrence appears in Calendar while the parent task stays `ACTIVE`.
- `phase-02-tasks-routines-filters-ranges`: PASSED. `TasksRoutinesView` carries filter state, `AvatarService.tasksRoutines(status, range, recurrence)` applies server-side status/range/recurrence filters, and the Tasks/Routines detail renderer emits HTMX controls for `status`, `range`, and `recurrence`. Service and controller tests prove the `ACTIVE`/`WEEK`/`RECURRING` filter includes the matching recurring task and excludes a done one-off task.
- `phase-02-today-review-notes-ui`: PASSED. Today Planner now renders a compact `reviewNotes` textarea in the review form, and controller coverage proves submitted notes persist in `PlannerDayMap.reviewNotes`.

## P1 - Calendar/Schedule ignores updated occurrence status

Classification: `code_defect`

Revalidation status: PASSED after scoped repair.

The Phase 02 service contract says the Today/Tasks/Calendar read models merge recurrence occurrence status, and the acceptance criteria require skip/snooze/restart behavior for missed recurring tasks without corrupting parent task state. `AvatarService.updateOccurrence(...)` writes the occurrence status to `avatar_planner_occurrences`, but `calendarSchedule(...)` builds recurrence calendar entries from `plannerCalendarProjection(...)` and uses `projection.status().name()` instead of the corresponding `PlannerOccurrence` row.

Evidence:

- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java:433` updates `PlannerOccurrence` status.
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java:557` iterates `plannerCalendarProjection(...)`.
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java:565` emits the projection status into the calendar entry.

Impact:

After a recurring task occurrence is skipped, snoozed, or restarted, Tasks/Routines can show the occurrence row, but Calendar/Schedule still renders the stale parent/projection status. This breaks the merged calendar read model and can make skipped/snoozed occurrences look active or planned on the calendar.

Expected repair:

Overlay `avatar_planner_occurrences` by `(taskId, occurrenceStart)` when building `CalendarScheduleView` and use occurrence status/metadata where present. Add a service test that updates an occurrence to `SNOOZED` or `SKIPPED` and asserts the calendar entry reflects that status without changing the parent `PlannerTask.status()`.

## P1 - Tasks/Routines does not implement required filters/range controls

Classification: `code_defect`

Revalidation status: PASSED after scoped repair.

The directive requires Tasks/Routines filters, recurrence, ranges, status, and skip/snooze/restart flows. The renderer shows metrics, a create form with priority and recurrence mode, and a limited task list, but no filter controls, range selectors, or status filtering. The docs now claim filters exist, which is not supported by the rendered UI.

Evidence:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1071` starts the Tasks/Routines renderer.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1080` renders only the create form.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1094` renders the bounded list.
- `docs/technical/avatar-planner-organizer.md:25` claims Tasks/Routines exposes filters.

Impact:

The widget cannot satisfy the required Tasks/Routines workflow from the dashboard/detail surface. This is not a browser-only uncertainty; the server-rendered markup has no filter/range controls or routes.

Expected repair:

Add HTMX-first filter/range/status controls for Tasks/Routines detail at minimum, with service/controller support or explicit server-side filtering parameters. Add controller/rendering tests that assert the controls and a representative filtered result.

## P2 - Today Planner daily review control cannot submit useful review notes

Classification: `code_defect`

Revalidation status: PASSED after scoped repair.

The directive requires daily review behavior. `reviewTodayPlanner(...)` accepts `reviewNotes`, but the Today Planner renderer only emits a button posting to `/_dashboards/_today/review`; it does not render an input/textarea named `reviewNotes`, so the UI can only stamp `reviewedAt` with null notes.

Evidence:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:671` accepts optional `reviewNotes`.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1063` renders the Review button.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1065` posts the review action without any review-notes form field.

Impact:

The Today Planner has restart and quick capture, but the review path is not useful for capturing an actual daily review from the UI.

Expected repair:

Render a compact HTMX review form in the Today Planner detail surface or summary/detail body with a `reviewNotes` field. Add controller/rendering coverage that review notes submitted from the rendered form persist in the day map.

# Risk Assessment

Schema additions are additive and stay in `avatar.sqlite`; no cross-database foreign keys were introduced. Tool methods call `AvatarService` and use Avatar supervisor authorization checks. I did not find external notification delivery or new automatic assignment creation in the Phase 02 planner paths.

The scoped repairs close the prior code-level defects and the subsequent browser-finding code gaps, with targeted regression coverage. Remaining risk is browser/UI quality: the repaired overdue/unscheduled sections, subtodo rows, and reminder form still require delegated Playwright rerun for visual quality, mobile scrolling, HTMX swap behavior, and interaction ergonomics.

# Criterion Table

| Criterion | Result | Evidence |
| --- | --- | --- |
| Calendar has real calendar/agenda structure, not only renamed list | PASS_CODE | `AvatarDashboardComponents.calendarMonthGrid(...)` renders a 42-cell month grid with `data-calendar-structure="month"` and an agenda list. Browser visual proof still required. |
| Due date, scheduled time block, reminder, and recurrence are distinct in schema/services/tools/UI labels | PASS_CODE | Schema/service/tool concepts are distinct, UI labels show entry kinds, and Calendar now overlays occurrence status metadata. |
| Missed recurring tasks can be skipped/snoozed/restarted without corrupting parent task state | PASS_CODE | Regression test proves snoozed occurrence status appears in Calendar while parent task remains `ACTIVE`. |
| Today Planner summary/detail/settings provide useful now/next/later/top priorities/time-block/quick capture/review/restart behavior | PASS_CODE_BROWSER_RERUN_READY | Now/next/later, overdue, unscheduled, quick capture, time blocks, restart, and review notes are rendered/tested. Browser rerun still required for usability. |
| Tasks/Routines support filters, recurrence/ranges/status, subtodos, and skip/snooze/restart flows | PASS_CODE_BROWSER_RERUN_READY | HTMX status/range/recurrence controls render, service filters apply, subtodo rows render, and occurrence actions remain present. Browser rerun still required. |
| Tool names are registered, authorized consistently, tested, and do not bypass boundaries | PASS_CODE | `AvatarToolsTest` and `ChatToolRegistryTest` passed; tool service uses `requireAvatarSupervisor(...)` and delegates to `AvatarService`. |
| No external notifications and no automatic assignment creation slipped in | PASS_CODE | Planner/reminder paths store dashboard records only; no new planner-driven assignment creation found. Existing explicit `avatar_submit_*` tools predate this phase. |
| Docs/spec/changelog/evidence accurately describe Phase 02 and do not overclaim browser proof | PASS_CODE | Docs/changelog/evidence now describe filters/review repair and keep browser proof pending. |
| Required tests/startup evidence adequate or rerun | PASS_CHECKS | Focused Maven command rerun passed 52 tests; bounded startup reached Tomcat and exited with timeout 124. |

# Commands And Evidence

- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest,AvatarToolsTest,ChatToolRegistryTest test`
  - Initial validation result: PASS, 48 tests run, 0 failures, 0 errors, 0 skipped.
  - Revalidation result after scoped repair: PASS, 51 tests run, 0 failures, 0 errors, 0 skipped.
  - Browser-findings repair revalidation result: PASS, 52 tests run, 0 failures, 0 errors, 0 skipped.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - Initial validation result: PASS startup smoke; Tomcat started on random port `38457`, application started in `3.127` seconds, timeout exited with code `124` after graceful shutdown.
  - Revalidation result after scoped repair: PASS startup smoke; Tomcat started on random port `39459`, application started in `3.131` seconds, timeout exited with code `124` after graceful shutdown.
  - Browser-findings repair revalidation result: PASS startup smoke; Tomcat started on random port `41719`, application started in `3.156` seconds, timeout exited with code `124` after graceful shutdown.
- `jq . artifacts/dashboard-widget-suite/validation-summary.json >/dev/null`
  - Result: PASS before and after browser-findings repair revalidation artifact update.
- `git diff --check`
  - Result: PASS before and after browser-findings repair revalidation artifact update.

# Browser Checklist Required Next

Code revalidation passed after browser-findings repair. Browser/Playwright proof rerun is now the next required gate.

Delegated browser proof should use app-live Playwright at desktop `1440x900` and mobile `390x844` with seeded planner data:

- `/` normal dashboard shows Today Planner, Tasks/Routines, and Calendar/Schedule summaries with one shell/nav and no duplicate roots.
- Open Today Planner detail; verify top priorities, now/next/later, overdue, unscheduled, time blocks, quick capture, restart, and daily review notes are usable.
- Submit quick capture and confirm the Today Planner widget refreshes without duplicating `#avatar-edit-container` or shell roots.
- Open Tasks/Routines detail; exercise filters/range/status controls, recurrence display, subtasks, project links, and skip/snooze/restart occurrence actions.
- After skip/snooze/restart, confirm parent task state is unchanged and both Tasks/Routines and Calendar/Schedule reflect occurrence status.
- Open Calendar/Schedule; verify a real month grid plus agenda, and visually distinct calendar events, task due dates, time blocks, reminders, and recurrence projections.
- Create a time block and in-dashboard reminder through HTMX and verify fragment refresh boundaries.
- Mobile: verify modal scrolling, no horizontal overflow, controls wrap/truncate sanely, and action targets remain visible.
- Visual critique must compare `/`, `/manage`, and `/agents` for dense operational styling, spacing, hierarchy, chips, row/list surfaces, modal bounds, and absence of nested-card clutter.

# Recommendations

Proceed to delegated Phase 02 browser proof rerun using the checklist above. Do not mark Phase 02 fully validated until that browser evidence is reconciled.

# Follow-ups

Browser proof rerun remains pending and must not be marked complete until delegated Playwright evidence is reconciled.
