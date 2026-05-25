# Avatar Shell Baseline Refactor Plan

## Context

This suite plans the next `/avatar` UI pass after the earlier layout-editing and visual-polish work. The goal of this pass is to establish the durable shell and interaction baseline for Avatar: agent-style tabs, a persistent right chat rail, compact top-level controls, row decorations that layer correctly above modules, and a dashboard that remains the only user-editable layout surface.

This is a planning artifact only. It is intended to hand implementation to scoped subagents without requiring them to invent UI structure, state persistence, validation depth, or sequencing.

## Goal

Make `/avatar` feel like the same operational family as `/agents` and `/dashboard` while preserving Avatar-specific behavior:

- a persistent right chat rail across all Avatar tabs;
- a single editable dashboard tab for widgets and layout;
- agent-style queue/history/profile/outputs/work-area tabs that swap in place without full page reloads;
- compact icon-first edit controls instead of the current bulky toolbar;
- no top-level Organizer button and no manual refresh button.

## Source Inputs

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-chat.js`
- `src/main/resources/static/js/avatar-layout-edit.js`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarService.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarRepository.java`
- `src/main/java/io/mindspice/magenta2/avatar/AvatarPreference.java`
- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`
- `.internal-dev/reviews/2026-05-23-avatar-high-level-design-review.md`
- `.internal-dev/notes/current-architecture-focus.md`

## Suite Files

- `implementation-plan.md`: decision-complete design and ordered implementation steps.
- `orchestration.md`: serial edit phases, delegated agent roster, validation gates, and shared-notes policy.
- `validation-red-team.md`: UI, persistence, and behavior acceptance checks with explicit failure triggers.
- `shared-notes.md`: repo-local coordination template for the future execution run.

## Non-Goals

- Do not implement interval refresh in this pass.
- Do not make non-dashboard Avatar tabs user-layout-editable.
- Do not add a new queue/history runtime or data model outside existing services and widgets.
- Do not reintroduce modal-first layout editing or a top-level Organizer action.
- Do not redesign `/agents`, `/dashboard`, or `/chat`; only reuse their operational language and patterns.

## Exit Criteria For Implementation

- `/avatar` renders a unified shell with in-place tab switching and a persistent right chat rail.
- Dashboard remains the only layout-editable tab and uses compact icon-driven edit controls.
- Row decoration renders above module-edit controls instead of falling behind widget chrome.
- Queue, History, Profile, Outputs, and Work Areas tabs use existing services and look consistent with agent operational tabs.
- Active Avatar tab persists through URL state, and desktop chat-rail width persists across tab swaps and reloads through browser-local state.
- Manual refresh is removed from the UI, and deferred auto-refresh work is recorded in `.internal-dev`.
- Playwright validation proves visual consistency against `/agents`, working tab swaps, correct mobile stacking, and desktop rail resizing persistence.
