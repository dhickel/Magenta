# Assignment Lifecycle Routes Are Not Agent-Scoped

## Summary

Agent-scoped lifecycle routes mutate assignments by assignment id only, without verifying the assignment belongs to the route agent.

## Scope

Assignment cancel/pause/resume/force-interrupt routes and service methods.

## Reproduction

1. Obtain an assignment id for agent B.
2. Call agent A's cancel/pause/resume/force route with that assignment id.

## Expected

Agent-scoped routes reject assignments that do not belong to the path agent.

## Actual

Methods load by assignment id and mutate without ownership check.

## Evidence

- `AgentOrchestrationController.java:149` REST lifecycle routes ignore ownership.
- `OrchestrationController.java:4954` UI lifecycle routes do the same.
- `OrchestrationController.java:5018` force interrupt is assignment-id only.
- `AssignmentService.java:166` cancel/pause/resume/force methods load by assignment id.

## Impact

High: cross-agent queue control is possible for any caller with an assignment id.

## Status

Open.

## Next Action

Add scoped service methods or route-side ownership checks and tests for cross-agent rejection.
