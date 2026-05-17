# Empty Workflows Validate and Complete as No-Ops

## Summary

Empty workflow drafts can be submitted and executed as successful no-ops.

## Scope

Workflow draft creation, submission, validation, and runner completion.

## Reproduction

1. Create a new workflow draft with no nodes.
2. Submit or start it.
3. Observe a valid/successful no-op path.

## Expected

Empty workflows should fail validation before submission/execution.

## Actual

Draft creation saves empty nodes/routes, submit checks graph validation, and the runner treats an empty node-run set as complete.

## Evidence

- `OrchestrationController.java:2184` creates an empty workflow draft.
- `OrchestrationController.java:2450` submit only checks `validateGraph`.
- `WorkflowRunner.java:645` completes when no node runs remain.

## Impact

Critical: public alpha can show green completion for a definition that did no work.

## Status

Open.

## Next Action

Require at least one executable node and a valid start path before validate/submit/run can succeed.
