## Deferred Idea

Add an explicit saved-plan execution resume/retry UX.

## Context

The plan execution timeout fix settles abandoned or failed executions into `NORMAL` / `NEEDS_REVIEW` and allows a later execution request to rerun the saved plan. That is a reset-and-rerun behavior: `markExecuting` starts fresh execution evidence and `clearConversationForExecution` clears the execution chat context.

## Out Of Scope Reason

The active bug was about avoiding indefinite `EXECUTE_PLAN` / `EXECUTING` state after stream timeout or client disconnect. A real resume workflow needs separate product decisions about what state should be preserved, what the model should see, and how the user chooses between retrying from scratch and continuing from partial evidence.

## Future Shape

- Add UI actions that distinguish retry from resume.
- Preserve failed execution evidence and validation feedback when resuming.
- Inject prior execution evidence into execution runtime instructions when the user chooses resume.
- Keep reset behavior available for cases where the previous run is misleading or stale.

## Validation Considerations

- Verify retry clears execution context and starts fresh.
- Verify resume keeps prior evidence visible and model-readable.
- Verify both paths settle to `NEEDS_REVIEW` on timeout/failure.
