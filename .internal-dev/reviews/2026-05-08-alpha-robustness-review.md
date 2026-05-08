# Scope

Alpha milestone robustness review of the backend core and high-risk web-facing paths. Reviewed chat REST/SSE, active turns, model/tool execution, file/shell/web tools, plan execution, task/workflow execution, durable orchestration assignments/jobs/schedules/events, SQLite persistence, and startup configuration.

Primary code references include:

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutor.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/web/AgentWebToolService.java`
- `src/main/resources/schema.sql`
- `src/main/resources/application.yml`

# Findings

## High: Shell Tool Cancellation Can Leak Processes

`AgentShellToolService.exec` starts a process at `AgentShellToolService.java:76`, waits at `:82`, and destroys it only when its timeout expires at `:84`. If the calling thread is interrupted before timeout, `InterruptedException` leaves the method without destroying the process or bounding the stdout/stderr capture futures. This matters because `MagentaWorkExecutor` interrupts running work during cancellation at `MagentaWorkExecutor.java:170`, and `ChatService.isRetryable` treats `InterruptedException` as retryable at `ChatService.java:1918`.

Impact: cancelled shell tool turns can leave child processes alive and may retry cancelled work instead of stopping. This is an alpha blocker if shell tools are enabled for real users.

## High: `web_fetch` Public-Host Guard Is Redirect-Bypassable

`AgentWebToolService` constructs a `HttpClient` with `HttpClient.Redirect.NORMAL` at `AgentWebToolService.java:44`. `fetch` validates only the original URI at `:101` and returns the final `response.uri()` at `:125` without validating the post-redirect host. A public URL can redirect to localhost or a private IP, bypassing `privateOrLocalHost` at `:173`.

Impact: this is an SSRF-class boundary failure for the model-visible web fetch tool.

## High: SSE Disconnects Can Leave Chat and Plan State Inconsistent

`ChatController.streamResolved` registers an active turn and cancels the subscription on completion/error/timeout at `ChatController.java:90-115`. Only the reactive error path discards the last user message for non-plan streaming at `:215`; client completion/disconnect can leave a dangling user turn after persistence. For plan execution, `onError` records execution failure through `failPlanExecution` at `:102-115`, meaning a client transport problem can mark the saved plan as failed or needs-review even when model execution may not have logically failed.

Impact: chat history, plan mode, and user-visible status can diverge under ordinary browser disconnects, refreshes, or proxy timeouts.

## Medium: Public Chat Request Validation Is Too Thin

`ChatController.chat` and `stream` pass request bodies directly to `ChatService` at `ChatController.java:69` and `:74`. `ChatService.resolve` accepts any nonblank `conversationId` and does not reject blank or null messages at `ChatService.java:340-355`. Later paths build `UserMessage` or prompt user content with that value at `ChatService.java:1220` and `:1677`. `ChatController.command` dereferences a possibly null request at `ChatController.java:340`.

Impact: invalid requests can create bad conversation ids, produce 500s, or fail deep inside model/persistence code instead of returning stable 400 responses.

## Medium: SQLite Foreign Keys Are Declared But Not Clearly Enabled

`application.yml` uses `jdbc:sqlite:./chat-memory.db` without an obvious per-connection `PRAGMA foreign_keys=ON`. `schema.sql` declares cascades for task runs and workflow runs at `schema.sql:173` and `schema.sql:201`. `TaskRepository.delete` and `WorkflowRepository.delete` delete only parent rows at `TaskRepository.java:109` and `WorkflowRepository.java:68`.

Impact: SQLite may not enforce cascade deletes, leaving orphaned runs after task/workflow deletion. Even if a particular runtime enables it indirectly, the code does not document or test that assumption.

## Medium: Orchestration Polling Can Submit Duplicate Workers For The Same Queued Assignment

`OrchestrationRunnerService.pollQueuedWork` finds queued assignments and submits background executor work at `OrchestrationRunnerService.java:108-116`. Assignments remain `QUEUED` until the background task later acquires a lease at `:131-136`. Under backlog or executor saturation, subsequent polls can resubmit the same queued row. Rejections from `executor.submitBackground` are not caught in the scheduler method.

Impact: duplicate submitted tasks waste executor capacity, and scheduler exceptions may create noisy or brittle runtime behavior.

## Medium: Workflow Streaming Is Actually Blocking Servlet Work

`WorkflowController.streamRun` creates an SSE emitter at `WorkflowController.java:95`, sends a few step events, then calls `workflowService.runSynchronously` before returning at `:128`. Unlike task streaming, this is not scheduled on bounded elastic and has no subscription cancellation path.

Impact: long workflow runs tie up request threads and cannot respond correctly to client disconnect or cancellation.

## Medium: Startup Config Validation Is Delayed For Some Model Errors

`ExternalAiConfigLoader.validate` verifies selected model keys and context lengths at `ExternalAiConfigLoader.java:58-105`, but does not validate every configured model's endpoint type, endpoint URL, remote model name, API key requirement, or every agent model reference. `ChatModelRouter` builds and validates model clients lazily when a model is first used.

Impact: bad model config may pass startup and fail at first real request, making alpha deployments harder to diagnose.

## Low/Medium: Audit Sequence Writes Are Not Atomic

`AuditRepository.nextSequence` reads `max(sequence)` and then inserts in separate statements at `AuditRepository.java:86-104`. Multiple concurrent messages for the same conversation can race and produce duplicate sequence values because `(conversation_id, sequence)` is only indexed, not unique.

Impact: audit chronology can be ambiguous during concurrent or retried activity. This is not user-facing by itself, but it weakens operational debugging.

# Risk Assessment

The backend has solid pieces: bounded executor lanes, explicit task/run status records, durable assignment leases, tool-loop guards, context compaction, and persisted plan/task/workflow snapshots. The risk is concentrated around operational edges: cancellation, client disconnects, shell/web tool boundaries, and persistence assumptions.

For alpha, the strongest blockers are shell process cleanup, web fetch redirect validation, and clear SSE failure semantics. These can produce security or state-corruption issues even with low traffic.

# Recommendations

1. Fix shell cancellation by wrapping process execution in `try/finally`, destroying on interruption, bounding reader futures, and treating cancellation separately from retryable transient IO.
2. Disable automatic redirects for `web_fetch` or manually validate every redirect target before following it.
3. Define separate outcomes for model error, client disconnect, user cancel, timeout, and validation failure in streaming chat/plan execution.
4. Add request-body validation for chat, commands, task/workflow create/update, orchestration assignment creation, and run requests.
5. Enable and test SQLite foreign-key enforcement or manually cascade deletes in repositories.
6. Acquire or mark orchestration leases before executor submission, or maintain an in-memory submitted set with cleanup.
7. Move workflow execution off the servlet thread and align it with task streaming semantics.
8. Fail fast on invalid AI config for all model and agent definitions.

# Follow-ups

- Add tests for interrupted shell execution and ensure child processes are cleaned up.
- Add tests for `web_fetch` redirect to localhost/private IP.
- Add SSE disconnect tests for normal chat, plan execution, and task execution.
- Add tests for invalid/null request bodies returning stable 400s.
- Add clean-database and upgraded-database tests around task/workflow delete behavior.
- Add orchestration scheduler tests under saturated executor/backlog conditions.
