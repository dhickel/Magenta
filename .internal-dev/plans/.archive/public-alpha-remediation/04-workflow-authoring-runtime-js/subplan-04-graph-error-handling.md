# Subplan 04: Graph Error Handling

## Goal

Make workflow graph network/server failures visible and prevent silent optimistic state drift.

## Implementation Steps

1. Centralize graph request error handling.
2. Revert or mark local graph state dirty when persistence fails.
3. Render server validation errors in the workflow editor.
4. Add tests or browser checks for forced network/server failure.

## Validation

Failed graph persistence is visible and does not appear saved.
