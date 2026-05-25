---
schema_version: 1
document_type: implementation-notes
status: active
created: 2026-05-25
owner: unassigned
---

# Implementation Notes

## Phase Status

- 2026-05-25: Plan suite created. No product code changed in this planning pass.
- 2026-05-25: Phase 01 implementation replaced the divider resize contract with a bottom-right Avatar chat corner handle and prepared sticky/follow behavior for Playwright validation.
- 2026-05-25: Scoped AC6 remediation fixed the Playwright-reported max-height bounds failure by clamping the chat panel to the visible viewport space below its current top offset.
- 2026-05-25: Scoped AC7/AC8 remediation removed the non-scrolling Avatar `.content-wrapper` overflow ancestor and made the mobile corner handle hidden in both CSS and JavaScript media state.

## Decisions

- Replace divider-based resizing with a bottom-right chat corner handle.
- Use a small pointer handler, not native CSS `resize`, because dashboard width must respond to chat width.
- Require Playwright before certification because prior static-only fixes failed in real use.
- Keep the repair Avatar-scoped by using `avatar-content-area` as the content target hook; after browser evidence showed document scrolling, do not force the Avatar `.content-wrapper` into a non-scrolling overflow container.

## Phase 01 Implementation Evidence

- Failure hypothesis fixed: a combination of wrong user affordance and sticky containment. The prior page exposed a full-height `.avatar-chat-resizer` divider as the primary interaction, had no bottom-right chat corner handle, persisted only width, and left Avatar sticky behavior inside the SimplyPages `.content-wrapper` overflow context.
- Current scroll-container hypothesis: SimplyPages framework CSS defines `.content-wrapper { overflow-y: auto; }`; the relevant framework sticky-sidebar pattern keeps sticky sidebar and main content inside the same scroll-aware content area rather than escaping that ancestor.
- SimplyPages sticky-sidebar evidence inspected after main-thread direction:
  - `simplypages/src/main/resources/static/css/framework.css` defines `.content-wrapper.scrollable-wrapper` with viewport-bounded height and `overflow-y: auto`, `.page-content.with-sticky-sidebar` as an aligned flex row, `.sticky-sidebar-main { flex: 1; min-width: 0; }`, and `.sticky-sidebar-aside { position: sticky; top: 20px; max-height: calc(100vh - 100px); overflow-y: auto; }`.
  - `Page.withStickySidebar(...)` builds a main content area and sticky aside together, enables independent scrolling, and routes later content into the sticky main area.
  - Demo `DocsPage` uses `Page.builder().withStickySidebar(sidebar, 9, 3)` for markdown docs browsing.
- Initial sticky repair path: Avatar controller marks its content target with `avatar-content-area`, and Phase 01 used that class as the only ancestor hook to make the Avatar content wrapper viewport-bounded and scrollable. Phase 02 AC7 evidence showed that wrapper did not become the actual scroll container, so the current CSS keeps the wrapper `height: auto` and `overflow: visible`. The Avatar grid remains normal-flow two-column content, and `.avatar-shell-rail` keeps `position: sticky`, `top: 1.25rem`, a CSS-variable max height, and local overflow. No shared `magenta.css` or SimplyPages framework files were edited.
- Resize contract: `compactChat(...)` renders `button.avatar-chat-corner-resizer[data-avatar-chat-corner-resizer="true"]` inside `aside#avatar-chat`; the old `data-avatar-chat-resizer` divider markup was removed.
- CSS bounds: desktop grid is now `minmax(22.85rem, var(--avatar-chat-rail-width)) minmax(0, 1fr)` with normal column gap. Desktop chat height uses `--avatar-chat-panel-height`, minimum `360px`, and `--avatar-chat-panel-max-height` with a static fallback until JavaScript writes the top-aware value. Narrow layouts hide the corner handle, reset chat height to auto, and keep messages bounded by viewport height.
- JavaScript bounds: width clamps to at least `366px`, at most `640px`, and also preserves a `520px` dashboard minimum. Height clamps to at least `360px` and at most the visible viewport space below the chat panel's current top offset, leaving a `24px` bottom margin.
- Persistence compatibility: width keeps the existing `magenta.avatar.chatRailWidthPx` key; height adds `magenta.avatar.chatPanelHeightPx`. Saved values are clamped on restore, and restore is skipped below the desktop breakpoint.

## Phase 02 AC6 Remediation Evidence

- Measured failure addressed: Playwright found `#avatar-chat` could reach `height: 1000` while its viewport `y` was about `301px`, placing the bottom-right handle below the `1100px` viewport and preventing an extreme shrink drag.
- Fix path: `avatar-shell.js` now computes panel max height from `window.innerHeight - chat.getBoundingClientRect().top - 24px`, with the existing `360px` minimum fallback. The same computed value is written to `--avatar-chat-panel-max-height` so `.avatar-shell-rail` and `.avatar-chat` share the JS clamp.
- Persistence behavior: drag-stop persistence and restore both use the same top-aware clamp; an overlarge saved `magenta.avatar.chatPanelHeightPx` is rewritten to the clamped value during restore.
- Asset freshness: Avatar CSS was bumped to `/css/avatar-dashboard.css?v=2` and shell JS to `/js/avatar-shell.js?v=3` so the next live browser validation does not reuse the failed Phase 01 assets.

## Phase 02 AC7/AC8 Remediation Evidence

- Measured sticky failure addressed: Playwright reported `document.scrollingElement` as `HTML`, `scrollY` moved from `0` to `137`, and `#avatar-chat` moved from top `301.015625` to `164.015625` with `.avatar-shell-main`. That means the prior Avatar-scoped `.content-wrapper` height/overflow rule created a non-scrolling overflow ancestor while the document remained the real scroll path.
- Sticky fix path: `avatar-dashboard.css` now sets `.content-wrapper:has(> #content-area.avatar-content-area)` to `height: auto` and `overflow: visible`, removing the non-scrolling overflow ancestor without changing shared `magenta.css` or SimplyPages framework CSS. The existing desktop `.avatar-shell-rail { position: sticky; top: 1.25rem; }` can now resolve against document/viewport scrolling.
- Mobile handle fix path: the narrow media query now scopes the handle rule through the Avatar content wrapper and uses `display: none !important`, `visibility: hidden`, and `pointer-events: none`. `avatar-shell.js` also synchronizes the corner handle's `hidden`, `aria-hidden`, and `tabIndex` state from the same `min-width: 1181px` media query, so validators that inspect DOM state instead of computed display see the handle as inactive on mobile/tablet.
- Asset freshness: after the final AC7/AC8 remediation, the main rollout bumped Avatar CSS to `/css/avatar-dashboard.css?v=3` and shell JS to `/js/avatar-shell.js?v=4` before live browser validation.

## Investigation Evidence To Record

Implementation worker should append:

- actual scroll container found in browser or DevTools;
- whether `.content-wrapper` or `#content-area` overflow constrained sticky behavior;
- chosen sticky repair path;
- exact CSS/JS dimension bounds used;
- whether old localStorage values were compatible after clamping.

Validation worker should append:

- live app URL and port;
- browser viewport sizes used;
- screenshots saved;
- before/after bounding boxes for chat, dashboard main, and handle;
- scroll measurements proving sticky/follow behavior;
- console/network errors and whether they were expected.

## Blockers

- None.

## Validation Results

- `rg -n "data-avatar-chat-resizer|avatar-chat-resizer|divider" ...`: no active product markup, CSS, JS, or user docs retain the old divider resize contract. Matches remain only in implementation evidence, changelog history, and the controller test assertion that the old hook is absent.
- `mvn -Dtest=AvatarDashboardControllerTest test`: passed. Maven reported `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`: startup reached a live embedded Tomcat server on port `44021`, reported `Started Magenta2Application in 3.722 seconds`, then the timeout stopped the process with graceful shutdown. Command exit was `124` because the timeout intentionally terminated the running app after successful startup.
- 2026-05-25 AC6 remediation, `mvn -Dtest=AvatarDashboardControllerTest test`: passed. Maven reported `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- 2026-05-25 AC6 remediation, `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`: startup reached a live embedded Tomcat server on port `33889`, reported `Started Magenta2Application in 3.47 seconds`, then the timeout stopped the process with graceful shutdown. Command exit was `124` because the timeout intentionally terminated the running app after successful startup.
- 2026-05-25 AC7/AC8 remediation, `mvn -Dtest=AvatarDashboardControllerTest test`: passed. Maven reported `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- 2026-05-25 AC7/AC8 remediation, `git diff --check`: passed with no whitespace errors.
- 2026-05-25 AC7/AC8 remediation, `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`: startup reached a live embedded Tomcat server on port `36897`, reported `Started Magenta2Application in 3.082 seconds`, then the timeout stopped the process with graceful shutdown. Command exit was `124` because the timeout intentionally terminated the running app after successful startup.
- 2026-05-25 final main rollout, `mvn -Dtest=AvatarDashboardControllerTest test`: passed. Maven reported `Tests run: 14, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.
- 2026-05-25 final main rollout, `git diff --check`: passed with no whitespace errors.
- 2026-05-25 final main rollout live app: started on `http://localhost:18080/avatar` with isolated SQLite database `/tmp/magenta2-avatar-corner-resize-playwright.sqlite`; page source confirmed `/css/avatar-dashboard.css?v=3`, `/js/avatar-shell.js?v=4`, the new `data-avatar-chat-corner-resizer`, and no old `data-avatar-chat-resizer`.
- 2026-05-25 final Playwright validation: passed all acceptance checks with artifacts under `target/playwright-avatar-corner-resize-final/`. Geometry proved desktop expand changed chat width `480px -> 640px`, chat height `620px -> 675px`, and main width `821.20px -> 661.20px`; shrink changed chat width `640px -> 420px`, chat height `675px -> 455px`, and main width `661.20px -> 881.20px`; scroll changed `window.scrollY` `0 -> 1146`, pinned chat/rail top at `20px`, and moved main top to `-844.98px`; narrow `1024px` validation had no horizontal overflow and hid the handle with `hidden`, `display:none`, `visibility:hidden`, and `pointer-events:none`.
- Note on final Playwright metrics: `metrics.json` contains a stale/over-strict `stickyFollowOnScroll.pass:false` boolean because it expected a near-zero top delta from the pre-sticky starting position. The captured geometry and validation agent report show correct sticky behavior: before reaching the sticky threshold the rail started at `301.02px`; after document scroll it pinned at the configured `20px` top while the dashboard content continued upward to `-844.98px`.

## Remediation History

- 2026-05-25: Phase 02 Playwright validation failed only AC6 bounds usability. Scoped remediation changed the height clamp from a fixed viewport margin to a current-top-aware viewport calculation and aligned CSS to that computed max-height variable. Playwright revalidation is still required for certification.
- 2026-05-25: Phase 02 Playwright rerun failed AC7 sticky follow and AC8 mobile/narrow handle visibility. Scoped remediation removed the non-scrolling Avatar content-wrapper overflow/height rule so sticky follows document scroll, and added CSS plus JS media-state hiding for the corner handle below `1181px`. Playwright revalidation is still required for certification.
- 2026-05-25: Final Playwright validation passed after asset version bumps to CSS `v=3` and shell JS `v=4`; no further remediation required.

## Senior Engineer Notes

Keep this file as the handoff ledger. The next agent should not have to infer what happened from terminal history. If Playwright fails, record the measured failure in this file before any fix worker starts so the revalidation loop has a concrete target.
