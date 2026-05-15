# Phase 03: Plans, Tasks, And Docker Execution

## Context

Plans and tasks have been refactored repeatedly, but alpha readiness requires proving that a user can create a task-like finalized plan, define inputs/outputs/deliverables, run it through an agent, and receive validated outputs from Docker-backed execution.

## Goal

Validate through Playwright that plan/task creation and execution work end to end with Docker enabled, including optional inputs, typed inputs/outputs, deliverables, model overrides, and output validation.

## In Scope

- Plan/task creation UI.
- Structured input types: user message, string, file path, number, JSON, and array variants.
- Structured output types matching input types.
- Deliverables distinct from outputs.
- No-input task behavior.
- No-output task behavior.
- Model override selectors.
- Submit-to-agent and execution status.
- Docker-backed output materialization.

## Out of Scope

- Workflow graph composition; phase `04` covers chaining.
- Cosmetic editor redesign unless the UI prevents validation.

## Implementation Steps

1. Create a no-input/no-output task from the UI and verify the execution prompt/state indicates no inputs and no expected outputs.
2. Create a typed-output task that writes:
   - one text file under `/output`
   - one JSON output artifact
   - one message output copied or materialized as an output when required
3. Create a typed-input task that accepts:
   - user message
   - string value
   - file path
   - number
   - JSON
   - one array input
4. Save and reload each editor to prove inputs, outputs, steps, deliverables, assumptions, validation criteria, and model overrides persist.
5. Submit the task to the agent created in phase `02`.
6. Watch execution from the browser until terminal state.
7. Verify Docker evidence:
   - execution references the agent/container runtime
   - files appear through output/workspace UI
   - host execution is not silently used when Docker is enabled
8. Verify output validation:
   - missing output keeps the task incomplete or failed
   - provided output passes type validation
   - deliverables can pass even when no outputs are expected

## Validation

Required Playwright checks:
- Editor controls save and survive page reload.
- Model override dropdowns contain configured models, not only `Default`.
- Submit-to-agent works from the plan/task surface and from agent detail if both exist.
- Execution produces visible run status transitions.
- Output artifacts are visible in `/outputs` or agent outputs tab.
- A no-output task does not require fake output artifacts.
- Invalid JSON/number/file-path inputs show server-rendered validation errors.

## Exit Criteria

- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/03-plans-tasks-docker-execution-evidence.md` exists.
- At least one Docker-backed task run completes and produces durable output evidence.
- Any editor persistence, model override, or Docker bypass defect is logged.
