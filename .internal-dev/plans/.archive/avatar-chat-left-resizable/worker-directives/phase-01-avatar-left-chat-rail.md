# Phase 01 Worker Directive: Avatar Left Chat Rail

## Objective

Move `/avatar` chat to a left rail, repair divider drag resizing, make the chat rail follow desktop scrolling, and ensure the dashboard fills the remaining right-side space.

## Supporting Docs To Read

- `.internal-dev/plans/avatar-chat-left-resizable/handoff-report.md`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `docs/AGENTS.md`

## Editable Files

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-shell.js`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/changelogs/<date>-avatar-chat-left-resizable.md`
- `.internal-dev/focus/unfinished-work.md` only if follow-up work is intentionally deferred or blocked.

## Forbidden Scope

- Do not edit `avatar-chat.js` unless a direct initialization break is discovered and reported first.
- Do not edit chat services, SSE controllers, model routing, Avatar persistence, Work Area services, shared `magenta.css`, `/chat`, `/dashboard`, or `/agents`.
- Do not add server-backed rail persistence.
- Do not run Playwright unless the user explicitly reverses the current skip.

## Experience Contract

Desktop layout:

- Chat rail is left.
- Divider sits immediately to the right of chat.
- Dashboard/main tab content fills everything to the right of the divider.
- Default chat width is slightly larger than a nav rail and comfortable for chat, roughly `29rem` to `31rem`.
- Minimum chat width remains usable, roughly `22rem` to `24rem`.
- Drag right expands chat and shrinks dashboard; drag left shrinks chat and expands dashboard.
- Chat remains visible while long dashboard content scrolls.

Interaction:

- Click-hold-drag resizes.
- Click without meaningful movement does not resize or persist.
- Width persists across reloads at desktop breakpoints.
- Mobile and tablet stack cleanly and hide the divider.

Visual failure examples:

- Right-side chat.
- Divider jumps to max on click.
- Divider cannot shrink on wide screens.
- Empty right margin remains because shell is still centered/capped.
- Chat scrolls away with dashboard.
- Mobile shows horizontal overflow or two cramped columns.

## Implementation Steps

1. Check `git status --short` and avoid overwriting unrelated user changes.
2. Update `AvatarDashboardComponents.page(...)` so `.avatar-shell-grid` renders left rail, divider, then main content.
3. Update `avatar-dashboard.css` to make `.avatar-shell-grid` a three-column desktop grid and remove or neutralize the `1680px` shell cap for the working area.
4. Move desktop sticky behavior to `.avatar-shell-rail` or otherwise make sticky containment valid.
5. Update mobile media rules so stacked layout hides the divider and leaves chat/dashboard usable.
6. Rewrite `avatar-shell.js` width calculation to use `.avatar-shell-grid.getBoundingClientRect().left` and clamp against grid width, not viewport right edge.
7. Add a click-without-drag guard before applying or persisting a new width.
8. Update focused controller tests to catch left-rail shell structure and required assets.
9. Update docs from right-rail language to left-rail language.
10. Add a changelog entry for the implemented behavior and validation.

## Acceptance Criteria

- Chat rail is on the left in component/CSS structure.
- Dashboard fills remaining right-side width.
- Divider is a true drag handle and uses grid-relative math.
- No click-only jump/persist behavior.
- Sticky/follow behavior is structurally valid on desktop.
- Mobile stacked behavior remains valid.
- Focused tests and docs are updated.

## Negative Checks

- Fail if `avatar-shell.js` still computes rail width with `window.innerWidth - pointerEvent.clientX`.
- Fail if chat remains right of `.avatar-shell-main`.
- Fail if `.avatar-shell` still prevents the dashboard from claiming available width.
- Fail if mobile media rules leave the divider visible.
- Fail if implementation touches backend chat/runtime/persistence code.

## Validation Commands

```bash
mvn -Dtest=AvatarDashboardControllerTest test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Expected evidence:

- Focused tests pass.
- Startup reaches ready state before timeout or blocker is recorded.
- No Playwright claim is made for this phase unless user approval changes.

## Stop Conditions

- Required behavior needs files outside the editable list.
- Local startup cannot run because of missing services/secrets; record the exact blocker.
- A product decision is needed for mobile ordering.
- Static inspection reveals the chat runtime itself is broken; stop before widening scope.

## Senior Engineer Notes

Treat this as shell geometry. The current Avatar chat client is intentionally compact and separate from `/chat`; do not drag the larger chat client into Avatar to solve a layout problem. Keep the divider behavior deterministic and local: grid bounds in, bounded CSS variable out.

The most important gotcha is wide desktop behavior. A fix that works on a 1440px viewport can still fail on a 2560px monitor if it measures from the viewport edge while the shell is centered. Use the grid element as the source of truth for both measurement and clamping.

## Do Not Close Unless

- `git diff` shows only allowed files.
- Left rail, divider, and main area are explicit in code/CSS.
- Resize math is grid-relative.
- Click-only resize is prevented.
- Sticky containment is addressed.
- Docs mention left rail.
- `mvn -Dtest=AvatarDashboardControllerTest test` passed.
- Bounded startup passed or a precise blocker is recorded.
