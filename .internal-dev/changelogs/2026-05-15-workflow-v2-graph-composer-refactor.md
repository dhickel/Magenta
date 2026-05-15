# Date
2026-05-15

# Change Summary
Implemented a breaking workflow v2 refactor for orchestration workflows:
- v2 definition contract now carries explicit schema fields (`schemaVersion`, `maxConcurrency`, `uiLayout`) and typed node ports.
- save/validate gates now enforce strict v2 graph constraints (typed routing for task ports, legacy binding rejection, control-route branch checks, DAG validation, and required input coverage).
- runtime execution moved to parallel ready-node scheduling with configurable max concurrency, deterministic join handling, approval gate resume branching, branch skipping for non-selected control paths, and persisted `finalOutputs` + artifact references.
- workflow run persistence now stores final output payloads and artifact id lists.
- workflow UI gained a JS-heavy graph composer surface (palette, canvas, side panel, route builder, diagnostics, save/validate).

# Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowDefinition.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowNode.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowPort.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRoute.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowNodeType.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRun.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowNodeRun.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/resources/schema.sql`
- `src/main/resources/static/js/orchestration/workflows.js`
- `src/main/resources/static/css/orchestration.css`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

# Behavioral Impact
- Workflow CRUD/validation/run paths now treat the v2 graph contract as canonical.
- Workflow runs return richer state including final outputs and artifact ids.
- Workflow execution semantics now support true parallel fan-out and explicit approve/reject branch routing.
- `/workflows` now exposes a JS graph composer instead of a minimal skeleton script.

# Risks
- Existing clients that depended on legacy binding semantics or looser control-route behavior may fail validation and require payload updates.
- Parallel node execution can expose latent assumptions in task template side effects if templates are not isolation-safe.

# Follow-up Items
- Re-enable Playwright MCP browser validation once MCP transport/profile lock is resolved.
- Consider extending graph composer to use server HTMX fragment actions for save/validate flows if strict HTMX mediation is required for this surface.
