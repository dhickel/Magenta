# Phase 08 Worker Directive: Run Display Name Boundary (#16)

## Objective

Remediate GitHub issue #16 by enforcing the user-visible `runDisplayName` contract across all non-job task/workflow assignment entry points.

## User-Visible Outcome

All public non-job task/workflow submissions require and persist a run display name consistently.

## Issues

- #16 `Assignments: Some TASK_RUN entry points bypass required user-visible run name`

## Direct Targets

- `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentRequest.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentTemplateParser.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- Tests:
  - `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java`
  - `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
  - `src/test/java/io/mindspice/magenta2/api/web/PublicApiRouteBindingTest.java`
  - `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentContextServiceTest.java`
- Docs/specs:
  - `.internal-dev/specifications/api.md`
  - `.internal-dev/specifications/services.md`
  - `docs/api/00-index.md` or route-specific API docs
  - `docs/technical/workspaces-tools-outputs.md` if assignment submission docs change
  - `.internal-dev/changelogs/2026-05-31-run-display-name-boundary.md`

## Forbidden Scope

- Do not require `runDisplayName` for `JOB_RUN` submissions where job context owns naming.
- Do not rename task/plan concepts broadly.
- Do not break compatibility for reading old records with null names; validate new submissions only.

## Supporting Docs To Read

- `.internal-dev/specifications/api.md` entry `API-20260526-01`
- `.internal-dev/specifications/services.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`

## Reproduction Probe Required Before Fix

Inventory public/UI-reachable assignment creation routes and add tests proving current missing-name bypasses are rejected after the fix:

- `PlanController.submitToAgent`
- `PlanController.streamRun`
- `AgentOrchestrationController.assign`
- Existing `TaskController` and `WorkflowController` positive/negative coverage remains green.

## Implementation Steps

1. Add/update request records to include `runDisplayName` where missing.
2. Enforce non-job `TASK_RUN`/`WORKFLOW_RUN` display names at the shared assignment boundary in addition to controller guards.
3. Ensure controllers return controlled `400` responses for missing names.
4. Preserve `JOB_RUN` behavior.
5. Update API docs/spec/changelog.

## Senior-Engineer Guidance

- Shared validation in `AssignmentTemplateParser` or `AssignmentService` prevents future bypasses.
- Controller-level validation is still useful for clearer route-specific messages.
- Legacy null values in existing rows should remain readable; do not add a breaking DB constraint without migration policy.

## Acceptance Criteria

- All new non-job task/workflow assignment submissions require nonblank `runDisplayName`.
- `JOB_RUN` submissions remain valid without route-supplied `runDisplayName` when job context handles naming.
- API docs/specs describe the field consistently.

## Negative Checks

- No accidental rejection of schedules/reactions that create `JOB_RUN` with valid job id.
- No data migration that breaks old rows.

## Validation Commands

- `mvn -q -Dtest=PublicRunSubmissionControllerTest,AgentOrchestrationControllerTest,PublicApiRouteBindingTest,AssignmentContextServiceTest test`
- Bounded startup.

## Evidence Expectations

- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-08-validation-report.md`

## Closeout Expectations

Main thread closes #16 after validation, commit, push, and email.

## Stop Conditions

- Stop if enforcing shared validation breaks existing schedule/reaction semantics and a product decision is needed.

## Do Not Close Unless

- The exact bypass paths named in #16 are covered by tests.
