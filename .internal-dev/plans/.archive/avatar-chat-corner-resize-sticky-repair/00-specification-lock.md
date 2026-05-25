---
schema_version: 1
document_type: specification-lock
status: active
created: 2026-05-25
owner: unassigned
---

# Avatar Chat Corner Resize Sticky Repair Specification

## Objective

Repair the `/avatar` chat rail after repeated failed divider/sticky attempts. Stop relying on divider dragging, make the Avatar chat module resize from its bottom-right corner, make horizontal resizing claim/release dashboard width, and prove in browser that the chat remains pinned to the top page-view margin while the user scrolls.

## Source Inputs

- User request on 2026-05-25 in branch `feature/avatar-chat-left-resizable`.
- Current focus: `.internal-dev/focus/current-focus.md` records active Avatar dashboard remediation focus.
- Prior failed handoff: `.internal-dev/plans/.archive/avatar-chat-left-resizable/handoff-report.md`.
- Prior implementation notes: `.internal-dev/plans/.archive/avatar-chat-left-resizable/shared/implementation-notes.md`.
- Avatar UI style and layout knowledge:
  - `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
  - `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
  - `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
  - `.internal-dev/knowledge/avatar-shell-resizable-rail-geometry.md`
- Current verified files:
  - `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
  - `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
  - `src/main/resources/static/css/avatar-dashboard.css`
  - `src/main/resources/static/js/avatar-shell.js`
  - `src/main/resources/static/js/avatar-chat.js`
  - `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
  - `docs/end-user/avatar-dashboard.md`
  - `docs/technical/avatar-dashboard-fragments.md`

## Locked Decisions

- Replace the divider interaction with a bottom-right corner drag handle on the chat module.
- Corner drag must change both width and height on desktop.
- Width is layout-coupled: a wider chat rail leaves less horizontal dashboard room; a narrower chat rail gives the dashboard more room.
- The chat should remain a left-side shell module on desktop.
- Desktop sticky/follow behavior must be certified by Playwright visual evidence, not inferred from CSS.
- Keep the implementation small and local to Avatar shell geometry, docs, tests, and closeout artifacts.
- Keep standard Avatar dashboard CRUD/layout behavior HTMX-first; JavaScript is acceptable only for the local pointer-resize behavior and existing SSE chat client.

## Assumptions To Verify

- The rendered `/avatar` shell is served through SimplyPages `.content-wrapper` and `#content-area`; current framework CSS gives `.content-wrapper` `overflow-y: auto`, which may become the sticky containing ancestor.
- Current DOM order is chat rail, divider, dashboard main. The new design should remove or ignore the divider element and use a two-column grid plus corner handle.
- Saved `localStorage` values from prior divider attempts may make validation misleading unless cleared before browser checks.
- `aria-hidden="true"` on the old divider does not prevent pointer events, but it makes the old affordance non-focusable/non-descriptive and should not remain the primary interaction.

## Non-Goals

- Do not redesign Avatar dashboard content, widgets, tabs, Work Area explorer, or chat runtime behavior.
- Do not change `/chat`, `/dashboard`, `/agents`, shared chat services, SSE protocol, model routing, Avatar persistence, or Work Area services.
- Do not add a server-backed rail-size preference.
- Do not introduce a new frontend framework, broad JavaScript layout system, or SimplyPages library change unless browser investigation proves the local shell cannot satisfy the requirement.
- Do not run a broad multi-agent campaign; this is one implementation worker plus one Playwright validation worker.

## Constraints

- Main thread remains planning-only for this plan suite.
- Mutating implementation must happen on the existing branch or a dedicated branch chosen by the orchestrator before phase work starts.
- Every mutating implementation/fix pass must be followed by validation before more mutation.
- Browser certification requires a live Spring app and fresh browser state.
- If Playwright cannot run, the work is not fully certified; record the blocker and consult the user.
- Repo validation instruction: test-execution subagents, including Playwright, should use `gpt-5.3-codex` with medium reasoning. Implementation worker default remains `gpt-5.5` medium unless the orchestrator overrides.

## Acceptance Criteria

- AC1: Desktop `/avatar` renders chat as a left rail and dashboard as the remaining right-side work area.
- AC2: No divider drag behavior is required or presented as the primary resize control.
- AC3: A visible bottom-right corner handle on the chat module supports pointer drag.
- AC4: Dragging the corner right increases chat width and decreases dashboard width; dragging left decreases chat width and increases dashboard width.
- AC5: Dragging the corner down/up increases/decreases chat height within sane viewport bounds.
- AC6: Width and height are clamped to preserve a usable chat and a usable dashboard.
- AC7: The chat remains pinned near the top of the page-view margin while the dashboard content scrolls.
- AC8: Mobile/narrow viewport remains usable: no horizontal split squeeze, no visible resize handle, and no horizontal overflow introduced by saved desktop sizes.
- AC9: Focused code validation passes.
- AC10: Playwright validation captures screenshots or bounding-box evidence for resize and sticky/follow behavior before completion is certified.

## Validation Criteria

- Code validation:
  - `mvn -Dtest=AvatarDashboardControllerTest test`
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Browser validation:
  - Launch a fresh live app process on a known port.
  - Use a fresh browser context or clear Avatar rail localStorage keys before checks.
  - Capture initial desktop screenshot and element bounding boxes.
  - Drag the bottom-right handle by a meaningful delta and re-measure chat width/height plus dashboard width.
  - Scroll the page down and confirm chat top remains pinned to the configured top margin.
  - Check a narrow/mobile viewport for stacking, no handle, no horizontal overflow, and readable controls.

## User-Decision Gates

- Ask the user before editing outside the exact allowed files in the worker directive.
- Ask the user if browser evidence shows sticky cannot work in the SimplyPages shell without a broader shell/content-wrapper change.
- Ask the user before accepting static-only validation in place of Playwright.
- Ask the user before changing the mobile interaction from stacked/non-resizable to any alternate mobile resize behavior.

## Stop Rules

- Stop if the implementation requires changing chat service/runtime behavior.
- Stop if the resize fix requires a SimplyPages framework patch rather than Avatar-local shell/CSS/JS changes.
- Stop if Playwright cannot run due to app startup, MCP/browser, auth, or local dependency blockers; record the exact blocker.
- Stop if the dashboard becomes unusable at desktop after expanding chat, or if mobile introduces horizontal overflow.

## Senior Engineer Notes

This is a browser geometry repair, not a chat feature. The prior plan was correct to suspect coordinate space and sticky containment, but it was static-only and therefore unproven. Treat every current CSS/JS claim as a hypothesis until Playwright measures actual `getBoundingClientRect()` positions before and after drag/scroll.

CSS `resize: both` looks tempting for a bottom-right corner, but it is not sufficient by itself because the grid column must change so dashboard width responds. A small pointer handler that writes CSS custom properties is the pragmatic path: it keeps the grid layout in control, permits clamping and persistence, and gives Playwright measurable outcomes.
