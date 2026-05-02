# Context

Magenta has an initial plan mode with saved execution plans, but it still relies on chat text for user questions, uses the current model for planning, separates assumptions from notes, and keeps `/clr-exec-plan` as a command variant.

# Goal

Refactor planning into a structured workflow with a dedicated planning model, explicit deliverables, structured user prompts, approval, save-as-task, and clear-context execution by default.

# In Scope

- Add top-level `planningModel` config using the existing `local-gemma-26b` default.
- Persist structured planning state, deliverables, prompts, answers, approval, and pre-planning model.
- Replace one-shot plan save with incremental planning tools and keep execution reporting.
- Add browser planning controls above the chat input.
- Remove `/clr-exec-plan` and make `/exec-plan` clear context by default.

# Out of Scope

- A generic task manager beyond saving the approved plan for later.
- The future validation agent.
- Broad redesign of chat, session, or job orchestration.

# Implementation Steps

- Extend config records, validation, examples, and tests for `planningModel`.
- Extend plan schema/records/state for deliverables, prompts, answers, approval state, and pre-planning model.
- Update plan runtime prompts and tools for incremental state updates and structured questions.
- Update chat service model routing and execution clearing behavior.
- Add planning-specific web endpoints and UI panel.
- Update tests, README, package guides, and changelog.

# Validation

- Run focused unit tests for config, plan service/repository/tools, chat service, and controller.
- Run the full Maven test suite if focused tests pass.

# Exit Criteria

- Planning uses Gemma by default, execution returns to the original model, `/clr-exec-plan` is gone, deliverables are model-visible, and the UI supports structured planning interactions.
