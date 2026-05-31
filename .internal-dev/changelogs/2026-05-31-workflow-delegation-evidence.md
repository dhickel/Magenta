# Date

2026-05-31

# Change Summary

Remediated GitHub issue #18 by making workflow `DELEGATION` nodes explicitly unsupported in the current alpha instead of fabricating completed child plan runs with empty outputs and evidence.

# Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`: fails delegation nodes with a clear unsupported message.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`: rejects delegation nodes during full/draft workflow validation.
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`: removes delegation from node type authoring controls and rejects manual delegation submissions.
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`: covers validation rejection and runtime bypass failure without child runs.
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`: covers editor hiding and manual-post rejection.
- `.internal-dev/specifications/services.md`: records the workflow delegation support boundary.
- `.internal-dev/specifications/api.md`: records the editor/API fragment behavior for unsupported delegation.
- `.internal-dev/knowledge/workflow-route-model.md`: documents delegation compatibility and runtime semantics.
- `docs/end-user/workflows.md`: documents the alpha limit for delegation nodes.
- `docs/technical/workflow-engine.md`: documents validation/runtime failure behavior for unsupported delegation.

# Behavioral Impact

Workflow definitions containing `DELEGATION` no longer validate or complete successfully. Direct runtime callers that bypass validation receive a failed workflow run and failed delegation node, with no fabricated child plan run.

# Specification Impact

Updated service and API specifications to make unsupported delegation an explicit alpha contract while keeping enum compatibility for saved serialized definitions.

# Risks

Existing saved workflow drafts that already contain delegation nodes must be edited to supported node types before they can validate or run. This is intentional to avoid false success history.

# Follow-up Items

Design real delegated subagent execution separately before making `DELEGATION` authorable or executable.
