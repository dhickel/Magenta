# Summary

Saved chat plans are marked completed after a model turn returns, even when measurable plan constraints are not verified.

# Scope

Chat plan execution through `/exec-plan` and `/clr-exec-plan`.

# Reproduction

Use a saved plan that requires a measurable result, such as sampling 50-100 forum posts. Execute the plan. If the model collects fewer posts or only generates an artifact without reading/verifying it, the plan is still marked completed as long as the chat turn returns.

# Expected

Plan completion should reflect satisfaction of explicit plan constraints, or the state should communicate that the model merely reported completion and still needs user review.

# Actual

`ChatService.executeSavedPlan()` calls `chat(...)` and then immediately calls `planService.markCompleted(conversationId)` when no exception is thrown.

# Evidence

Conversation `48e9dc4f-5aab-4d8f-bba4-b430bf451362` had a saved plan requiring a minimum of 50 posts and up to 100, but the tool output reported `Total posts collected: 40`. The plan status is `NORMAL` / `COMPLETED`.

# Impact

Users can receive a completed-plan state for incomplete research or execution work. This weakens trust in plan mode and hides missed acceptance criteria.

# Status

Open.

# Next Action

Design a small verification contract for measurable plan steps, or introduce a post-execution state that separates "assistant returned" from "plan verified complete".
