---
schema_version: 1
document_type: review
status: browser-rerun-passed
created: 2026-05-29
review_type: phase-01-browser-proof
verdict: PASS_BROWSER_PROOF
---

# Scope

Delegated Phase 01 browser/Playwright proof for the dashboard widget suite on branch `feature/dashboard-widget-suite`.

Validated against:

- `.internal-dev/reviews/2026-05-29-dashboard-widget-suite-phase-01-validation.md`
- `artifacts/dashboard-widget-suite/validation-summary.json`
- `.internal-dev/plans/dashboard-widget-suite/worker-directives/phase-01-platform-foundation.md`
- `.internal-dev/plans/dashboard-widget-suite/06-ui-ux-contract-and-visual-validation-criteria.md`
- `.internal-dev/plans/dashboard-widget-suite/shared/validation-matrix.md`
- repo/package governance and browser workflow knowledge named in the dispatch

# Findings

## 1. Settings invalid binding returns 400 without a visible modal error

Severity: high
Classification: `code_defect`

The settings scenario requires `sourceMode=agent` without `agentId` to return a 400 modal error. The browser observed the expected HTTP 400 from:

`PUT /dashboards/dashboard-2de03988-acb6-4d39-9b36-436e46432610/widgets/widget-8b35657f-1e9c-4a09-8380-25a987f0ef8a/settings`

But the returned modal did not show `Agent source mode requires an agent id.` or any visible validation error. It re-rendered the normal settings form with no error text.

Evidence:

- Screenshot: `artifacts/dashboard-widget-suite/phase-01-browser/desktop-settings-invalid-agent-no-error.png`
- Console log: `artifacts/dashboard-widget-suite/phase-01-browser/console-messages.txt`
- Browser observation: 400 response was captured; modal remained open but error text was absent.

Required repair:

Render the settings validation message in the returned modal for 400 binding errors, preserving user-entered values where practical.

## 2. Settings modal close duplicates `#avatar-edit-container`

Severity: high
Classification: `code_defect`

After opening Todos settings and clicking the modal `Close` button, the DOM contained two `#avatar-edit-container` elements:

`<div id="avatar-edit-container" hx-swap-oob="true"><div id="avatar-edit-container"></div></div>`

This violates the Phase 01 duplicate-root/single-modal-host contract and can destabilize later HTMX swaps.

Evidence:

- Browser DOM inspection immediately after close found duplicate id `avatar-edit-container`.
- Settings screenshots before/after path: `artifacts/dashboard-widget-suite/phase-01-browser/desktop-settings-modal.png`

Required repair:

Make `/dashboards/_modal/clear` return only the intended replacement for the existing modal host, not a nested duplicate element when swapped into `#avatar-edit-container`.

## 3. Catalog removes used single-instance widgets instead of showing them disabled

Severity: medium
Classification: `code_defect`

The catalog contract says single-instance types should be disabled only when already present, while multi-instance Notes, Outputs, and Recent Work remain addable where width allows. After adding a Todos widget to a fresh row, reopening that row's catalog omitted `todos` entirely instead of rendering it disabled.

Evidence:

- Before adding Todos, catalog contained `daily-tasks`, `todos`, `calendar`, `notes`, `outputs`, `system`, `alerts`, and `recent-work`.
- After adding Todos, catalog contained `daily-tasks`, `calendar`, `notes`, `outputs`, `system`, `alerts`, and `recent-work`; `todos` was absent.
- Screenshot before broader mutation: `artifacts/dashboard-widget-suite/phase-01-browser/desktop-widget-catalog.png`

Required repair:

Keep used single-instance widget types visible in the catalog as disabled options with a clear reason. Continue leaving multi-instance widgets addable when row capacity permits.

## 4. Duplicate single-instance add has server rejection but no discoverable recoverable UI path

Severity: medium
Classification: `code_defect`

Because the used `todos` catalog item disappears, there is no discoverable UI control to attempt a duplicate add. A forced browser request to the row add route returned the correct 400 JSON:

`{"error":"dashboard widget already exists: todos"}`

However, the user-facing HTMX flow does not surface that as an HTML fragment. The screenshot of the error is diagnostic only because the browser harness injected the raw response into the modal host for evidence.

Evidence:

- Forced request: `POST /_dashboards/_layout/rows/row-6619a232-41ac-4e9a-b125-fde97b701225/widgets`
- Response: HTTP 400 with JSON error.
- Screenshot: `artifacts/dashboard-widget-suite/phase-01-browser/desktop-duplicate-todos-response.png`

Required repair:

Provide a user-visible recoverable error fragment for HTMX duplicate-add attempts, or make the disabled catalog item the supported visible duplicate state and update the scenario contract accordingly.

## 5. `/dashboard` reference surface is unavailable

Severity: medium
Classification: `plan_defect_or_product_contract_defect`

The visual validation criteria require comparison against `/dashboard`, but the live app returns a Spring Whitelabel 404 at `/dashboard`. `/agents` loaded and was captured; `/manage` was captured as a possible operational substitute, but it cannot satisfy the named `/dashboard` reference requirement.

Evidence:

- `curl -i http://localhost:18080/dashboard` returned HTTP 404.
- Screenshot: `artifacts/dashboard-widget-suite/phase-01-browser/reference-dashboard.png`
- Substitute screenshot only: `artifacts/dashboard-widget-suite/phase-01-browser/reference-manage-dashboard-substitute.png`

Required repair:

Either restore/route the expected `/dashboard` operational surface or revise the plan criteria to name the current operational reference route.

# Scenario Results

| Scenario | Result | Evidence |
| --- | --- | --- |
| Start app locally with isolated runtime data. | PASS | Started on `18080` with `--magenta.root.path=/tmp/magenta2-dashboard-widget-suite-browser` and isolated SQLite URLs. |
| Desktop `/` normal Assistant dashboard: one nav, one `#dashboard-home`, one chat rail, no duplicate shell/nav/root. | PASS | DOM counts: one `nav`, one `#dashboard-home`, one `.avatar-shell`, one `.avatar-chat`; screenshot `desktop-home.png`. |
| Mobile `/` normal Assistant dashboard. | PASS | No horizontal overflow at `390x844`; screenshot `mobile-home.png`. |
| `/dashboards/assistant?edit=true` compact in-place controls. | PASS_WITH_VISUAL_NOTES | Controls exist and are compact; screenshots `desktop-edit.png`, `mobile-edit.png`. Some desktop icon clusters crowd small widget headers but did not block the tested interactions. |
| Dashboard selector switching uses fragment route and canonical URL. | PASS | Clicks issued `GET /dashboards/assistant/_page` and `GET /dashboards/dashboard-2de03988-acb6-4d39-9b36-436e46432610/_page`, swapped `#dashboard-home`, pushed canonical URLs, and left one shell/nav/root. |
| Widget catalog instance policy. | FAIL | Multi-instance Notes remained addable after one Notes instance; after adding Todos, `todos` disappeared instead of rendering disabled. |
| Multi-instance Notes: add two Notes widgets and submit from second. | PASS | Second form posted to `/dashboards/dashboard-2de03988-acb6-4d39-9b36-436e46432610/widgets/widget-b7545abc-3cc2-41ad-b0d0-77e127c9fbf6/_notes`; replaced root was `#avatar-widget-widget-b7545abc-3cc2-41ad-b0d0-77e127c9fbf6`; no duplicate `avatar-widget-*` ids. Screenshots `desktop-two-notes.png`, `desktop-second-note-submit.png`. |
| Single-instance duplicate add rejection. | FAIL | Forced duplicate add returned HTTP 400, but the visible UI lacks a discoverable disabled duplicate control and does not provide an HTML recoverable error path. |
| Settings modal open/save/cancel. | FAIL | Open and valid save passed; invalid 400 lacks visible error; close/cancel duplicates `#avatar-edit-container`. |
| Visual critique against `/`, edit mode, `/dashboard`, `/agents`. | FAIL | `/` and `/agents` captured; `/dashboard` returned 404. Mobile stacking and overflow passed for changed surface. |

# Commands And Runtime

App command:

```bash
rm -rf /tmp/magenta2-dashboard-widget-suite-browser && mkdir -p /tmp/magenta2-dashboard-widget-suite-browser
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --magenta.root.path=/tmp/magenta2-dashboard-widget-suite-browser --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-dashboard-widget-suite-browser/magenta.sqlite?foreign_keys=true --magenta.orchestration.runner-delay-ms=60000 --magenta.orchestration.scheduler-delay-ms=60000 --magenta.orchestration.assignment-history-purge-delay-ms=60000'
```

Runtime evidence:

- Tomcat started on port `18080`.
- Isolated data root: `/tmp/magenta2-dashboard-widget-suite-browser`
- Browser tooling: Playwright MCP against `http://localhost:18080`.

Additional checks:

- `curl -i -s http://localhost:18080/dashboard`
  - Result: HTTP 404.
- Console evidence: `artifacts/dashboard-widget-suite/phase-01-browser/console-messages.txt`
- Network evidence: `artifacts/dashboard-widget-suite/phase-01-browser/network-requests.txt`

# Screenshots

- `artifacts/dashboard-widget-suite/phase-01-browser/desktop-home.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/desktop-edit.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/desktop-widget-catalog.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/desktop-two-notes.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/desktop-second-note-submit.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/desktop-settings-modal.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/desktop-settings-invalid-agent-no-error.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/desktop-duplicate-todos-response.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/mobile-home.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/mobile-edit.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/reference-dashboard.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/reference-agents.png`
- `artifacts/dashboard-widget-suite/phase-01-browser/reference-manage-dashboard-substitute.png`

# Visual Critique

The normal Assistant dashboard is aligned with the current blue-gray operational style: dense panels, small radii, thin borders, compact controls, semantic chips, and bounded widget bodies. The chat rail plus dashboard grid work well on desktop and stack cleanly on mobile without horizontal overflow.

Edit mode keeps layout controls in-place and avoids a large separate editor form. The controls are compact, but dense icon clusters crowd the top of narrow widgets on desktop and mobile. This is acceptable for Phase 01 basics but should be watched before richer widgets add longer headings or chips.

The widget catalog is a bounded modal-like picker and generally matches the visual system. It currently fails the instance-policy presentation contract because used single-instance widgets disappear rather than remaining visible as disabled options.

The settings modal is scroll-safe at the tested desktop viewport and uses restrained operational styling. Its validation state is not acceptable because the 400 binding error does not render visible error text, and close/cancel leaves a duplicate modal-host id.

The required `/dashboard` visual reference could not be reviewed because the route returns 404. `/agents` matches the same blue-gray shell language but has more dead space than the dashboard widget surface; the new dashboard surface is denser and more useful in first viewport.

# Risk Assessment

The multi-instance Notes route repair holds in the live browser path. The remaining failures are interaction-contract defects, not tooling constraints: invalid settings feedback, modal-host uniqueness, catalog instance presentation, and the missing `/dashboard` reference route.

# Recommendations

Verdict: `NEEDS_REPAIR`.

Repair the settings modal error rendering and modal clear duplicate-root defect first because they directly violate browser/HTMX root stability. Then align catalog presentation with the disabled single-instance contract and either restore `/dashboard` or revise the plan reference route.

# Scoped Repair Notes

2026-05-29 repair worker disposition before delegated browser rerun:

- Findings 1-4 are treated as product/UI code defects and repaired in `AvatarDashboardController`, `AvatarDashboardComponents`, and focused controller tests.
- Finding 5 is treated as a criteria correction. The live `web.md` specification already names `/manage` and `/agents` as dashboard visual references; the stale dashboard-widget-suite plan references to `/dashboard` were corrected to `/manage`.
- Playwright/browser proof remains pending. This review verdict stays `NEEDS_REPAIR` until the browser-proof agent reruns the failed settings, modal-root, catalog, duplicate-add, and visual-reference scenarios.

# Browser Rerun - 2026-05-29

Verdict: `PASS_BROWSER_PROOF`.

Runtime:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --magenta.root.path=/tmp/magenta2-dashboard-widget-suite-browser-rerun --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-dashboard-widget-suite-browser-rerun/magenta.sqlite?foreign_keys=true --magenta.orchestration.runner-delay-ms=60000 --magenta.orchestration.scheduler-delay-ms=60000 --magenta.orchestration.assignment-history-purge-delay-ms=60000'
```

Evidence directory: `artifacts/dashboard-widget-suite/phase-01-browser-rerun/`

Rerun results:

| Scenario | Result | Evidence |
| --- | --- | --- |
| Desktop and mobile `/` roots. | PASS | One top nav, one `#dashboard-home`, one chat rail, no duplicate ids, no mobile horizontal overflow. Screenshots `desktop-home.png`, `mobile-home.png`. |
| `/dashboards/assistant?edit=true` controls. | PASS_WITH_VISUAL_NOTES | Add-widget, insert-row, move, remove, and width-picker controls were present and compact. Dense icon clusters remain on narrow widgets but did not block interactions or create overflow/duplicate roots. Screenshots `desktop-edit.png`, `mobile-edit.png`, `desktop-width-picker.png`. |
| Settings invalid binding. | PASS | `PUT /dashboards/assistant/widgets/{todosWidgetId}/settings` returned HTTP 400 and visibly rendered `Agent source mode requires an agent id.` in the modal. Screenshot `desktop-settings-invalid-agent-visible.png`. |
| Settings modal close. | PASS | Closing the modal left one empty `#avatar-edit-container`, no nested modal host, and no duplicate ids. Screenshot `desktop-settings-closed.png`. |
| Catalog instance policy. | PASS | Used single-instance Todos stayed visible as disabled with `Already on this dashboard.`; Notes and Outputs remained addable where row width allowed. Screenshot `desktop-widget-catalog-disabled-items.png`. |
| Duplicate single-instance add. | PASS | A forced stale HTMX duplicate Todos submit returned HTTP 400 with recoverable HTML catalog error `dashboard widget already exists: todos`, preserved disabled catalog state, and left no duplicate ids. Screenshot `desktop-duplicate-todos-recoverable-error.png`. |
| Multi-instance Notes second submit. | PASS | Two Notes widgets were added to one row. The second submitted to `/dashboards/assistant/widgets/widget-b4e39672-855c-4683-9bfb-17c2dc8e9c1e/_notes`, refreshed the second root, and no duplicate `avatar-widget-*` ids remained. Screenshots `desktop-two-notes.png`, `desktop-second-note-submit.png`. |
| Dashboard selector switching. | PASS | Created a `Browser Rerun` dashboard, selected it through the dashboard selector, observed `GET /dashboards/{dashboardId}/_page` 200, canonical URL push, one shell/root, and no duplicate ids. Screenshot `desktop-dashboard-selector-switched.png`. |
| Visual references `/manage` and `/agents`. | PASS | `/manage` and `/agents` loaded as operational references with no duplicate ids and no horizontal overflow. `/manage` correctly has both top nav and management side nav. Screenshots `reference-manage.png`, `reference-agents.png`. |

Console/network reconciliation:

- `console-messages.txt` contains only expected browser resource errors for the intentional HTTP 400 negative tests.
- `network-requests.txt` records the expected 400 settings and duplicate-add requests plus successful fragment requests.
- `browser-rerun-results.json` reports `PASS_BROWSER_PROOF` with 12 checks passing and no failures.

# Rerun Visual Critique

The Assistant dashboard remains aligned with the compact blue-gray operational language used by `/manage` and `/agents`: thin borders, small radii, dense widget panels, clear chips, and useful first-viewport density. The normal dashboard and mobile dashboard had no duplicate shell/root ids and no horizontal overflow.

Edit mode uses in-place controls and avoids oversized editor chrome. The icon clusters are still dense on narrow cards, especially when repeated test rows are present, but the controls remain visible, reachable, and bounded. The widget catalog and settings modal are scroll-safe at the tested viewport; validation errors are visible HTML rather than raw transport output.

The `/manage` reference is denser and more table/list-oriented, while `/agents` has a larger empty detail pane when no agent is selected. The Phase 01 dashboard surface fits between those references: more compact than `/agents`, less side-nav heavy than `/manage`, and consistent enough for this browser rerun.

# Updated Recommendation

Phase 01 browser proof is passed. Do not claim full dashboard-widget-suite validation yet because later phases remain pending.
