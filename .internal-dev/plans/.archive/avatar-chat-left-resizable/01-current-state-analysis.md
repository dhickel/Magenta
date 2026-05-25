# Current State Analysis

## Files And Behavior

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java` owns the page composition, shell tabs, tab panel, and compact chat component.
- `src/main/resources/static/css/avatar-dashboard.css` owns Avatar shell grid, rail, resizer, sticky chat, mobile stacking, and component styling.
- `src/main/resources/static/js/avatar-shell.js` owns divider drag and `localStorage` persistence.
- `src/main/resources/static/js/avatar-chat.js` owns compact Avatar SSE chat and should not need changes.
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java` currently asserts shell assets and chat anchors but does not prove left/right order or resize math.
- `docs/end-user/avatar-dashboard.md` and `docs/technical/avatar-dashboard-fragments.md` still describe the current right-rail behavior.

## Current Layout

`AvatarDashboardComponents.page(...)` renders:

1. page header;
2. `.avatar-shell`;
3. `.avatar-shell-grid`;
4. `.avatar-shell-main`;
5. `.avatar-shell-rail`;
6. divider inside the rail;
7. compact chat.

CSS then makes `.avatar-shell-grid` a two-column grid: main fluid column first, rail fixed-ish column second. The chat is therefore right-side by design.

## Current Resize Behavior

`avatar-shell.js` uses:

```js
window.innerWidth - pointerEvent.clientX
```

for both applying width during pointer move and persisting width on pointer up. This is vulnerable to centered shell margins, max-width caps, browser zoom, and the requested left-rail direction.

## Current Sticky Behavior

`.avatar-chat` is sticky, but its parent rail is a content-sized grid item under an outer grid with `align-items: start`. That structure likely prevents sticky behavior from working across the height of the dashboard column.

## Current Full-Width Conflict

`.avatar-page` is full width, but `.avatar-shell` and `.avatar-page-header` are capped at `1680px` and centered. The user specifically wants Avatar to use full screen real estate because it lacks side nav.

## Discovered Risks

- A persisted old width may still apply after moving from right rail to left rail. This is acceptable if the value is still a chat width and gets clamped correctly.
- Moving chat to the left may expose overly wide dashboard widgets on ultra-wide screens; this is expected because the user asked the dashboard to claim remaining right space.
- Sticky behavior can fail again if the worker leaves sticky on the chat element but keeps the rail content-sized.
- Mobile ordering may need product judgment if chat-first hides dashboard controls; keep the safest current stacked order unless a quick inspection shows otherwise.
