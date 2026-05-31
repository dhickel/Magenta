# Phase 05 Validation Report: CANCEL_REQUESTED Lease Guard

## Scope
Validated Phase 05 only for GitHub issue #13: late lease-owner writes must not overwrite `CANCEL_REQUESTED` assignments. Reviewed the worker directive, required governance docs, service specification, orchestration lease knowledge, package guide, implementation diff, targeted tests, and changelog.

Validated files:
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `.internal-dev/specifications/services.md`
- `.internal-dev/changelogs/2026-05-31-cancel-requested-lease-guard.md`

## Criteria Checked
| Criterion | Result | Evidence |
| --- | --- | --- |
| Inspect targeted diff | PASS | Diff is limited to repository guard, runner terminal helpers, runtime tests, services spec, and changelog; unrelated dirty `.gitignore`, `AGENTS.md`, and review file were not part of the phase. |
| Late completion/failure/waiting/checkpoint writes cannot overwrite `CANCEL_REQUESTED` with matching lease owner | PASS | `saveAssignmentIfLeaseOwner` now requires persisted status `RUNNING`, except explicit `CANCEL_REQUESTED` -> `CANCELLED`. Tests cover late `COMPLETED`, `FAILED`, `WAITING`, and `RUNNING` checkpoint/progress writes and assert persisted `CANCEL_REQUESTED` state remains unchanged. |
| Explicit `CANCEL_REQUESTED` -> `CANCELLED` finalization works and clears lease fields | PASS | Repository permits only this cancel finalization from `CANCEL_REQUESTED`; test asserts `CANCELLED`, null `leaseOwner`, null `leaseExpiresAt`, error text, and `completedAt`. |
| Force-interrupt and stale cancel recovery are not weakened | PASS | Existing `forceInterruptedAssignmentRejectsLateLeasedCompletion`, `runningCancelInterruptsLocalWorkAndFinalizesCancelled`, `staleCancelRequestedAssignmentsRecoverToCancelled`, and scoped force-interrupt tests remain passing. Repository force-interrupt and stale-cancel methods are unchanged. |
| Runner helper behavior does not throw unexpectedly when guarded save returns current `CANCEL_REQUESTED` | PASS | `AssignmentService.saveIfLeaseOwner` returns the current persisted row on guarded no-op. Runner `complete`, `fail`, and `waiting` helpers detect `CANCEL_REQUESTED` and call `cancel(saved)`, which is allowed by the repository guard. |
| No unrelated lifecycle state redesign | PASS | No broad lifecycle redesign observed; status policy changed only at the lease-owner guarded save boundary and the corresponding runner terminal helpers. |
| Changelog/spec updates are correct | PASS after validator self-remediation | `services.md` adds `SVC-20260531-02` with the lease guard contract. Changelog initially lacked required `.internal-dev` changelog headings; validator normalized the one file to the required template. |

## Commands Run
- `sed -n '1,240p' .internal-dev/plans/github-issue-backlog-remediation-20260531/worker-directives/phase-05-cancel-requested-lease-guard.md`
- `sed -n '1,220p' .internal-dev/AGENTS.md && sed -n '1,220p' .internal-dev/specifications/AGENTS.md`
- `sed -n '1,260p' .internal-dev/specifications/services.md`
- `sed -n '1,240p' .internal-dev/knowledge/orchestration-lease-heartbeat-and-task-sse.md`
- `sed -n '1,240p' src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- `git diff -- .internal-dev/specifications/services.md .internal-dev/changelogs/2026-05-31-cancel-requested-lease-guard.md src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `rg -n "force|interrupt|stale|CANCEL_REQUESTED|CANCELLED|saveAssignmentIfLeaseOwner|recover" src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `mvn -q -Dtest=OrchestrationRuntimeTest test` - PASS, with expected JVM/sqlite warnings and normal test logs.
- `git diff --check` - PASS.

## Evidence Reviewed
- `OrchestrationRuntimeRepository.saveAssignmentIfLeaseOwner` validates current row exists, lease owner matches, and current status is `RUNNING` unless the requested transition is `CANCEL_REQUESTED` -> `CANCELLED`; SQL update is narrowed to `where id = ? and status = ? and lease_owner = ?`.
- `OrchestrationRunnerService.complete`, `fail`, and `waiting` now route observed `CANCEL_REQUESTED` results to `cancel(saved)` instead of returning late terminal state.
- `AssignmentService.saveIfLeaseOwner` returns current persisted assignment when the guarded repository save no-ops, enabling runner helpers to observe `CANCEL_REQUESTED` without throwing.
- Runtime tests add named regression coverage for each late-write class required by the directive and explicit cancel finalization.
- Adjacent tests for force-interrupt, running cancel interruption, stale cancel recovery, and scoped assignment controls passed in the focused suite.

## Browser Proof Status
Not applicable. Phase 05 is backend repository/service lifecycle behavior with no UI/browser surface change.

## Findings
None requiring remediation.

## Validator Self-Remediation
- Changed `.internal-dev/changelogs/2026-05-31-cancel-requested-lease-guard.md` only.
- Reason: obvious one-file documentation formatting issue; original changelog did not follow the required `.internal-dev` changelog headings.
- Validation: `git diff --check` passed after the edit. No product code changed by the validator.

## Required Remediation
None.

## Residual Risk
- The directive did not explicitly list `.internal-dev/AGENTS.md` or `.internal-dev/specifications/AGENTS.md`, though those governance docs apply to validation of spec/changelog artifacts. Validator read them anyway from the user-provided repo instructions; this is a directive completeness nit, not a product-code blocker.
- Validation was focused to `OrchestrationRuntimeTest` per directive. No full-suite or Spring Boot startup smoke was required or run for this phase.

## Pass/Fail
PASS after validator self-remediation of changelog formatting. Product implementation satisfies Phase 05 acceptance criteria.
