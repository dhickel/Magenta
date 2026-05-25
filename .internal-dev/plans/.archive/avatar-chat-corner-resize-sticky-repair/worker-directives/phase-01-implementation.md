---
schema_version: 1
document_type: worker-directive
phase: 01
role: implementation_worker_agent
model: gpt-5.5
reasoning: medium
mutating: true
status: active
created: 2026-05-25
owner: unassigned
---

# Phase 01 Implementation Directive

## Objective

Investigate why the prior Avatar chat divider/sticky fix failed in browser terms, then implement the small repair: remove reliance on divider resizing and make the left Avatar chat module resize from a bottom-right corner handle while staying pinned during page scroll.

## Required Supporting Docs To Read

- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/00-specification-lock.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/01-current-state-analysis.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/02-target-design.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/shared/senior-engineer-guidance.md`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `.internal-dev/knowledge/avatar-shell-resizable-rail-geometry.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `docs/AGENTS.md`

## Exact Editable Files

You may edit only:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java` only if needed for Avatar-specific shell/content classing or asset version bump
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-shell.js`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/changelogs/2026-05-25-avatar-chat-corner-resize-sticky-repair.md`
- `.internal-dev/plans/avatar-chat-corner-resize-sticky-repair/shared/implementation-notes.md`
- `.internal-dev/focus/unfinished-work.md` only if work is intentionally left incomplete, blocked, paused, or deferred

## Forbidden Scope

- Do not edit `avatar-chat.js` unless you first document a direct chat initialization bug in `shared/implementation-notes.md`.
- Do not change chat service, SSE endpoints, model routing, Avatar persistence, Work Area services, `/chat`, `/dashboard`, `/agents`, shared `magenta.css`, or SimplyPages framework code.
- Do not add a server-backed size preference.
- Do not retain a divider as the primary resize affordance.

## Investigation Steps

1. Inspect the current rendered shell and CSS/JS code before editing.
2. Identify the likely sticky containing ancestor from CSS. Pay special attention to SimplyPages `.content-wrapper { overflow-y: auto; }`.
3. Record in `shared/implementation-notes.md` what failure mode you are fixing: divider UX, missing corner resizing, sticky ancestor/overflow, stale localStorage, or a combination.
4. If you need to edit outside the allowed files to make sticky work, stop and report instead of broadening scope.

## Implementation Steps

1. Replace the divider-based resize contract:
   - remove or de-emphasize `.avatar-chat-resizer` markup and behavior;
   - add a bottom-right handle inside `compactChat(...)` with a stable hook such as `data-avatar-chat-corner-resizer="true"`.
2. Change desktop grid CSS to two columns so chat width directly controls dashboard width:
   - left rail `minmax(<rail-min>, var(--avatar-chat-rail-width))`;
   - dashboard `minmax(0, 1fr)`;
   - use ordinary `column-gap`.
3. Add CSS for the corner handle:
   - visible at the bottom-right of chat on desktop;
   - hidden on `max-width: 1180px`;
   - small but easy to hit;
   - does not overlap the textarea or send button in a way that blocks use.
4. Add `--avatar-chat-panel-height` behavior:
   - desktop chat uses the variable for height;
   - messages flex and remain internally scrollable;
   - mobile ignores saved desktop dimensions.
5. Rewrite `avatar-shell.js` resize behavior:
   - bind to `[data-avatar-chat-corner-resizer='true']`;
   - compute width/height from pointer deltas relative to the starting chat box;
   - clamp width against grid width and minimum dashboard width;
   - clamp height against viewport height and minimum chat height;
   - persist width under `magenta.avatar.chatRailWidthPx`;
   - persist height under `magenta.avatar.chatPanelHeightPx`;
   - clamp again on restore.
6. Repair sticky/follow behavior:
   - keep the rail in normal flow and sticky on desktop if browser constraints permit;
   - if `.content-wrapper`/`#content-area` overflow is the blocker, use a scoped Avatar-only CSS or shell class approach;
   - do not make global shell overflow changes.
7. Update tests:
   - assert the corner handle hook is present;
   - assert the old divider hook is absent or no longer primary;
   - assert rail/main order remains correct;
   - assert script asset version if changed.
8. Update docs:
   - end-user doc says bottom-right corner drag, not divider drag;
   - technical doc describes width/height localStorage keys and corner handler.
9. Add the changelog entry and update implementation notes with commands/evidence.

## Experience Contract

Desktop:

- Chat is a compact operational panel on the left.
- The resize affordance is visually a bottom-right corner handle.
- Dragging right/down makes the chat wider/taller; dragging left/up makes it narrower/shorter.
- Dashboard content immediately gains or loses horizontal room as chat width changes.
- Chat remains at the top page-view margin after the user scrolls down a long dashboard.
- Transcript scroll remains internal; composer remains reachable.

Mobile/narrow:

- Shell stacks cleanly.
- Resize handle is hidden.
- Desktop width/height persistence does not cause horizontal overflow or clipped chat.

Visual failure examples:

- A full-height divider remains the thing users must drag.
- The corner handle moves chat but dashboard width does not respond.
- Chat scrolls away with dashboard content.
- The handle covers the text input or submit button.
- Mobile shows a squeezed desktop split or horizontal overflow.

## Acceptance Criteria

Meet AC1-AC9 from `00-specification-lock.md`. AC10 is validated by Phase 02, but your implementation must support it.

## Negative Checks

- Search for `data-avatar-chat-resizer` after changes. If it remains, document why it is not active/primary.
- Search docs for "divider" and ensure it is not the user resize instruction.
- Clear old localStorage and reload in a browser manually if you run a local smoke check.
- Do not claim sticky is fixed without browser evidence; record it as implementation-ready for Playwright.

## Validation Commands

Run:

```bash
mvn -Dtest=AvatarDashboardControllerTest test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Record command results in `shared/implementation-notes.md`. If startup is blocked by local services or secrets, record the exact blocker and do not claim full code validation.

## Stop Conditions

- Required behavior cannot be met without editing outside allowed files.
- Sticky follow requires SimplyPages framework changes.
- App startup cannot be attempted.
- Tests fail in a way unrelated to this change and cannot be isolated safely.

## Senior Engineer Notes

Keep this repair boring. The layout should be a grid with one variable-width rail and one flexible dashboard. The JS should only calculate clamped dimensions and write CSS variables. Do not add watchers, timers, mutation observers, or a state store.

The most likely subtle bug is still ancestor overflow. Before spending time changing rail heights, check whether the shell wrapper is the scroll/sticky container. A scoped Avatar overflow fix is more likely to repair follow behavior than another round of `top` tweaking.

## Do Not Close Unless

- [ ] You recorded the failure hypothesis you fixed.
- [ ] Desktop has a bottom-right corner handle, not a divider dependency.
- [ ] Horizontal drag updates the chat width variable that controls the grid column.
- [ ] Vertical drag updates chat panel height.
- [ ] Desktop and mobile CSS paths are separated.
- [ ] Focused Maven test passes or exact blocker is recorded.
- [ ] Bounded Spring startup passes or exact blocker is recorded.
- [ ] Docs and changelog are updated.
- [ ] Implementation notes contain validation evidence and any residual risk.
