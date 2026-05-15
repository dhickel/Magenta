## Date

2026-05-14

## Change Summary

Fixed the workflow creation entry point so the Workflows page no longer exposes a bare title/summary-only new workflow form as the primary creation surface. The workflow list now renders server-side fallback content immediately, and the New Workflow action creates a persisted draft before opening the full node, route, validation, submit, and delete editor.

Also fixed the default local database migration path for older `workflow_definitions` tables that still had `steps_json` but not `nodes_json`. Fresh restarts against an existing `chat-memory.db` now add graph columns instead of returning 500 on `/workflows`.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

## Behavioral Impact

- `/workflows` no longer depends on HTMX to replace a permanent `Loading...` placeholder before useful list content exists.
- `New Workflow` now targets `/workflows/_editor/_draft` with HTMX POST and opens the full editor surface immediately.
- The legacy title/summary-only editor endpoint remains available for compatibility, but it is no longer the primary page action.
- Existing local SQLite databases with the old workflow table shape are migrated in-place with empty graph columns.

## Risks

- The New Workflow action now persists an untitled draft immediately. This is intentional so node and route CRUD endpoints have a workflow id, but abandoned drafts may appear in the list.
- Legacy `steps_json` workflow rows are exposed as empty graph drafts rather than deeply converted, because old sequential bindings do not map cleanly to the route-aware editor without explicit user review.

## Follow-up Items

- Consider adding an explicit discard-empty-draft cleanup flow if abandoned drafts become noisy during operator use.
