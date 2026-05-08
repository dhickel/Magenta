# Non-Security Alpha Remediation Correction

## Date
2026-05-08

## Change Summary

Correction implementation for validation findings on the `readiness-fixes` branch. Addresses five gaps found during validation review: assignment request validation regression, missing runtime feature flag enforcement, synchronous side-panel SSE execution, inadequate stream lifecycle test coverage, and missing ChatStreamEvent wire-contract tests.

## Files

### Modified

- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java` — Added `AgentAssignmentCreateRequest` DTO (no `agentId`, path-owned), converted side-panel `/chat/stream` to async Reactor-based SSE with `SubscriptionGuard` + `Schedulers.boundedElastic()`, removed synchronous `send()` helper
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java` — Injected `@Value("${magenta.features.schedules-enabled:false}")` flag, gated `pollDueSchedules()` with early return, added secondary constructor for test compat
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationEventService.java` — Injected `@Value("${magenta.features.reactions-enabled:false}")` flag, gated `handle()` to mark events handled without creating assignments, added secondary constructor for test compat
- `src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java` — Added package-private `callbacks()` factory + `LifecycleCallbacks` record for testable lifecycle callback registration
- `.internal-dev/plans/readiness-fixes/work-log.md` — Added validation correction status section, fixed stale 2.2 status

### Test Files Modified

- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java` — 4 new assignment DTO tests (assignUsesPathAgentIdWithoutBodyAgentId, assignIgnoresUnknownJsonFieldAgentId, +2 updated), 4 async chat tests (returnsBeforeChatServiceCompletes, emitsStartAndDone, emitsErrorForBlankMessage, emitsErrorForUnsupportedChatResponse), added BlockingChatService + AgentIdCapturingAssignmentService stubs
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationDurableRuntimeTest.java` — 2 new tests (disabledSchedulesDoNotFireOrCreateAssignments, disabledReactionsDoNotEnqueueAssignments), added disabledScheduleServices/disabledReactionServices factory methods
- `src/test/java/io/mindspice/magenta2/api/web/SseStreamLifecycleTest.java` — 5 new callback tests invoking real lifecycle callbacks (onCompletion, onTimeout, onError with/without domain handlers)

### New Test Files

- `src/test/java/io/mindspice/magenta2/ai/chat/model/ChatStreamEventSerializationTest.java` — 8 tests covering all sealed event types (Start, Chunk, Tool, SystemNotice, Interrupt, Context, Done, Error) with browser-consumed field verification

## Behavioral Impact

- `/api/agents/{agentId}/assignments` now accepts UI payloads without body `agentId` (path variable is authoritative)
- Disabled schedules no longer fire, create firings, or create assignments even if persisted rows exist
- Disabled reactions no longer enqueue assignments from persisted enabled reactions (events are still marked handled)
- Side-panel chat returns emitter immediately; model call runs on bounded elastic thread; cleanup via SubscriptionGuard
- All stream lifecycle callback paths (completion, timeout, error) are externally testable via `LifecycleCallbacks`

## Risks

- Superseded by third-pass validation. The second-pass correction passed automated tests, but later browser validation found remaining SSE first-event timing and abort-cleanup gaps. See `.internal-dev/changelogs/2026-05-08-third-pass-remediation-implementation.md` and `.internal-dev/reviews/2026-05-08-third-pass-remediation-validation-review.md`.

## Follow-up Items

- Closed by third-pass remediation and fallback browser validation on 2026-05-08.
