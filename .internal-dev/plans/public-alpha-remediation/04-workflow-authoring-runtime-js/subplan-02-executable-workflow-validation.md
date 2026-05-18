# Subplan 02: Executable Workflow Validation

## Goal

Prevent empty or non-executable workflows from validating, submitting, or running.

## Implementation Steps

1. Update executable validation to require at least one executable node and a valid start path.
2. Ensure submit/run paths call executable validation, not draft persistence validation.
3. Return clear operator-visible errors.
4. Add regression tests for empty, disconnected, and valid workflows.

## Validation

Empty workflows cannot complete as green no-ops.
