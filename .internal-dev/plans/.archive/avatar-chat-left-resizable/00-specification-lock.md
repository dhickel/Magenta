# Specification Lock

## Objective

Repair the Avatar shell layout so chat functions as a left, side-nav-like but wider rail with a real draggable divider, sticky/following desktop behavior, and a dashboard area that claims all remaining right-side width.

## Source Inputs

- User request in this session.
- `.internal-dev/focus/AGENTS.md`
- `.internal-dev/focus/current-focus.md`
- `.internal-dev/focus/unfinished-work.md`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `docs/AGENTS.md`
- Static inspection of Avatar shell/component/CSS/JS files.

## Locked Decisions

- Planning-only pass now; no product implementation in this thread.
- Playwright is intentionally skipped for this pass by user request.
- Implementation should be handed to a GPT-5.3 medium agent.
- Chat moves to the left or is kept left after implementation.
- Divider behavior is drag-resize, not click-toggle.
- Resize claims space from the dashboard/right view.
- `/avatar` should use full screen real estate because it has no side nav.

## Assumptions

- Browser-local rail width persistence remains acceptable.
- The compact Avatar chat client remains the right chat surface; only shell placement and resize behavior need repair.
- No new backend API, persistence, or chat service changes are needed.
- Existing mobile stacked behavior remains the safest mobile default.

## Non-Goals

- No `/chat` redesign.
- No backend chat/SSE/model routing changes.
- No Avatar persistence or Work Area service changes.
- No new server-backed shell preferences.
- No broad SimplyPages or shared CSS refactor.

## Constraints

- Keep UI consistent with Magenta operational console styling.
- Use existing SimplyPages component style and avoid raw string markup.
- Keep JavaScript narrow and behavior-specific.
- Update docs when user-facing behavior changes.
- Run non-browser validation after implementation; do not claim Playwright validation unless the user allows it later.

## Acceptance Criteria

- Avatar chat rail appears on the left at desktop sizes.
- Dashboard/right content fills the remaining width.
- Divider is positioned between chat and dashboard.
- Dragging divider changes chat width smoothly and dashboard width inversely.
- Simple click without drag does not jump width or persist a new width.
- Resize math is relative to the shell/grid bounds.
- Chat remains visible while scrolling long dashboard content.
- Mobile stacks cleanly and hides divider.
- Docs and focused controller tests match the left-rail contract.

## Validation Criteria

- `mvn -Dtest=AvatarDashboardControllerTest test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Static review of `avatar-shell.js` confirms no `window.innerWidth - pointerEvent.clientX` rail-width calculation remains.
- Static review of CSS/component order confirms left rail + divider + main grid.

## User-Decision Gates

- Ask the user before changing backend chat behavior.
- Ask the user before adding server-backed rail persistence.
- Ask the user before changing the mobile ordering if dashboard-first vs chat-first becomes a product decision.
- Ask the user before running Playwright during their active collaborative pass.

## Stop Rules

- Stop if the fix requires editing outside the allowed files in `handoff-report.md`.
- Stop if the shell cannot use full width without conflicting with a higher-priority layout contract.
- Stop if validation is blocked by local dependencies and record the blocker.
