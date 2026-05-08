# Non-Security Alpha Remediation — Validation Follow-up

Date: 2026-05-08

Follow-up to `.internal-dev/reviews/2026-05-08-non-security-alpha-remediation-validation-review.md`. All six recommendations from the original review have been addressed. No unresolved blocking findings remain.

## Recommendation Status

| # | Finding | Status | Evidence |
|---|---------|--------|----------|
| 1 | Assignment request validation | Fixed | `AgentAssignmentCreateRequest` DTO without `agentId`. Controller maps path `agentId` into `AssignmentRequest`. Tests: `assignUsesPathAgentIdWithoutBodyAgentId`, `assignIgnoresUnknownJsonFieldAgentId`. |
| 2 | Feature flags at runtime | Fixed | `ScheduleService.pollDueSchedules()` early-returns when disabled. `OrchestrationEventService.handle()` marks events handled without creating assignments. Tests: `disabledSchedulesDoNotFireOrCreateAssignments`, `disabledReactionsDoNotEnqueueAssignments`. |
| 3 | Side-panel SSE lifecycle | Fixed | Async via `Flux.defer` + `Schedulers.boundedElastic()` + `SubscriptionGuard`. Tests: `agentChatStreamReturnsBeforeChatServiceCompletes`, error paths. |
| 4 | Stream lifecycle test coverage | Fixed | `LifecycleCallbacks` record + `callbacks()` factory. Tests invoke real onCompletion/onTimeout/onError callbacks with handler verification. |
| 5 | ChatStreamEvent serialization + browser | Fixed | `ChatStreamEventSerializationTest` covers 8 event types with browser-consumed fields. Startup smoke passes. Browser MCP probe pending (infrastructure-dependent). |
| 6 | Evidence cleanup | Fixed | Correction changelog created. Work log status table corrected (2.2: pending→completed). Correction section added. |

## Validation Commands Re-run

```bash
mvn test -Dtest=AgentOrchestrationControllerTest,OrchestrationDurableRuntimeTest,SseStreamLifecycleTest,ChatStreamEventSerializationTest
# Result: 48 tests, 0 failures

mvn test
# Result: 306 tests, 0 failures

timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-correction-smoke.sqlite'
# Result: Tomcat started on port 32849, no errors, exit code 124 (timeout, expected)
```

## Outstanding

- Playwright MCP browser validation: infrastructure-dependent. The profile infrastructure that enables MCP browser control is not available in this session. Code changes are test-covered and startup-smoke-verified. Browser JS payloads are unchanged (no JS modifications needed).
