# Avatar Agent UI Refactor Plan

## Context

This suite is the implementation and orchestration plan for the next `/avatar` pass. It replaces the current flat widget-position editor with a SimplyPages-style row/column decorator model, adds first-class Work Areas and file exploration, upgrades organizer data into planner-grade records, and wires assignment submission/output routing through the existing agent/project workspace runtime.

This is a planning artifact, not the implementation commit. Implementation should start from a clean worktree on this branch or a continuation branch and follow the lane ownership and validation gates in `orchestration.md`.

## Goal

Make `/avatar` feel and behave like a compact Magenta operational console aligned with `/dashboard` and `/agents`, while adding practical workspace controls for agent work: Work Areas, file browsing/editing, assignment Work Area selection, output redirects, planner/task organizer modals, and durable recurrence input.

## Source Inputs

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/avatar-schema.sql`
- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`
- `.internal-dev/notes/current-architecture-focus.md`
- SimplyPages docs:
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/reference/editing-api-reference.md`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
- SimplyPages demo:
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`
  - `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/pages/HtmxEditingDemoPage.java`

## Suite Files

- `implementation-plan.md`: senior-engineer design, schema/API contracts, and ordered implementation steps.
- `orchestration.md`: lane ownership, serialization points, subagent prompts, validation gates, and commit workflow.
- `validation-red-team.md`: automated, browser, runtime, and security acceptance gates.

## Non-Goals

- Do not implement a public email ingestion path.
- Do not build scheduler/contact-user/wait-for-input automation for planner tasks in v1.
- Do not replace the existing assignment/runtime architecture with an Avatar-specific runtime.
- Do not redesign `/dashboard`, `/agents`, `/chat`, or plan-chat behavior except where submit forms need Work Area/output picker controls.

## Exit Criteria For Implementation

- `/avatar` uses row plus 12-column dashboard layout editing with HTMX autosave and OOB refreshes.
- First-party widget instances are single-instance in v1 and added from a predefined catalog.
- Work Areas are persisted, explorable, selectable for assignments, and honored by runtime aliases and output routing.
- Planner task, recurrence, subtodo, calendar projection, and note-link data are durable in `avatar.sqlite`.
- Submit surfaces support Work Area and output redirect selection; plan chats do not.
- Runtime validation proves `workspace/`, `root/`, and output-routing behavior.
- Playwright subagent screenshots pass strict visual comparison against `/dashboard` and `/agents`.
