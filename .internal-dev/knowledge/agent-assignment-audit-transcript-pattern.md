## Topic

Agent assignment audit transcript rendering and conversation linkage.

## Source References

- `AssignmentService.transcript(...)`
- `AssignmentAuditTranscriptRenderer`
- `OrchestrationRunnerService.runTaskThroughModel(...)`
- `WorkflowExecutionObserver`
- `AuditRepository.findByConversationIds(...)`

## Key Takeaways

Assignment transcript linkage should live in assignment checkpoint/output/evidence maps, not in task or workflow node output values. The stable keys are `activeConversationId`, `conversationId`, and `conversationIds`.

Before a blocking model-backed task call starts, checkpoint the generated conversation id while the assignment is still `RUNNING`. This lets the queue transcript poller show audit events while execution is in progress.

Workflow task-node conversation ids are surfaced to the parent assignment through a lightweight observer callback. This keeps workflow output values domain-focused while still allowing completed workflow assignments to display historical audit segments.

## Engine Relevance

The agent queue tab can render operational diagnostics without becoming a chat input surface. CRUD and row actions stay HTMX-first: Delete targets the queue tab, Watch targets `#agent-live-transcript`, and transcript polling refreshes the read-only panel every two seconds.

## Open Questions

If workflows later support parallel task nodes with overlapping model calls, the transcript may need grouping controls by workflow node key. The current implementation keeps chronological audit order across conversation ids.
