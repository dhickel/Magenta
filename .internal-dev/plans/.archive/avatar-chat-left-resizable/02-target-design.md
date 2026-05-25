# Target Design

## Shell Layout

Use a desktop three-column shell grid:

1. left chat rail;
2. drag divider;
3. main Avatar tab/dashboard area.

The rail width is controlled by `--avatar-chat-rail-width`. The main area uses `minmax(0, 1fr)` and fills all remaining width.

## Component Contract

`AvatarDashboardComponents.page(...)` should render clear sibling regions under `.avatar-shell-grid`:

- `.avatar-shell-rail` containing `compactChat(...)`;
- `.avatar-chat-resizer` with `data-avatar-chat-resizer="true"`;
- `.avatar-shell-main` containing tabs and tab panel.

Avoid placing the divider inside the rail after the move; keeping it as a grid sibling makes the geometry and accessibility clearer.

## CSS Contract

- `.avatar-shell` should use full available width.
- `.avatar-shell-grid` should use left rail, divider, right main columns.
- `.avatar-shell-rail` should own desktop sticky positioning.
- `.avatar-chat` should remain the bordered chat card and keep viewport-bounded internal message scrolling.
- Mobile/tablet breakpoint should stack columns and hide `.avatar-chat-resizer`.

## JavaScript Contract

`avatar-shell.js` should:

- initialize once for the current shell root;
- restore saved width only on desktop;
- compute drag width from `.avatar-shell-grid.getBoundingClientRect().left`;
- clamp to min, max, and available grid width while preserving a usable dashboard column;
- ignore click-without-drag using a small movement threshold;
- persist the bounded applied width after a real drag.

## Compatibility

- Existing `localStorage` key can stay as `magenta.avatar.chatRailWidthPx`.
- Existing Avatar tab HTMX swaps remain unchanged.
- Existing `avatar-chat.js` SSE behavior remains unchanged.
- Existing mobile stacked behavior remains mostly unchanged except for left-rail source order if component order affects stacking.

## Rationale

The current bug is caused by shell geometry and coordinate-space mismatch. A three-column grid with container-relative pointer math directly models the intended interaction and avoids hidden assumptions about viewport edges, shell centering, and rail side.
