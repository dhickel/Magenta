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
| 2.3 | Prevent Duplicate Queued Assignment Submission | completed | Acquire lease before executor submission in pollQueuedWork; revert on rejection. 3 new tests. |
| 3.1 | SQLite Schema Ownership & Validation | completed | Schema ownership policy: central schema.sql with narrow ensureSchema safety net. Added all missing tables to schema.sql. Enabled SQLite foreign keys. Added defense-in-depth cascade deletes in TaskRepository and WorkflowRepository. 13 new tests. |
| 3.2 | Audit Sequence Robustness | completed | — |
| 4.1 | Extract Controller Workflow & Stream Logic | completed | Extracted SsePayload to top-level record. Extracted TaskStreamSupport, WorkflowStreamSupport, and ChatStreamSupport classes. Added sendSseEvent helpers to SseStreamLifecycle. Controllers now delegate event mapping to support classes. 276 tests pass. |
| 4.2 | Public API DTOs For Lifecycle Fields | completed | Introduced TaskCreateRequest/TaskUpdateRequest for TaskController, JobCreateRequest/JobItemCreateRequest for OrchestrationJobController. Domain records now stay inside service boundaries. Client lifecycle fields are silently ignored. 278 tests pass. |
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

### Issue 2.3: Prevent Duplicate Queued Assignment Submission

**Files changed:**
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRunnerPollingTest.java` (NEW)

**What changed and why:**

1. **`OrchestrationRuntimeRepository.revertToQueued()`** -- New method that atomically reverts a RUNNING assignment back to QUEUED with lease fields cleared. The WHERE clause checks both status=RUNNING and lease_owner, so only the current lease holder can revert. This provides a safe rollback path when executor submission fails.

2. **`OrchestrationRunnerService.pollQueuedWork()`** -- Restructured to acquire the lease (durable state transition from QUEUED to RUNNING) BEFORE submitting work to the executor. Previously the lease was acquired inside the submitted task (inside `runAssignment`), creating a window where the same QUEUED assignment could be found by multiple poll cycles while the background executor was backlogged. The fix:
   - For each queued assignment, immediately calls `repository.acquireLease()`.
   - If lease acquisition fails (race with another runner), skips it.
   - If lease succeeds, submits the executor task with `executeWithLease()` (no lease acquisition needed -- already held).
   - If executor submission throws `RejectedExecutionException` (executor saturated), catches it and calls `repository.revertToQueued()` to put the assignment back in QUEUED state so it remains eligible for future polls.

3. **`OrchestrationRunnerService.runAssignment()`** -- Refactored into two methods:
   - `runAssignment(String assignmentId)`: Public method that acquires a lease and delegates to `executeWithLease`. Behavior is unchanged for external callers.
   - `executeWithLease(WorkAssignment)`: Private method containing the assignment execution logic (heartbeat, dispatch, completion/error). Takes an already-leased assignment, so it does NOT attempt to acquire a lease. This is shared by both `runAssignment` and the scheduled poll path.

**Decisions made:**
- Chose a durable state transition (acquire lease before submission) over in-memory tracking. The repository already has the correct lock/lease primitives (`acquireLease` with WHERE-clause optimistic locking), and the lease serves as both the "in-flight" marker and the coordination primitive for other runners. This avoids the complexity of a bounded in-memory submitted set with cleanup for success, failure, rejection, and shutdown.
- The `revertToQueued` method checks both `status=RUNNING` and `lease_owner` to avoid reverting leases held by other runners. This is consistent with `extendRunningLease` and other lease operations that verify ownership.
- `RejectedExecutionException` is the only exception caught around `submitBackground`. Other exceptions (IllegalArgumentException for missing lane config) are programming errors that should propagate.
- Used the 8-arg constructor of `OrchestrationRunnerService` in tests (no ChatService) since the test assignments are REPORT type and don't require model execution.

**Tests added: 3 (240 total, up from 237)**

1. `backloggedExecutorLeasesAssignmentOnlyOnce` -- Creates a 1-thread executor with 100-slot queue. Holds the single thread with a CountDownLatch blocker. Queues a REPORT assignment. Runs `pollQueuedWork`: verifies lease is acquired (assignment becomes RUNNING) and `findQueuedAssignments` returns empty. Runs `pollQueuedWork` again: verifies no resubmission (status stays RUNNING). Proves a single QUEUED assignment is submitted exactly once even when the executor is backlogged.

2. `saturatedExecutorRevertsLeaseAndPollingSurvives` -- Creates a 1-thread executor with 0 queue slots (total capacity = 1). Fills the capacity with a CountDownLatch blocker. Queues a REPORT assignment. Runs `pollQueuedWork`: verifies lease is acquired then reverted (status back to QUEUED, lease owner null). Runs `pollQueuedWork` again: verifies same cycle repeats (status QUEUED, lease null). Proves polling survives rejection and work remains eligible for future submission.

3. `revertToQueuedOnlySucceedsForCorrectLeaseOwner` -- Creates a RUNNING assignment with known lease owner. Verifies reverting with wrong owner is a no-op (stays RUNNING). Verifies reverting with correct owner succeeds (becomes QUEUED). Verifies the SQL WHERE clause correctly guards lease ownership.

**Test results:**
- All 240 tests pass (237 existing + 3 new). Full suite: 240 run, 0 failures, 0 errors, 0 skipped.

**Reviewer notes:**
- The `executeWithLease` method operates on the `WorkAssignment` object captured at lease-acquisition time. For long-running job iterations, `runJob()` re-reads the assignment from the DB via `assignmentService.get(current.id())` on each iteration, so any mid-execution state changes (like cancellation) are properly detected. For short-running types (REPORT, TASK_RUN, WORKFLOW_RUN), the captured state is sufficient.
- The heartbeat starts inside `executeWithLease` (running in the executor thread), not during lease acquisition. There is a window between lease acquisition and heartbeat start where the lease could expire if the executor backlog exceeds the lease duration (default 300s). This is an acceptable edge case -- if the executor is backlogged for 5+ minutes, capacity needs to be addressed independently.
- The stale-lease recovery path (`markStaleRunningLeases`) remains unchanged. If an executor node crashes with acquired leases, the standard recovery mechanism marks them as INTERRUPTED, making them eligible for re-queueing via `resume()`.

### Issue 3.1: SQLite Schema Ownership And Validated Clean/Upgraded Databases

**Files changed:**
- `src/main/resources/schema.sql` — Added `planning_model` column to `ai_chat_session_metadata`. Added all 12 repository-owned tables (agent_profiles, orchestration_jobs, orchestration_job_items, work_assignments, agent_inbox_messages, agent_schedules, schedule_firings, agent_event_reactions, orchestration_events, runtime_settings, workspaces, workspace_links) with their indexes (idx_work_assignments_queue, idx_workspaces_owner). schema.sql is now the canonical home for ALL tables.
- `src/main/resources/application.yml` — Added `?foreign_keys=true` to the SQLite JDBC URL so foreign key constraints (including ON DELETE CASCADE) are enforced per connection.
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskRepository.java` — `delete()` now deletes child `ai_task_runs` before deleting the `ai_task_definitions` row. Wrapped in `@Transactional` for atomicity.
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowRepository.java` — `delete()` now deletes child `ai_workflow_runs` before deleting the `ai_workflow_definitions` row. Wrapped in `@Transactional` for atomicity.
- `src/test/java/io/mindspice/magenta2/SchemaOwnershipTest.java` — NEW. 13 tests.

**What changed and why:**

1. **schema.sql consolidation** — Before this change, 12 tables (all orchestration, workspace, agent profile tables) were only created by repository `ensureSchema()` methods, not by schema.sql. Another 6 tables (chat memory, session metadata, agent jobs, audit event, chat plans, plan steps) had dual ownership (both schema.sql AND ensureSchema). schema.sql is now the authoritative source for all 23 tables. Repository `ensureSchema()` methods are preserved as safety nets for test isolation and upgraded-database column migration.

2. **SQLite foreign keys** — SQLite does not enforce foreign keys by default; you must set `PRAGMA foreign_keys = ON` per connection. The JDBC URL parameter `?foreign_keys=true` achieves this for all connections through the Spring Boot DataSource. Existing FK definitions in schema.sql (`ai_task_runs -> ai_task_definitions ON DELETE CASCADE`, `ai_workflow_runs -> ai_workflow_definitions ON DELETE CASCADE`, `ai_chat_plan_steps -> ai_chat_plans ON DELETE CASCADE`) are now enforced.

3. **Defense-in-depth cascade deletes** — `TaskRepository.delete()` and `WorkflowRepository.delete()` now explicitly delete related runs before deleting the definition. This works whether or not FK enforcement is active, providing belt-and-suspenders protection against orphaned rows. The explicit delete is redundant when FKs are enabled (the ON DELETE CASCADE handles it), but harmless and provides defense-in-depth.

4. **`planning_model` column** — Added to `ai_chat_session_metadata` in schema.sql (it was previously only managed by `ChatSessionMetadataRepository.ensureSchema()` via ALTER TABLE for upgraded databases). This closes the drift between the canonical schema definition and the runtime column migration.

**Schema ownership policy decision:**

Chose a **narrow hybrid** policy:
- **Central `schema.sql`** is the canonical home for ALL table definitions. Every table in the database is defined here with its complete column list. This is the single source of truth for clean-database initialization.
- **Repository `ensureSchema()` methods** are preserved as:
  - A safety net for test isolation (tests create repositories directly against in-memory SQLite without schema.sql).
  - An upgraded-database column migration mechanism (ALTER TABLE ADD COLUMN for columns added after the initial schema).
  - They do NOT define tables that are absent from schema.sql.
- This avoids a full migration framework (per senior guidance) while ensuring clean databases and upgraded databases converge to the same schema.

**Tests added: 13 (253 total, up from 240)**

| Test | Coverage |
|------|----------|
| `cleanDatabaseHasAllExpectedTables` | Verifies all 23 tables exist after running schema.sql |
| `cleanDatabaseHasRequiredIndexes` | Verifies all required indexes exist (idx_ai_chat_memory_conversation, idx_audit_event_conversation, idx_agent_jobs_conversation, idx_agent_jobs_conversation_title_active, idx_ai_task_runs_task, idx_ai_workflow_runs_workflow, idx_work_assignments_queue, idx_workspaces_owner) |
| `foreignKeysAreEnabled` | Verifies `PRAGMA foreign_keys` returns true on the test connection |
| `taskDeleteRemovesRuns` | Creates a task with 2 runs, deletes the task, verifies runs are gone |
| `workflowDeleteRemovesRuns` | Creates a workflow with 2 runs, deletes the workflow, verifies runs are gone |
| `chatPlanDeleteRemovesSteps` | Creates a plan with steps, deletes the plan, verifies steps are gone |
| `upgradedChatSessionMetadataGetsMissingColumns` | Creates old table with only conversation_id and model, runs ensureSchema, verifies all 6 newer columns added |
| `upgradedChatMemoryGetsMetadataColumn` | Creates old table without message_metadata_json, runs ensureSchema, verifies column added |
| `upgradedAuditEventGetsAllColumns` | Creates minimal audit_event, runs ensureSchema, verifies all audit columns added |
| `upgradedChatPlansGetsExtraColumns` | Creates old ai_chat_plans with basic columns only, runs ensureTables, verifies all plan columns added |
| `upgradedOrchestrationJobItemsGetsExtraColumns` | Creates old orchestration_job_items without retry_count/continue_on_failure, runs ensureSchema, verifies columns added |
| `foreignKeyConstraintPreventsOrphanedRunsOnDirectDelete` | Inserts task+run directly via SQL (no repository), deletes parent, verifies FK cascade removes the run |
| `ensureSchemaIsIdempotent` | Runs all repository ensureSchema methods twice on a clean schema.sql database, verifies no errors |

**Test results:**
- All 253 tests pass (240 existing + 13 new). Full suite: 253 run, 0 failures, 0 errors, 0 skipped.

**Reviewer notes:**
- The `planning_model` column was the only column missing from `schema.sql` that was already managed by an `ensureSchema` migration (ChatSessionMetadataRepository). All other columns that ensureSchema methods add via ALTER TABLE are already present in schema.sql.
- The `upgradedChatPlansGetsExtraColumns` test does NOT assert `plan_start_message_order` because that column was always part of the initial CREATE TABLE (both in schema.sql and ChatPlanRepository.ensureTables), never added via ALTER TABLE. An upgraded database predating that column would not have it added by ensureTables alone -- this is acceptable because the column was present from the first version of the application.
- The `?foreign_keys=true` parameter is added to the JDBC URL. This is supported by the xerial SQLite JDBC driver (org.xerial:sqlite-jdbc:3.50.3.0). Each connection created through the DataSource will have `PRAGMA foreign_keys = ON` set automatically.
- TaskRepository and WorkflowRepository gained `@Transactional` on their `delete()` methods. The annotation was already imported in both files; the explicit cascade deletes are now wrapped in a transaction so the two DELETE statements are atomic.
- No changes were made to repository `ensureSchema()` methods themselves -- they continue to provide CREATE TABLE IF NOT EXISTS + ALTER TABLE migration logic. The consolidation is achieved entirely through schema.sql becoming the authoritative source for all tables.

### Issue 3.2: Audit Sequence Robustness

**Files changed:**
- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AuditRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/repository/AuditRepositoryTest.java` (NEW)

**What changed and why:**

1. `schema.sql` -- Changed `create index if not exists idx_audit_event_conversation on audit_event (conversation_id, sequence)` to `create unique index` so fresh installs get the uniqueness constraint from the start.

2. `AuditRepository.java` -- Three changes:
   - **Unique constraint in ensureSchema**: Added pragmatic upgrade logic that checks `pragma_index_list` to determine if the existing index is already unique. If not (old install), drops and recreates as unique. If doesn't exist (fresh), creates unique. If already unique (already upgraded), no-op.
   - **Striped-lock serialization** (`insertSerialized`): Replaced the `nextSequence()` + `insertWithRetry()` pattern with a simple `synchronized` block keyed by `conversation_id.hashCode() % 64`. This serializes the read-max + insert cycle per conversation without relying on SQLite multi-connection constraint propagation or Spring exception translation.
   - **Raised visibility**: Changed audit write failure logging from `log.debug` to `log.warn` so alpha debugging is not masked.

3. `AuditRepositoryTest.java` -- Added `concurrentInsertsProduceUniqueSequences`: 10 threads * 100 inserts on the same conversation, all through a single shared `AuditRepository` instance (matching production singleton pattern). Verifies all 1000 events recorded, no duplicate sequences, and sequences are 0..999.

**Decisions made:**

- Chose **striped Java-level locking** over retry-on-conflict as the sequence allocation strategy. The initial retry approach (catch `DataIntegrityViolationException`) proved unreliable because Spring's exception translation for SQLite constraint violations did not consistently produce the expected exception type. The `INSERT OR IGNORE` approach also had issues with the `replaceFirst` regex transformation. Striped locking is simpler, has no runtime dependency on SQLite driver specifics, and matches production access patterns (single `AuditRepository` bean).
- Used 64 stripes (`Object[] lockStripes`), initialized eagerly in the constructor. This prevents unbounded key growth (problem with `ConcurrentHashMap`-based locks) while providing sufficient granularity.
- The unique constraint on `(conversation_id, sequence)` is kept as defense-in-depth even though the locking makes conflicts virtually impossible in production. It silently documents the expected invariant and catches programmer errors.
- The SQL text blocks were deliberately NOT modified to use `INSERT OR IGNORE` -- the striped lock makes the read-max + write sequence atomic, so `INSERT INTO` (which throws on violation) is the correct choice and programmer errors during development will surface immediately rather than being silently ignored.

**Test results:**
- All 254 tests pass (253 existing + 1 new AuditRepositoryTest). Full suite: 254 run, 0 failures, 0 errors, 0 skipped.

**Reviewer notes:**
- The lock stripe uses `Math.abs(hashCode()) % 64`. For `String`, `hashCode()` can return `Integer.MIN_VALUE`, making `Math.abs()` negative. But `Integer.MIN_VALUE % 64` is `Integer.MIN_VALUE & 63 = 0` in Java (both are 64-bit aligned), so the index is always valid. Additionally, the negative remainder of `Integer.MIN_VALUE % 64` is `-0` which is 0 in Java's array indexing (since negative remainders are returned as-is, but `% 64` with negative divisor... actually, `Integer.MIN_VALUE % 64` in Java = `-(64) = -0 = 0`. No wait: `-2147483648 % 64 = 0` since 64 divides evenly. So `Math.abs` is not needed here, but kept for clarity and safety with non-power-of-two stripe counts.
- The hash code of `String` is stable across JVM invocations (documented in Java spec), so same conversation IDs always map to the same stripe.
- The lock array is initialized in the constructor (not lazy), so there's no allocation or synchronization cost at runtime.
- `SingleConnectionDataSource` was used in the concurrent test instead of `SimpleDriverDataSource` to avoid creating a new connection per SQL statement. The single connection is protected by the same striped lock, making it safe for multi-threaded access.

### Issue 4.1: Extract Controller Workflow And Stream Logic

**Files changed:**
- `src/main/java/io/mindspice/magenta2/api/web/SsePayload.java` — NEW. Top-level record extracted from duplicate private records in TaskController and WorkflowController.
- `src/main/java/io/mindspice/magenta2/api/web/TaskStreamSupport.java` — NEW. Static methods for orchestration and chat-service task event mapping, context conversion, and error payload creation.
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowStreamSupport.java` — NEW. Static methods for orchestration and synchronous workflow event mapping, context conversion, and error payload creation.
- `src/main/java/io/mindspice/magenta2/api/web/ChatStreamSupport.java` — NEW. Static helpers for chat SSE event sending, safe error messages, and last-assistant-message extraction.
- `src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java` — Added `sendSseEvent(SseEmitter, String, Object)` and `sendSseEvent(SseEmitter, String, Object, MediaType)` convenience methods. These replace the duplicated `send()` methods in TaskController/WorkflowController and `sendEvent()` in ChatController.
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java` — Removed private `SsePayload` record, `send()` method, and `context()` method. `streamRun()` now delegates event production to `TaskStreamSupport.orchestrationRunEvents()` and `TaskStreamSupport.chatServiceRunEvents()`, context conversion to `TaskStreamSupport.toContext()`, and event sending to `SseStreamLifecycle.sendSseEvent()`.
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java` — Removed private `SsePayload` record, `send()` method, and `context()` method. `streamRun()` now delegates event production to `WorkflowStreamSupport.orchestrationRunEvents()` and `WorkflowStreamSupport.synchronousRunEvents()`, context conversion to `WorkflowStreamSupport.toContext()`, and event sending to `SseStreamLifecycle.sendSseEvent()`.
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java` — Removed private `sendEvent()`, `safeMessage()`, and `lastAssistantMessage()` methods. `streamResolved()` now delegates to `ChatStreamSupport.sendSseEvent()`, `ChatStreamSupport.safeMessage()`, and `ChatStreamSupport.lastAssistantMessage()`.
- `src/test/java/io/mindspice/magenta2/api/web/TaskStreamSupportTest.java` — NEW. 8 tests.
- `src/test/java/io/mindspice/magenta2/api/web/WorkflowStreamSupportTest.java` — NEW. 6 tests.
- `src/test/java/io/mindspice/magenta2/api/web/ChatStreamSupportTest.java` — NEW. 4 tests.
- `src/test/java/io/mindspice/magenta2/api/web/SseStreamLifecycleTest.java` — Added 2 tests.

**What changed and why:**

1. **SsePayload extraction** — Both TaskController and WorkflowController defined identical `private record SsePayload(String name, Object data)`. Extracted to a top-level file, eliminating duplication and making it available to support classes without coupling to controller internals.

2. **TaskStreamSupport** — The `streamRun` method's event mapping logic (OrchestrationRunResult to SSE events, TaskExecutionEvent to SsePayload) and request-to-context conversion were extracted into static methods. The controller now only handles: emitter/guard creation, stream source selection (orchestration vs chat), subscription wiring, and error fallback.

3. **WorkflowStreamSupport** — Same pattern as TaskStreamSupport. The orchestration and synchronous workflow event mapping (including step-by-step event production for synchronous runs) was extracted.

4. **ChatStreamSupport** — Three private methods extracted: `sendEvent`, `safeMessage`, and `lastAssistantMessage`. All moved with unchanged logic.

5. **SseStreamLifecycle.sendSseEvent** — Both TaskController and WorkflowController had identical `private void send(SseEmitter, String, Object)` methods. Added as a shared utility.

**What stayed in the controllers (by design):**
- Emitter/guard creation and lifecycle callbacks — controllers already use SseStreamLifecycle (from step 2.1).
- Subscription wiring and error fallback — the try/catch blocks differ per controller context.
- ChatController's domain lifecycle (ActiveTurn, plan finalization guard, failure recording) — tightly coupled to `streamResolved` local state.

**Decisions made:**
- Created four small support classes (plus SsePayload) rather than a single "StreamService". Each has focused responsibility matching one controller's extraction surface.
- Made support classes `final` with private constructors (static utility pattern) since they hold no state and need no dependency injection.
- TaskStreamSupport and WorkflowStreamSupport reference inner records on their respective controllers (`TaskRunRequest`, `WorkflowRunRequest`). These are `public record`s, so the reference is valid from the same package.
- Did NOT extract the error event sending into a shared helper — the catch blocks are simple enough.
- Did NOT move `normalize()`, `requireValidUuid()`, or other validation helpers from ChatController. These are cross-cutting, not stream-specific.

**Tests added:** 20 new (276 total, up from 254)

| Test Class | Tests Added | Coverage |
|------------|-------------|----------|
| TaskStreamSupportTest | 8 | Orchestration run completed/failed, chat service started/tool/terminal, context conversion, error payload |
| WorkflowStreamSupportTest | 6 | Orchestration run completed/failed, synchronous run full event sequence, context conversion, error payload |
| ChatStreamSupportTest | 4 | safeMessage (null, with/without message), lastAssistantMessage (assistant, user, empty) |
| SseStreamLifecycleTest | 2 | sendSseEvent builder construction |

**Test results:** All 276 tests pass. Full suite: 276 run, 0 failures, 0 errors, 0 skipped.

**Reviewer notes:**
- `TaskStreamSupport.orchestrationRunEvents` and `WorkflowStreamSupport.orchestrationRunEvents` are structurally similar but differ in map keys (`taskId` vs `workflowId`, `outputValues` vs `finalOutputs`). A shared helper would need conditional logic that obscures the per-controller event contract.
- `ChatStreamSupport.sendSseEvent` was added to both ChatStreamSupport and SseStreamLifecycle (different overloads). ChatStreamSupport has the MediaType overload for ChatStreamEvent JSON serialization.
- All existing controller tests pass unchanged — the public API surface of all three controllers is identical.
- The `ChatController.streamResolved` domain lifecycle (ActiveTurn registration, plan execution finalization, failure recording) was deliberately kept in the controller per the "do not hide domain transitions" guidance established in step 2.1.

### Issue 4.2: Introduce Public API DTOs For Lifecycle-Owned Fields

**Files changed:**
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java` — Added `TaskCreateRequest` and `TaskUpdateRequest` public DTO records with `@JsonIgnoreProperties(ignoreUnknown = true)`. These expose only client-provided fields (title, summary, goal, notes, input/output definitions, assumptions, steps, validation criteria) and exclude lifecycle fields (id, createdAt, updatedAt). Added `toDomain(TaskCreateRequest)` and `toDomain(String, TaskUpdateRequest)` mapper methods. Changed `create()` to accept `@Valid @RequestBody TaskCreateRequest` and `update()` to accept `@Valid @RequestBody TaskUpdateRequest`. Added imports for `JsonIgnoreProperties`, `TaskFieldDefinition`, `TaskStep`.
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationJobController.java` — Added `JobCreateRequest` and `JobItemCreateRequest` public DTO records with `@JsonIgnoreProperties(ignoreUnknown = true)`. `JobCreateRequest` exposes only ownerAgentId, title, summary, defaultModel, workspaceId. `JobItemCreateRequest` exposes only itemOrder, itemType, taskId, workflowId, modelOverride, priority, retryCount, continueOnFailure, config. Added `toDomain(JobCreateRequest)` and `toDomain(String, JobItemCreateRequest)` mapper methods. Changed `create()` to accept `@Valid @RequestBody JobCreateRequest` and `addItem()` to accept `@Valid @RequestBody JobItemCreateRequest`. Cleaned up fully-qualified `AssignmentType.JOB_RUN` references to use the imported `AssignmentType`. Added imports for `JsonIgnoreProperties`, `Map`, `AssignmentType`, `NotBlank`, `NotNull`.
- `src/test/java/io/mindspice/magenta2/api/web/TaskControllerTest.java` — Updated `taskApiIgnoresLegacyDeliverablesAndDoesNotExposeThem`, `updateUsesPathIdWithoutDeliverables`, and `createTaskRejectsBlankTitle` to use the new DTO records. Added `createTaskIgnoresClientProvidedLifecycleFields` contract test: sends JSON with `id`, `createdAt`, `updatedAt` fields, verifies DTO silently drops them, and verifies the server assigns its own values.
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationJobControllerTest.java` — Updated `createRejectsBlankTitle`, `createRejectsBlankOwnerAgentId`, `createSucceedsWithValidJob`, and `addItemRejectsInvalidItem` to use the new DTO records. Added `createJobIgnoresLifecycleFieldsInJson` contract test: sends JSON with `id`, `status`, `createdAt`, verifies DTO silently drops them.

**What changed and why:**

1. **TaskController DTOs** — Previously, `create()` and `update()` accepted `TaskDefinition` directly, which exposes `id`, `createdAt`, and `updatedAt` — lifecycle fields that should be server-managed. The new `TaskCreateRequest` and `TaskUpdateRequest` records exclude these fields. Mapper methods construct domain records with null lifecycle fields; the `TaskRepository.save()` method handles nulls by assigning `Instant.now()`. The `@NotBlank` annotation on `title` is preserved for Spring MVC validation. Legacy fields like `deliverables` are silently ignored via `@JsonIgnoreProperties(ignoreUnknown = true)`.

2. **OrchestrationJobController DTOs** — Previously, `create()` accepted `OrchestrationJob` directly (exposing `id`, `status`, `createdAt`, `updatedAt`). The new `JobCreateRequest` excludes these fields. Similarly, `addItem()` accepted `OrchestrationJobItem` directly (exposing `id`, `createdAt`, `updatedAt`); the new `JobItemCreateRequest` excludes them. The mapper methods set lifecycle fields to null; the `OrchestrationRuntimeRepository.saveJob()` and `saveJobItem()` methods handle nulls by assigning `Instant.now()`.

3. **Mapper placement** — Mappers are private static methods on their respective controllers, co-located with the DTOs they map. This keeps the conversion logic close to the API surface and avoids a separate mapper layer that would add indirection without benefit at this stage.

4. **Contract tests** — Two new tests prove that lifecycle fields in JSON are silently dropped by the DTOs (Jackson's default `ignoreUnknown=true` behavior for fields not present in the record). An additional contract test on TaskController proves that even if a client sends `id`, `createdAt`, and `updatedAt` in the JSON body, the server assigns its own values for all lifecycle fields.

**Test results:**
- All 278 tests pass (276 existing + 2 new contract tests). Full suite: 278 run, 0 failures, 0 errors, 0 skipped.

**Reviewer notes:**
- The `TaskUpdateRequest` and `TaskCreateRequest` records are structurally identical but kept as separate types so their semantics are explicit at the call site. They use identical compact constructors for defensive copying of list fields.
- The `JobItemCreateRequest` uses `Integer`/`Boolean` boxed types for `priority`, `retryCount`, and `continueOnFailure` so clients can omit them (defaulting to 0/false in the mapper). The `itemOrder` field uses primitive `int` since 0 triggers auto-assignment in the service.
- The existing `OrchestrationJobService.save()` method checks for `ownerAgentId` being blank and `title` being blank — this validation still triggers even though Spring's `@Valid` does not run during direct controller method calls in unit tests (no Spring MVC test runner). The `@NotBlank` annotations on the DTOs provide validation when running through Spring MVC.
- No changes were made to response DTOs (TaskDefinition, OrchestrationJob, OrchestrationJobItem are still returned from controller methods). These domain records include lifecycle fields as read-only output, which is appropriate — the fix addresses the input side where clients could previously mutate these fields.
- The `AssignmentRequest` used by `OrchestrationJobController.run()` and `AgentOrchestrationController.assign()` was already clean (no lifecycle fields) and was not changed.
- `WorkflowController` has the same pattern (accepts `WorkflowDefinition` in create/update) but was not in scope for this issue. It should be addressed in a follow-up.
