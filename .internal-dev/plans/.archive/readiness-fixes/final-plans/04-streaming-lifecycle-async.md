# Plan: Streaming Lifecycle and Async Boundaries Remediation

## Context (What's broken, why)

Magenta2 has five SSE (Server-Sent Events) streaming endpoints spanning chat, task execution, workflow execution, and agent orchestration. While the streaming paths are generally functional, lifecycle and error handling behavior is uneven across controllers. The ChatController has the most mature lifecycle handling (ActiveTurn tracking, domain failure recording, configurable timeouts, and message discard on error), but the TaskController, WorkflowController, and AgentOrchestrationController lack equivalent domain lifecycle integration. This unevenness creates several risks:

1. **Orphaned database state**: Task and workflow runs can be left in `RUNNING` status in the database when transport fails (client disconnect, SSE timeout, internal error during stream setup) because only ChatController records domain failures.
2. **Inconsistent event contracts**: ChatController emits `ChatStreamEvent.Error` objects on the `"error"` event; Task/Workflow emit `Map.of("event", "failed", "error", ...)` on a `"failed"` event; AgentOrchestration emits `Map.of("event", "error", ...)` on an `"error"` event. Frontend clients have no single contract to handle.
3. **Missing interrupt support**: Task, workflow, and agent chat streams do not register active turns and therefore cannot be interrupted client-side (no `/turns/{turnId}/interrupt` path).
4. **Setup-phase error inconsistency**: ChatController calls `emitter.completeWithError()` for errors before the subscription is created. Task/Workflow controllers attempt to `trySendSseEvent` a `"failed"` payload and only clean up if the send succeeds, leaving emitters incompletely terminated when the client has already disconnected.
5. **No configurable timeouts on non-Chat streams**: Only ChatController accepts a `planExecutionStreamTimeoutSeconds` property. Task/workflow/agent streams have no SSE timeout, so they could hang indefinitely on the server side.

Additionally, the `AgentOrchestrationController.chat()` method routes through `Mono.fromCallable(() -> chatService.chat(...))` which is a synchronous blocking call wrapped in a reactive container. While it's scheduled on `boundedElastic`, it is not a true streaming chat — it calls the synchronous one-shot `chat()` method rather than the streaming `stream()` method. This means agent chats produce only two events (`start` and `done`/`error`) with no intermediate `chunk` or `tool` events, which may be an intentional simplification but should be documented as such.

## Goal

Unify SSE lifecycle behavior across chat/task/workflow/orchestration streams so terminal outcomes, cleanup, and non-blocking execution semantics are consistent and verifiable.

## In Scope

- Shared stream outcome contract and centralized emitter/subscription lifecycle handling.
- Guaranteed cleanup on completion, timeout, failure, and disconnect paths.
- Async return guarantees and timeout coverage for all stream endpoints.

## Out of Scope

- Replacing transport technology (SSE stays SSE).
- Redesigning task/workflow business semantics.
- Mandatory conversion of agent chat into token-level streaming in this phase.

## Current Architecture (Inventory)

### SSE Endpoints Inventory

| # | Endpoint | Controller | Method | File:Line | Uses ActiveTurn | Domain Cleanup | Timeout Config |
|---|----------|------------|--------|-----------|-----------------|----------------|----------------|
| 1 | `POST /api/chat/stream` | ChatController | `stream()` | ChatController.java:77 | Yes | Yes | No (0L) |
| 2 | `POST /api/chat/{id}/plan/execute/stream` | ChatController | `streamPlanExecution()` | ChatController.java:83 | Yes | Yes | Configurable |
| 3 | `POST /api/tasks/{id}/runs/stream` | TaskController | `streamRun()` | TaskController.java:175 | No | No | No (0L) |
| 4 | `POST /api/workflows/{id}/runs/stream` | WorkflowController | `streamRun()` | WorkflowController.java:100 | No | No | No (0L) |
| 5 | `POST /api/agents/{id}/chat/stream` | AgentOrchestrationController | `chat()` | AgentOrchestrationController.java:180 | No | No | No (0L) |

### Detailed Lifecycle Analysis Per Endpoint

#### 1. ChatController.stream() / streamPlanExecution() (ChatController.java:92-275)

**Lifecycle Setup** (lines 93-118):
- Creates an `ActiveTurn` via `activeTurnRegistry.register()`.
- Creates an `SseEmitter` with optional timeout (0L for regular stream, configurable for plan execution).
- Creates a `SubscriptionGuard`.
- Defines `domainCleanup` runnable: disposes guard, completes active turn.
- Defines `failPlanExecution` consumer: runs domainCleanup, records execution failure if plan execution and not already finalized (via `AtomicBoolean` gate).
- Registers `emitter.onCompletion(domainCleanup)`, `emitter.onTimeout(failPlanExecution)`, `emitter.onError(failPlanExecution)`.

**Error Handling** (lines 131-271):
- **Pre-subscription error** (lines 131-134): If the initial `"start"` event send fails, calls `emitter.completeWithError(e)` and returns emitter immediately. No subscription is created, no guard set. This is correct — no leak — but the ActiveTurn is never completed (potential orphan in registry, though no subscription exists so no real harm).
- **onNext error** (lines 197-201): Catches exceptions from event sending inside the subscribe's `onNext`. Calls `failPlanExecution.accept(...)` (which disposes guard, completes turn, potentially records failure) and then `emitter.completeWithError(e)`. The `planExecutionFinalized` gate prevents double-recording of failures when onError also fires.
- **onError** (lines 204-221): Completes active turn. For plan execution: records execution failure (guarded by `planExecutionFinalized`). For non-plan: discards last user message. Sends `"error"` SSE event with `ChatStreamEvent.Error`. Calls `emitter.complete()`. Falls back to `completeWithError` if the error event send fails.
- **onComplete** (lines 223-271): Completes active turn. For plan execution: calls `handlePlanExecutionStreamFinished()`. Sends compaction notice if context was compacted. Sends `"done"` SSE event with `ChatStreamEvent.Done`. Calls `emitter.complete()`. Falls back to `failPlanExecution` + `completeWithError` on failure.

**Blocking analysis**: `subscribeOn(Schedulers.boundedElastic())` is used (line 137). The emitter is returned immediately after `guard.set(subscription)` (line 274). **Non-blocking**.

**Issues**:
- Pre-subscription error path (line 131-134) does not complete the ActiveTurn.
- In onNext error handler, `failPlanExecution` is called AND `emitter.completeWithError(e)` is called. The guard is already disposed by `failPlanExecution`, but `completeWithError` may trigger the emitter's onError callback which calls `failPlanExecution` again (guarded by atomic, so safe but wasteful).
- Uses `ChatStreamSupport.sendSseEvent()` which declares `throws Exception` and forces `APPLICATION_JSON` media type, whereas `SseStreamLifecycle.sendSseEvent()` throws `IOException` without explicit media type. Two competing APIs.

#### 2. TaskController.streamRun() (TaskController.java:175-232)

**Lifecycle Setup** (lines 176-179):
- Creates an `SseEmitter` with 0L timeout.
- Creates a `SubscriptionGuard`.
- Registers callbacks with `null` handlers via `SseStreamLifecycle.registerCallbacks(emitter, guard, null, null)`.
- **No ActiveTurn registration** — no interrupt support.
- **No domain cleanup callbacks** — timeout/error/completion only dispose the guard.

**Error Handling** (lines 200-230):
- **onNext** (lines 205-209): If `trySendSseEvent` fails (client disconnect), calls `guard.dispose()` and `completeQuietly(emitter)`. Otherwise no special handling.
- **onError** (lines 211-215): If `trySendSseEvent` for `"failed"` succeeds, calls `completeQuietly(emitter)`. **BUG**: If the send fails, the emitter is NOT completed.
- **onComplete** (line 217): Calls `completeQuietly(emitter)`.
- **Setup-phase catch blocks** (lines 220-229): Try to send `"failed"` event; only `completeQuietly` if send succeeds. Same bug as onError.

**Blocking analysis**: `subscribeOn(Schedulers.boundedElastic())` used (line 203). Emitter returned immediately (line 231). **Non-blocking**.

**Issues**:
- No `taskService` failure recording on transport failure — task runs persist as RUNNING.
- onError / catch-block incomplete termination when `trySendSseEvent` returns false.
- No ActiveTurn — no user-initiated cancellation support.
- Fixed 0L timeout with no configuration option.

#### 3. WorkflowController.streamRun() (WorkflowController.java:100-146)

Identical pattern to TaskController. Same issues apply.

**Additional issue**: For the synchronous path (`WorkflowStreamSupport.synchronousRunEvents()`), the `terminalRunEvents` call at line 72 calls `workflowService.runSynchronously(workflowId)` which is blocking. This is wrapped in `Flux.defer()` and scheduled on `boundedElastic`, so it does not block the servlet thread. However, if the Flux subscription happens after the emitter times out (or client disconnects), the synchronous execution will still run to completion with no consumer — wasted computation.

#### 4. AgentOrchestrationController.chat() (AgentOrchestrationController.java:180-237)

**Lifecycle Setup** (lines 181-183): Same as Task/Workflow — emitter + guard + null callbacks.
**No ActiveTurn** — no interrupt support.

**Error Handling** (lines 215-233):
- **onNext** (lines 216-224): If send fails → `guard.dispose()` + `completeQuietly()`. If event is `"done"` or `"error"` → `completeQuietly()`.
- **onError** (lines 226-233): Tries to send `"error"` event; `completeQuietly` only if send succeeds. **Same bug as Task/Workflow**.

**Blocking analysis**: The main computation in `donePayload()` calls `chatService.chat()` synchronously inside `Mono.fromCallable().subscribeOn(Schedulers.boundedElastic())`. The emitter returns immediately. **Non-blocking**.

**Issues**:
- Agent chat uses synchronous one-shot `chat()` instead of streaming `stream()` — intentional or oversight?
- No ActiveTurn — no interrupt support.
- Fixed 0L timeout.
- ChatService.chat() is synchronous — if it has a long LLM response time, the boundedElastic thread could be tied up for extended periods. The property `magenta.ai.openai-compatible-read-timeout-seconds: 360` (6 minutes) at the HTTP client level means individual agent chats could occupy a boundedElastic thread for up to 6 minutes.

### Cross-Controller Comparison Matrix

| Capability | ChatController | TaskController | WorkflowController | AgentOrchestrationController |
|------------|:-:|:-:|:-:|:-:|
| ActiveTurn / Interrupt | Yes | No | No | No |
| Domain failure recording | Yes | No | No | No |
| Message discard on error | Yes (non-plan) | No | No | No |
| Configurable SSE timeout | Plan exec only | No | No | No |
| Guard disposed on all paths | Yes | Partial (see bugs) | Partial (see bugs) | Yes |
| Subscribed on boundedElastic | Yes | Yes | Yes | Yes (Mono) |
| Emitter returns before completion | Yes | Yes | Yes | Yes |
| Consistent error event format | ChatStreamEvent.Error | Map | Map | Map |
| Terminal event name | `"done"` | `"completed"/"failed"` | `"completed"/"failed"` | `"done"` |

### Supporting Infrastructure Files

| File | Role |
|------|------|
| `SseStreamLifecycle.java` | Shared lifecycle: emitter creation, guard, callback registration, trySend/completeQuietly |
| `SsePayload.java` | Shared event payload record (name + data) |
| `ChatStreamSupport.java` | Chat-specific helpers: sendSseEvent with JSON, safeMessage, lastAssistantMessage |
| `TaskStreamSupport.java` | Task-specific: event mapping, context conversion, error payload |
| `WorkflowStreamSupport.java` | Workflow-specific: event mapping, synchronous run events, error payload |
| `ActiveTurnRegistry.java` | Turn lifecycle: register, complete, interrupt |
| `SubscriptionGuard.java` | Inner class in SseStreamLifecycle: thread-safe Disposable tracking |
| `GlobalExceptionHandler.java` | Catches ResponseStatusException, validation errors — does NOT have an SseEmitter-aware handler |

## Target Architecture

### 1. Stream Outcome Contract

Define a canonical enum `StreamOutcome` in `io.mindspice.magenta2.api.web`:

```java
public enum StreamOutcome {
    PROGRESS,           // In-progress event (tool, chunk, context, step_started, etc.)
    COMPLETED,          // Normal stream completion with results
    FAILED,             // Domain-level failure (model error, tool error, validation failure)
    CANCELLED,          // User-initiated cancellation via interrupt
    TIMEOUT,            // Server-side SSE timeout fired
    CLIENT_DISCONNECT   // Transport layer disconnect detected by failed send
}
```

Every SSE endpoint must:
1. Emit `"progress"` events for intermediate data when that endpoint has intermediate data (chunks, tool results, step updates).
2. Emit exactly ONE terminal event per stream, using the outcome name as the event name (`"completed"`, `"failed"`, `"cancelled"`, `"timeout"`, `"client_disconnect"`).
3. Use a consistent payload structure: `{ "outcome": "<outcome>", "data": {...}, "error": "<message-or-null>" }`.

### 2. Centralized Cleanup Mechanism

Enhance `SseStreamLifecycle` with a `StreamCleanup` interface:

```java
public interface StreamCleanup {
    /** Called on ANY terminal path before emitter completes. */
    void onTerminal(StreamOutcome outcome, Throwable error);

    /** Disposes the subscription regardless of outcome. */
    default void disposeSubscription(SubscriptionGuard guard) {
        guard.dispose();
    }
}
```

Add a convenience method:

```java
public static void wireLifecycle(
    SseEmitter emitter,
    SubscriptionGuard guard,
    StreamCleanup cleanup
) {
    emitter.onCompletion(() -> {
        cleanup.disposeSubscription(guard);
        cleanup.onTerminal(StreamOutcome.COMPLETED, null);
    });
    emitter.onTimeout(() -> {
        cleanup.disposeSubscription(guard);
        cleanup.onTerminal(StreamOutcome.TIMEOUT, 
            new IllegalStateException("SSE stream timed out"));
    });
    emitter.onError(error -> {
        cleanup.disposeSubscription(guard);
        cleanup.onTerminal(StreamOutcome.FAILED, error);
    });
}
```

Each controller creates a `StreamCleanup` implementation that handles domain-specific cleanup:
- **ChatController**: complete ActiveTurn, record execution failure (plan) or discard message (non-plan).
- **TaskController**: mark task run as failed in TaskService if stream did not complete normally.
- **WorkflowController**: mark workflow run as failed in WorkflowService if stream did not complete normally.
- **AgentOrchestrationController**: no persistent state to clean up (stateless agent chat).

### 3. Async Guarantees

All four controllers already use `subscribeOn(Schedulers.boundedElastic())` and return the emitter immediately. These guarantees must be documented and enforced via tests:

- **Rule**: No SSE controller method may block waiting for stream completion before returning the emitter.
- **Rule**: All Flux/Mono chains that produce stream content must be subscribed on `boundedElastic`.
- **Rule**: `wireLifecycle()` must be called before the subscription is created (so timeout/completion/error/disconnect callbacks are in place before any events flow).

Additionally, add a Spring `WebMvcConfigurer` that sets the async request timeout for all controllers to match the configured SSE timeout or a sensible default (e.g., 5 minutes), ensuring the servlet container does not kill the async context prematurely.

### 4. LLM Startup Latency Allowances in Tests

Where tests interact with actual LLM infrastructure (or stubs that simulate it), encode 30s-class timeout allowances:

- Any test that waits for an actual model response must use a minimum timeout of 30,000ms.
- Test infrastructure (stubs, fakes) that simulates cold-start latency should include a configurable delay parameter with a default of 30s.
- Document the property `magenta.ai.openai-compatible-read-timeout-seconds: 360` and ensure tests account for this.

## Implementation Steps

### Step 1: Define StreamOutcome Enum (Create)

**File**: `src/main/java/io/mindspice/magenta2/api/web/StreamOutcome.java`

- Create enum with values: `PROGRESS`, `COMPLETED`, `FAILED`, `CANCELLED`, `TIMEOUT`, `CLIENT_DISCONNECT`.
- Add a static method `terminalOutcomes()` returning `Set.of(COMPLETED, FAILED, CANCELLED, TIMEOUT, CLIENT_DISCONNECT)` for validation.
- Document each outcome with Javadoc.

### Step 2: Define StreamCleanup Interface and Wire Method (Create/Modify)

**File**: `src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java`

Additions:
- Add `StreamCleanup` interface (functional, with default `disposeSubscription` method).
- Add `public static void wireLifecycle(SseEmitter emitter, SubscriptionGuard guard, StreamCleanup cleanup)` method.
- Deprecate `registerCallbacks(SseEmitter, SubscriptionGuard, Runnable, Consumer<Throwable>)` in favor of `wireLifecycle`.
- Add `public static void sendTerminalEvent(SseEmitter emitter, StreamOutcome outcome, Object data)` that constructs the standardized terminal payload and sends it.
- Add `public static void completeWithTerminal(SseEmitter emitter, StreamOutcome outcome, Object data)` that sends the terminal event then calls `completeQuietly`.
- Ensure `trySendSseEvent` is used consistently within all lifecycle paths.

### Step 3: Standardize ChatController (Modify)

**File**: `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`

- Replace the manual `emitter.onCompletion/onTimeout/onError` wiring with `SseStreamLifecycle.wireLifecycle(emitter, guard, chatCleanup)`.
- Implement `StreamCleanup` as a local class or anonymous lambda that:
  - On terminal: disposes subscription, completes ActiveTurn.
  - On FAILED (plan execution): records execution failure via `chatService.recordExecutionFailure()` (with `planExecutionFinalized` gate).
  - On FAILED (non-plan): discards last user message via `chatService.discardLastUserMessage()`.
  - On TIMEOUT (plan execution): records execution failure.
  - On CANCELLED: records cancellation in plan execution if applicable.
- Move the `"error"` event sending logic to use `sendTerminalEvent(emitter, StreamOutcome.FAILED, errorPayload)`.
- Move the `"done"` event sending logic to use `sendTerminalEvent(emitter, StreamOutcome.COMPLETED, donePayload)`.
- Fix pre-subscription error path (line 131-134): ensure ActiveTurn is completed before `completeWithError`.

### Step 4: Standardize TaskController (Modify)

**File**: `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`

- Replace `SseStreamLifecycle.registerCallbacks(emitter, guard, null, null)` with `SseStreamLifecycle.wireLifecycle(emitter, guard, taskCleanup)`.
- Implement `StreamCleanup` that:
  - On terminal: disposes subscription.
  - On COMPLETED: no additional domain action (the stream already emitted a terminal status event that the TaskService received).
  - On FAILED/TIMEOUT/CLIENT_DISCONNECT: calls `taskService.failActiveRun(conversationId, errorMessage)` if a run was started. This requires tracking the `conversationId` and `runId` from the stream context.
- Add ActiveTurn registration so task runs can be interrupted:
  - Inject `ActiveTurnRegistry` into TaskController.
  - Register a turn at stream start.
  - Include `turnId` and `token` in the `"started"` event payload only if task execution actually polls and honors interrupts. Do not expose cancellation handles that cannot cancel work.
- Fix the onError and catch-block incomplete termination bug: always call `completeQuietly` or `completeWithError` after attempting to send the error event, regardless of whether the send succeeded.
- Add configurable timeout via `@Value("${magenta.task.run-stream-timeout-seconds:0}")`.
- Replace `"failed"` event name with `StreamOutcome.FAILED` name and standardized payload.

### Step 5: Standardize WorkflowController (Modify)

**File**: `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`

- Same pattern as TaskController (Step 4).
- Add ActiveTurn registration for workflow runs.
  - Include `turnId` and `token` only if workflow execution actually polls and honors interrupts. Otherwise defer interrupt exposure and document the deferral.
- Add configurable timeout via `@Value("${magenta.workflow.run-stream-timeout-seconds:0}")`.
- Ensure synchronous run path (`WorkflowStreamSupport.synchronousRunEvents`) handles stream termination properly when client disconnects before the synchronous run completes.

### Step 6: Standardize AgentOrchestrationController (Modify)

**File**: `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`

- Replace `SseStreamLifecycle.registerCallbacks(emitter, guard, null, null)` with `SseStreamLifecycle.wireLifecycle(emitter, guard, agentChatCleanup)`.
- Implement `StreamCleanup` that only disposes the subscription (no domain state to clean).
- Add configurable timeout via `@Value("${magenta.agent.chat-stream-timeout-seconds:0}")`.
- Fix the onError incomplete termination bug.

### Step 7: Consolidate SSE Event Sending (Modify)

**Files**: `ChatStreamSupport.java`, `TaskStreamSupport.java`, `WorkflowStreamSupport.java`, `SseStreamLifecycle.java`

- Move the `sendSseEvent(SseEmitter, String, Object)` with explicit media type from `ChatStreamSupport` to `SseStreamLifecycle` (remove the `ChatStreamSupport` version).
- Ensure all SSE event sends go through `SseStreamLifecycle.sendSseEvent()` (the `throws Exception` version for explicit media type, or `throws IOException` version for default).
- In `TaskStreamSupport` and `WorkflowStreamSupport`, replace ad-hoc `Map.of("event", ...)` payloads with a shared terminal event builder that includes `StreamOutcome`.
- Add `static SsePayload terminalPayload(StreamOutcome outcome, Map<String, Object> data, String error)` to `SsePayload` or a new `StreamEventBuilder` utility.

### Step 8: Add Spring Async Configuration (Create)

**File**: `src/main/java/io/mindspice/magenta2/api/web/WebMvcConfig.java`

- Implement `WebMvcConfigurer`.
- Override `configureAsyncSupport()` to set:
  - `asyncRequestTimeout`: match the longest configured stream timeout (from properties) plus a buffer, or default to 600,000ms (10 minutes).
  - `taskExecutor`: use a Spring `AsyncTaskExecutor` appropriate for MVC async work. Do not wire Reactor's `boundedElastic` scheduler directly into `WebMvcConfigurer`.

### Step 9: Update application.yml (Modify)

**File**: `src/main/resources/application.yml`

Add properties:
```yaml
magenta:
  task:
    run-stream-timeout-seconds: 0
  workflow:
    run-stream-timeout-seconds: 0
  agent:
    chat-stream-timeout-seconds: 0
```

### Step 10: Extend GlobalExceptionHandler for SseEmitter Errors (Modify)

**File**: `src/main/java/io/mindspice/magenta2/api/web/GlobalExceptionHandler.java`

- Add an `@ExceptionHandler` for exceptions that occur during SSE setup (before the emitter is returned). These should still return a proper HTTP error response rather than an SseEmitter that will error.
- Consider: the handler currently returns `ResponseEntity<Map<String, Object>>` but SSE endpoints cannot return that type. Instead, ensure that exceptions thrown before the SseEmitter is created (e.g., in `streamPlanExecution()` at line 88) are caught and converted properly — which they already are via `ResponseStatusException`.
- Add clear documentation that the exception handler does NOT handle SseEmitter-level errors (those are handled in-stream).

## Validation

### Category A: SSE Lifecycle Tests

| Test | File | What it verifies |
|------|------|-----------------|
| `chatStreamEmitsProgressThenCompleted` | `ChatControllerTest.java` | Normal path: start -> chunk -> done events, emitter completed, ActiveTurn removed |
| `chatStreamEmitsErrorOnFailure` | `ChatControllerTest.java` | onError path: error event sent, failure recorded for plan execution |
| `chatStreamEmitsErrorOnTimeout` | `ChatControllerTest.java` | onTimeout path: timeout event sent, failure recorded |
| `taskStreamEmitsStartedThenCompleted` | `TaskControllerTest.java` | Normal path: started -> progress -> completed events |
| `taskStreamMarksRunFailedOnTransportError` | `TaskControllerTest.java` | Transport disconnect: run marked as failed in service |
| `workflowStreamEmitsStepEventsAndCompleted` | `WorkflowControllerTest.java` | Normal path: started -> step_started -> step_completed -> completed |
| `agentChatStreamEmitsStartAndDone` | `AgentOrchestrationControllerTest.java` | Normal path: start -> done events |
| `agentChatStreamEmitsErrorForBlankMessage` | Already exists at AgentOrchestrationControllerTest:100 | Verify error envelope matches new format |
| `streamCleanupCalledOnAllTerminalPaths` | `SseStreamLifecycleTest.java` | Verify wireLifecycle calls onTerminal with correct outcome for completion, timeout, error, disconnect |
| `guardDisposedOnAllTerminalPaths` | `SseStreamLifecycleTest.java` | Verify SubscriptionGuard is always disposed |

### Category B: Non-Blocking Verification Tests

| Test | File | What it verifies |
|------|------|-----------------|
| `chatStreamReturnsBeforeCompletion` | Already exists at ChatControllerTest:171 | Keep — ensure emitter returned before stream completes |
| `taskStreamReturnsBeforeCompletion` | Already exists at TaskControllerTest:91 | Keep — ensure emitter returned before task execution |
| `workflowStreamReturnsBeforeCompletion` | Already exists at WorkflowControllerTest:126 | Keep — ensure emitter returned before workflow execution |
| `agentChatStreamReturnsBeforeChatServiceCompletes` | Already exists at AgentOrchestrationControllerTest:42 | Keep — ensure emitter returned before chat completes |
| `streamReturnsWithin5msWhenDelayedWork` | New test per controller | Measure max return latency of controller method under worst-case service latency |

### Category C: Latency Allowance Tests

| Test | File | What it verifies |
|------|------|-----------------|
| `planExecutionStreamTimeoutAllows30sModelStartup` | `ChatControllerTest.java` | Configure 30s timeout, verify emitter accepts 29s delay |
| `agentChatStreamHandlesSlowChatService` | `AgentOrchestrationControllerTest.java` | Stub chatService with 30s delay, verify emitter returns immediately and events eventually arrive |
| `taskStreamTimeoutAllowsLongRunningExecution` | `TaskControllerTest.java` | Configure timeout, verify long-executing task doesn't prematurely timeout |

### Milestone Gate Validation Contract

Relevant alpha-gate snippets to carry into validation:
- `architectural-alignment-report.md`: "The streaming architecture is robustly implemented using Spring's `SseEmitter`" and "`SseStreamLifecycle` ... managing emitter lifecycles, subscription guards, and error handling."
- `alpha-milestone-gate-summary.md`: "SSE and HTMX patterns are well-implemented."
- `e2e-and-stability-report.md`: "Initial runs failed due to a tight 10s timeout. Increasing the timeout to 30s resolved this."
- `e2e-and-stability-report.md`: "Planning mode initialization ... can take 5-15 seconds depending on model responsiveness."

The implementing agent must launch a validation sub-agent after completing this plan. The sub-agent must receive this plan file, the alpha-gate snippets above, the final `git diff`, SSE-focused test output, and proof of non-blocking stream return checks.

Validation sub-agent prompt:
```text
You are validating the Streaming Lifecycle and Async Boundaries remediation in Magenta2. Read `.internal-dev/plans/readiness-fixes/final-plans/04-streaming-lifecycle-async.md`, then manually inspect all changed SSE endpoints, `SseStreamLifecycle`, stream support classes, timeout config, and frontend-facing event payload changes. Do not trust the implementer's summary of cleanup or async behavior without checking the code and tests.

Validation contract:
- Confirm all stream endpoints return the `SseEmitter` immediately and subscribe work asynchronously.
- Confirm every completion, error, timeout, and client-disconnect path disposes subscriptions and performs domain cleanup exactly once.
- Confirm terminal event payloads are compatible during any transition period and include the standardized outcome field.
- Confirm task/workflow cancellation handles are not exposed unless cancellation is actually implemented and tested.
- Confirm tests encode 30s-class latency allowances where actual or simulated LLM startup latency is involved.

Return findings first, ordered by severity, with file/line references and any missing terminal-path coverage.
```

Manual work proof to verify:
- Inspect controller methods to ensure no `.block()`, `.blockLast()`, or synchronous wait occurs before returning the emitter.
- Verify direct emitter lifecycle tests cover completion, error, timeout, failed send/client disconnect, and setup errors.
- Verify focused controller tests, `mvn test`, startup smoke, and any Playwright/live stream validation required by changed browser behavior.

## Risk Assessment and Rollback Strategy

### Risks

1. **Backward compatibility**: Changing SSE event payloads from `Map.of("event", "failed", ...)` to the standardized `{ "outcome": "FAILED", ... }` format could break frontend clients. **Mitigation**: Keep both the old `"event"` field and add the new `"outcome"` field in terminal payloads during a transition period. Document the deprecation.

2. **TaskService.failActiveRun requirements**: The `StreamCleanup` in TaskController needs access to the conversation ID and run ID to call `taskService.failActiveRun()`. These values are currently scoped inside the Flux chain and not available to the cleanup handler. **Mitigation**: Track them in an `AtomicReference<String>` set during the `"started"` event emission, accessible by the cleanup callback.

3. **WorkflowService lacks a `failRun` method**: The current `WorkflowService` may not have a method to mark a run as failed from the controller. **Mitigation**: Either add `workflowService.failRun(runId, errorMessage)` or check if the existing API supports it.

4. **ActiveTurn for task/workflow**: Adding ActiveTurn support means the frontend can now cancel task/workflow streams. The current `ActiveTurnRegistry.interrupt()` mechanism enqueues an interrupt message that the streaming logic must poll. For task/workflow streams, the interrupt flow is different: it should cancel the assignment or terminate execution rather than inject an interrupt message. **Mitigation**: Either implement end-to-end cancellation semantics in this plan, or explicitly defer ActiveTurn exposure for task/workflow streams. Do not register or return interrupt handles without a working cancellation path.

5. **boundedElastic thread exhaustion**: AgentOrchestrationController holds a boundedElastic thread for the duration of `chatService.chat()`. If many agent chats occur simultaneously, the bounded elastic pool (default: 10 * CPU cores cap) could be exhausted. **Mitigation**: Configure a dedicated scheduler for agent chat, or switch agent chat to use the async `MagentaWorkExecutor` pattern used by chat turns.

### Rollback Strategy

1. Keep the old `registerCallbacks(emitter, guard, null, null)` method available (deprecated, not removed) in case the `wireLifecycle` method introduces issues.
2. Behind a feature flag `magenta.features.standardized-stream-events` (default: `true`). If issues arise, set to `false` to revert to old event format.
3. Git-tag the commit before starting implementation so rollback is a single `git revert`.

## Exit Criteria

1. All five SSE endpoints use `SseStreamLifecycle.wireLifecycle()` with a domain-aware `StreamCleanup` implementation.
2. All terminal SSE events (error, done, failed, completed, timeout) use the standardized `StreamOutcome`-based payload format.
3. `SubscriptionGuard` is disposed on every terminal path (completion, timeout, error, client disconnect) for every endpoint — verified by test.
4. Task and workflow runs are persisted with a terminal status (FAILED) when the transport layer fails — verified by test.
5. Configurable SSE timeouts exist for all endpoints, defaulting to 0 (no timeout) to preserve current behavior.
6. Non-blocking emitter return is verified for all five endpoints — verified by existing and new tests.
7. LLM startup latency allowance (30s-class) is encoded in tests where applicable.
8. `GlobalExceptionHandler` documentation clearly states it does not handle in-stream SseEmitter errors.
9. No regression in existing SSE endpoint tests.

### Critical Files for Implementation
- /home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java
- /home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/ChatController.java
- /home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/TaskController.java
- /home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java
- /home/hickelpickle/Code/Java/magenta2/src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java
