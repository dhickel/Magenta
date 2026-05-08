# Context

The second-pass correction implementation was validated in:

- `.internal-dev/reviews/2026-05-08-post-validation-correction-review.md`
- `.internal-dev/plans/readiness-fixes/post-validation-correction-validation-criteria.md`

That validation closed the assignment request regression and largely closed runtime feature-flag enforcement, but it did not pass the full post-validation criteria. The remaining failures are concentrated in browser-visible SSE behavior, stream abort robustness, exact persistence assertions for disabled alpha features, and stale `.internal-dev` evidence.

This plan is the third-pass remediation contract. It is intentionally narrow. Do not re-open unrelated readiness fixes or security-class work.

# Goal

Make the `readiness-fixes` branch pass the post-validation criteria by:

- Making the agent side-panel SSE stream emit a browser-visible `start` event promptly before model work blocks.
- Hardening task/workflow/side-panel SSE send failure handling so client aborts do not become dropped Reactor errors or uncaught bounded-elastic exceptions.
- Adding deterministic validation for browser-visible task/workflow stream behavior.
- Closing the remaining feature-flag persistence assertion gaps.
- Reconciling `.internal-dev` evidence after the fixes are proven.

# In Scope

- `AgentOrchestrationController.chat` side-panel SSE lifecycle and tests.
- Shared SSE send/abort behavior in `SseStreamLifecycle` and stream controllers where needed.
- Task and workflow stream tests or validation fixtures needed to prove prompt events and clean abort behavior.
- Exact disabled schedule/reaction persistence assertions in `OrchestrationDurableRuntimeTest`.
- Browser validation evidence through Playwright MCP or a documented same-origin fallback.
- Work-log, changelog, validation review, and knowledge updates for this third pass.

# Out of Scope

- Security-class work: auth, secrets, SSRF, frontend injection, selected-agent shell policy, or shell hardening.
- Productizing schedules or event reactions. They remain disabled and inert by default.
- Rewriting the whole streaming layer from Spring MVC `SseEmitter` to WebFlux.
- Reworking model prompts, task execution quality, or workflow semantics beyond what is needed to make stream lifecycle observable and robust.
- Revalidating already-closed assignment behavior except as part of the final browser smoke.

# Implementation Steps

## 1. Fix Agent Side-Panel SSE First Event Timing

### Issue

The side-panel endpoint returns a Java `SseEmitter` before `chatService.chat(...)` completes, but the browser still receives no SSE event until after the blocking model call returns. In the second-pass fallback browser run, `/api/agents/{agentId}/chat/stream` timed out after 30 seconds without receiving an event.

### Cause

The correction wrapped the blocking model call in `Flux.defer(...).subscribeOn(Schedulers.boundedElastic())`, but it built both `start` and `done` payloads inside the deferred work and emitted them together in the subscriber after `chatService.chat(...)` returned.

Where it went wrong:

```java
var start = new LinkedHashMap<String, Object>();
start.put("event", "start");
// ...
ChatResponse response = chatService.chat(...); // blocking
// ...
return Flux.just(new AgentChatSsePayload(start, done));
```

The unit test only proved the controller method returned while `chatService.chat(...)` was blocked. It did not initialize the emitter or assert that `start` was actually written to the browser before the model call completed.

### Hard Decision

Keep this endpoint as SSE and keep the existing browser contract:

- `start`: `agentId`, optional `agentName`
- `done`: `agentId`, `conversationId`, `model`, `message`
- `error`: `error`

Do not switch the side panel to JSON. Do not invent new event names. The fix is to flush `start` before the blocking chat call.

### Code Targets

- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/main/resources/static/js/orchestration/agent-chat.js` only if the event contract must be adjusted, which should not be necessary.

### Implementation Shape

Build and send `start` before calling `chatService.chat(...)`. Then run the model call on bounded elastic and send terminal `done` or `error`.

Preferred shape:

```java
@PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public SseEmitter chat(@PathVariable String agentId, @Valid @RequestBody AgentChatRequest request) {
    SseEmitter emitter = SseStreamLifecycle.createEmitter();
    SseStreamLifecycle.SubscriptionGuard guard = SseStreamLifecycle.guardSubscription();
    SseStreamLifecycle.registerCallbacks(emitter, guard, null, null);

    Disposable subscription = Flux.defer(() -> {
            AgentProfile agent = agentProfileService.get(agentId);
            String message = request == null ? null : request.message();
            if (message == null || message.isBlank()) {
                return Flux.just(new AgentChatEvent("error", Map.of(
                    "event", "error",
                    "error", "message is required"
                )));
            }

            Map<String, Object> start = new LinkedHashMap<>();
            start.put("event", "start");
            start.put("agentId", agent.id());
            start.put("agentName", agent.name());

            return Flux.concat(
                Flux.just(new AgentChatEvent("start", start)),
                Flux.fromCallable(() -> buildDonePayload(agent, request, message))
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(done -> new AgentChatEvent("done", done))
                    .onErrorReturn(new AgentChatEvent("error", Map.of(
                        "event", "error",
                        "error", "agent chat failed"
                    )))
            );
        })
        .subscribe(
            event -> {
                if (!SseStreamLifecycle.trySendSseEvent(emitter, event.name(), event.data())) {
                    guard.dispose();
                }
                if ("done".equals(event.name()) || "error".equals(event.name())) {
                    emitter.complete();
                }
            },
            error -> {
                SseStreamLifecycle.trySendSseEvent(emitter, "error", Map.of(
                    "event", "error",
                    "error", error.getMessage()
                ));
                emitter.complete();
            }
        );

    guard.set(subscription);
    return emitter;
}

private record AgentChatEvent(String name, Map<String, Object> data) {}
```

Notes for the implementer:

- The exact helper names can differ, but `start` must be emitted before `chatService.chat(...)` blocks.
- Avoid returning generic `"agent chat failed"` if current tests require the original exception message. If exposing the message is the established pattern, use `error.getMessage()`.
- If Bean Validation rejects blank message before controller entry in real MVC, the second-pass criteria's "blank message emits SSE error" is not actually satisfiable for blank browser requests. Choose one behavior and document it. The preferred behavior for this pass is to remove `@NotBlank` from `AgentChatRequest.message` and let the SSE endpoint emit an `error` event for blank messages, because the criteria explicitly requires SSE `error`.

### Tests To Add Or Strengthen

Add an initialized-emitter timing test. Direct controller return is not enough.

Test shape:

```java
@Test
void agentChatStreamFlushesStartBeforeChatServiceCompletes() throws Exception {
    BlockingChatService chatService = new BlockingChatService();
    AgentOrchestrationController controller = controllerWith(chatService);

    SseEmitter emitter = controller.chat("agent-1",
        new AgentChatRequest(null, "hello", null, "agent detail"));
    CapturedSse captured = initializeEmitter(emitter);

    assertThat(captured.awaitEventContaining("\"event\":\"start\"", 1, TimeUnit.SECONDS)).isTrue();
    assertThat(chatService.started.await(1, TimeUnit.SECONDS)).isTrue();
    assertThat(chatService.completed.getCount()).isEqualTo(1);

    chatService.release.countDown();
    assertThat(captured.awaitEventContaining("\"event\":\"done\"", 2, TimeUnit.SECONDS)).isTrue();
}
```

Use the existing `ResponseBodyEmitter.Handler` reflection pattern from `TaskControllerTest` or extract a small local test helper if duplication gets excessive.

Also add:

- Blank message produces `error` SSE and completes, or document if MVC 400 is now the intended behavior.
- Unsupported `ChatResponse` produces `error` SSE and completes.
- Client completion/timeout/error disposes the guard. If this is hard to observe from controller tests, use `SseStreamLifecycle.callbacks(...)` plus an initialized-emitter abort test.

## 2. Harden SSE Send Failure And Client Abort Handling

### Issue

The fallback browser validation timed out task and workflow streams. After the browser aborted the requests, the server logged:

- `Operator called default onErrorDropped`
- `AsyncRequestNotUsableException` / broken pipe from task SSE send.
- `IllegalStateException: ResponseBodyEmitter has already completed` from workflow SSE error handling.

### Cause

The stream controllers throw from `onNext` when `SseEmitter.send(...)` fails. Reactor then routes the exception through subscriber error paths that may try to send another `failed` event to an already completed or disconnected emitter.

Where it went wrong:

```java
event -> {
    try {
        SseStreamLifecycle.sendSseEvent(emitter, event.name(), event.data());
    } catch (java.io.IOException ioException) {
        throw new RuntimeException(ioException);
    }
}
```

Throwing from the subscriber is the wrong cleanup mechanism for a client disconnect. The transport is already gone.

### Hard Decision

Treat `SseEmitter.send(...)` failures as terminal transport cleanup, not as domain failures. Do not send a second `failed` event after a broken pipe or "already completed" condition.

### Code Targets

- `src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/SseStreamLifecycleTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/TaskControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/WorkflowControllerTest.java`

### Implementation Shape

Add a no-throw send helper that returns success/failure:

```java
public static boolean trySendSseEvent(SseEmitter emitter, String name, Object data) {
    try {
        sendSseEvent(emitter, name, data);
        return true;
    } catch (IllegalStateException | IOException exception) {
        return false;
    }
}
```

Then use it in stream subscribers:

```java
Disposable subscription = stream
    .subscribeOn(Schedulers.boundedElastic())
    .subscribe(
        event -> {
            if (!SseStreamLifecycle.trySendSseEvent(emitter, event.name(), event.data())) {
                guard.dispose();
                emitter.complete();
            }
        },
        error -> {
            if (SseStreamLifecycle.trySendSseEvent(emitter, "failed",
                    Map.of("event", "failed", "error", error.getMessage()))) {
                emitter.complete();
            }
        },
        emitter::complete
    );
```

Guard against double completion if tests expose it. A small `AtomicBoolean completed` helper in each controller is acceptable if there is no clean shared abstraction yet.

Do not hide domain errors. If the model or service actually fails before the client disconnects, the stream should still emit `failed`/`error` and complete.

### Tests To Add Or Strengthen

- `SseStreamLifecycleTest.trySendReturnsFalseWhenEmitterAlreadyCompleted`.
- Task stream initialized-emitter test where handler throws on send; assert the stream does not produce an uncaught/dropped exception if practical.
- Workflow stream equivalent for completed emitter or broken send.

If Reactor dropped-error assertions are awkward, add deterministic controller tests around a fake emitter handler plus a browser/fallback validation step that confirms server logs no `onErrorDropped` or bounded-elastic uncaught error during a forced abort.

## 3. Add Deterministic Browser Validation For Task And Workflow Streams

### Issue

The second-pass browser fallback could validate `/chat`, assignment, disabled routes, and chat SSE, but task and workflow streams timed out after 30 seconds. That left the command gate incomplete.

### Cause

The live browser probe used model-backed task/workflow execution, which can be slow or dependent on local model behavior. The validation criteria need proof of stream lifecycle and browser parsing, not proof that a local model finishes quickly.

### Hard Decision

For this validation pass, use deterministic controlled fixtures for stream transport. Model quality and speed are not the thing being tested.

### Code Targets

Primary:

- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- `.internal-dev/reviews/<date>-third-pass-remediation-validation-review.md`

Potential production/test support if needed:

- `src/test/java/io/mindspice/magenta2/api/web/TaskControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/WorkflowControllerTest.java`

### Implementation Shape

Preferred browser validation approach:

1. Run the app on an isolated SQLite DB.
2. Use Playwright MCP first.
3. If MCP is blocked, use the same Chrome DevTools Protocol fallback pattern from the second-pass validation.
4. Validate browser contract with endpoints that produce prompt events.

For task/workflow streams, avoid live model dependence by using orchestration-context paths only if they are deterministic enough in current code. If they still call the model, keep unit/integration tests as the deterministic proof and make browser validation assert prompt `started` event plus clean abort behavior, not full model completion.

Browser criteria for task stream:

- Request `/api/tasks/{taskId}/runs/stream`.
- First received event is `started` within 2 seconds.
- Either terminal event arrives within a bounded longer window, or the validation intentionally aborts after receiving `started` and confirms clean cleanup with no server dropped-error logs.

Browser criteria for workflow stream:

- Request `/api/workflows/{workflowId}/runs/stream`.
- First received event is `started` within 2 seconds.
- For a deterministic two-step fixture, expect `step_started`, `step_completed`, and terminal `completed` if no model call is needed.
- If model calls are unavoidable, abort after `started` and verify clean cleanup.

Do not count a full 30-second timeout with no event as a pass.

### Test Helper Shape

Use a browser-side parser that reads incrementally instead of waiting for the entire response body:

```js
async function readFirstSseEvent(url, body, timeoutMs = 2000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify(body || {}),
      signal: controller.signal
    });
    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let text = "";
    while (!text.includes("\\n\\n")) {
      const {value, done} = await reader.read();
      if (done) break;
      text += decoder.decode(value, {stream: true});
    }
    return parseSse(text)[0];
  } finally {
    clearTimeout(timer);
    controller.abort();
  }
}
```

The second-pass fallback used `response.text()`, which waits for stream completion. For lifecycle validation, use incremental reads.

## 4. Close Feature-Flag Persistence Assertion Gaps

### Issue

Runtime gating is implemented, but tests do not assert every persistence side effect called out by the validation criteria.

### Cause

The tests assert no assignment and no `nextRunAt` advance, but they do not directly assert:

- No `schedule_firings` row is created.
- Disabled reaction events are marked handled.

### Hard Decision

Add exact persistence assertions. Do not change runtime behavior unless the assertions reveal a bug.

### Code Targets

- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationDurableRuntimeTest.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java` only if no query helper exists and a repository-level helper is cleaner than raw SQL in tests.

### Implementation Shape

For disabled schedules, prefer a repository helper if one already exists. Otherwise use `JdbcTemplate` in the test service fixture.

Example assertion:

```java
Integer firingCount = services.jdbcTemplate().queryForObject(
    "select count(*) from schedule_firings where schedule_id = ?",
    Integer.class,
    schedule.id()
);
assertThat(firingCount).isZero();
```

If `Services` does not expose `JdbcTemplate`, either add it to the test-only record or use a repository method that returns firing count.

For disabled reactions, publish or send the event, then assert `handledAt` is non-null:

```java
OrchestrationEvent event = services.repository().findEvents(...).getFirst();
assertThat(event.handledAt()).isNotNull();
```

If the repository does not expose event lookup by type/source, add a narrow test helper method only if it belongs in production. Otherwise use `JdbcTemplate` in the test.

## 5. Reconcile `.internal-dev` Evidence

### Issue

The correction changelog and work log claimed evidence closeout before browser validation actually passed.

### Cause

The implementation pass updated docs optimistically from automated-test evidence. Browser validation was listed as a follow-up, but the work log marked the evidence gate fixed.

### Hard Decision

Do not mark the third pass complete until browser evidence is actually recorded. If browser infrastructure is blocked, the fallback probe must be documented with exact blocker, commands, and results.

### Code Targets

- `.internal-dev/plans/readiness-fixes/work-log.md`
- `.internal-dev/changelogs/2026-05-08-non-security-alpha-remediation-correction.md`
- New changelog for this third-pass implementation.
- New validation review after implementation:
  - `.internal-dev/reviews/<date>-third-pass-remediation-validation-review.md`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` if the incremental SSE fallback parser becomes the reusable browser pattern.

### Implementation Shape

Work-log status must distinguish:

- second-pass correction attempted
- second-pass validation found open issues
- third-pass remediation implemented
- third-pass validation passed or failed

Do not overwrite the history. Add a new status section at the top with concrete evidence.

# Validation

Run validation in this order.

## Focused Tests

Required:

```bash
mvn test -Dtest=AgentOrchestrationControllerTest,SseStreamLifecycleTest
mvn test -Dtest=TaskControllerTest,WorkflowControllerTest
mvn test -Dtest=OrchestrationDurableRuntimeTest
```

Expected coverage:

- Side-panel `start` event is emitted before blocked chat completion.
- Side-panel terminal `done` and `error` behavior still matches `agent-chat.js`.
- SSE send failure does not throw into Reactor dropped-error paths.
- Disabled schedules create no firings and no assignments.
- Disabled reactions create no assignments and mark events handled.

## Full Tests

```bash
mvn test
```

No skipped or quarantined third-pass tests.

## Startup Smoke

```bash
rm -f /tmp/magenta2-third-pass-remediation-smoke.sqlite
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-third-pass-remediation-smoke.sqlite'
```

Exit code `124` is acceptable only if logs show healthy Tomcat startup before timeout.

## Browser Validation

Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` first.

Use Playwright MCP first. If MCP is blocked, record the exact blocker and use the Chrome DevTools Protocol fallback against the same live app.

Browser validation must prove:

- `/chat` loads.
- Basic chat SSE emits parseable `start`, `context`, `chunk`, and `done` events.
- Agent assignment UI/API payload without body `agentId` succeeds and uses path `agentId`.
- Agent side-panel stream emits `start` within 2 seconds.
- Agent side-panel stream emits terminal `done` or `error`, or the test intentionally aborts after `start` and proves clean cleanup.
- Task run stream emits `started` within 2 seconds and either reaches terminal event or aborts cleanly after `started`.
- Workflow run stream emits `started` within 2 seconds and either reaches terminal event or aborts cleanly after `started`.
- Disabled schedules and reactions routes return 404.
- Server logs do not show `onErrorDropped`, `ResponseBodyEmitter has already completed`, or broken-pipe stack traces after intentional browser aborts.

## Evidence Review

Create:

```text
.internal-dev/reviews/<date>-third-pass-remediation-validation-review.md
```

The review must list each third-pass issue as `closed` or `still open`. Any high or medium still-open finding blocks completion.

# Exit Criteria

- Side-panel SSE emits browser-visible `start` promptly before blocking model work.
- Task, workflow, and side-panel stream aborts clean up without dropped Reactor errors or uncaught bounded-elastic exceptions.
- Browser validation passes through Playwright MCP or a documented same-origin fallback.
- Disabled schedule/reaction tests assert the exact persistence effects required by the criteria.
- `mvn test` passes.
- Startup smoke reaches healthy Tomcat startup on isolated SQLite.
- Work log, changelog, knowledge, and third-pass validation review are updated with actual evidence.
