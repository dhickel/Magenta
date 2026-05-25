---
schema_version: 1
document_type: target-design
status: active
created: 2026-05-25
owner: unassigned
---

# Target Design

## Desktop Layout Contract

Use a two-column desktop shell grid:

- Left column: `minmax(<chat-min>, var(--avatar-chat-rail-width))`
- Right column: `minmax(0, 1fr)`
- A normal grid `column-gap` replaces the divider column.

The chat rail remains first in DOM order. The dashboard main remains second and naturally receives all remaining width. Horizontal resizing is implemented by updating `--avatar-chat-rail-width` on the Avatar shell scope.

## Bottom-Right Resize Contract

Render a visible bottom-right handle inside `aside#avatar-chat`:

- Suggested hook: `data-avatar-chat-corner-resizer="true"`.
- Suggested class: `avatar-chat-corner-resizer`.
- The handle should be a compact corner affordance, not a full-height divider.
- It should have an accessible label or `title`/`aria-label` because it is a user-facing control.
- It must not cover the text area submit path or make the composer hard to use.

## CSS Resize Decision

Do not rely on native CSS `resize: both` as the final implementation. It is allowed only as visual fallback inspiration.

Native CSS resize is insufficient here because:

- it does not reliably update the grid track that controls dashboard width;
- it does not provide consistent min/max dashboard clamping;
- it does not naturally persist width/height;
- it generally requires `overflow` changes that can interfere with the chat transcript and sticky behavior;
- Playwright evidence would be harder to interpret because dashboard response is the real requirement.

Use a small pointer handler in `avatar-shell.js` instead.

## Pointer Handler Design

Bind pointer events to `[data-avatar-chat-corner-resizer='true']`.

On pointer down:

- Require desktop breakpoint `min-width: 1181px`.
- Capture starting pointer `clientX/clientY`.
- Capture starting chat/rail width and height from `getBoundingClientRect()`.
- Capture the grid bounds and available viewport height.
- Use pointer capture where supported.

On pointer move:

- Apply only after a small movement threshold, such as 3px.
- New width = `startWidth + (currentClientX - startClientX)` for the left rail.
- New height = `startHeight + (currentClientY - startClientY)`.
- Clamp width and height before applying.
- Write `--avatar-chat-rail-width` and `--avatar-chat-panel-height` to `.avatar-shell`.

On pointer up/cancel:

- Persist clamped values to `localStorage`.
- Suggested keys:
  - keep existing `magenta.avatar.chatRailWidthPx` for width compatibility;
  - add `magenta.avatar.chatPanelHeightPx` for height.
- Release pointer capture and remove document listeners.

## Bounds

Recommended desktop width bounds:

- Minimum chat width: align CSS and JS around `22.85rem` / `366px`.
- Maximum chat width: `min(640px, gridWidth - mainMinWidth)`.
- Minimum dashboard width: keep current `520px` unless Playwright shows it is too narrow for `/avatar` widgets.

Recommended desktop height bounds:

- Minimum chat height: around `360px` so header, messages, and composer stay usable.
- Default chat height: roughly `min(620px, calc(100vh - 1.5rem))`, or the closest CSS/JS equivalent.
- Maximum chat height: `viewportHeight - stickyTop - bottomMargin`, typically `window.innerHeight - 24`.
- The transcript area should flex to consume remaining space and remain internally scrollable.

## Sticky / Follow Design

Preferred target:

- `.avatar-shell-rail` is sticky on desktop with `top` near the page-view margin.
- `.avatar-chat` is a normal card whose height is `var(--avatar-chat-panel-height)`.
- The chat rail does not depend on a full-height divider or full viewport min-height.

Browser investigation must verify the actual sticky containing block. If `.content-wrapper` or `#content-area` overflow prevents sticky behavior:

- First preference: a scoped Avatar CSS override, such as an Avatar-specific content wrapper/content target class or a narrowly scoped selector that makes the relevant shell overflow visible for `/avatar`.
- Second preference: a local shell wrapper/class change in `AvatarDashboardController` if SimplyPages gives a clean API for content target/wrapper classes.
- Last resort: a fixed-position left rail plus dashboard offset driven by the same CSS width variable, but only if sticky cannot be made reliable without broader shell changes.

Do not apply a global `.content-wrapper` overflow change that could affect other pages.

## Mobile / Narrow Viewport

At `max-width: 1180px`:

- Shell stacks to one column.
- Corner handle is hidden.
- JS ignores saved width and height.
- Chat becomes static and auto-height, with transcript still usable.
- No horizontal overflow is introduced.

The exact order may remain chat before dashboard unless Playwright shows first-viewport usability is worse than dashboard-first. Changing mobile order is a user-decision gate.

## Compatibility And Docs

- Existing left rail docs should be updated from divider resizing to corner resizing.
- `AvatarDashboardControllerTest` should catch the new DOM hooks and absence of old divider reliance.
- Script asset version for `avatar-shell.js` should be bumped if browser cache would otherwise hide the fix.
- Existing `avatar-chat.js` should remain untouched unless investigation proves chat initialization itself is directly broken.

## Observability

No new server observability is required. Browser validation evidence should include:

- bounding boxes for chat, dashboard main, handle, and scroll container;
- screenshots before/after resize and after scroll;
- console/network error capture sufficient to detect failed asset loading or JavaScript exceptions.

## Security

No new server mutation or API route is introduced. The resize handler is local browser state only. Do not weaken existing HTMX/CSRF behavior.

## Senior Engineer Notes

Keep the layout in normal document flow if at all possible. A fixed-position rail can satisfy "follows me" but it creates a second layout system that must be reconciled with dashboard width, top nav, mobile, modals, and Work Area overlays. A sticky rail plus grid CSS variables is the smaller, more maintainable repair if the shell overflow issue is handled.

Treat persisted dimensions as hostile input. Clamp on restore, not only during drag. Old divider-era width values and odd zoom/browser sizes should not produce a clipped dashboard or an unusable chat.
