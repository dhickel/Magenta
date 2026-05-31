---
schema_version: 1
document_type: changelog
status: finalized
date: 2026-05-31
---

# CANCEL_REQUESTED Lease Guard

## Date
2026-05-31

## Change Summary
- Tightened orchestration lease-owner saves so normal late writes only mutate `RUNNING` assignments.
- Preserved the explicit `CANCEL_REQUESTED` to `CANCELLED` finalization path for runner-observed cancellations.
- Added regression coverage for late `COMPLETED`, `FAILED`, `WAITING`, and checkpoint/progress writes against `CANCEL_REQUESTED` rows.

## Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `.internal-dev/specifications/services.md`
- `.internal-dev/changelogs/2026-05-31-cancel-requested-lease-guard.md`

## Behavioral Impact
Cancellation requests remain authoritative over late lease-owner completion, failure, waiting, or checkpoint/progress writes. Explicit cancellation finalization still moves assignments from `CANCEL_REQUESTED` to `CANCELLED` and clears lease fields.

## Specification Impact
Updated `SVC-20260531-02` in `.internal-dev/specifications/services.md` to record the orchestration assignment cancellation lease guard contract.

## Risks
Risk is concentrated in assignment lifecycle transitions around cancellation, force-interrupt, and stale lease recovery. Focused repository/runtime tests cover those paths.

## Follow-up Items
- None.

## Validation
- Passed: `mvn -q -Dtest=OrchestrationRuntimeTest test`
