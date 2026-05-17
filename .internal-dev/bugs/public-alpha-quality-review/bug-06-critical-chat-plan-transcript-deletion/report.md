# Saved Plan Execution Deletes Conversation Transcript

## Summary

Saved-plan execution clears the persisted conversation transcript before starting execution.

## Scope

Chat saved-plan execution flow.

## Reproduction

1. Create a chat conversation with planning history.
2. Execute a saved/approved plan through the chat direct execution path.
3. Reload history.

## Expected

Runtime context can be fresh without deleting user-visible/audit conversation history.

## Actual

Execution clears the conversation and saves an empty chat-memory list.

## Evidence

- `ChatService.java:607` clears conversation before execution.
- `PlanService.java:543` clears conversation for execution.
- `ChatMemoryRepository.java:60` deletes existing rows before inserting the provided list.
- `chat-client.js:1070` tells the user context is being cleared and renders empty history.

## Impact

Critical: audit/user transcript loss during a core workflow.

## Status

Open.

## Next Action

Preserve transcript history and isolate execution context through a separate run/session context model.
