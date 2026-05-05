# Note: Sync/Stream Conversation Serialization Gap

2026-05-04 — dhickel

Synchronous chat calls (`ChatService.chat()`) use `ConversationTurnCoordinator` for per-conversation serialization (queueing subsequent turns). Streaming calls (`ChatService.stream()`) now use a per-conversation `Semaphore` (rejecting concurrent streams with an error).

These two mechanisms don't coordinate. A sync `chat()` call and a `stream()` call for the same conversation could theoretically execute concurrently, each with its own model invocation, potentially corrupting conversation state.

**Why this exists**: the `ConversationTurnCoordinator` was coupled to `MagentaWorkExecutor` — it required blocking an executor thread. The streaming path needed to not block, so it was moved to a lightweight semaphore. Unifying the two would require the coordinator to support non-blocking/reactive workflows.

**Mitigation**: the UI doesn't issue sync and stream requests for the same conversation simultaneously. This is an architectural gap worth closing but not a current bug.

**Follow-up**: refactor `ConversationTurnCoordinator` to use a reactive-compatible gate (e.g., `Semaphore` with an optional queue) and route both sync and stream calls through the same mechanism.
