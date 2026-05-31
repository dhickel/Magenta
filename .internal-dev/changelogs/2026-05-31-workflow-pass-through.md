# Date

2026-05-31

# Change Summary

Implemented workflow `PASS_THROUGH` full-map forwarding semantics for GitHub issue #17. No-port pass-through routes now validate as dependency routes and forward every source output into downstream inputs. Existing saved pass-through routes with both ports remain compatible as single-port mappings.

# Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`: split pass-through validation from port-mapped data-route validation and made no-port pass-through satisfy downstream required inputs when source outputs are known.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`: materializes canonical pass-through by deterministically merging all source outputs into target inputs.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRoute.java`: clarified route comment.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRouteType.java`: clarified pass-through enum comment.
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`: added no-port validation/runtime reproduction coverage and legacy port-bearing compatibility coverage.
- `.internal-dev/knowledge/workflow-route-model.md`: documented canonical no-port pass-through, legacy port-bearing compatibility, deterministic merge order, and collision behavior.
- `.internal-dev/specifications/services.md`: added the service contract for pass-through validation/materialization.
- `docs/technical/workflow-engine.md`: documented route materialization semantics.
- `docs/end-user/workflows.md`: documented author-facing pass-through usage and collision behavior.

# Behavioral Impact

Workflow authors can leave pass-through route port fields blank to forward a full source output map. Runtime input resolution applies pass-through keys in sorted source-output key order; later incoming routes overwrite earlier route values for matching keys, and node config remains the final override. Existing port-bearing pass-through routes continue to run as single-port mappings.

# Specification Impact

Updated `services.md` because workflow route validation and materialization are service-owned behavior. `api.md` was not changed because the payload shape did not change; nullable route port fields were already supported.

# Risks

Full-map pass-through can populate more downstream inputs than an old single-port pass-through route. The deterministic collision rules are documented, and compatibility behavior is preserved for saved routes with both ports.

# Follow-up Items

Independent validation should confirm issue #17 acceptance criteria and prepare the phase validation report.
