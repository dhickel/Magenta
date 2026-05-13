# 2026-05-12 - Phase 04 Workflow Builder Redesign

## Change Summary
Introduced route-aware workflow definitions with explicit graph edges (WorkflowRoute), a graph validator (WorkflowValidator), route-aware execution in WorkflowRunner, and an HTMX-first workflow builder UI in OrchestrationController. Replaced the old sequential-node-index execution model and raw JSON binding editor with a structured node/tree editor using CRUD HTMX partials and submit-to-agent flow.

## Files
- Created: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRoute.java`
- Created: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRouteType.java`
- Created: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- Modified: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowDefinition.java` — added `routes` field and graph helpers
- Modified: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowNode.java` — added `label`, `inputName`, `config`; deprecated old constructor
- Modified: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java` — added `routes_json` column with migration
- Modified: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java` — compatibility importer, graph validation
- Modified: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java` — graph-traversal execution with route semantics
- Modified: `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java` — workflow HTMX partials, submit-to-agent, removed run UI
- Modified: `src/main/resources/static/js/orchestration/workflows.js` — stripped to 12-line skeleton
- Modified: `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java` — updated workflow tests, added StubWorkflowService

## Behavioral Impact
- Workflow execution now follows dependency graph order (routes determine readiness) instead of sequential node index.
- LOG routes materialize output artifacts without creating downstream dependencies.
- PASS_THROUGH routes forward all source outputs as a map to the downstream node.
- MAP_OUTPUT routes map a single source output to a single destination input.
- Old inputBindings are auto-imported to routes on save (STEP_OUTPUT becomes MAP_OUTPUT route).
- The workflow page no longer has a Run button or run panel; Submit to Agent creates a WORKFLOW_RUN assignment.
- workflows.js is a 12-line skeleton; all CRUD goes through HTMX.
- Backward-compatible: old WorkflowNode constructors and WorkflowDefinition constructor without routes are preserved as `@Deprecated`.

## Risks
- The graph traversal runner rewrites the core execution loop. Regression risk is mitigated by all 15 existing WorkflowRunnerTest tests passing.
- The routes_json column migration is online (ALTER TABLE ADD COLUMN with default); works for SQLite in-memory and on-disk.
- The compatibility importer runs on every save; it may generate route IDs that collide with explicitly created routes if ID generation logic is not coordinated.

## Follow-up Items
- Fan-out parallelism (multiple nodes executing concurrently) is deferred for future phase.
- Conditional route expressions beyond simple route types are out of scope.
- The structured tree editor is HTML-based; a canvas graph editor remains a future consideration.
