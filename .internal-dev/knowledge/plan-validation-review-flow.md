# Plan Validation Review Flow

## Topic
Anonymous chat plan execution completion and review-state handling.

## Source References
- GitHub issue #5: plan completion validation leaking into user review flow.
- GitHub issue #4: `NEEDS_REVIEW` falling back to PLAN mode.
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/resources/static/js/chat-client.js`

## Key Takeaways
- `plan_complete` is the only path that can mark anonymous plan execution as trusted completion.
- Validation failure should remain tool-loop feedback while automatic repair attempts are available.
- If execution finishes without validator-passed completion, persist a controlled `NEEDS_REVIEW` message instead of ordinary assistant text.
- `NEEDS_REVIEW` should resolve as normal chat mode with status preserved in `ChatPlanState`, so clients can show evidence without reinstalling planning prompts or planning controls.
- Durable validation feedback should include a pass/fail status and criterion-level remediation so history reloads still explain the review state.

## Engine Relevance
This keeps Magenta's executor/validator boundary explicit: model output can propose completion, but Magenta state changes define whether completion is trusted. It also prevents UI mode resolution from turning an execution review into draft planning.

## Open Questions
- Whether to introduce a first-class `PlanMode.REVIEW` once review actions become more than evidence display.
- Whether issue #3's malformed tool-call repair should add dedicated transcript entries for recovered validation/tool-call failures.
