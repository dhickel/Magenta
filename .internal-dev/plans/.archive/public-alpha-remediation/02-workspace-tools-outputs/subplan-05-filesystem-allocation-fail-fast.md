# Subplan 05: Filesystem Allocation Fail Fast

## Goal

Stop execution immediately when required workspace/output allocation fails.

## Implementation Steps

1. Locate allocation catch blocks in plan/task execution.
2. Convert required allocation failures into explicit failed run state with operator-visible message.
3. Remove stale Docker fallback comments.
4. Add test proving allocation failure does not continue with null paths.

## Validation

Focused run failure test plus operator/audit message assertion.
