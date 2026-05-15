# Phase 06: End-User Operational Flows

## Context

After isolated checks pass, the app must still work as one coherent operator experience.

## Goal

Run complete browser journeys that exercise the main operational loops end to end with Docker-backed agents.

## In Scope

- Agent creation and enablement.
- Task creation and submission.
- Workflow build/run with approval gate and inbox resume.
- Job/project/schedule setup and assignment.
- Output inspection and chat follow-up.
- Navigation, reload persistence, HTMX behavior, and mobile sanity checks.

## Out of Scope

- Repeating every isolated negative test from phase `07`.

## Implementation Steps

1. Start from a clean browser session and create a new agent.
2. Enable/start the agent and verify Docker state before assigning work.
3. Create a task with structured inputs and outputs; submit it to the agent; inspect outputs.
4. Build a workflow that includes a task node plus a user approval gate; run it; approve through inbox; verify resume to terminal state.
5. Create a job/project path that assigns agent work and writes output.
6. Verify schedules/reactions where they are part of the current backend contract.
7. Use the relevant agent chat surface and model override controls, then confirm state persists after reload.
8. Navigate back through dashboard, agents, outputs, jobs, projects, workflows, and settings to ensure the operator can follow the entire story without shell knowledge.
9. Resize to a narrow viewport and verify the main controls remain reachable.

## Validation

Required checks:
- Full flows work from the browser without raw API intervention.
- Persisted state remains correct after reload and route changes.
- Docker-backed provenance remains visible through the journey.
- Console/network capture has no unexpected failures.

## Exit Criteria

- `.internal-dev/reviews/docker-runtime-parity-validation/06-operational-flow-evidence.md` exists.
