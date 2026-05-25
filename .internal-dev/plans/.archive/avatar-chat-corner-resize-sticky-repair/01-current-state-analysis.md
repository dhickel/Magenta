---
schema_version: 1
document_type: current-state-analysis
status: active
created: 2026-05-25
owner: unassigned
---

# Current State Analysis

## Verified Branch And Prior Work

- Branch is `feature/avatar-chat-left-resizable`.
- Recent commits include `ff8762e Fix avatar chat rail resizing` and `ba7cdc3 Update current Avatar focus`.
- Prior archived plan reports static-only diagnosis and explicitly skipped Playwright. User has now reported the divider still fails and chat still does not follow scrolling in real use.

## Current Avatar Shell Markup

Verified in `AvatarDashboardComponents.java`:

- `page(...)` renders `#avatar-page` with `data-avatar-shell="true"` and includes `pageHeader(...)`, `.avatar-shell`, `.avatar-shell-grid`, `.avatar-shell-rail`, `.avatar-chat-resizer`, and `.avatar-shell-main`.
- Current child order is rail, resizer, main at `AvatarDashboardComponents.java:82-92`.
- `compactChat(...)` renders `aside#avatar-chat.avatar-chat` with `data-avatar-chat="true"` and `data-avatar-chat-rail="true"` at `AvatarDashboardComponents.java:793-820`.
- The old divider is a `Div` with class `avatar-chat-resizer`, `data-avatar-chat-resizer="true"`, and `aria-hidden="true"` at `AvatarDashboardComponents.java:86-89`.

## Current CSS Behavior

Verified in `avatar-dashboard.css`:

- `.avatar-page` is full width with local padding at lines 1-6.
- `.avatar-page-header` remains capped at `max-width: 1680px` at lines 23-27.
- `.avatar-shell` defaults `--avatar-chat-rail-width: 30rem`, removes the max-width cap, and has no horizontal centering at lines 53-57.
- `.avatar-shell-grid` uses three desktop columns: `minmax(22.85rem, var(--avatar-chat-rail-width)) 0.85rem minmax(0, 1fr)` with a `0.4rem` gap at lines 59-65.
- `.avatar-shell-rail` is sticky with `top: 0.65rem`, `min-height` and `max-height` set to `calc(100vh - 1.3rem)` at lines 122-130.
- `.avatar-chat-resizer` is a full-height vertical divider with `cursor: col-resize`, `touch-action: none`, and a rounded gradient at lines 132-146.
- `.avatar-chat` is a flex column card with `min-height: 100%` and `max-height: 100%` at lines 389-399 and 639-647.
- `.avatar-chat-messages` is internally scrollable with `max-height: 46vh` at lines 679-687.
- At `max-width: 1180px`, the shell stacks to one column, the rail becomes static, the resizer is hidden, and chat height limits are removed at lines 1306-1325.

## Current JavaScript Behavior

Verified in `avatar-shell.js`:

- The script listens for `DOMContentLoaded` and `htmx:afterSettle` at lines 9-10.
- Initialization is single-shot per `[data-avatar-shell='true']` via `dataset.avatarShellInitialized` at lines 12-18.
- It restores a saved width from `localStorage` key `magenta.avatar.chatRailWidthPx` only at the desktop breakpoint at lines 72-80.
- It binds pointer events to `[data-avatar-chat-resizer='true']`, not a chat corner handle, at lines 29-70.
- Current pointer math computes left rail width as `pointerEvent.clientX - grid.getBoundingClientRect().left` at lines 92-98.
- Clamping uses `AVATAR_RAIL_MIN = 366`, `AVATAR_RAIL_MAX = 640`, and `AVATAR_MAIN_MIN = 520` at lines 1-7 and 100-109.
- There is no vertical chat height persistence or bottom-right corner resize behavior.

## Current Chat Runtime Boundary

Verified in `avatar-chat.js`:

- `avatar-chat.js` owns only compact Avatar chat submit/SSE behavior.
- It initializes once per `[data-avatar-chat]` and consumes `/api/chat/stream`.
- It does not own shell sizing or sticky behavior and should stay out of this repair unless a direct initialization bug is proven.

## Shell And Sticky Ancestor Risk

Verified in SimplyPages framework CSS:

- `.content-wrapper` has `overflow-y: auto` in `framework.css:333-337`.
- Sticky positioning is constrained by the nearest ancestor with a scrolling mechanism. Even when the page visually scrolls normally, an `overflow-y: auto` ancestor can change which box sticky sticks within.
- This is a stronger current hypothesis for "chat does not follow" than the old child-vs-container sticky claim alone.
- Browser validation must identify whether scrolling happens on `document.scrollingElement`, `.content-wrapper`, another shell wrapper, or an internal panel.

## Why The Prior Divider/Sticky Attempt Likely Failed

The previous implementation appears to have improved static layout but still relies on a thin divider column and `position: sticky` inside the SimplyPages shell. In browser terms, likely failure causes are:

- Sticky ancestor/overflow: SimplyPages `.content-wrapper { overflow-y: auto; }` can become the sticky containing ancestor and prevent the rail from pinning as expected during page scroll.
- Sticky height constraints: `.avatar-shell-rail` currently sets both min and max height to nearly the viewport height. If its parent/scroll container is not the actual scrolling box, sticky may appear inert or constrained.
- Grid gaps and divider width: the divider is a separate `0.85rem` grid track plus gap. A pointer target that is visually thin can be hard to hit and can feel broken even when events fire.
- JavaScript init after HTMX: tab swaps do not replace the shell, so the single-shot init is probably fine. If a future HTMX swap replaces the shell root, the new root should reinitialize, but validation should inspect actual event listeners by behavior.
- Pointer events: `aria-hidden="true"` does not block pointer events, but the old resizer is non-semantic and not keyboard/focus friendly. Its full-height rail affordance also no longer matches the user's desired interaction.
- CSS `resize` limitations: native CSS resizing changes the element box, not necessarily the grid track or persisted shell state. It would likely let the chat overflow rather than reliably shrinking/growing the dashboard.
- Header/shell constraints: the SimplyPages top banner/nav and `#content-area` wrapper add vertical and horizontal structure around Avatar. Sticky `top` must be chosen against the actual viewport margin after those elements scroll or remain visible.
- Stale state: old `localStorage` width can mask whether the current page is using default, clamped, or broken geometry.

## Existing Tests And Docs

- `AvatarDashboardControllerTest.avatarShellRendersCompactChatWidgetRootsAndScopedAssets` asserts script inclusion, chat roots, shell root, and current order rail -> resizer -> main.
- Docs already describe a left divider resize behavior in:
  - `docs/end-user/avatar-dashboard.md:16`
  - `docs/technical/avatar-dashboard-fragments.md:86-91`
- These docs must change from divider behavior to bottom-right corner behavior.

## Current Risks

- A CSS-only fix could pass static review but fail in the actual SimplyPages scroll container.
- Leaving the divider markup in place may cause workers or users to keep testing the wrong behavior.
- Native `resize` may not reflow the dashboard column.
- A fixed-position chat could satisfy follow behavior but break dashboard width accounting unless the main column is explicitly offset by the same CSS width.
- Mobile could inherit persisted desktop dimensions unless CSS and JS ignore sizing below `1181px`.

## Senior Engineer Notes

The important correction is investigative humility. Do not treat `position: sticky` as "done" because the CSS declaration exists. The validation worker must measure the chat rail before and after scrolling and must report the actual scroll container. If the nearest overflow ancestor is `.content-wrapper`, fix that scoped to Avatar before chasing more pointer math.

The old divider math is less suspect now than before because it is grid-relative in current code. The real issue is that the user does not want this interaction anymore, and the real-use failure shows that a full-height divider is the wrong surface to certify. Remove that ambiguity by making the bottom-right handle the only resize contract.
