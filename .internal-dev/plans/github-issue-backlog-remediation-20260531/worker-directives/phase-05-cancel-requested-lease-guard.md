# Phase 05 Worker Directive: CANCEL_REQUESTED Lease Guard (#13)

## Objective

Remediate GitHub issue #13 so late lease-owner writes cannot overwrite `CANCEL_REQUESTED` assignments with completed, failed, waiting, or checkpoint states unless an explicit cancellation transition allows it.

## User-Visible Outcome

User-requested cancellation remains authoritative and assignment history cannot report misleading late success.

## Issues

- #13 `CANCEL_REQUESTED assignments can be overwritten by late lease writes`

## Direct Targets

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java` only if transition policy belongs there
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `.internal-dev/specifications/services.md`
- `.internal-dev/changelogs/2026-05-31-cancel-requested-lease-guard.md`

## Forbidden Scope

- Do not redesign assignment lifecycle states.
- Do not change force-interrupt behavior except to keep it compatible.
- Do not weaken stale cancel recovery to `CANCELLED`.

## Supporting Docs To Read

- `.internal-dev/specifications/services.md`
- `.internal-dev/knowledge/orchestration-lease-heartbeat-and-task-sse.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`

## Reproduction Probes Required Before Fix

Add tests that save an assignment in `CANCEL_REQUESTED` with a matching lease owner, then attempt late writes through the guarded path for:

- `COMPLETED`
- `FAILED`
- `WAITING`
- checkpoint/progress update that would preserve non-cancel status incorrectly

The persisted assignment must remain cancellation-owned according to the target policy.

## Implementation Steps

1. Add the failing/targeted tests.
2. Define the transition policy in the narrowest location:
   - `saveAssignmentIfLeaseOwner` should normally accept only `RUNNING` rows for non-cancel writes.
   - Explicit cancel finalization should use a dedicated path or allowed transition from `CANCEL_REQUESTED` to `CANCELLED`.
3. Ensure runner paths that observe `CANCEL_REQUESTED` route to cancellation instead of late completion/failure/waiting.
4. Preserve heartbeat/stale lease behavior.
5. Update service spec/changelog.

## Senior-Engineer Guidance

- A matching lease owner is not enough once the row says cancellation was requested.
- Keep the guard data-driven and test the repository boundary; service tests can then prove runner behavior.
- Late writes should return empty/no-op rather than mutate cancellation state.

## Acceptance Criteria

- Late completion/failure/waiting/checkpoint writes cannot overwrite `CANCEL_REQUESTED`.
- Explicit cancellation finalization to `CANCELLED` still works.
- Existing force-interrupt and stale cancel tests pass.

## Negative Checks

- No assignment remains indefinitely `CANCEL_REQUESTED` because cancel finalization path was blocked.
- No unrelated status transitions change.

## Validation Commands

- `mvn -q -Dtest=OrchestrationRuntimeTest test`

## Evidence Expectations

- Validator report: `.internal-dev/plans/github-issue-backlog-remediation-20260531/validation/phase-05-validation-report.md`

## Closeout Expectations

Main thread closes #13 after validation, commit, push, and email.

## Stop Conditions

- Stop if a real product policy says a late completion should override cancellation.

## Do Not Close Unless

- Tests cover every late write class named in the issue.
