# Date

2026-05-14

# Change Summary

Canonicalized production workflow execution on `io.mindspice.magenta2.ai.orchestration.workflow`, retired the legacy chat workflow service from Spring production wiring, added model-backed task-node execution through `WorkflowTaskExecutor`, and expanded the workflow builder with schema-aware route selectors plus validation-gated high-priority submit-to-agent behavior.

# Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowTaskExecutor.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowNodeType.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowStreamSupport.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
- `.internal-dev/knowledge/workflow-route-model.md`
- `.internal-dev/notes/alpha-deferred-targets.md`
- `.internal-dev/notes/operational-ui-contract-missing-features.md`

# Behavioral Impact

- `WORKFLOW_RUN` assignments and job workflow items execute canonical orchestration workflow runs instead of the legacy linear chat workflow tables.
- Workflow task nodes execute through the same chat-backed task path as orchestration assignments and propagate persisted `TaskRun.outputValues()`.
- Approval rejection fails the workflow and approved gates resume execution.
- Validation, copy/fan-out, and log/materialize control nodes have deterministic runner behavior.
- The workflow editor no longer relies on raw free-text route fields for task schema selection and blocks submit-to-agent when validation has errors.
- Workflow submit-to-agent defaults to high priority.

# Risks

- The legacy `ai.chat.workflow` package remains in source as deprecated migration/test reference, but it is no longer registered as a Spring production bean.
- Conditional route evaluation, cyclic retry loops, parallel ready-node execution, and drag-canvas editing remain intentionally deferred.
- Browser-level workflow creation and approval validation still needs a Playwright MCP pass with real task fixtures.

# Follow-up Items

- Run the full Playwright MCP workflow-builder scenario with task A, validation/log/control, approval, and task B once suitable live fixtures are available.
- Decide when to delete the deprecated `ai.chat.workflow` package entirely.
- Implement deferred workflow language/canvas features only after the alpha acyclic route contract is stable.
