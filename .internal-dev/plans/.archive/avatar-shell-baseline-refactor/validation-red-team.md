# Avatar Shell Baseline Refactor Validation Red Team

## Context

This validation plan exists to stop a superficially functional Avatar shell refactor from being accepted with broken operational UX, hidden layering regressions, or weak cross-tab behavior. It applies after implementation of the shell baseline plan in this directory.

## Goal

Prove that `/avatar` behaves like an operational Magenta surface, not a loose widget page with tabs bolted on. The pass must verify persistent right-rail behavior, dashboard-only editability, correct row-decoration layering, and visual consistency with `/agents`.

## In Scope

- `/avatar`
- `/avatar?edit=true`
- Avatar tab switching
- Avatar right chat rail
- Desktop divider resize persistence
- Mobile stacked layout
- Visual comparison against `/agents`
- Deferred refresh policy confirmation

## Out Of Scope

- Future interval-refresh behavior
- Planner automation
- Broader `/dashboard` or `/chat` redesign
- Deep end-to-end business workflow testing outside the focused Avatar shell scope

## Implementation Steps

1. Run targeted automated tests for Avatar controller/service changes.
2. Run bounded Spring startup with the app live.
3. Run focused Playwright checks against the live app using `gpt-5.3-codex` with reasoning `high`.
4. Capture screenshots for:
   - `/avatar` desktop default
   - `/avatar?edit=true` desktop
   - `/avatar` mobile
   - each non-dashboard tab at desktop
   - desktop divider drag state before and after reload
   - a comparison `/agents` screen used as visual reference
5. Review screenshots for:
   - tab-row consistency
   - border/radius/spacing density
   - persistent rail behavior
   - row-decoration visibility
   - overflow and dead-space problems
6. Confirm `.internal-dev/focus/unfinished-work.md` records the deferred auto-refresh follow-up.

## Validation

### Automated Expectations

- Avatar shell render tests pass.
- Tab fragment route tests pass.
- Preference persistence tests pass if added.

### Browser Expectations

- Tab changes update only the left panel content, not the entire page.
- Chat rail remains present with transcript and composer intact while tabs change.
- Desktop divider drag changes rail width immediately and persists after a reload.
- Mobile shows no drag handle and no horizontal overflow.
- Dashboard edit mode still works, but non-dashboard tabs show no layout-edit controls.
- Row decoration renders above module edit controls in screenshots.

### Visual Failure Conditions

Reject the implementation if any of the following appear:

- bulky text-button toolbar behavior returns;
- Organizer or manual refresh is still visible at the shell level;
- tab row looks materially different from agent tabs without a justified reason;
- row move controls render behind widget chrome;
- persistent chat rail disappears or remounts on tab switches;
- divider width resets on tab change or reload;
- desktop leaves large incoherent dead zones between content and rail;
- mobile produces clipped text, overflow, or a visible drag affordance;
- non-dashboard tabs appear as random widget collections instead of operational panels.

## Exit Criteria

- All targeted tests pass.
- Bounded startup succeeds or any blocker is explicitly documented.
- Playwright screenshots and critique accept the shell visually and behaviorally.
- Deferred auto-refresh work is recorded as unfinished/deferred work.
- Remaining issues, if any, are documented as specific follow-up items rather than silently accepted.
