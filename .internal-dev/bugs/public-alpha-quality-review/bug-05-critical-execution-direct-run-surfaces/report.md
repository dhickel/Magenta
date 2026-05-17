# Public Direct-Run Surfaces Bypass Submit-to-Agent Semantics

## Summary

Multiple public chat/API/UI routes still execute plans, tasks, workflows, or jobs directly beside the intended submit-to-agent queue semantics.

## Scope

Chat plan execution, plan/task/workflow/job API run routes, and job UI controls.

## Reproduction

1. Approve/save a plan in `/chat`.
2. Use `Execute now`, or call `/api/plans/{id}/runs/stream`, `/api/tasks/{id}/runs/stream`, `/api/workflows/{id}/runs`, or `/api/jobs/{id}/runs`.

## Expected

User-triggered run semantics should require saved-definition submission to an agent queue.

## Actual

Direct run routes and UI controls remain active.

## Evidence

- `chat-client.js:206` renders `Execute now`.
- `ChatController.java:97` streams saved plan execution directly.
- `ChatController.java:442` exposes non-stream direct plan execution.
- `PlanController.java:235`, `TaskController.java:174`, `WorkflowController.java:88`, and `JobController.java:131` expose direct run routes.
- `OrchestrationController.java:3586` renders job `Start Run`.

## Impact

Critical: the product has split execution semantics, bypassing queue visibility/recovery and user-approved priority behavior.

## Status

Open.

## Next Action

Define which routes remain internal-only, remove or gate direct-run user routes, and route public run actions through assignment submission.
