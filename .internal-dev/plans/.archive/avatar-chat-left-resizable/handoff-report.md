# Avatar Chat Left Resizable Handoff Report

## Objective

Investigate the reported `/avatar` chat/sidebar behavior from static code inspection and hand off a concrete fix plan to a GPT-5.3 medium implementation agent. This report must not implement product code.

## Locked User Request

- Move or keep Avatar chat on the left, occupying the side-nav-like area that `/avatar` otherwise does not have.
- Make the chat rail slightly larger than a normal navigation rail by default so it works as a common chat width.
- Add or repair a click-held drag divider/resize affordance.
- Resizing must claim space from the dashboard/right view; the dashboard must fill all remaining right-side real estate.
- The chat window should follow the user while the dashboard scrolls.
- Do not run Playwright in this pass. The user explicitly skipped browser tests because they are actively working with us.

## Current State Observed

Verified from static inspection:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:72` renders the `/avatar` shell.
- `AvatarDashboardComponents.page(...)` currently renders `.avatar-shell-main` first and `.avatar-shell-rail` second, so chat is on the right, not the left. See `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:82`.
- The divider is rendered inside `.avatar-shell-rail` before `compactChat(...)`, which only makes sense for a right rail. See `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:87`.
- `compactChat(...)` renders the chat as an `aside#avatar-chat` with `data-avatar-chat-rail="true"`. See `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java:793`.
- `.avatar-shell` is capped at `max-width: 1680px` and centered. This conflicts with the new requirement that `/avatar` use full screen real estate because it has no side nav. See `src/main/resources/static/css/avatar-dashboard.css:53`.
- `.avatar-shell-grid` currently uses `grid-template-columns: minmax(0, 1fr) minmax(22.85rem, calc(var(--avatar-chat-rail-width) + 1.3rem))`, so dashboard takes the left fluid column and chat gets the right fixed-ish column. See `src/main/resources/static/css/avatar-dashboard.css:59`.
- `.avatar-shell-rail` is a two-column internal grid of `0.85rem` divider plus chat. See `src/main/resources/static/css/avatar-dashboard.css:121`.
- `.avatar-chat` is `position: sticky` with `top: 0.65rem`, `min-height` and `max-height` based on viewport height. See `src/main/resources/static/css/avatar-dashboard.css:636`.
- At the mobile breakpoint, the shell stacks to one column, hides the resizer, and makes `.avatar-chat` static. See `src/main/resources/static/css/avatar-dashboard.css:1305`.
- `src/main/resources/static/js/avatar-shell.js:34` computes rail width as `window.innerWidth - pointerEvent.clientX`.
- `src/main/resources/static/js/avatar-shell.js:41` persists the same viewport-derived width on pointer up.
- The existing docs still describe a right chat rail and divider between main content and chat. See `docs/end-user/avatar-dashboard.md:16` and `docs/technical/avatar-dashboard-fragments.md:86`.

## Likely Root Causes

### Divider Click-Jump And Lock

The resize math uses the viewport right edge, not the actual shell/grid right edge:

```js
window.innerWidth - pointerEvent.clientX
```

That is plausible for a full-width, right-aligned right rail only. It breaks when the shell is centered and capped at `1680px`: the calculation includes the right page margin as part of the rail width. On a wide desktop, a pointer down near the divider can immediately calculate a width greater than the configured `AVATAR_RAIL_MAX` and clamp to max. Because the pointer cannot move into the right margin inside the shell, the divider can appear to jump-expanded and locked.

This same formula is also the wrong direction for the requested left rail. A left rail must derive width from the shell/grid left edge to the pointer X position, not from viewport right edge.

### Chat Does Not Follow Scroll

The chat itself is sticky, but it lives inside `.avatar-shell-rail`, and the outer grid uses `align-items: start`. That makes the rail item size to its content instead of stretching alongside the taller dashboard content. Sticky positioning is constrained by its containing block, so a rail whose height is essentially the chat height gives the sticky element little or no room to remain pinned while the dashboard scrolls.

The more robust fix is to make the rail container the sticky element on desktop, or otherwise make the rail's containing block stretch with the dashboard column. For this target, prefer a sticky `.avatar-shell-rail` containing a normal full-height chat card.

### Avatar Does Not Use Full Real Estate

The CSS intentionally centers the shell with `max-width: 1680px`. The new request says Avatar has no side nav, so the chat should use the left rail area and the dashboard should claim the rest of the screen. The implementation should remove the shell cap for the Avatar working area or raise it only if a hard readability constraint is later approved.

## Target UX Contract

Desktop:

- Chat appears on the left.
- The left chat rail defaults around `29rem` to `31rem`, with a minimum around `22rem` to `24rem`.
- The divider sits immediately to the right of the chat rail and is visibly draggable.
- Dragging the divider right expands chat and reduces the dashboard column; dragging left shrinks chat and gives dashboard more room.
- A simple click without meaningful pointer movement must not jump or persist a new width.
- Rail width persists in browser-local state.
- Dashboard/right content uses all remaining width to the right edge of the Avatar shell.
- The chat rail remains visible while the user scrolls long dashboard content.
- The chat transcript area remains internally scrollable and the composer remains reachable.

Mobile/tablet:

- Keep the current stacked behavior at the existing breakpoint unless the worker finds an obvious broken intermediate layout.
- Hide the drag divider on stacked layouts.
- Chat should stack in a predictable place. Prefer chat first only if it does not bury the dashboard controls; otherwise preserve practical dashboard-first stacking and document the decision.

Visual failure examples that must fail review:

- Chat remains on the right.
- Divider changes width on click without drag.
- Divider clamps to max and cannot shrink on wide screens.
- Dashboard has a large unused right margin after chat moves left.
- Chat scrolls away while long dashboard content continues.
- Chat rail overlaps tab controls, dashboard cards, modals, or Work Area explorer content.
- Mobile shows a tiny unusable dashboard column beside chat instead of stacking.

## Implementation Methodology

1. Change the shell structure in `AvatarDashboardComponents.page(...)` so the left rail, divider, and main content are separate siblings in `.avatar-shell-grid`.
2. Prefer the grid order: `.avatar-shell-rail`, `.avatar-chat-resizer`, `.avatar-shell-main`.
3. Update CSS to use a three-column desktop grid:
   - `minmax(<rail-min>, var(--avatar-chat-rail-width))`
   - divider width around `0.75rem` to `0.9rem`
   - `minmax(0, 1fr)` for dashboard/right content
4. Make `.avatar-shell` and the shell header use full available Avatar width instead of the current centered `1680px` cap, unless another repo rule conflicts.
5. Move sticky behavior to the rail container on desktop:
   - `.avatar-shell-rail { position: sticky; top: 0.65rem; align-self: start; }`
   - keep `.avatar-chat` as the visual card with viewport-bounded height.
6. Rewrite `avatar-shell.js` resize math around shell/grid bounds:
   - find the grid element with `resizer.closest(".avatar-shell-grid")`
   - capture `grid.getBoundingClientRect().left`
   - compute left rail width as `pointerEvent.clientX - gridLeft`
   - clamp against min, max, and available dashboard width
   - ignore non-drag clicks with a small movement threshold before applying/persisting
7. Rename local constants/storage key only if needed. Keeping the same `magenta.avatar.chatRailWidthPx` key is acceptable; saved right-rail widths still represent a chat width.
8. Update docs to say the chat rail is on the left.
9. Update `AvatarDashboardControllerTest` to assert ordering/anchors that catch regressions at a cheap static level.

## Exact Editable Boundaries

Allowed implementation files:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-shell.js`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/changelogs/<date>-avatar-chat-left-resizable.md`
- `.internal-dev/focus/unfinished-work.md` only if the implementation intentionally leaves follow-up work incomplete or blocked.

Read-only/supporting files:

- `.internal-dev/focus/AGENTS.md`
- `.internal-dev/focus/current-focus.md`
- `.internal-dev/focus/unfinished-work.md`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/knowledge/avatar-work-area-ui-refactor.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `docs/AGENTS.md`

Forbidden scope:

- Do not rewrite `avatar-chat.js` unless the shell move reveals a direct chat initialization regression.
- Do not change chat service, SSE, model routing, Avatar persistence, Work Area services, or layout persistence.
- Do not redesign `/chat`, `/dashboard`, `/agents`, or shared `magenta.css`.
- Do not add a new frontend framework or a broad JavaScript layout system.
- Do not add a server-backed rail-width preference.

## Validation Commands

Playwright is intentionally skipped for this investigation by user request. The implementation worker should still run non-browser validation:

```bash
mvn -Dtest=AvatarDashboardControllerTest test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

If the worker touches docs only incidentally, no separate docs command exists. If Maven startup is blocked by local config or service dependencies, record the blocker precisely and do not claim full validation.

Later visual checks, when Playwright is allowed again:

- Desktop `/avatar`: chat left, divider right of chat, dashboard fills the rest.
- Desktop long dashboard/edit content: left chat rail remains visible while scrolling.
- Drag divider left/right: width follows pointer smoothly and persists across reload.
- Wide desktop: no right-margin width pollution and no max-clamp lock.
- Mobile `/avatar`: stacked, no divider, no horizontal overflow.
- `/avatar?edit=true`: row/widget edit controls still layer correctly and do not collide with the left rail.

## Worker Directive

Hand this directive to the GPT-5.3 medium implementation agent:

> You are implementing the Avatar left chat rail and repaired divider behavior. Read `.internal-dev/plans/avatar-chat-left-resizable/handoff-report.md`, the three Avatar guidance docs listed there, and the nearest package guides before editing. You may edit only `AvatarDashboardComponents.java`, `avatar-dashboard.css`, `avatar-shell.js`, `AvatarDashboardControllerTest.java`, `docs/end-user/avatar-dashboard.md`, `docs/technical/avatar-dashboard-fragments.md`, and required `.internal-dev` closeout files. Move the Avatar chat rail to the left, make the divider a true drag handle that resizes from shell/grid bounds instead of viewport right edge, make chat follow scrolling on desktop, and let the dashboard fill the remaining right-side width. Keep mobile stacked and divider hidden. Do not touch backend chat behavior, Avatar persistence, Work Area services, `/chat`, `/dashboard`, `/agents`, or shared CSS. Run `mvn -Dtest=AvatarDashboardControllerTest test` and a bounded Spring startup. Do not run Playwright unless the user reverses the explicit skip. Stop and report if the required UX cannot be met without editing outside the allowed files or if startup is blocked by missing local dependencies.

Do not close unless:

- Chat is left of dashboard in the rendered component order or CSS order.
- Divider width math is relative to the shell/grid bounds, not `window.innerWidth`.
- Click without drag does not resize/persist a width.
- The rail can shrink and expand on wide centered or full-width screens.
- Chat remains visible while long dashboard content scrolls on desktop.
- Dashboard claims all remaining right-side space.
- Mobile remains stacked with no draggable divider.
- Tests and docs reflect left-rail behavior.

## Static-Inspection Uncertainty

The exact browser symptom was not reproduced in this pass because Playwright was intentionally skipped. The root causes above are high-confidence from code structure, especially the viewport-based resize calculation and shell max-width interaction. The implementation worker should verify locally with browser devtools or Playwright later when allowed, especially on a viewport wider than `1680px`, because that is the most likely way to reproduce the click-jump/lock behavior.

## Senior Engineer Notes

This is a shell geometry bug, not a chat runtime bug. Keep the fix boring: component order, grid columns, sticky containment, and pointer math. Avoid turning Avatar into a custom SPA surface. The existing split where `avatar-chat.js` owns SSE chat and `avatar-shell.js` owns rail geometry is a good boundary; preserve it.

The subtle failure mode is coordinate-space mismatch. Any resize code that uses viewport coordinates without subtracting the grid or shell origin will regress on centered layouts, zoomed browsers, and future sidebars. Compute in the coordinate space of the actual resizable container, clamp against the same container's width, and then write one CSS custom property.
