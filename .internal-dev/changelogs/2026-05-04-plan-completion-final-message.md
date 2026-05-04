# Date
2026-05-04

# Change Summary
Added `finalMessage` parameter to `plan_complete` so the executing model submits its intended user-facing message for validator review. The validator now verifies the proposed final message alongside the evidence and artifact contents. After validation passes, the system delivers the validated `finalMessage` verbatim — not whatever the model says afterward. This closes a gap where the model could produce inaccurate or unverified text after validation approval. The tool loop in ChatService detects when `plan_complete` validation succeeds and breaks out before the next model call, short-circuiting the repair checks.

# Files
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ExecutionPlan.java` — added `String finalMessage` field
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ChatPlanRepository.java` — added `final_message` column, persistence in `find()`/`save()`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java` — threaded field through all construction sites, added `markCompleted(cid, finalMessage)` overload and `finalMessage()` accessor, updated `executionInstructions()`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanCompletionService.java` — added `finalMessage` to `complete()`, passed to validator via `validationInput()`, stored atomically on success, updated `VALIDATOR_SYSTEM_PROMPT`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/PlanSaveTools.java` — added `finalMessage` `@ToolParam` to `plan_complete`, updated tool description
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` — detects mode transition after `plan_complete` success, breaks tool loop, delivers stored `finalMessage` verbatim, guarded empty-final-response repair, updated `invalidExecutionCompletionControlMessage()`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java` — updated mock and prompt count assertion

# Behavioral Impact
- `plan_complete` now accepts an optional `finalMessage` parameter: the intended user-facing completion message, delivered verbatim after validation passes.
- The validator system prompt instructs the validator to review the proposed final message for consistency with the approved plan and evidence.
- When `plan_complete` validation passes, the ChatService tool loop detects the mode transition (EXECUTE_PLAN → NORMAL) and breaks out immediately. No further model calls are made for that turn. The stored `finalMessage` becomes the assistant response.
- The empty-final-response repair is guarded by `planCompletionDetected` to prevent firing after a validated completion.
- Execution instructions and the `plan_complete` tool description now describe `finalMessage` as the user-facing deliverable.
- The `invalidExecutionCompletionControlMessage` repair message includes `finalMessage` in its instructions.

# Risks
- If the model omits `finalMessage` from `plan_complete`, the response will be empty after validation passes. The guard falls back to `new AssistantMessage("")`. A future enhancement could require `finalMessage` or provide a better fallback.
- Breaking the tool loop is safe because it occurs between synchronous HTTP calls to Ollama — no mid-generation state is interrupted, and `PlanToolExecutionContext` cleanup runs in a finally block regardless.
- The `planCompletionDetected` flag only triggers when the plan mode transitions from EXECUTE_PLAN to NORMAL. If any other code path transitions the mode for a different reason mid-execution, it could falsely trigger. Currently only `plan_complete`/`markCompleted` transitions out of EXECUTE_PLAN during the tool loop.

# Follow-up Items
- End-to-end test with a real model calling `plan_complete` with `finalMessage` and verifying the message is delivered verbatim.
- Consider making `finalMessage` required (non-optional) in `plan_complete` to ensure the model always submits a validated user-facing message.
