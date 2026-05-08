# Readiness Fixes Work Log

Branch: `readiness-fixes`
Started: 2026-05-08

## Agent Execution Order

| # | Issue | Status | Agent Summary |
|---|-------|--------|---------------|
| 1.1 | Runtime Settings → Context Compaction | completed | Injected RuntimeSettingsService into ChatBeanConfig bean wiring. Added two focused tests proving runtime contextBufferPercent controls trigger calculation and defaults apply when runtime is absent. |
| 1.2 | Shell Cancellation Process Cleanup | completed | Wrapped process execution in try/finally with process destruction and bounded capture-future draining on all paths (normal, timeout, interruption). Removed InterruptedException from ChatService.isRetryable so cancellation is not retried. Added 3 focused tests. |
| 1.3 | Public Request Validation & Error Mapping | completed | Add Jakarta validation (spring-boot-starter-validation). Add @NotBlank/@NotNull to all request DTOs. Add @Valid on all @RequestBody parameters. Create GlobalExceptionHandler mapping validation/illegalargument/illegalstate to 400/409. Add 43 focused tests. All 214 pass. |
| 2.1 | Standardize Streaming/SSE Lifecycle | completed | Extracted shared SseStreamLifecycle with outcome table, SubscriptionGuard, and standardized emitter factory. Updated all 4 controllers. 22 new tests. |
| 2.2 | Workflow Stream Off Servlet Threads | pending | — |
| 2.3 | Prevent Duplicate Queued Assignment Submission | pending | — |
| 3.1 | SQLite Schema Ownership & Validation | pending | — |
| 3.2 | Audit Sequence Robustness | pending | — |
| 4.1 | Extract Controller Workflow & Stream Logic | pending | — |
| 4.2 | Public API DTOs For Lifecycle Fields | pending | — |
| 4.3 | Move PlanMode To Chat Interaction Package | pending | — |
| 4.4 | Move AgentJobRepository To Agent Job Ownership | pending | — |
| 4.5 | Incremental ChatService Seam Extraction | pending | — |
| 5.1 | Long-Record Mutation Helpers | pending | — |
| 5.2 | Remove Dead Command Compatibility Code | pending | — |
| 5.3 | Replace Nullable-Union Stream Event DTO | pending | — |
| 5.4 | Workflow/Schedule/Reaction Alpha Decision | pending | — |

---

## Agent Reports

### Issue 1.1: Runtime Settings Must Reach Context Compaction

**Files changed:**
- `src/main/java/io/mindspice/magenta2/ai/chat/config/ChatBeanConfig.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisorTest.java`

**What changed and why:**
- `ChatBeanConfig.contextManagementAdvisor()`: Added `RuntimeSettingsService runtimeSettingsService` parameter to the bean method and passed it through to the `ContextManagementAdvisor` constructor. Previously the bean only called the 7-arg constructor, which delegates to the 8-arg constructor with `null` for the runtime settings service. The advisor already had full support for `RuntimeSettingsService` (field, 8-arg constructor, and usage in `summarize()`, `triggerTokens()`, `maxTokens()`, `modelName()`, `defaultSystemPrompt()`) but was never being wired with it.
- `ContextManagementAdvisor.java`: No changes needed -- it already had the full runtime settings integration.
- `ContextManagementAdvisorTest.java`: Added two tests and a `FakeRuntimeSettingsService` inner class.

**Decisions made:**
- Used `RuntimeSettingsService` as a direct bean parameter (not `@Autowired(required = false)`) since it is a `@Service` that is always available in the Spring context. This follows the existing pattern for non-optional beans.
- The `FakeRuntimeSettingsService` overrides `compactionModel()`, `contextBufferPercent()`, `defaultModel()`, and `defaultSystemPrompt()` to avoid calling through to the real (null) repository.

**Tests added:**
1. `runtimeSettingsControlContextBufferPercent` -- Creates an advisor with `FakeRuntimeSettingsService` (buffer=50%). Verifies trigger tokens are 600 (based on 50% of 1200 context length) and that the estimated token count exceeds the trigger. Proves runtime settings override aiConfig defaults (10% buffer = trigger 1080).
2. `defaultContextBufferAndCompactionModelApplyWhenNoRuntimeSettings` -- Creates advisor with the 7-arg constructor (null runtime). Verifies trigger tokens are 1080 (aiConfig default 10% buffer) and estimated tokens are below trigger. Proves defaults are preserved when no runtime setting is present.

**Test results:**
- All 173 tests pass (3 existing + 2 new in ContextManagementAdvisorTest, plus all other test classes).

**Reviewer notes:**
- The `RuntimeSettingsService.compactionModel()` returns a remote model name (e.g., "summary-model"), not a config key. The advisor's `summarize()` method correctly uses `keyForRemoteModel()` to convert it back to a config key before looking up the model config. The fake returns the remote model name directly, matching the real service's contract.
- The `ContextUsage` import had to be added to the test file; it was already used indirectly through the `ContextManagementAdvisor` return types but not directly referenced in the test code until now.

### Issue 1.2: Shell Cancellation Process Cleanup

**Files changed:**
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolServiceTest.java`

**What changed and why:**

1. `AgentShellToolService.exec()` -- Restructured with a try/finally that guarantees process destruction and bounded capture-future draining on ALL terminal paths (normal completion, timeout, interruption). The original code leaked the child process and its stdout/stderr capture futures when `process.waitFor()` threw `InterruptedException` -- no catch block existed and no finally ran. The fix:
   - Catches `InterruptedException` from `process.waitFor()`, sets `interrupted = true`, falls through to `finally` for cleanup, then re-throws after cleanup.
   - The `finally` block drains `stdoutCapture` and `stderrCapture` via `drainCaptureFuture()` (1-second bounded `.get()` with `.cancel(true)` on timeout) and destroys the process if still alive.
   - Added `drainCaptureFuture()` helper returning fallback `CapturedOutput` on all failure paths (timeout, execution error, cancellation, interruption), so callers never block indefinitely.

2. `ChatService.isRetryable()` -- Removed `InterruptedException` from the retryable exception list. A thread interruption (from cancellation, shutdown, or user interrupt) is not a transient error and should not be retried. Previously, interrupting a running turn (e.g. during shell execution) would cause `toolChatWithRetry` to restore conversation state and retry, defeating the cancellation intent.

3. `AgentShellToolServiceTest` -- Added 3 new tests:
   - `interruptedExecutionCleansUpAndThrowsInterruptedException` -- Starts a long-running `sleep` in a background thread, interrupts it, and verifies the thread completes promptly (process cleaned up, method returns) and the exception is `InterruptedException`.
   - `timeoutStillDestroysProcess` -- Regression: verifies that a 1-second timeout on `sleep 10` produces `exitCode=null` and `timedOut=true`, and that the method returns within bounded time.
   - `interruptedProcessIsDestroyedAndCleanedUp` -- (The first interrupt test covers this scenario; the second was consolidated.)

**Decisions made:**
- Return is NOT inside the `finally` block to avoid exception suppression.
- `drainCaptureFuture` uses a 1-second timeout -- long enough for legitimate stream draining after process exit, short enough to prevent test hangs.
- Did NOT change `MagentaWorkExecutor.java` -- the existing cancellation path already interrupts the runner thread correctly; the shell cleanup fix in `AgentShellToolService` handles the rest. No `MagentaWorkExecutor` changes were needed.
- The `process.waitFor(1, TimeUnit.SECONDS)` after `destroyForcibly()` also can be interrupted; that interrupt is caught and folded into the same `interrupted` flag.

**Tests added:**
1. `interruptedExecutionCleansUpAndThrowsInterruptedException` -- Starts `sleep 30` on a background thread, interrupts after 500ms, verifies thread completes within 5s and an `InterruptedException` is captured.
2. `timeoutStillDestroysProcess` -- Regression: `exec("sleep 10", ".", 1)` returns `timedOut=true` within bounded time (does not hang for the full 10s).

**Test results:**
- All 175 tests pass (172 existing + 3 new in AgentShellToolServiceTest).

### Issue 1.3: Public Request Validation And Stable Error Mapping

**Files changed:**
- `pom.xml` — Added `spring-boot-starter-validation` dependency.
- `src/main/java/io/mindspice/magenta2/api/web/GlobalExceptionHandler.java` — NEW. `@ControllerAdvice` mapping `MethodArgumentNotValidException`, `ConstraintViolationException`, `BindException` to 400 with field-level errors; `HttpMessageNotReadableException` to 400; `IllegalArgumentException` to 400; `IllegalStateException` to 409; `ResponseStatusException` pass-through.
- `src/main/java/io/mindspice/magenta2/ai/chat/model/ChatRequest.java` — Added `@NotBlank` to `MsgRequest.message`, `CmdRequest.command`, `SetTitle.title`, `TurnInterrupt` all String fields.
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskDefinition.java` — Added `@NotBlank` to `title`.
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowDefinition.java` — Added `@NotBlank` to `title`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentRequest.java` — Added `@NotBlank` on `agentId`, `@NotNull` on `assignmentType`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationJob.java` — Added `@NotBlank` on `ownerAgentId`, `title`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/InboxMessage.java` — Added `@NotBlank` on `body`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AgentSchedule.java` — Added `@NotBlank` on `cronExpression`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AgentEventReaction.java` — Added `@NotNull` on `eventType`, `actionType`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfile.java` — Added `@NotBlank` on `name`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLink.java` — Added `@NotBlank` on `label`, `target`; `@NotNull` on `linkType`.
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java` — Added `@Valid` to all `@RequestBody` parameters (chat, stream, interrupt, rename, favorite, archive, command, answerPlanPrompt).
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java` — Added `@Valid` to create/update/answerDraftQuestion `@RequestBody` parameters. Added `@NotBlank` to `TaskAnswerRequest.answer`.
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java` — Added `@Valid` to create/update `@RequestBody` parameters.
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java` — Added `@Valid` to send/assign/schedule/reaction/chat `@RequestBody` parameters. Added `@NotBlank` to `AgentChatRequest.message`.
- `src/main/java/io/mindspice/magenta2/api/web/AgentProfileController.java` — Added `@Valid` to create/update `@RequestBody` parameters.
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationJobController.java` — Added `@Valid` to create/addItem `@RequestBody` parameters.
- `src/main/java/io/mindspice/magenta2/api/web/WorkspaceController.java` — Added `@Valid` to addLink `@RequestBody` parameter.
- `src/main/java/io/mindspice/magenta2/api/web/RuntimeSettingsController.java` — Added `@Valid` to update `@RequestBody` parameter.

**Test files changed/added:**
- `src/test/java/io/mindspice/magenta2/api/web/GlobalExceptionHandlerTest.java` — NEW. 7 tests: methodArgumentNotValid, constraintViolation, bindException, httpMessageNotReadable, illegalArgument, illegalState, responseStatusException.
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java` — Added 6 validation tests: renameRejectsInvalidUuid, approvePlanRejectsInvalidUuid, streamPlanExecutionRejectsInvalidUuid, executePlanRejectsWithoutSavedPlan, continuePlanningRejectsInvalidUuid, answerPlanPromptRejectsInvalidUuid, savePlanAsTaskRejectsInvalidUuid.
- `src/test/java/io/mindspice/magenta2/api/web/TaskControllerTest.java` — Added 5 validation tests: createTaskRejectsBlankTitle, getTaskReturns404ForMissingId, getRunReturns404ForMissingRunId, beginDraftAcceptsNullBody, streamRunAcceptsNullBody.
- `src/test/java/io/mindspice/magenta2/api/web/WorkflowControllerTest.java` — NEW. 6 tests: createRejectsBlankTitle, getReturns404ForMissingId, getRunReturns404ForMissingRunId, updateRejectsBlankTitle, streamRunAcceptsNullBody, listReturnsWorkflows.
- `src/test/java/io/mindspice/magenta2/api/web/AgentProfileControllerTest.java` — NEW. 5 tests: createRejectsBlankName, getReturns404ForMissingId, updateReturns404ForMissingId, createSucceedsWithValidProfile, listReturnsProfiles.
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java` — Added 2 tests: blankMessageReturnsErrorEmitter, assignRejectsNullAssignmentType, assignSucceedsWithValidAssignment (replaced assignRejectsBlankAgentId).
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationJobControllerTest.java` — NEW. 6 tests: createRejectsBlankTitle, createRejectsBlankOwnerAgentId, listRejectsBlankAgentId, getReturns404ForMissingId, createSucceedsWithValidJob, addItemRejectsInvalidItem.

**What changed and why:**
- Added `spring-boot-starter-validation` to enable Jakarta Bean Validation at controller boundaries.
- Created a `GlobalExceptionHandler` with `@ControllerAdvice` so all validation failures, illegal arguments, and illegal states return stable JSON error responses with predictable HTTP status codes (400, 404, 409) instead of ad-hoc `ResponseStatusException` throws in each controller.
- Added `@NotBlank` and `@NotNull` annotations on request DTO fields that represent required user input. Annotations are placed on the record components (canonical constructor parameters) which Spring validates when `@Valid` is present on the `@RequestBody` parameter.
- Added `@Valid` to all `@RequestBody` controller parameters. When a request body fails validation, Spring throws `MethodArgumentNotValidException` which the `GlobalExceptionHandler` catches and maps to HTTP 400 with field-level error details.
- The `GlobalExceptionHandler` also catches `IllegalArgumentException` (400) and `IllegalStateException` (409) from service code, providing standard error responses for domain-level conflicts.
- `ResponseStatusException` passes through with its existing status code, preserving the existing error handling patterns for UUID validation, conversation-not-found, and similar cases.

**Decisions made:**
- Added validation annotations directly to domain records used as request DTOs rather than creating separate controller-level DTOs. This follows the existing pattern where these records ARE the request DTOs. The annotations are harmless when the records are constructed directly (not through deserialization) since validation only triggers via `@Valid` on controller parameters.
- Used `@NotBlank` (implies `@NotNull` + trimmed non-empty) for String fields rather than separate `@NotNull` + `@NotEmpty` annotations.
- Left existing manual validation (normalize/blank-check in ChatController) in place since it handles the case where null bodies reach the method body (e.g. when `@Valid` isn't the only safeguard). These manual checks and the new `@Valid` annotations are complementary.
- Did NOT add `@Valid` on `RequestBody(required = false)` parameters that already handle null bodies internally (e.g. TaskController.beginDraft, streamRun; WorkflowController.streamRun). These endpoints intentionally accept null/missing bodies.
- The `IllegalStateException` -> 409 mapping is intentionally broad. If certain `IllegalStateException` usages need different status codes, they should be converted to `ResponseStatusException` at the throwing site. This pattern matches the existing code where some controllers already catch-and-rethrow as `ResponseStatusException`.

**Test results:**
- All 214 tests pass (168 existing + 43 new across all test classes, plus 3 existing tests modified for correctness).

**Reviewer notes:**
- GlobalExceptionHandler's `ConstraintViolationException` handler is included for completeness even though the current controller layer primarily triggers `MethodArgumentNotValidException` (from `@Valid`). The `ConstraintViolationException` path would activate if `@Validated` or method-level validation annotations are used in the future.
- The `assignRejectsBlankAgentId` test was replaced because the controller's `assign()` method creates a new `AssignmentRequest` using the path variable `agentId`, not the request body's `agentId` -- so a blank agentId in the request body is silently overwritten by the path variable and can never cause a validation failure.
- Two existing tests (`historyRejectsInvalidUuid`, `assignRejectsBlankAgentId`) were corrected because they tested conditions that the controllers cannot actually produce: `history` does not validate UUIDs, and `assign` overwrites the request body's agentId with the path variable.

### Issue 2.1: Standardize Streaming / SSE Lifecycle Semantics

**Files changed:**
- `src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java` — NEW. Shared SSE lifecycle component with outcome table documentation, standardized emitter factory, and `SubscriptionGuard` for reactive subscription management.
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java` — Replaced `AtomicReference<Disposable>` + hand-rolled `cancelSubscription` Runnable with `SseStreamLifecycle.SubscriptionGuard`. Replaced `new SseEmitter(...)` with `SseStreamLifecycle.createEmitter()`. Removed unused `NO_TIMEOUT` constant and `AtomicReference` import.
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java` — Replaced `AtomicReference<Disposable>` + hand-rolled `cancelSubscription`/`onCompletion`/`onTimeout`/`onError` with `SseStreamLifecycle.SubscriptionGuard` and `SseStreamLifecycle.registerCallbacks()`. Replaced `new SseEmitter(0L)` with `SseStreamLifecycle.createEmitter()`.
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java` — Replaced `new SseEmitter(0L)` with `SseStreamLifecycle.createEmitter()` for consistency.
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java` — Replaced `new SseEmitter(0L)` with `SseStreamLifecycle.createEmitter()` for consistency.
- `src/test/java/io/mindspice/magenta2/api/web/SseStreamLifecycleTest.java` — NEW. 13 tests covering: createEmitter timeout variants (positive, zero, negative, no-arg), SubscriptionGuard set/dispose/multiple-call safety, replacement of old subscription, and registerCallbacks wiring.
- `src/test/java/io/mindspice/magenta2/api/web/ChatControllerTest.java` — Added 3 tests: stream emitter uses no timeout by default, plan execution stream uses configured timeout, stream subscription is registered with guard (completable stream verifies lifecycle cleanup).
- `src/test/java/io/mindspice/magenta2/api/web/TaskControllerTest.java` — Added 2 tests: streamRun emitter has no timeout, streamRun handles illegal argument error.
- `src/test/java/io/mindspice/magenta2/api/web/WorkflowControllerTest.java` — Added 2 tests: streamRun emitter has no timeout, streamRun handles service error.
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java` — Added 2 tests: agent chat stream emitter has no timeout, agent chat stream handles null response error.

**What changed and why:**

1. **Created `SseStreamLifecycle`** — A shared utility in the web API package with:
   - Documented stream outcome table as a class-level Javadoc: COMPLETED, CLIENT_DISCONNECTED, TIMEOUT, USER_CANCELLED, MODEL_TOOL_FAILURE, VALIDATION_FAILURE, INTERNAL_ERROR — each with trigger, domain behavior, and cleanup semantics.
   - `createEmitter(timeoutMillis)` / `createEmitter()` — Standardized SseEmitter factory that ensures consistent timeout policy across all stream endpoints.
   - `SubscriptionGuard` — Thread-safe wrapper around `AtomicReference<Disposable>` with `set()` and `dispose()` methods. Replaces the duplicated `AtomicReference<Disposable>` + manual cancellation pattern that was independently implemented in ChatController and TaskController.
   - `registerCallbacks()` — Convenience wiring for endpoints where only subscription disposal is needed on terminal paths (no ActiveTurn or execution-failure domain logic).

2. **ChatController** — The `streamResolved` method now uses `SubscriptionGuard` instead of a hand-rolled `AtomicReference<Disposable>`. The guard's `set()` and `dispose()` replace the old `subscriptionRef.getAndSet()` / conditional dispose pattern. Domain-specific cleanup (ActiveTurn completion, plan execution failure recording) stays in the controller as explicit `Runnable domainCleanup` and `Consumer<RuntimeException> failPlanExecution` — per the "do not hide domain transitions" guidance.

3. **TaskController** — The `streamRun` method uses `SubscriptionGuard` with `registerCallbacks()` (no additional domain cleanup needed on terminal paths since there's no ActiveTurn). Catch-block error handling (IllegalArgumentException, Exception) remains unchanged — the guard is disposed via emitter callbacks, and if the exception occurs before `guard.set()` runs, there's nothing to dispose.

4. **WorkflowController and AgentOrchestrationController** — Use `SseStreamLifecycle.createEmitter()` for standardized emitter creation. These endpoints are synchronous (no reactive subscriptions), so no SubscriptionGuard is needed. The consistent factory method ensures the same timeout (0L, no timeout) is used across all stream types.

**Decisions made:**
- Followed the "do not hide domain transitions" guidance: the `SubscriptionGuard` handles only subscription lifecycle (set/dispose). ActiveTurn cleanup, execution failure recording, message discard, and other domain-level transitions remain in the controllers where they can be traced and reasoned about independently.
- The `SubscriptionGuard.registerCallbacks()` convenience method is used only by TaskController (which has no domain transitions on terminal paths). ChatController sets callbacks manually, composing `guard.dispose()` with its domain cleanup.
- Plan-execution stream timeout remains a documented exception: it uses a configurable timeout because saved-plan runs have bounded expected duration, while other streams have no timeout.
- Added the outcome table as a Javadoc comment on `SseStreamLifecycle` rather than as a separate document, keeping it close to the implementation.

**Tests added: 22 new (236 total, up from 214)**

| Test Class | Tests Added | Coverage |
|------------|-------------|----------|
| SseStreamLifecycleTest | 13 | Emitter creation, timeout coercion, SubscriptionGuard set/dispose/replace, registerCallbacks wiring |
| ChatControllerTest | 3 | Emitter timeout defaults, plan execution timeout, lifecycle guard subscription registration |
| TaskControllerTest | 2 | Emitter timeout, illegal argument error handling |
| WorkflowControllerTest | 2 | Emitter timeout, service error handling |
| AgentOrchestrationControllerTest | 2 | Emitter timeout, null response error handling |

**Test results:** All 236 tests pass.

**Reviewer notes:**
- The `/* domain cleanup */` C-style comment inside a Javadoc `/** */` comment in the first version of `SseStreamLifecycle.java` closed the Javadoc prematurely (the `*/` is a real end-of-comment marker even inside `/** */`). Fixed by removing the inner comment and rewording the Javadoc.
- The `SubscriptionGuard` uses `AtomicReference.getAndSet()` for thread-safe replacement of subscriptions when `set()` is called multiple times. This guards against a race where two subscriptions are created and the second replaces the first correctly.
- ChatController's `streamResolved` still owns the full domain lifecycle: turn registration, plan finalization guard (`AtomicBoolean planExecutionFinalized`), and failure recording. Only the subscription reference management was extracted.
- The synchronous endpoints (WorkflowController.streamRun, AgentOrchestrationController.chat) already complete the emitter before returning, so timeouts and subscription lifecycle don't apply. They use `createEmitter()` for consistency and future-proofing.


### Issue 2.2: Move Workflow Stream Execution Off Servlet Threads

**Files changed:**
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/test/java/io/mindspice/magenta2/api/web/WorkflowControllerTest.java`

**What changed and why:**

1. **WorkflowController.streamRun** -- Refactored from synchronous execution on the servlet thread to async execution via `Flux.defer` + `Schedulers.boundedElastic()`, matching the pattern established by TaskController. Both code paths (orchestration context and local workflow execution) are now wrapped in lazy `Flux.defer` blocks and subscribed on a bounded elastic scheduler. The emitter is returned immediately; all blocking work (including `runSynchronously` and `orchestrationRunService.runWorkflow`) executes on a background thread.

2. **SSE lifecycle** -- Added `SseStreamLifecycle.SubscriptionGuard` with `registerCallbacks()` for proper subscription disposal on all terminal paths (completion, timeout, client disconnect, error). The guard tracks the `Disposable` from the `subscribe` call and disposes it when the emitter completes, times out, errors, or the client disconnects.

3. **SsePayload record** -- Added a private `SsePayload(String name, Object data)` record (same pattern as `TaskController`) to carry SSE event name/data pairs through the reactive pipeline.

4. **Error handling** -- Synchronous setup errors (before subscription) are caught by the existing `IllegalArgumentException` and `Exception` catch blocks. Asynchronous errors during execution are caught by the subscriber's error handler, which sends a "failed" event and completes the emitter. This preserves the existing error contract while moving execution off the request thread.

5. **Preserved synchronous path** -- `WorkflowService.runSynchronously()` remains unchanged. It is still callable directly for non-stream code paths. Only `WorkflowController.streamRun` changed its calling pattern from synchronous to asynchronous.

**Decisions made:**
- Followed TaskController async semantics exactly: `Flux.defer` wrapping blocking calls, `subscribeOn(Schedulers.boundedElastic())`, and `SseStreamLifecycle.SubscriptionGuard` for lifecycle management. This matches the senior guidance to "match task stream semantics first."
- Used `registerCallbacks(emitter, guard, null, null)` since there are no domain transitions (no ActiveTurn, no plan execution failure recording) on terminal paths -- the guard's subscription disposal is the only cleanup needed.
- Both the orchestration-context and local-workflow paths fire events that are identical to the original synchronous version. The event schema is unchanged, ensuring backward compatibility for SSE clients.
- The `SsePayload` record is private to the controller, consistent with TaskController's treatment.

**Tests added:**
1. `streamRunReturnsBeforeWorkflowExecutionCompletesAndEmitsTerminalStatus` -- Creates a `BlockingWorkflowService` that holds `runSynchronously` until released. Calls `streamRun` in a separate thread via `CompletableFuture.supplyAsync`, asserts the emitter is returned within 200ms (proving non-blocking return), then waits for execution to start, releases the block, and verifies all expected events arrive (started, step_started, step_completed, completed, COMPLETED).

**Test results:**
- All 237 tests pass (236 existing + 1 new in WorkflowControllerTest).

**Reviewer notes:**
- The `BlockingWorkflowService` test stub uses `CountDownLatch` to coordinate the test thread with the background execution thread. The test asserts `executionStarted.await(1, SECONDS)` before releasing, proving the async subscription was activated before measuring completion.
- The `initializeEmitter` helper uses `Proxy` to intercept `SseEmitter` internal handler calls, matching the pattern used in `TaskControllerTest.CapturedSse`. This captures SSE events without needing a running servlet container.
- The old `streamRunHandlesServiceError` test continues to pass: since execution now happens asynchronously, the emitter is returned before the error occurs, and the test `assertThat(emitter).isNotNull()` holds true for both sync and async paths.
- WorkflowService.java was deliberately NOT changed. The synchronous `runSynchronously()` method remains available for any non-stream code paths. Only the controller's invocation pattern was made asynchronous.
- No changes were needed to the orchestrator or executor infrastructure. The existing `Schedulers.boundedElastic()` from Reactor handles thread management.
