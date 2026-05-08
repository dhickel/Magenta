# Orchestration Lease Heartbeat And Task SSE

## Topic
Keeping long-running orchestration assignments leased while task SSE requests return promptly.

## Source References
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`

## Key Takeaways
- Controller SSE endpoints should subscribe to blocking or model-backed work on `Schedulers.boundedElastic()` and return the `SseEmitter` immediately.
- Register emitter completion, timeout, and error callbacks to dispose the Reactor subscription.
- Lease heartbeat updates should be guarded by assignment id, `RUNNING` status, and current lease owner.
- Assignment completion should clear lease owner and expiry, which naturally stops later guarded heartbeat updates from changing terminal assignments.

## Engine Relevance
This pattern prevents servlet request thread starvation during direct task streaming and prevents the stale lease recovery path from interrupting valid long-running orchestration work.

## Open Questions
- Whether future multi-node orchestration should add monotonic fencing tokens in addition to lease owner strings.
- Whether heartbeat scheduling should move to a shared scheduler if more runtime services need periodic per-run maintenance.
