# Workflow Builder Rejects Necessary Intermediate States

## Summary

The HTMX workflow builder validates on every node add/update, making common valid workflows impossible to build incrementally.

## Scope

Workflow editor routes and `WorkflowValidator`.

## Reproduction

1. Create a workflow draft.
2. Add an approval/control node or task node.
3. Attempt to build the required branches/inputs in subsequent steps.

## Expected

Draft editing supports incomplete intermediate graph states and only enforces full execution validity at validate/submit time.

## Actual

Node add calls validated save immediately. Validation rejects approval gates until both branches exist and rejects task nodes until required inputs are already satisfied.

## Evidence

- `OrchestrationController.java:2262` saves workflow after adding a node.
- `WorkflowValidator.java:203` requires control routes with `APPROVED` or `REJECTED`.
- `WorkflowValidator.java:310` rejects task nodes with unsatisfied required inputs.
- `OrchestrationController.java:2634` route form lacks a condition control.

## Impact

Critical: public-alpha users cannot author normal approval-gate workflows through the editor.

## Status

Open.

## Next Action

Separate draft structural persistence from execution validation; add condition editing and tests for incrementally building an approval workflow.
