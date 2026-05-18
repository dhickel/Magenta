# Filesystem Allocation Failure Continues Execution

## Summary

Task execution workspace/output allocation failures are logged but execution continues with null paths.

## Scope

`PlanService` task execution allocation under filesystem runtime.

## Reproduction

1. Force workspace/output allocation failure.
2. Start task execution.
3. Observe execution continues with null temp/output paths.

## Expected

Filesystem allocation failure should be a hard, operator-visible execution failure.

## Actual

The exception is caught and execution continues.

## Evidence

- `PlanService.java:811` allocation block catches failures.
- `PlanService.java:828` stale comment says Docker level will fail later.

## Impact

Medium: filesystem runtime can produce confusing downstream failures or missing outputs rather than immediate clear failure.

## Status

Implemented and validated in public alpha remediation domain 02 subplan 05.

## Next Action

Continue with public alpha remediation domain 02 subplan 06.
