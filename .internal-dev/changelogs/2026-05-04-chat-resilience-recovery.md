# Date
2026-05-04

# Change Summary
Added conversation resilience and recovery for transient failures. When model API
calls fail due to network interruptions, timeouts, or chat switches, the system
now snapshots conversation state before the turn, rolls back on failure, and
retries once with a clean context. Added error logging, HTTP timeouts on model
calls, interrupt acceptance during MODEL_CALL phase, and thread interrupt flag
cleanup.

# Files
- `MagentaWorkExecutor.java` — clear interrupt flag in PrioritizedWork.run() finally
- `ChatService.java` — SLF4J logger, snapshot/restore helpers, retry wrappers for
  streaming and non-streaming paths, error logging at key points
- `ChatModelRouter.java` — 30s connect / 120s read timeout on OpenAiApi RestClient
- `ActiveTurnRegistry.java` — accept interrupts during MODEL_CALL phase
- `ChatController.java` — unchanged (existing error handler is compatible)

# Behavioral Impact
- Transient model API failures now trigger one automatic retry with a clean
  conversation snapshot, so the model never sees partial tool results from a
  crashed turn.
- Model calls time out after 120s instead of blocking indefinitely.
- Users can send interrupt messages during slow model calls; they queue for the
  current turn rather than being deferred.
- Thread pool threads no longer carry stale interrupt flags between tasks.
- All turn failures are now logged at ERROR level with conversation context.

# Risks
- Retry adds latency on transient failures (one extra model call). Acceptable
  since the alternative was a visible error requiring manual re-send.
- The 120s read timeout may need tuning per model/endpoint.
- Snapshot/restore depends on `ChatMemoryRepository` being available; falls
  back gracefully when null.

# Follow-up Items
- Consider per-model timeout configuration instead of global constants.
- Consider adding a circuit breaker for endpoints that fail repeatedly.
- Monitor log output for retry frequency to detect unstable endpoints.
