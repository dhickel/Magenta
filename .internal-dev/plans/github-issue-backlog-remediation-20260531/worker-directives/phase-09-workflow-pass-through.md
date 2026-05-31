# Phase 09 Worker Directive: Workflow PASS_THROUGH Semantics (#17)

## Objective

Remediate GitHub issue #17 so workflow `PASS_THROUGH` validation and runtime match documented full-output forwarding semantics.

## User-Visible Outcome

Workflow authors can use `PASS_THROUGH` routes without source/target ports to forward the complete source output map to a downstream node.

## Issues

- #17 `Workflow: PASS_THROUGH routes validated and executed as single-port mappings`

## Direct Targets

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRoute.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRouteType.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java` only if editor route forms require update
- Tests:
  - `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
  - `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java` only if UI/editor changes
- Docs/specs:
  - `.internal-dev/knowledge/workflow-route-model.md`
  - `.internal-dev/specifications/api.md` if payload changes
  - `.internal-dev/specifications/services.md`
  - `.internal-dev/changelogs/2026-05-31-workflow-pass-through.md`

## Forbidden Scope

- Do not add conditional routing or parallel execution.
- Do not alter `MAP_OUTPUT`, `CONTROL`, or `LOG` semantics except where shared helper logic requires safe cleanup.
- Do not remove compatibility for existing saved single-port pass-through routes unless a migration/user decision is made.

## Supporting Docs To Read

- `.internal-dev/knowledge/workflow-route-model.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/services.md`

## Reproduction Probe Required Before Fix

Add a minimal workflow fixture/test that:

- Defines a source node with multiple outputs.
- Defines a `PASS_THROUGH` route with no ports.
- Validates the graph successfully.
- Executes/materializes downstream inputs containing the full source output map.

Also add a compatibility test for old port-bearing pass-through if current saved data may contain it.

## Implementation Steps

1. Add validator tests showing no-port `PASS_THROUGH` is accepted and port-required errors do not fire.
2. Add runtime test showing full-map forwarding.
3. Update `WorkflowValidator.validateRoutes` to treat `PASS_THROUGH` separately from port-based data routes.
4. Update `WorkflowRunner.resolveNodeInputs` so `PASS_THROUGH` merges or nests the full source output map according to documented contract. Preferred: merge all source outputs into target inputs unless docs specify a wrapper key.
5. Update editor/API docs if route form assumptions change.

## Senior-Engineer Guidance

- The existing knowledge file says `fromOutputName` and `toInputName` are null for `PASS_THROUGH`; code must match that.
- Decide and document collision behavior when pass-through maps contain keys already present in node config or other routes.
- Preserve topological dependency behavior: `PASS_THROUGH` still creates a dependency.

## Acceptance Criteria

- No-port `PASS_THROUGH` validates.
- Runtime forwards all source outputs deterministically.
- Existing `MAP_OUTPUT` type checking remains intact.
- Docs/knowledge/specs agree with code.

## Negative Checks

- Do not silently drop source output fields.
- Do not require ports for `PASS_THROUGH`.
- Do not break route cycle/dependency detection.

## Validation Commands

- `mvn -q -Dtest=WorkflowRunnerTest test`
- Add `OrchestrationControllerTest` if UI/editor code changes.
- Bounded startup if route binding/controller changes.

## Evidence Expectations

- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-09-validation-report.md`

## Closeout Expectations

Main thread closes #17 after validation, commit, push, and email.

## Stop Conditions

- Stop if product decision is needed for merge-vs-nest pass-through collision semantics and no current spec resolves it.

## Do Not Close Unless

- Reproduction fixture covers saved workflow validation and runtime input materialization.
