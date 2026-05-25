# Alpha Deferred Targets

## Summary

Workflow canonicalization now uses the route-aware orchestration workflow model as the production path. The targets below remain intentionally out of scope for the current alpha remediation because they expand workflow semantics or editor complexity beyond the saved-workflow-only builder.

## Deferred Targets

- Cyclic workflows and retry loops: keep validation rejecting cycles. Add bounded retry-loop semantics later as an explicit node/route feature with run limits and clear failure states.
- Conditional routing language: route records have a `condition` field, but the runner does not evaluate expressions. Add a small, auditable condition language later rather than embedding arbitrary expression execution now.
- Rich validator feedback loops: validation currently returns blocking errors and warnings. Future work should support validator nodes that can produce structured remediation suggestions, retry decisions, and operator-facing feedback.
- Drag-canvas editing: the alpha builder remains a hybrid tree/form editor. A drag canvas can be layered on later as a visual affordance over the same saved nodes/routes schema.
- Parallel ready-node execution: the graph model can represent fan-out, but the runner executes ready nodes sequentially for now. Add bounded parallelism only after cancellation, output ordering, and approval semantics are hardened.
- Mid-chat planning and task-planning loop hardening: future sprint should test and iron out chat loops that switch between normal chat, plan chat, mid-chat planning, and task planning. The alpha UI may expose a New Plan Chat entrypoint, but exhaustive loop hardening remains separate.

## Status

Deferred by design for alpha. Do not treat any of these as missing functionality for the current remediation gate unless the user explicitly expands scope.
