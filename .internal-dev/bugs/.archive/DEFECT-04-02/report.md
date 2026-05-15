# DEFECT-04-02: Task Nodes Are No-Ops Without Wired taskNodeExecutor

## Summary
Task nodes in workflows complete instantly with empty `outputValues: {}` because `taskNodeExecutor` is null. The fallback `planService.startRun()` creates a PlanRun record but does not execute the plan through Docker. Workflows are effectively non-functional for task execution.

## Scope
- `WorkflowRunner.executeTaskNode()` checks `taskNodeExecutor != null` before dispatching
- `taskNodeExecutor` is wired by `ChatService` which is not active in the workflow execution context
- Fallback path: `planService.startRun()` creates PlanRun → insta-completes without Docker/model call
- Affects all workflow runs with task nodes

## Reproduction
1. Create a workflow with a task node referencing a valid finalized plan
2. Submit workflow to agent: `POST /api/workflows/{id}/runs`
3. Poll run status: `GET /api/workflow-runs/{runId}`
4. Task node completes in ~3ms with `outputValues: {}`
5. No container activity, no model invocation

## Expected
Task node execution should follow the same Docker-backed execution path as direct plan submission (`POST /plans/_submit/{planId}`), which runs the plan through the model in the agent's Docker container and produces real outputs.

## Actual
Task node completes instantly. No Docker execution occurs. No output values are produced.

## Evidence
- Phase 04 evidence file: `.internal-dev/reviews/docker-backed-alpha-e2e-validation/04-workflows-gates-inbox-resume-evidence.md`
- Run `2c25ba45-...` and `02812ad5-...` both show task nodes COMPLETED with empty outputs
- Log: `planService.startRun() allocated temp/output dirs // No Docker execution, no model call // Completed instantly`

## Impact
**Alpha blocker.** The core workflow pipeline is non-functional for task execution. Users can build and validate workflows but execution produces no real results.

## Status
Fixed

## Resolution
Modified `WorkflowRunner.executeTaskNode()`:
- Removed the `planService.startRun()` fallback entirely — no production path uses it as fake workflow task execution fallback
- When `taskNodeExecutor` is null, throws `RuntimeException("Task node execution requires model-backed task execution")`
- When executor is available, calls `taskNodeExecutor.execute()` and returns `planRun.outputValues()`
- If `PlanRun.status()` is FAILED or NEEDS_REVIEW, throws `RuntimeException` with status and error text
- The exception is caught by `executeFromCheckpoint()` which marks the node FAILED and the run FAILED

## Evidence
- `mvn -Dtest=WorkflowRunnerTest test` passes all 23 tests including:
  - `taskNodeFailsWithoutExecutor` — run FAILED with "requires model-backed task execution"
  - `taskNodeWithExecutorReturnsOutputs` — run COMPLETED, outputValues contains "executed-successfully"
  - `taskNodeWithExecutorReturnsOutputsAndRoutesDownstream` — 2-node workflow routes output values from source to dest
- `mvn test` passes except one pre-existing failure in `WorkspaceLeaseServiceTest.acquireWritable_workspaceNotFoundThrows` (unrelated)

## Next Action
None — fix verified.
