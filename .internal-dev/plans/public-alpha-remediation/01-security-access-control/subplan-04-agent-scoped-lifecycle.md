# Subplan 04: Agent-Scoped Assignment Lifecycle

## Context

bug-12 reports cancel/pause/resume/force-interrupt routes loading assignments by assignment id without verifying the route agent owns the assignment.

## Goal

Make agent-scoped lifecycle routes mutate only assignments belonging to the route agent.

## In Scope

- Ownership checks in service methods or controller adapters.
- Cross-agent rejection tests for lifecycle routes.
- Preserve existing queue/history semantics for valid assignments.

## Out of Scope

- Redesigning queue history or assignment state machines.

## Implementation Steps

1. Locate lifecycle routes and service methods for cancel, pause, resume, and force interrupt.
2. Add route-agent ownership checks before mutation.
3. Prefer service methods that accept both `agentId` and `assignmentId`.
4. Return meaningful non-2xx errors for cross-agent attempts.
5. Add focused tests for valid same-agent and rejected cross-agent controls.

## Validation

- Same-agent lifecycle operations still work.
- Cross-agent assignment ids are rejected and do not mutate state.
- Queue/history rendering remains intact.

## Exit Criteria

Assignment lifecycle mutation is scoped to the route agent.
