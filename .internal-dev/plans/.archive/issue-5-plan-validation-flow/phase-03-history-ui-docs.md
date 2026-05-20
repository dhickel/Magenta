# Phase 03 - History, UI, And Docs

## Context
The browser appends live tool activity during SSE, then reloads persisted history on `done`. If relevant tool or validation events are only live/transient, the detailed view can appear to vanish.

## Goal
Keep validation/tool evidence discoverable after reload and document the resulting behavior.

## In Scope
- Confirm `ToolTranscriptService` persists `plan_complete` tool results and renders them in history.
- Ensure validation pass/fail summaries remain in `planState.validationFeedback`.
- Update docs for the chat plan execution lifecycle and review state.
- Complete `.internal-dev` changelog/knowledge/notes as applicable and commit all intended work.

## Out of Scope
- A new transcript storage table.
- Large UI restructuring or a new execution evidence panel.
- Deep end-to-end Playwright campaigns unless startup succeeds and focused validation requires it.

## Implementation Steps
1. Add regression tests that persisted history includes tool transcript messages after plan execution reload where feasible with existing test seams.
2. Update the chat UI status text for `NEEDS_REVIEW` and validation feedback.
3. Update `docs/` technical/API or end-user docs covering plan execution states, validator-gated completion, and review-state behavior.
4. Run focused tests, application context smoke test, and Playwright UI validation if the app can start.
5. Write `.internal-dev/changelogs/...`, `.internal-dev/knowledge/...`, and deferred notes only for confirmed out-of-scope ideas.
6. Commit implementation and `.internal-dev` updates, staging only files related to this task.

## Validation
- Focused tests from prior phases pass.
- Startup smoke: `timeout 30s ./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=0`.
- For UI changes, run focused Playwright validation on a subagent while the app is running and capture screenshots.

## Exit Criteria
- Validation/tool history is durable enough to remain discoverable after reload.
- Docs and `.internal-dev` artifacts are complete.
- Final commit contains only intended task files.
