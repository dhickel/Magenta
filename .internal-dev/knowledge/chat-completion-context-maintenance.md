---
schema_version: 1
document_type: knowledge
date: 2026-05-22
owner: codex
status: active
---

# Chat Completion Context Maintenance

## Topic

Completed chat-plan reload and finalization must remain read-safe even when stored chat context is over the compaction trigger and the configured local compaction model is unavailable.

## Source References

- `ContextManagementAdvisor.maintainStoredContext(...)`
- `ChatService.maintainContextUsage(...)`
- `ChatService.snapshotContextUsage(...)`
- `ChatController.history(...)`
- `ChatController.planExecutionStream(...)`
- `.internal-dev/plans/chat-completion-compaction-reload-repair/phase-01-orchestrated-diagnosis-and-fix.md`

## Key Takeaways

- Read-only history reload should use `snapshotContextUsage(...)`, not `maintainContextUsage(...)`.
- Completed anonymous execution finalization should use a usage snapshot after validator-approved completion so post-completion compaction cannot hide the final transcript.
- Model-backed compaction failures during maintenance should degrade context metadata while preserving stored messages.
- A usage snapshot may report context above the trigger. That is acceptable for reload and completed finalization; the next prompt-building model send is responsible for reducing or rejecting oversized context.
- Plan stream disconnect diagnostics are transport-level events and should not be treated as execution-domain failures when the underlying execution reaches `COMPLETED`.

## Engine Relevance

This pattern protects user-visible completion state from local model availability issues. It is most relevant to live chat reloads, SSE finalization callbacks, and any future read path that wants context usage metadata without mutating the transcript.

## Open Questions

- None.
