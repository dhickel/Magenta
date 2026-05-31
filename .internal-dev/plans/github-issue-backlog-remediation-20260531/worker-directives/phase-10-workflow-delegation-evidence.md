# Phase 10 Worker Directive: Workflow Delegation Completion Evidence (#18)

## Objective

Remediate GitHub issue #18 so `DELEGATION` workflow nodes cannot fabricate completed child plan runs without real delegated execution evidence.

## User-Visible Outcome

Workflow run history no longer shows false delegated success; unsupported delegation is rejected or held explicitly instead of auto-completing.

## Issues

- #18 `Workflow: DELEGATION nodes can fabricate completed child plan runs`

## Direct Targets

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowNodeType.java` only if unsupported status/validation changes need docs
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java` only if UI currently exposes unsupported delegation
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java` only if a real existing execution boundary is used without broad refactor
- Tests:
  - `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
  - `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java` if UI exposure changes
- Docs/specs:
  - `.internal-dev/specifications/services.md`
  - `.internal-dev/specifications/api.md` if UI/API validation changes
  - `.internal-dev/knowledge/workflow-route-model.md` if node semantics are documented there
  - `.internal-dev/changelogs/2026-05-31-workflow-delegation-evidence.md`

## Forbidden Scope

- Do not build a full delegated subagent runtime unless already available and simple to call.
- Do not mark child runs completed with empty outputs/evidence.
- Do not remove enum values without migration/user decision.

## Supporting Docs To Read

- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/knowledge/task-execution-placeholder-test-gap.md`
- `.internal-dev/knowledge/workflow-route-model.md`

## Reproduction/Triage Required Before Fix

Before implementing, determine whether `DELEGATION` is supported in current alpha:

- Inspect UI/API exposure in `OrchestrationController`.
- Add a test around `WorkflowRunner.executeDelegationNode` or an executable workflow with `DELEGATION` that currently creates a completed child plan run with empty evidence.
- Confirm whether a real chat-backed execution path exists that can be safely invoked in this scope.

## Implementation Steps

1. Add reproduction test proving fabricated completion is impossible after the fix.
2. If delegation is not supported, make validator/runtime reject or fail `DELEGATION` nodes with a clear message.
3. If a safe existing execution path is available, route delegation through it and complete only after real terminal evidence.
4. Ensure workflow run status reflects waiting/failed/real completion accurately.
5. Update UI labels/docs if `DELEGATION` remains visible but disabled/unsupported.
6. Update specs/changelog.

## Senior-Engineer Guidance

- A clear unsupported error is better than a fake completed child run.
- Completion evidence should include child run id, terminal status, outputs, and validation evidence when real execution happens.
- Keep fix bounded; broader delegation/subagent orchestration can be deferred as stale naming/deferred capability.

## Acceptance Criteria

- `DELEGATION` cannot auto-complete a child run with empty outputs/evidence.
- Runtime and validator behavior agree.
- Tests prove the old fabricated success path is gone.
- User-facing/editor behavior does not invite unsupported delegation as if it works.

## Negative Checks

- No placeholder/default outputs.
- No silent completed child run.
- No broad workflow engine rewrite.

## Validation Commands

- `mvn -q -Dtest=WorkflowRunnerTest test`
- Add controller tests if UI exposure changes.
- Bounded startup if controller/bean wiring changes.

## Evidence Expectations

- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-10-validation-report.md`

## Closeout Expectations

Main thread closes #18 after validation, commit, push, and email.

## Stop Conditions

- Stop if real delegation support requires a new product/runtime design.

## Do Not Close Unless

- Test coverage proves no fabricated completion remains.
