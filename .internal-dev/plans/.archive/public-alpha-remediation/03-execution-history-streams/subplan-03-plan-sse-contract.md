# Subplan 03: Plan SSE Contract

## Goal

Emit semantic event names from `/api/plans/{planId}/runs/stream`.

## Implementation Steps

1. Reuse `TaskStreamSupport` mapping or emit `TaskExecutionEvent.event()` consistently.
2. Avoid wrapper event names based on Java class names.
3. Add endpoint-level SSE test covering at least start/progress/completion or error event names.

## Validation

SSE clients receive stable semantic event names, not `TaskExecutionEvent` for every payload.
