# Phase 06: Chat, Model Overrides, And Agent Surfaces

## Context

The app has multiple chat-adjacent surfaces: primary chat, agent chat panels, planning mode, and model override selectors in operational editors. These must remain working after the operational UI refactors.

## Goal

Validate through Playwright that chat, planning, agent consultation, and model override behavior work from the browser and do not regress while Docker-backed operational flows run.

## In Scope

- `/chat` normal chat SSE flow.
- Planning mode from chat.
- Agent side-panel chat on operational pages.
- Project/job/top-level agent chat box if present.
- Model selector population and persistence.
- Execution model/planning model override behavior.
- Interrupt/cancel behavior where exposed.

## Out of Scope

- Model quality evaluation beyond transport, routing, and task-following smoke.
- Long-running benchmark prompts.

## Implementation Steps

1. Open `/chat` in Playwright and run a bounded smoke prompt.
2. Verify SSE event sequence and persisted history.
3. Enter planning mode, answer at least one planning question, approve, and execute a minimal safe plan if the UI supports it.
4. Open operational pages that include agent chat panels and verify:
   - panel attaches to DOM
   - panel can be opened
   - messages stream or return actionable errors
   - active agent identity is clear
5. Validate model selectors:
   - chat model select
   - planning model select
   - plan/task model overrides
   - job/project model overrides
6. Create a small task or plan with non-default model overrides and verify persisted state and execution metadata reflect the override.
7. Trigger interrupt/cancel on a running chat or plan execution if controls are available.

## Validation

Required Playwright checks:
- `/chat` loads expected controls and streams a response.
- Session switching/history still works.
- Planning flow persists state after reload.
- Operational agent chat is not a dead visual panel.
- Model dropdowns contain configured options.
- Non-default override selection survives save/reload and appears in run metadata or execution request evidence.
- Browser console/network errors are captured and classified.

## Exit Criteria

- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/06-chat-model-overrides-agent-surfaces-evidence.md` exists.
- Any model selector, chat panel, planning, SSE, or interrupt regression is logged.
