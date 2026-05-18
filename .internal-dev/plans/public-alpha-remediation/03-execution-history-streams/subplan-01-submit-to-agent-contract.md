# Subplan 01: Submit-To-Agent Contract

## Goal

Route public plan/task/workflow execution controls through saved-definition assignment submission with consistent high priority.

## Implementation Steps

1. Inventory public direct-run routes from bug-05 and review-only findings.
2. Remove UI controls that call direct run, or replace them with submit-to-agent actions.
3. Keep direct execution methods package-private/internal if needed for workers/tests.
4. Preserve request context fields by mapping them into assignment metadata or rejecting unsupported fields explicitly.
5. Normalize chat and operational route default priority to the agreed high-priority submit behavior.

## Validation

Tests assert assignments are created and direct run repositories/services are not invoked by public routes.
