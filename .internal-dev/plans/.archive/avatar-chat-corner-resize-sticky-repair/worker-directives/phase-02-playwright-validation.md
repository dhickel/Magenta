---
schema_version: 1
document_type: worker-directive
phase: 02
role: validation_redteam_agent
model: gpt-5.3-codex
reasoning: medium
mutating: false
status: active
created: 2026-05-25
owner: unassigned
---

# Phase 02 Playwright Validation Directive

## Objective

Validate the implemented Avatar corner-resize/sticky repair against the plan criteria using a live Spring app and Playwright. Be adversarial: prove resize geometry and sticky behavior with screenshots and bounding boxes, not by trusting code.

## Required Supporting Docs To Read

- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/00-specification-lock.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/shared/validation-matrix.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/worker-directives/phase-01-implementation.md`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` only for browser workflow gotchas, stale state, and MCP failure handling
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`

## Editable Files

None. This is non-mutating validation. Return findings to the orchestrator. If the orchestration runtime explicitly allows validation evidence updates, append only to:

- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/shared/implementation-notes.md`

## Forbidden Scope

- Do not modify production code, tests, docs, CSS, JS, config, or database schema.
- Do not accept screenshots without bounding-box measurements for resize/sticky claims.
- Do not validate against an old running app or stale browser state.

## Setup

1. Confirm the working tree has the implementation changes expected by Phase 01.
2. Ensure no stale app process is serving old assets on the validation port.
3. Start a live app on a known port, preferably:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-avatar-corner-resize-playwright.sqlite --magenta.executor.chat-threads=4'
```

4. Use a fresh browser context or clear:

```js
localStorage.removeItem("magenta.avatar.chatRailWidthPx");
localStorage.removeItem("magenta.avatar.chatPanelHeightPx");
```

5. Hard reload `/avatar` and capture console/network errors.

## Validation Steps

### Desktop Initial Layout

- Viewport: at least `1440x900`.
- Open `/avatar`.
- Verify:
  - `#avatar-chat` is left of `.avatar-shell-main`;
  - bottom-right handle exists and is visible;
  - old divider is absent or not visible/interactive;
  - dashboard has useful width and no stranded right margin.
- Capture screenshot and bounding boxes for:
  - `#avatar-chat`
  - `[data-avatar-chat-corner-resizer='true']`
  - `.avatar-shell-main`
  - `.avatar-shell-grid`

### Corner Drag Resize

- Drag the corner handle down and right by a meaningful amount, such as `+120px x` and `+100px y`.
- Measure before and after:
  - chat width increased;
  - chat height increased;
  - dashboard/main width decreased.
- Drag the handle up and left by a meaningful amount.
- Measure:
  - chat width decreased;
  - chat height decreased;
  - dashboard/main width increased.
- Capture screenshots after each drag.

### Bounds

- Attempt to drag far right and confirm dashboard still has usable width.
- Attempt to drag far left/up and confirm chat remains usable.
- Confirm no text input, submit button, or transcript is clipped.

### Sticky Follow

- Capture `#avatar-chat.getBoundingClientRect().top` before scroll.
- Scroll the page or actual scroll container down enough that dashboard content moves.
- Capture:
  - actual scroll container used (`document.scrollingElement`, `.content-wrapper`, or other);
  - `#avatar-chat.getBoundingClientRect().top` after scroll;
  - `.avatar-shell-main`/dashboard content position after scroll.
- Pass only if chat top remains pinned near the intended page-view top margin while dashboard content scrolls.
- Capture screenshot after scroll.

### Mobile / Narrow Viewport

- Viewport: around `390x844` and one tablet-ish width below `1180px`.
- Open `/avatar` fresh.
- Verify:
  - shell stacks;
  - corner handle is hidden;
  - no horizontal overflow (`document.documentElement.scrollWidth <= window.innerWidth + tolerance`);
  - chat and dashboard controls remain readable.
- Capture screenshot and overflow measurement.

## Acceptance Criteria

Validate all ACs in `00-specification-lock.md`. Report pass/fail per criterion.

## Negative Checks

- Fail if the only resize-tested element is a divider.
- Fail if chat dimensions change but dashboard width does not.
- Fail if sticky is inferred from CSS rather than measured after scroll.
- Fail if old app/browser cache could explain the result.
- Fail if mobile has horizontal overflow.

## Required Evidence

Return:

- app URL/port and startup method;
- viewport sizes;
- screenshots paths;
- before/after bounding boxes;
- scroll container identity and scroll measurements;
- console/network errors;
- final pass/fail table.

## Stop Conditions

- App cannot start.
- Playwright/MCP cannot open the local app.
- The page under test is clearly stale relative to implementation changes.
- A blocking JavaScript error prevents resize code from loading.

## Senior Engineer Notes

A passing Playwright run should make the failure impossible to miss: the numbers should show width/height changes and the screenshots should show the chat still pinned after scroll. If the result is ambiguous, mark validation failed and ask for a diagnose-fix-revalidate loop.

## Do Not Close Unless

- [ ] Live app, not static HTML, was tested.
- [ ] Browser state was fresh or localStorage was cleared.
- [ ] Initial layout screenshot and boxes were captured.
- [ ] Resize screenshot and boxes prove width and height changes.
- [ ] Dashboard width response was measured.
- [ ] Scroll screenshot and boxes prove sticky/follow behavior.
- [ ] Mobile/narrow screenshot and overflow check were captured.
- [ ] Final result explicitly says pass or fail with blockers/findings.
