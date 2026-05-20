# Anonymous Chat Plan Clean Execution Fix

## Context
The `/chat` anonymous planning execution controls should execute approved `SESSION_PLAN` drafts from the chat page. The browser should use the SSE execution route so the user sees execution start and streamed progress. Clean execution should omit prior transcript from the model prompt but must not delete or rewrite the persisted conversation history.

## Goal
Fix anonymous `/chat` approved plan execution so `Approve And Exec Clean` visibly starts over the streaming path and uses clean prompt context without losing chat history. Keep saved `/plans` task planning separate from anonymous in-chat planning in docs.

## In Scope
- Browser `/chat` execution button routing and visible streaming handling.
- `/api/chat/{conversationId}/plan/execute/stream` request binding for `clearContext`.
- Prompt-context suppression for clean anonymous execution without destructive memory writes.
- Focused controller/service/frontend tests and documentation updates.

## Out of Scope
- Redesigning saved `/plans` chat beyond verifying the existing tabbed behavior and deterministic four-question opening sequence.
- Changing model-generated planning content or anonymous plan schema.
- Full production deployment validation.

## Implementation Steps
1. Add a prompt-scoped clean-context flag to resolved chat requests and context management.
2. Wire the plan execution SSE endpoint to accept `clearContext` and pass it into `ChatService.resolveSavedPlanExecution`.
3. Update `chat-client.js` so approved execution uses `/plan/execute/stream`, renders a streaming assistant response, and refreshes history/session state after completion.
4. Add regression tests for non-destructive clean context and stream request binding.
5. Update docs to explicitly distinguish anonymous in-chat planning from saved task planning.

## Validation
Run focused Maven tests for chat service/controller/frontend/saved plan chat and orchestration controller plan tabs. Run Spring Boot startup smoke. Run Playwright validation on `/chat` and `/plans` through a validation subagent if possible.

## Exit Criteria
Clean execution streams visibly, preserves existing chat history, omits prior transcript from clean prompt assembly, keeps saved plan chat in the Planning Chat tab with four deterministic opening questions, and documents the planning split.
