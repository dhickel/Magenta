# Scope

Delegated Phase 02 browser/Playwright proof for the Magenta dashboard widget suite on branch `feature/dashboard-widget-suite`.

Validated against:

- `.internal-dev/reviews/2026-05-29-dashboard-widget-suite-phase-02-validation.md`
- `artifacts/dashboard-widget-suite/validation-summary.json`
- `.internal-dev/plans/dashboard-widget-suite/worker-directives/phase-02-personal-planning-core.md`
- `.internal-dev/plans/dashboard-widget-suite/06-ui-ux-contract-and-visual-validation-criteria.md`
- Repo browser validation policy in `AGENTS.md`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/dashboard-api-contract.md`
- `.internal-dev/knowledge/htmx-route-render-contract-validation.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`

Runtime:

- Command: `mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --magenta.root.path=/tmp/magenta2-dashboard-widget-suite-phase-02-browser --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-dashboard-widget-suite-phase-02-browser/magenta.sqlite?foreign_keys=true --magenta.orchestration.runner-delay-ms=60000 --magenta.orchestration.scheduler-delay-ms=60000 --magenta.orchestration.assignment-history-purge-delay-ms=60000'`
- Port: `18080`
- Isolated root: `/tmp/magenta2-dashboard-widget-suite-phase-02-browser`
- Browser evidence directory: `artifacts/dashboard-widget-suite/phase-02-browser/`

# Browser Rerun Update - 2026-05-29

Verdict after targeted Phase 02 repairs: `PASS_BROWSER_PROOF`.

Rerun runtime:

- Command: `mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --magenta.root.path=/tmp/magenta2-dashboard-widget-suite-phase-02-browser-rerun --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-dashboard-widget-suite-phase-02-browser-rerun/magenta.sqlite?foreign_keys=true --magenta.orchestration.runner-delay-ms=60000 --magenta.orchestration.scheduler-delay-ms=60000 --magenta.orchestration.assignment-history-purge-delay-ms=60000'`
- Port: `18080`
- Isolated root: `/tmp/magenta2-dashboard-widget-suite-phase-02-browser-rerun`
- Browser evidence directory: `artifacts/dashboard-widget-suite/phase-02-browser-rerun/`

Rerun findings:

- No open browser product findings remain for the Phase 02 rerun scope.
- `browser-proof-results.json` reports `PASS_BROWSER_PROOF` with 21 passed checks and 0 failed checks.
- `console-messages.txt` and `network-requests.txt` are empty; no browser console messages or HTTP `>=400` responses were captured.
- The previous failed Phase 02 browser artifacts under `artifacts/dashboard-widget-suite/phase-02-browser/` remain as historical failure evidence and are superseded by `artifacts/dashboard-widget-suite/phase-02-browser-rerun/`.

Resolved prior findings:

- `phase-02-today-overdue-unscheduled-hidden`: PASSED_BROWSER_RERUN. Today Planner detail now shows `Top priorities`, `Now`, `Next`, `Later`, `Overdue`, and `Unscheduled`, with quick capture, restart, review notes, and time block content visible.
- `phase-02-quick-capture-no-visible-confirmation`: PASSED_BROWSER_RERUN. A quick-captured item remained visible after the Today Planner HTMX refresh, with one shell/nav, one `#dashboard-home`, one `#avatar-edit-container`, and no duplicate ids.
- `phase-02-tasks-subtodos-hidden`: PASSED_BROWSER_RERUN. Tasks/Routines detail shows status/range/recurrence filters, recurrence metadata, occurrence actions, linked project text, and seeded subtodo text.
- `phase-02-calendar-reminder-affordance-missing`: PASSED_BROWSER_RERUN. Calendar/Schedule detail shows both time-block and reminder HTMX forms, and submitting each refreshed the widget root without duplicate ids.

Rerun criterion table:

| Criterion | Result | Evidence |
| --- | --- | --- |
| `/` normal dashboard shows Today Planner, Tasks/Routines, Calendar/Schedule with one shell/nav and no duplicate roots | PASS | `desktop_normal_dashboard_widgets_and_single_shell`; screenshot `phase-02-browser-rerun/desktop-home-seeded.png`. |
| Today Planner detail exposes top priorities, now/next/later, overdue, unscheduled, time blocks, quick capture, restart, review notes | PASS | `today_detail_core_controls`, `today_detail_overdue_and_unscheduled_are_usable`; screenshot `desktop-today-detail.png`. |
| Quick capture refreshes Today Planner without duplicate roots/containers and visibly shows captured item | PASS | `quick_capture_refresh_boundaries`; screenshot `desktop-after-quick-capture.png`. |
| Review notes submit and remain visible | PASS | `today_review_notes_visible_after_submit`; screenshot `desktop-today-review-notes-persisted.png`. |
| Tasks/Routines filters/ranges/recurrence, occurrence actions, project link, and subtodos | PASS | `tasks_detail_filters_and_recurrence_controls`, `tasks_detail_project_and_subtodo_placeholders`, `tasks_detail_occurrence_actions_visible`, `tasks_filters_apply_server_fragment`; screenshots `desktop-tasks-detail.png`, `desktop-tasks-filtered-recurring-week.png`. |
| Skip/snooze/restart leaves parent task unchanged and Calendar/Schedule reflects occurrence status | PASS | `tasks_occurrence_status_visible_and_parent_unchanged`, `calendar_reflects_occurrence_status_and_reminder`; screenshots `desktop-tasks-after-occurrence-action.png`, `desktop-calendar-after-reminder-form.png`. |
| Calendar/Schedule real month grid plus agenda and distinct event/task/timeblock/reminder/recurrence data | PASS | `calendar_grid_and_agenda_structure`, `calendar_distinct_item_kinds`; screenshot `desktop-calendar-detail-before-actions.png`. |
| Create time block and reminder through HTMX and verify widget-root refresh boundaries | PASS | `timeblock_htmx_refresh_boundaries`, `reminder_htmx_form_refresh_boundaries`; screenshots `desktop-after-timeblock-submit.png`, `desktop-calendar-after-reminder-form.png`. |
| Mobile modal scrolling/no horizontal overflow/visible controls | PASS | `mobile_today_modal_no_horizontal_overflow`, `mobile_tasks_modal_controls_stay_in_view`, `mobile_calendar_modal_no_horizontal_overflow`; screenshots `mobile-today-detail.png`, `mobile-tasks-detail.png`, `mobile-calendar-detail.png`. |
| Visual critique against `/`, `/manage`, `/agents` | PASS_WITH_NOTES | Reference screenshots `reference-manage-desktop.png`, `reference-agents-desktop.png`; planner widgets match the dense low-radius operational style. Calendar remains dense on mobile and should be watched for touch ergonomics, but no blocking overflow or hidden controls were observed. |

Rerun commands and evidence:

- `mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --magenta.root.path=/tmp/magenta2-dashboard-widget-suite-phase-02-browser-rerun --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-dashboard-widget-suite-phase-02-browser-rerun/magenta.sqlite?foreign_keys=true --magenta.orchestration.runner-delay-ms=60000 --magenta.orchestration.scheduler-delay-ms=60000 --magenta.orchestration.assignment-history-purge-delay-ms=60000'`
  - Result: PASS runtime startup; Tomcat started on port `18080`.
- `BASE_URL=http://localhost:18080 node artifacts/dashboard-widget-suite/phase-02-browser-rerun/browser-proof-rerun.mjs`
  - Result: PASS_BROWSER_PROOF; wrote `artifacts/dashboard-widget-suite/phase-02-browser-rerun/browser-proof-results.json`.
- `artifacts/dashboard-widget-suite/phase-02-browser-rerun/console-messages.txt`
  - Result: empty.
- `artifacts/dashboard-widget-suite/phase-02-browser-rerun/network-requests.txt`
  - Result: empty.

# Findings

The findings below are the original failed browser proof findings from `artifacts/dashboard-widget-suite/phase-02-browser/`. They are retained as historical failure evidence and superseded by the passing rerun update above.

## P1 - Today Planner detail does not expose overdue and unscheduled work as usable sections

Classification: `code_defect`

Browser proof verdict: FAILED.

The Phase 02 browser checklist requires Today Planner detail to make top priorities, now/next/later, overdue, unscheduled, time blocks, quick capture, restart, and review notes usable. The detail modal renders top priorities, now, next, later, quick capture, restart, and review notes, but overdue appears only as a metric and unscheduled is not rendered.

Evidence:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1033` renders metrics, including `Overdue`.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1044` through `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1047` render only `Top priorities`, `Now`, `Next`, and `Later` phase lists.
- Browser artifact `artifacts/dashboard-widget-suite/phase-02-browser/desktop-today-detail.png` shows no overdue or unscheduled detail section.
- `browser-proof-results.json` check `today_detail_overdue_and_unscheduled_are_usable` failed: sections were `Top priorities,Now,Next,Later`; modal text contained `Overdue=true`, `Unscheduled=false`.

Impact:

Seeded overdue and unscheduled planning data cannot be worked from the detail modal. This also makes quick-captured unscheduled items hard to confirm visually.

Expected repair:

Render compact overdue and unscheduled sections in Today Planner detail/summary body, with bounded rows matching the existing phase-list style. Quick capture should make the newly captured item visible or otherwise provide a clear refreshed state.

## P1 - In-dashboard reminder creation is not exposed in Calendar/Schedule

Classification: `code_defect`

Browser proof verdict: FAILED.

The required scenario says to create a time block and an in-dashboard reminder through HTMX. Calendar/Schedule exposes a time-block HTMX form, but no reminder form or visible reminder creation affordance. The route exists and returns a calendar widget fragment when posted with an HTMX-style request, but the user cannot discover or submit it from the dashboard.

Evidence:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1178` through `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1184` render only the time-block form.
- Browser artifact `artifacts/dashboard-widget-suite/phase-02-browser/desktop-calendar-detail-before-actions.png` shows the month grid, agenda, and time-block form but no reminder form.
- `browser-proof-results.json` check `calendar_timeblock_and_reminder_htmx_affordances` failed: `time block form=true; reminder form=false`.
- Route-level probe `reminder_htmx_route_returns_calendar_fragment` passed, so this is a missing UI affordance rather than a backend route absence.

Impact:

Calendar/Schedule cannot satisfy the visible in-dashboard reminder workflow even though reminder records can be created through the server route.

Expected repair:

Add a compact HTMX reminder form or action in Calendar/Schedule detail, targeting the Calendar/Schedule widget root and preserving the single modal/shell constraints.

## P2 - Tasks/Routines hides seeded subtodos in detail

Classification: `code_defect`

Browser proof verdict: FAILED.

The Phase 02 scenario asks Tasks/Routines detail to exercise subtasks/project-link placeholders if present. The seeded linked project appears, but the seeded subtodo does not render in the Tasks/Routines detail row.

Evidence:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1104` through `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1113` render task title/meta, project link text, and occurrence actions, but not `safe.subtodos()`.
- Browser artifact `artifacts/dashboard-widget-suite/phase-02-browser/desktop-tasks-detail.png` shows the project link but no subtodo text.
- `browser-proof-results.json` check `tasks_detail_project_and_subtodo_placeholders` failed: `project link visible=true; subtodo visible=false`.

Impact:

Tasks/Routines can show recurrence and project linkage, but subtasks are not visible from the detail surface after being added.

Expected repair:

Render a compact subtodo count/list/placeholder per task when subtodos exist, using existing row/list density.

## P2 - Quick capture refresh does not produce a visible captured item in Today Planner

Classification: `code_defect`

Browser proof verdict: FAILED.

Quick capture submitted successfully and the widget refresh boundary remained clean, but the captured unscheduled item was not visible in the refreshed Today Planner widget. This appears to be a presentation gap tied to the missing unscheduled section.

Evidence:

- `browser-proof-results.json` check `quick_capture_refresh_boundaries` failed: `quick visible=false; editContainers=1; duplicates=none`.
- Browser artifact `artifacts/dashboard-widget-suite/phase-02-browser/desktop-after-quick-capture.png` shows the refreshed dashboard without duplicate shell roots or duplicate ids.

Impact:

The user receives no clear visible confirmation that quick capture added the item from the planner UI.

Expected repair:

After quick capture, display the captured item in an unscheduled/new-capture section or show a compact success state tied to the refreshed widget.

# Risk Assessment

No browser/runtime tooling constraint occurred. Playwright ran against the live app on port `18080` with isolated runtime data and produced screenshots, console/network logs, and JSON evidence.

The UI is otherwise directionally aligned with the dense operational visual contract: low-radius panels, compact controls, clear grid/agenda surfaces, no hero composition, and no duplicate shell roots. Mobile screenshots did not show horizontal overflow in the checked planner modals.

# Criterion Table

| Criterion | Result | Evidence |
| --- | --- | --- |
| `/` normal dashboard shows Today Planner, Tasks/Routines, Calendar/Schedule with one shell/nav and no duplicate roots | PASS | `desktop_normal_dashboard_widgets_and_single_shell` passed; screenshot `desktop-home-seeded.png`. |
| Today Planner detail exposes top priorities, now/next/later, overdue, unscheduled, time blocks, quick capture, restart, review notes | FAIL | Core controls passed, but overdue/unscheduled failed; screenshot `desktop-today-detail.png`. |
| Quick capture refreshes Today Planner without duplicate roots/containers | FAIL | No duplicate roots/containers, but captured item not visible after refresh. |
| Review notes submit and remain visible | PASS | `today_review_notes_visible_after_submit` passed; screenshot `desktop-today-review-notes-persisted.png`. |
| Tasks/Routines filters/ranges/recurrence and occurrence actions work | PASS | Filter and occurrence checks passed; screenshots `desktop-tasks-detail.png`, `desktop-tasks-filtered-recurring-week.png`, `desktop-tasks-after-occurrence-action.png`. |
| Tasks/Routines subtodo/project placeholders if present | FAIL | Project link visible; subtodo hidden. |
| Skip/snooze/restart leaves parent task unchanged and Calendar/Schedule reflects occurrence status | PASS | Parent meta stayed `planned`; Calendar showed `SKIPPED`. |
| Calendar/Schedule real month grid plus agenda and distinct event/task/timeblock/reminder/recurrence data | PASS | 42 cells plus agenda; seeded kinds visible in agenda; screenshot `desktop-calendar-detail-before-actions.png`. |
| Create time block and reminder through HTMX | FAIL | Time block form passed; reminder route works but no visible reminder form/affordance. |
| Mobile modal scrolling/no horizontal overflow/visible controls | PASS | Mobile Today, Tasks, and Calendar checks passed; screenshots `mobile-today-detail.png`, `mobile-tasks-detail.png`, `mobile-calendar-detail.png`. |
| Visual critique against `/`, `/manage`, `/agents` | PASS_WITH_NOTES | Reference screenshots captured; planner widgets match dense operational styling, with the gaps listed above. |

# Commands And Evidence

- `mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --magenta.root.path=/tmp/magenta2-dashboard-widget-suite-phase-02-browser --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-dashboard-widget-suite-phase-02-browser/magenta.sqlite?foreign_keys=true --magenta.orchestration.runner-delay-ms=60000 --magenta.orchestration.scheduler-delay-ms=60000 --magenta.orchestration.assignment-history-purge-delay-ms=60000'`
  - Result: PASS runtime startup; Tomcat started on port `18080`.
- `node artifacts/dashboard-widget-suite/phase-02-browser/browser-proof.mjs`
  - Result: FAILED_PRODUCT_DEFECTS; wrote `artifacts/dashboard-widget-suite/phase-02-browser/browser-proof-results.json`.
- `artifacts/dashboard-widget-suite/phase-02-browser/console-messages.txt`
  - Result: no console messages captured.
- `artifacts/dashboard-widget-suite/phase-02-browser/network-requests.txt`
  - Result: no HTTP `>=400` responses captured.

Screenshots:

- `desktop-home-seeded.png`
- `desktop-today-detail.png`
- `desktop-after-quick-capture.png`
- `desktop-today-review-notes-persisted.png`
- `desktop-tasks-detail.png`
- `desktop-tasks-filtered-recurring-week.png`
- `desktop-tasks-after-occurrence-action.png`
- `desktop-calendar-detail-before-actions.png`
- `desktop-after-timeblock-submit.png`
- `desktop-calendar-after-reminder-route.png`
- `reference-manage-desktop.png`
- `reference-agents-desktop.png`
- `mobile-today-detail.png`
- `mobile-tasks-detail.png`
- `mobile-calendar-detail.png`

# Recommendations

Original recommendation before rerun was to return Phase 02 to a scoped repair worker. That repair has now been completed and browser-rerun validated.

Historical remediation handoff:

- Worker model: `gpt-5.3` high unless the parent overrides dashboard-suite remediation model selection.
- Repair Today Planner detail to render overdue and unscheduled sections and make quick-captured unscheduled items visibly confirm after refresh.
- Repair Tasks/Routines detail to render subtodo information when subtodos exist.
- Repair Calendar/Schedule detail to expose an HTMX reminder creation affordance alongside the time-block workflow.
- Keep changes HTMX-first and preserve one shared `#avatar-edit-container`, one shell root, and no duplicate ids.
- Rerun focused controller/service tests plus this browser proof script against a clean isolated root.

# Follow-ups

Phase 02 browser rerun verdict: `PASS_BROWSER_PROOF`.

Later dashboard-widget-suite phases and final integration validation remain pending, so this does not claim full suite validation.
