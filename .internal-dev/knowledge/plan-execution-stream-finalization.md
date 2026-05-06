## Topic

Saved-plan execution stream finalization

## Source References

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`

## Key Takeaways

- Saved-plan execution is entered by `ChatService.resolveSavedPlanExecution`, which clears chat context, clears tracked context usage, and marks the plan `EXECUTE_PLAN` / `EXECUTING`.
- Successful stream completion must call `ChatService.handlePlanExecutionStreamFinished`, which records fallback evidence when no structured completion ledger exists and moves still-executing plans to `NEEDS_REVIEW`.
- Failure, timeout, and client-disconnect paths must call `ChatService.recordExecutionFailure`, which records failure evidence and moves the plan to `NORMAL` / `NEEDS_REVIEW`.
- Re-executing a saved or review-needed plan is effectively a reset-and-rerun: `markExecuting` clears previous execution evidence and starts from the saved plan. There is no true resume-from-last-tool-state behavior.

## Engine Relevance

The stream controller owns transport lifecycle events, so it must bridge servlet/SSE timeout and client-abandon signals back into plan lifecycle state. The plan service should remain the source of truth for mode/status transitions, while the controller decides which terminal path applies to the stream.

## Open Questions

- Should Magenta preserve previous failed execution evidence when rerunning a `NEEDS_REVIEW` plan, or is clearing it the correct reset behavior?
- Should there be a separate resume action that keeps evidence and validation feedback visible while asking the model to continue from the last recorded report?
