# Avatar Visual Layout Refactor Plan Suite

## Status

Archived after implementation and validation on 2026-05-23.

## Objective

Refactor `/avatar` from the visually broken widget collage shown in the May 23 screenshots into a dense, balanced operational console with in-place layout editing. The work also hardens project instructions so future agents must inspect SimplyPages examples, test UI changes visually with Playwright, and judge practical layout quality rather than only confirming that elements render.

## Plan Files

- `00-advanced-plan.md`: durable implementation plan and acceptance criteria.
- `01-orchestration-plan.md`: execution graph, subagents, validation gates, email checkpoints, and closeout contract.

## Required Source References

- Root `AGENTS.md`.
- `src/main/java/io/mindspice/magenta2/avatar/AGENTS.md`.
- `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md`.
- `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`.
- SimplyPages docs under `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs`.
- SimplyPages demo controller `/home/hickelpickle/Code/Java/cannasite/java-html-framework/demo/src/main/java/io/mindspice/demo/EditingDemoController.java`.
