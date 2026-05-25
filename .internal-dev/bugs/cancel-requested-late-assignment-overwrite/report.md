# Cancel Requested Assignment Can Be Overwritten By Late Lease Writes

## Summary

`CANCEL_REQUESTED` orchestration assignments can be overwritten by late lease-owner writes that mark the assignment `COMPLETED`, `FAILED`, or `WAITING`.

## Scope

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- Orchestration assignment cancellation and lease-guarded persistence.

## Reproduction

From the May 25 quality review:

1. Create or load an assignment row in `CANCEL_REQUESTED` with a matching lease owner.
2. Call the lease-owner guarded save path used by runner completion/failure/waiting transitions.
3. Save a target assignment state such as `COMPLETED`.

The reviewer reproduced this against the real repository with a temporary SQLite database.

## Expected

Once an assignment is `CANCEL_REQUESTED`, late completion, failure, waiting, or checkpoint writes should not silently replace the cancellation state unless an explicit transition policy allows that state change.

## Actual

`OrchestrationRuntimeRepository.saveAssignmentIfLeaseOwner` accepts rows currently in `RUNNING` or `CANCEL_REQUESTED` and writes the target status provided by the caller. `OrchestrationRunnerService` routes completion, failure, waiting, and cancel writes through that same guarded save path.

## Evidence

- `OrchestrationRuntimeRepository.java` allows guarded saves when the current row is `RUNNING` or `CANCEL_REQUESTED`.
- `OrchestrationRunnerService.java` routes `complete`, `fail`, `waiting`, and `cancel` through that guarded save path.
- Existing cancellation tests cover force-interrupt rejection and happy-path cancellation but not late completion/failure/waiting after `CANCEL_REQUESTED`.

## Impact

High. A cancellation requested by the user or runtime can be lost if a worker returns late with a terminal or waiting write, causing misleading assignment history and potentially continuing workflows that should be treated as canceled.

## Status

Open. Discovered during the May 25 alpha-readiness quality review and not yet remediated. Mirrored to GitHub: https://github.com/dhickel/Magenta/issues/13.

## Next Action

Add repository/service tests for late completion, failure, waiting, and checkpoint writes after `CANCEL_REQUESTED`, then harden the assignment transition guard so cancel-requested rows cannot silently become completed, failed, or waiting.
