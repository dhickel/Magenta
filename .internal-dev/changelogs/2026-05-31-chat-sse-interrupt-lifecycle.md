# Date

2026-05-31

# Change Summary

Remediated GitHub issues #14 and #15 for chat stream lifecycle handling. SSE error/disconnect callbacks now perform idempotent domain cleanup for active chat turns and active saved-plan execution registrations. Plain streaming now enters `MODEL_CALL` before the blocking provider call, matching the advertised interrupt token contract and allowing the interrupt endpoint to interrupt the in-flight worker thread. Tool-unsupported fallback to plain streaming follows the same interrupt behavior.

Targeted repair after browser validation: browser/client disconnect cleanup now cancels the active turn's model worker before completing cleanup, so the same-conversation stream lock can release promptly instead of waiting for the abandoned provider call to return naturally.

Escalated targeted repair after validator review: removed the short aged-owner takeover path. Same-conversation stream locks now remain exclusive for a legitimately active stream regardless of age, and retry is permitted only after explicit owner abandon/disconnect/cancel evidence releases the lock.

Targeted repair after browser abort validation: chat SSE streams now send lightweight comment heartbeats so browser fetch aborts are detected before the next model event. Heartbeat send failure routes through the same owner-only abandon/cancel cleanup path. Cancelled stream owners also carry a persistence fence through `ContextManagementAdvisor` so a late abandoned provider response cannot append an assistant message after a retry begins.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`: made stream domain cleanup idempotent and invoked from `onError`, completion, timeout, subscriber error, and send-failure paths.
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`: targeted repair cancels the active model worker on transport disconnect and timeout cleanup while leaving normal completion and provider-error cleanup non-cancelling.
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`: targeted browser-abort repair starts a short SSE heartbeat and routes heartbeat send failure through transport cleanup.
- `src/main/java/io/mindspice/magenta2/api/web/ChatStreamSupport.java`: synchronizes chat SSE writes with heartbeat probes on the emitter object.
- `src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java`: added reusable SSE comment heartbeat support for transport liveness probes.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`: set plain streaming active turns to `MODEL_CALL` and wrapped blocking model calls with active worker-thread registration.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`: constrained stream-lock release to normal terminal cleanup or explicit owning-turn abandon; age alone no longer permits same-conversation takeover.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`: passes the active-turn cancellation fence into streaming chat prompts and checks cancellation before finalizing streamed messages.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`: skips assistant persistence when the streaming owner was cancelled while the provider call was in flight.
- `src/main/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistry.java`: added active worker-thread tracking and interruption for accepted `MODEL_CALL` interrupts.
- `src/main/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistry.java`: targeted repair added cancellable turn completion that removes active-turn/plan-execution registrations and interrupts any registered worker.
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`: covered SSE `onError` cleanup and plain stream interrupt endpoint alignment.
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java`: targeted repair covers `onError` cancellation of the active model worker.
- `src/test/java/io/mindspice/magenta2/api/web/SseStreamLifecycleTest.java`: covers heartbeat failure invoking transport cleanup callback.
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`: covered normal plain streaming and tool-unsupported fallback interrupt behavior.
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`: covered long active-stream exclusivity beyond the previous age threshold, explicit owning-turn abandon allowing retry, and abandoned owner cancellation preventing late assistant persistence.
- `src/test/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistryTest.java`: covered model/tool/checkpoint interrupt acceptance and worker-thread interruption.
- `src/test/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistryTest.java`: targeted repair covers cancellation interrupting a worker and releasing active plan-execution registration.
- `.internal-dev/specifications/api.md`: documented chat stream active-turn and interrupt API contract.
- `.internal-dev/specifications/web.md`: documented browser chat interrupt affordance expectations.
- `.internal-dev/specifications/architecture.md`: documented active-turn phase and worker-thread lifecycle behavior.
- `docs/technical/api-reference.md`: updated chat stream and interrupt route semantics.
- `docs/technical/chat-planning-tasks.md`: updated active-turn interrupt phase semantics.

# Behavioral Impact

Plain stream `start` payloads still advertise `turnId` and `interruptToken`; those values are now actionable during the blocking model call. SSE client disconnect/error cleanup removes active-turn and saved-plan execution state without treating a client disconnect as saved-plan execution failure finalization. Disconnect/timeout/heartbeat-failure cleanup also interrupts the abandoned worker so quick same-conversation retry is not held behind an orphaned provider call.

Elapsed time alone does not make an active same-conversation stream replaceable. A retry can proceed quickly after disconnect/error/cancel/heartbeat-failure evidence releases the owning stream lock; otherwise the existing active-stream conflict remains in force until the prior stream terminates. If the old provider call returns after cancellation, the cancellation fence prevents its assistant response from being persisted as a duplicate turn.

# Specification Impact

Updated API, web, and architecture specifications for the active-turn lifecycle, interrupt contract, disconnect heartbeat detection, cancellation persistence fence, and evidence-based stream-lock release.

# Risks

Provider cancellation is best-effort: Magenta interrupts the worker thread during `MODEL_CALL`, but the underlying Spring AI/client/provider stack must honor thread interruption for immediate cancellation. If the provider returns after the turn was cancelled, the assistant persistence fence is expected to suppress the abandoned response. Browser proof is still required because servlet abort behavior depends on transport write failure surfacing through the heartbeat.

# Follow-up Items

Browser validation should re-run the focused #14 proof: abort a browser stream and retry the same conversation within the short bounded window, plus re-check the #15 advertised-token interrupt acceptance.
