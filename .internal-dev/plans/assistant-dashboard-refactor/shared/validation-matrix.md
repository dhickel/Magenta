# Assistant Dashboard Refactor Validation Matrix

## Code-Level Validation

Required commands:

- `mvn test -Dtest=AvatarDashboardControllerTest,OrchestrationControllerTest`
- Any new focused dashboard repository/service test class added by the worker.
- `mvn test` unless runtime or local dependency blockers are documented and user-approved.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

If static JavaScript changes:

- `node --check src/main/resources/static/js/avatar-chat.js`
- `node --check src/main/resources/static/js/avatar-layout-edit.js`
- `node --check src/main/resources/static/js/avatar-workarea-editor.js`
- Check any newly created/renamed JS files with `node --check`.

## Browser Validation

Run focused Playwright validation in a subagent, not inline. Repo policy says testing/Playwright validation uses `gpt-5.3-codex` with reasoning effort `medium`; if that model route is unavailable, record `TOOLING_CONSTRAINT` and stop for approval before substituting.

Screenshots are evidence artifacts, not completion criteria by themselves. The Playwright pass must reach each required state, capture evidence, then inspect the captured UI against the functional and visual-quality criteria below. A screenshot that captures a broken, missing, stale, overflowing, or visually degraded state is a failing or blocked validation result, not a pass.

The browser-proof prompt must pass these UI quality criteria to the Playwright agent. The agent must return screenshot paths, observations, and explicit pass/fail notes for each changed surface so the validator can reconcile evidence instead of treating image capture as success.

Required screenshot evidence:

- `/` desktop: dashboard selector row, Assistant selected, dashboard content visible.
- `/` mobile: selector wraps/stacks cleanly; no horizontal overflow.
- Create-dashboard modal desktop and mobile.
- Newly created empty dashboard selected or visible.
- Assistant dashboard normal mode with chat rail.
- Assistant dashboard edit mode with in-place row/widget controls.
- `/manage` desktop: top nav `Home`, `Chat`, `Agents`, `Manage`; side nav has `System` above `Orchestration`; no obsolete Communication section if only Inbox/Agents would remain.
- `/agents` and one `/agents/{agentId}` detail view proving Agents is top-nav reachable.
- Agent detail Work Area browser/editor route proving Work Areas moved out of Assistant and remain usable.

Required visual-quality criteria:

- Each changed surface should read as a polished Magenta operational tool: clear hierarchy, consistent component styling, restrained density, and no marketing-page or placeholder feel.
- Layout spacing, alignment, and grouping should make the interface easy to scan without crowding controls or leaving awkward unused regions.
- Interactive controls must look intentionally clickable, have clear labels or familiar icon affordances, and remain reachable by mouse and keyboard.
- Text should wrap, clamp, or truncate within its intended container without widening the page, clipping important content, or overlapping neighboring controls.
- Modals and bounded panes should fit desktop and mobile viewports, keep actions visible or reachable, and scroll internally when content exceeds available space.
- Navigation must not duplicate shell chrome, preserve the final top-nav order, and avoid stale labels or route names from the removed Avatar abstraction.
- Mobile layouts must stack in a coherent order, preserve primary actions, and avoid horizontal overflow or compressed unreadable controls.
- Work Area browser surfaces in agent detail must retain the compact row/list, inspector, and editor quality of the existing browser rather than regressing into loose cards or raw forms.
- The Playwright agent must call out any visible inconsistency with the surrounding operational console styling, including mismatched borders, radii, typography scale, button treatment, table density, or panel hierarchy.

## Evidence Index

Because this plan has browser proof and multiple validation surfaces, final evidence must be recorded in:

`artifacts/assistant-dashboard-refactor/validation-summary.json`

It should include:

- final status;
- command results;
- browser artifact directory;
- screenshots captured;
- superseded artifacts;
- model/tooling constraints;
- validator pass/fail;
- residual risks.

Evidence status must be conservative and match completed gates. Do not use `validated`, `passed`, `complete`, or equivalent final wording until code/doc validation, Playwright/browser proof, and validator reconciliation have all passed. Use explicit intermediate statuses such as:

- `implementation_checks_passed`
- `validator_failed`
- `repair_in_progress`
- `code_validation_passed_playwright_pending`
- `playwright_failed`
- `blocked_tooling_constraint`
- `fully_validated`

The evidence index is invalid if its top-level status conflicts with nested validator status, missing browser artifacts, failed commands, or unresolved residual risks.

## Final Validation Sweep

Before sign-off:

- Search code/docs/specs for user-facing `Avatar` labels and route references.
- Search docs and `.internal-dev` for stale route claims, `/tmp` evidence, pending/planned/not implemented wording, stale phase wording, and TODO markers introduced by the work.
- Verify there are no stale `/avatar` or `/dashboard` redirect/deprecation expectations in tests, docs, or rendered UI.
- Verify Work Areas are absent from Assistant and present in agent detail.
