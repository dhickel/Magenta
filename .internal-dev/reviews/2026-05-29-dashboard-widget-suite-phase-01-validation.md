---
schema_version: 1
document_type: review
status: browser-rerun-ready
created: 2026-05-29
review_type: phase-01-code-validation
verdict: PASS_CODE_VALIDATION_BROWSER_RERUN_READY
---

# Scope

Validated Phase 01 dashboard widget platform foundation on branch `feature/dashboard-widget-suite` against:

- `.internal-dev/plans/dashboard-widget-suite/worker-directives/phase-01-platform-foundation.md`
- `.internal-dev/plans/dashboard-widget-suite/00-specification-lock.md`
- `.internal-dev/plans/dashboard-widget-suite/03-target-architecture-and-widget-contract.md`
- `.internal-dev/plans/dashboard-widget-suite/04-data-model-and-migration-design.md`
- `.internal-dev/plans/dashboard-widget-suite/06-ui-ux-contract-and-visual-validation-criteria.md`
- `.internal-dev/plans/dashboard-widget-suite/shared/implementation-notes.md`
- `.internal-dev/plans/dashboard-widget-suite/shared/validation-matrix.md`
- `.internal-dev/reviews/2026-05-29-dashboard-widget-suite-plan-redteam.md`
- repo/package governance: `AGENTS.md`, `.internal-dev/AGENTS.md`, `.internal-dev/specifications/AGENTS.md`, `docs/AGENTS.md`, `src/main/java/io/mindspice/magenta2/AGENTS.md`, `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`, `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- targeted specs/knowledge: `.internal-dev/specifications/{web,simplypages,architecture,service-graph,services,api,decisions,deferred-features}.md`, `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`, `.internal-dev/knowledge/dashboard-fragment-navigation.md`, `.internal-dev/knowledge/htmx-route-render-contract-validation.md`, `.internal-dev/knowledge/workarea-operational-ui-consistency.md`

# Findings

## Browser Findings Repair Revalidation - 2026-05-29

Verdict: `PASS_CODE_VALIDATION_BROWSER_RERUN_READY`.

No remaining code findings were found in the browser-findings repair after validator self-remediation. The repair points now satisfy code-level criteria and are ready for the delegated Playwright/browser rerun. Browser proof was not run in this validation pass.

Validator self-remediation:

- Changed files: `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`, `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- Classification: `simple_validator_edit`
- Reason: the repair attached `hx-on::before-swap` to forms whose `hx-target` is `#avatar-edit-container`. HTMX fires `htmx:beforeSwap` for the swap target, so a child form-local handler is not reliable for a 400 response targeting the modal host. The validator moved the same hook onto the stable `#avatar-edit-container` host and its OOB replacement fragments, leaving the form hooks intact.
- Evidence: focused controller test now asserts the page-level modal host carries the 400 swap hook; focused Maven tests and startup smoke passed.

Repair criteria:

| Criterion | Result | Evidence |
| --- | --- | --- |
| Settings invalid binding visibly swaps the 400 modal error. | PASS_CODE_LEVEL_BROWSER_PENDING | `saveWidgetSettings` returns HTTP 400 with the settings modal and validation error; `#avatar-edit-container` now owns the HTMX 400 swap hook, and settings forms still target that host. |
| Modal clear avoids duplicate `avatar-edit-container` ids. | PASS_CODE_LEVEL_BROWSER_PENDING | `AvatarDashboardController.clearDashboardModal()` returns an empty fragment for innerHTML replacement instead of a nested host element. |
| Used single-instance catalog widgets remain visible but disabled. | PASS_CODE_LEVEL_BROWSER_PENDING | Catalog renders used single-instance widgets with `avatar-catalog-item-disabled`, `aria-disabled="true"`, disabled submit button, and visible `Already on this dashboard.` text; Notes remains addable. |
| Duplicate single-instance add returns a recoverable HTML catalog error. | PASS_CODE_LEVEL_BROWSER_PENDING | `addLayoutWidget` catches service validation failure, sets HTTP 400, and returns `widgetCatalogModal(..., exception.getMessage())`; tests assert no raw widget-grid fragment is returned. |
| Visual reference criteria use active `/manage` route. | PASS_CODE_LEVEL_BROWSER_PENDING | `.internal-dev/plans/dashboard-widget-suite/00-specification-lock.md` and `06-ui-ux-contract-and-visual-validation-criteria.md` now use `/manage` instead of stale `/dashboard`. |

Repair revalidation commands:

- `mvn -Dtest=AvatarDashboardControllerTest test`
  - PASS. 19 tests run, 0 failures, 0 errors, 0 skipped.
- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest test`
  - PASS. 34 tests run, 0 failures, 0 errors, 0 skipped.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - PASS startup smoke. Spring Boot started on random port `37219` in 3.152 seconds, then exited with code 124 due to the expected timeout and graceful shutdown.
- `jq . artifacts/dashboard-widget-suite/validation-summary.json >/dev/null`
  - PASS.
- `git diff --check -- src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java .internal-dev/plans/dashboard-widget-suite/00-specification-lock.md .internal-dev/plans/dashboard-widget-suite/06-ui-ux-contract-and-visual-validation-criteria.md .internal-dev/reviews/2026-05-29-dashboard-widget-suite-phase-01-browser-proof.md artifacts/dashboard-widget-suite/validation-summary.json`
  - PASS.

Required browser rerun checklist:

- Desktop `1440x900` and mobile `390x844`.
- `/` normal Assistant dashboard: one top nav, one `#dashboard-home`, one `#avatar-edit-container`, one chat rail, no duplicate script/nav/root.
- `/dashboards/assistant?edit=true`: compact in-place row/widget controls; add-row/add-widget, move, remove, width picker; no oversized editor chrome.
- Modal host behavior: open settings, submit invalid `sourceMode=agent` without `agentId`, assert the HTTP 400 response visibly replaces modal content with the validation error and does not leave stale modal content; close modal and assert `#avatar-edit-container` remains unique and empty.
- Catalog behavior: add a single-instance widget, reopen catalog, assert that widget remains visible as disabled with `aria-disabled` and `Already on this dashboard.`, while multi-instance widgets such as Notes remain enabled.
- Duplicate single-instance rejection: attempt to add duplicate `todos` or `calendar`; assert visible recoverable catalog error, disabled state still rendered, no JSON/raw error, no layout corruption, and no duplicate modal host.
- Multi-instance Notes regression: add two Notes widgets, submit note capture from the second instance, assert the response/replaced root is the second instance id and no duplicate `avatar-widget-*` ids exist.
- Dashboard selector switching: request goes to `/dashboards/{dashboardId}/_page`, swaps `#dashboard-home`, pushes canonical URL, leaves one shell/nav/root and preserves a single modal host.
- Visual critique: compare `/`, edit mode, `/manage`, and `/agents`; inspect density, spacing, control affordances, text wrapping, modal scroll, mobile stacking, horizontal overflow, z-index/top-nav interactions, and duplicate-root absence.

## Repair Revalidation Update - 2026-05-29

Verdict after focused repair revalidation: `PASS_CODE_VALIDATION`.

The prior code defect is repaired. Real Notes widget instances now render instance-scoped note capture routes, `POST /dashboards/{dashboardId}/widgets/{widgetInstanceId}/_notes` validates that the target instance is a Notes widget on the requested dashboard, and the response refreshes `widgetByInstance(...)` for the submitting widget. The focused regression test covers two Notes widgets and proves submitting through the second instance returns only the second instance root.

The prior missing-evidence finding is also addressed. The repair worker created `artifacts/dashboard-widget-suite/validation-summary.json`; during independent revalidation, the validator found that the JSON parsed but did not match the validation-matrix top-level field contract. This was corrected as validator self-remediation because it was a validation byproduct, not product code.

No broader risk expansion was observed in the repaired files.

Updated criterion results for failed findings:

| Criterion | Revalidation Result | Evidence |
| --- | --- | --- |
| Multiple instances of a multi-instance widget type can exist on one dashboard. | PASS | `AvatarDashboardControllerTest.noteCaptureRefreshesSubmittingWidgetInstanceWhenMultipleNotesWidgetsExist` proves the second Notes widget emits and receives the instance-scoped route/root. |
| UI shell avoids duplicate widget roots after fragment swaps. | PASS_CODE_LEVEL_BROWSER_PENDING | Code/test evidence proves the second Notes response does not include the first Notes root. Live HTMX DOM duplicate-id validation remains in delegated Playwright proof. |
| Canonical evidence index exists. | PASS AFTER VALIDATOR SELF-REMEDIATION | `artifacts/dashboard-widget-suite/validation-summary.json` now uses the required top-level fields from `shared/validation-matrix.md`. |

Repair revalidation commands:

- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest test`
  - PASS. 34 tests run, 0 failures, 0 errors, 0 skipped.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - PASS startup smoke. Spring Boot started on random port `38179` in 3.097 seconds, then exited with code 124 due to the expected timeout and graceful shutdown.
- `jq . artifacts/dashboard-widget-suite/validation-summary.json >/dev/null`
  - PASS before validator schema remediation.

Validator self-remediation:

- Changed file: `artifacts/dashboard-widget-suite/validation-summary.json`
- Reason: the repair-created evidence index was valid JSON but did not conform to the validation matrix's required top-level fields.
- Scope: validation artifact only; no product code, schema, migrations, API contracts, or UI source edited by the validator.

## 1. Multi-instance widget quick actions can replace the wrong widget root

Severity: high
Classification: `code_defect`
Revalidation status: `PASSED`

The implementation allows multiple `notes` widgets, but the rendered note capture form still posts to the type-scoped compatibility route and targets the current instance root. `AvatarDashboardComponents` derives the HTMX target from the rendered widget instance id (`#avatar-widget-{widgetInstanceId}`), then renders the Notes form with `hx-post="/_dashboards/_notes"` and that instance target. `AvatarDashboardController.createNote()` saves the note and returns `widget("notes")`, and the compatibility `widget(String widgetKey)` route resolves the first matching widget of that type.

Evidence:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:253` sets the summary root id from `rootId(widget.widgetId())`.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:956` derives the action target from the current rendered instance id.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:1094-1097` renders the Notes form with `hx-post="/_dashboards/_notes"` and `hx-target="#{currentInstanceRoot}"`.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:502-508` returns `widget("notes")` after create.
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java:183-195` resolves `widget("notes")` by taking the first row widget whose `widgetKey` equals `notes`.
- `docs/technical/avatar-dashboard-fragments.md:37` and `docs/technical/avatar-dashboard-layout-persistence.md:57` document that compatibility widget routes resolve the first matching instance.

Impact:

Submitting the form from the second `notes` widget can replace that second DOM target with HTML rooted at the first widget id. In a browser this can produce duplicate `id="avatar-widget-{firstNotesId}"`, strand the second instance, and violate the Phase 01 negative check for no duplicate roots after fragment swaps. It also means the summary update is not truly instance-id stable for multi-instance widgets.

Required repair:

Make rendered quick-action forms for multi-instance-capable widgets return the same instance root they targeted. A narrow repair can add instance-id summary action routes, pass `dashboardId`/`widgetInstanceId` through the Notes form, or have the action route return `widgetByInstance(...)`/equivalent after mutation. Add a controller test with two `notes` widgets that submits from the second instance and asserts the response root id is the second instance id and not the first.

## 2. Canonical dashboard-widget-suite evidence index is missing

Severity: medium
Classification: `docs_or_evidence_defect`
Revalidation status: `PASSED_AFTER_VALIDATOR_SELF_REMEDIATION`

The validation matrix names `artifacts/dashboard-widget-suite/validation-summary.json` as the canonical evidence index with required status, command, validator, browser, artifact, superseded-artifact, tooling-constraint, stale-reference, and final-reconciler fields. The worktree does not contain that file. Existing validation-summary files are for unrelated work: `artifacts/workarea-ui-consistency-repair/browser-rerun/validation-summary.json`, `artifacts/assistant-dashboard-refactor/validation-summary.json`, and `artifacts/agents-selector-chat-resize/validation-summary.json`.

Impact:

Phase evidence currently lives in the worker prompt and this review artifact, not in the plan's canonical evidence path. This should not block targeted code repair, but it must be fixed before browser proof or integration validation can honestly reconcile unit validator, startup, browser, and residual-risk state.

Required repair:

Create or update `artifacts/dashboard-widget-suite/validation-summary.json` after the code repair with `status` no stronger than `code_validation_passed_playwright_pending` until delegated Playwright proof is reconciled.

## 3. `.gitignore` contains a broad config ignore change outside the worker's reported scope

Severity: low
Classification: `docs_or_evidence_defect`
Revalidation status: `UNCHANGED_UNRELATED`

The worktree includes `.gitignore` changing `/config/ai-config.example.json` to `/config/`. The worker report did not list `.gitignore`, and the user prompt warned it may be unrelated/pre-existing. I did not revert it or count it as Phase 01 implementation evidence.

Impact:

If this change is intended for Phase 01, it needs explicit ownership because it broadens ignored config behavior beyond the dashboard-widget suite. If it is pre-existing, it should remain outside the Phase 01 commit/review scope.

# Criterion Results

| Criterion | Result | Evidence |
| --- | --- | --- |
| Multiple instances of a multi-instance widget type can exist on one dashboard. | PASS | Persistence/add paths pass for `notes`; repaired instance-scoped note capture returns the submitting widget root for the second Notes instance. |
| Single-instance widgets are rejected by service validation and persisted constraint. | PASS | `AvatarService.addDashboardWidget` rejects existing single-instance types; `user_dashboard_widgets` has `unique(dashboard_id, single_instance_key)`; repository tests cover duplicate `todos`. |
| Legacy Assistant seed renders after migration; migration preserves rows/widgets/settings. | PASS | Rebuild migration copies legacy rows and preserves settings; `migratesLegacyUserDashboardWidgetTableToInstanceModel` passed. |
| Settings JSON defaults and invalid binding errors are deterministic. | PASS | Registry defaults are deterministic; controller returns settings modal with `Agent source mode requires an agent id.` for invalid binding; focused tests passed. |
| Detail/settings routes use stable HTMX targets and OOB refreshes. | PASS | Instance detail/settings routes and OOB save exist and tests cover them; repaired Notes quick action now uses an instance route for real widget instances. |
| Widget catalog follows registry instance policy, not hard-coded disables. | PASS | Catalog disables only `definition.singleInstance()` types already used and leaves `notes` addable. |
| Controllers do not bypass services with raw repository/filesystem access. | PASS | Phase 01 widget instance/settings paths delegate through `AvatarService`; Work Area routes continue using `WorkAreaExplorerService`. |
| UI shell remains dense Avatar/dashboard style and avoids duplicate shell/nav/root after fragment swaps. | PASS_CODE_LEVEL_BROWSER_PENDING | Repaired test proves the second Notes response excludes the first Notes root. Delegated Playwright still must verify live HTMX DOM and visual quality. |
| Docs/spec/changelog updates are present and accurate for Phase 01. | PASS | Specs, docs, changelog, and canonical evidence index are present; evidence index was validator self-remediated to match the validation matrix field contract. |
| Required tests/startup evidence is adequate or rerun. | PASS | Reran focused Maven tests and bounded startup successfully; details below. |

# Commands And Checks

- `git status --short --branch`
  - Confirmed branch `feature/dashboard-widget-suite`.
  - Observed expected Phase 01 modified/untracked files plus warned pre-existing/unrelated `.gitignore` and `.internal-dev/reviews/2026-05-28-model-alias-internal-review.md`.
- `rg --files -g 'AGENTS.md' ...`
  - Located and read applicable governance files.
- Targeted `sed -n`/`nl -ba` reads of worker directive, supporting plan files, red-team review, specs, knowledge files, production code, tests, docs, and changelog.
- `git diff --stat`, `git diff --name-only`, and targeted `git diff`
  - Reviewed implementation scope, docs/spec deltas, schema delta, and `.gitignore` delta.
- `mvn -Dtest=AvatarRepositoryTest,AvatarServiceTest,AvatarDashboardControllerTest test`
  - PASS. 33 tests run, 0 failures, 0 errors, 0 skipped.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - PASS startup smoke. Spring Boot started on random port `43671` in 2.987 seconds, then exited with code 124 due to the expected timeout and graceful shutdown.
- `find artifacts -maxdepth 3 -type f -name 'validation-summary.json'`
  - FAIL evidence check for this plan. No `artifacts/dashboard-widget-suite/validation-summary.json` present.

# Browser Checklist

Do not run browser proof until the code defect above is repaired and focused tests pass. After repair, delegate Playwright with the plan-required model/tooling constraints and record evidence in `artifacts/dashboard-widget-suite/validation-summary.json`.

Required scenarios:

- Desktop `1440x900` and mobile `390x844`.
- `/` normal Assistant dashboard: one top nav, one `#dashboard-home`, one chat rail, no duplicate script/nav/root.
- `/dashboards/assistant?edit=true`: compact in-place row/widget controls, add-row/add-widget, move, remove, width picker, no oversized editor chrome.
- Dashboard selector switching: request goes to `/dashboards/{dashboardId}/_page`, swaps `#dashboard-home`, pushes canonical URL, leaves one shell/nav/root.
- Widget catalog: single-instance types disabled only when already present; `notes`, `outputs`, and `recent-work` remain addable when policy allows.
- Multi-instance `notes`: add two notes widgets, submit note capture from the second instance, assert the response/replaced root is the second instance id and no duplicate `avatar-widget-*` ids exist.
- Single-instance rejection: attempt adding duplicate `todos` or `calendar`; verify visible recoverable error/400 behavior and no layout corruption.
- Settings modal: open/save/cancel on a widget instance; invalid `sourceMode=agent` without `agentId` returns a 400 modal error; valid save closes modal OOB and refreshes the correct summary root.
- Visual critique: compare `/`, edit mode, `/manage`, and `/agents`; inspect density, spacing, control affordances, text wrapping, modal scroll, mobile stacking, horizontal overflow, and duplicate-root absence.

# Missing Tests, Docs, And Workflow Items

- Add a focused controller test for quick-action response identity on the second instance of a multi-instance widget.
- Create/update `artifacts/dashboard-widget-suite/validation-summary.json` before browser or integration signoff.
- Treat `.gitignore` as unrelated/pre-existing unless the implementation owner explicitly claims it and documents why broad `/config/` ignore belongs in this phase.

# Risk Assessment

The persistence foundation is mostly sound: registry definitions exist, multi-instance rows persist, single-instance rows are constrained, migration preserves legacy settings, and service/controller tests pass. The remaining code defect is in the rendered HTMX interaction graph, not the database model. It is still blocking because Phase 01 explicitly introduced multi-instance widget instances and stable HTMX roots; leaving first-match type routes wired into multi-instance summary cards can corrupt the live DOM.

# Recommendations

Verdict: `PASS_CODE_VALIDATION_BROWSER_RERUN_READY`.

Do not start Phase 02 until delegated Playwright proof for Phase 01 is complete and reconciled. The next step is the browser rerun checklist above, with results recorded back into `artifacts/dashboard-widget-suite/validation-summary.json`.
