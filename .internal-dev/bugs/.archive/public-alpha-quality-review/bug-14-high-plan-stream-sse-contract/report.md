# Plan Run Stream Emits Wrong SSE Event Names

## Summary

`/api/plans/{planId}/runs/stream` sends every event under the class name `TaskExecutionEvent` instead of semantic event names.

## Scope

Plan run SSE endpoint.

## Reproduction

1. Stream a plan run from `/api/plans/{planId}/runs/stream`.
2. Observe event names and payload shape.

## Expected

Events should match canonical task stream names: `started`, `tool`, `progress`, `completed`, `failed`.

## Actual

The plan controller sends `event.getClass().getSimpleName()` and wraps the actual event under `data`.

## Evidence

- `PlanController.java:247` delegates to task execution stream.
- `PlanController.java:257` sends event class name.
- `TaskExecutionEvent.java:7` is the class for all task execution events.
- `TaskStreamSupport.java:79` has the correct named event mapping.

## Impact

High: public SSE clients cannot reliably consume plan-run stream events.

## Status

Open.

## Next Action

Reuse `TaskStreamSupport` mapping or emit `TaskExecutionEvent.event()` consistently; add endpoint-level SSE tests.
