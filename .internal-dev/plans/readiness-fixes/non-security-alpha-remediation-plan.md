# Context

The May 8 alpha review artifacts found that Magenta is close to an alpha-MVP backend shape, but several non-security readiness issues should be addressed before or alongside alpha exposure. This plan consolidates those findings into coherent remediation tracks so implementation can proceed by behavior area rather than by duplicate review bullet.

Input artifacts:

- `.internal-dev/reviews/2026-05-08-alpha-robustness-review.md`
- `.internal-dev/reviews/2026-05-08-alpha-cohesion-contracts-review.md`
- `.internal-dev/reviews/2026-05-08-alpha-simplification-refactor-targets-review.md`
- `.internal-dev/reviews/2026-05-08-alpha-consolidated-milestone-review.md`

The readiness/security review is intentionally not an input for this plan. Security-class findings are out of scope here, including authentication, authorization, secret/default cleanup, SSRF hardening, frontend injection fixes, selected-agent shell policy hardening, and shell command policy redesign. If a group below touches a shared area, the implementer should keep the fix limited to non-security readiness behavior.

# Goal

Bring the non-security alpha readiness surface to a predictable, testable baseline by fixing operational correctness issues, standardizing stream and error semantics, tightening persistence assumptions, and cleaning the most important ownership boundaries without turning this into a broad rewrite.

# In Scope

- Runtime settings reaching context compaction.
- Streaming/SSE lifecycle consistency across chat, task, workflow, and orchestration side-panel streams.
- Shell cancellation process cleanup limited to interruption robustness and resource cleanup.
- Public request validation and stable error mapping.
- SQLite schema ownership, foreign-key behavior, and clean/upgraded database validation.
- Duplicate orchestration worker submission for queued assignments.
- Workflow stream execution off servlet request threads.
- `ChatService` breadth reduction planning and incremental seam extraction where it directly reduces alpha risk.
- Controller workflow logic extraction into focused service/SSE support code.
- `PlanMode` package ownership cleanup.
- Public API DTO cleanup for task/job/assignment lifecycle fields.
- `AgentJobRepository` package ownership cleanup.
- Long-record mutation pattern cleanup in plan/task services.
- Dead command compatibility code and unused stale utility removal.
- Nullable-union stream event DTO cleanup.
- Audit sequence robustness.
- Workflow, schedule, and reaction alpha-facing decision, with documentation or hiding if not ready.

# Out of Scope

- Authentication, authorization, login/session, or remote deployment access control.
- API key, secret, example config, or production default policy changes.
- SSRF/web-fetch redirect hardening.
- Frontend injection remediation in task/workflow pages.
- Shell allowlist revocation, command policy redesign, or structured shell command input as a security hardening project.
- Selected-agent chat prompt/tool/shell policy changes.
- Broad rearchitecture of chat, orchestration, task, workflow, or persistence layers.
- New product features, schedulers, queues, orchestration capabilities, workflow graph semantics, or migration frameworks unless explicitly needed by the remediations below.

# Implementation Steps

## 1. Low-Risk Correctness Fixes

### 1.1 Runtime Settings Must Reach Context Compaction

Issue statement: `ContextManagementAdvisor` supports `RuntimeSettingsService`, but `ChatBeanConfig` constructs it without that service. Runtime changes to compaction model or context buffer may not affect actual context management.

Why it needs addressing: Alpha users can change runtime settings and reasonably expect them to affect model context behavior. Ignoring those settings creates confusing operations and makes context issues hard to diagnose.

How to address it:

- Inject `RuntimeSettingsService` into the advisor bean wiring.
- Preserve existing defaults when no runtime setting is present.
- Add a focused test that changes compaction model/context buffer settings and proves the advisor uses them during context management.

Code targets:

- `src/main/java/io/mindspice/magenta2/config/ChatBeanConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/context/ContextManagementAdvisor.java`
- Existing runtime settings service/repository tests.

Consolidated overlap notes: This appears as a blocker in the consolidated review and as a P1 simplification/refactor target. Treat it as a concrete behavior fix, not a general settings redesign.

Senior guidance: Keep the constructor and bean change small. Do not add a new settings abstraction unless the existing `RuntimeSettingsService` cannot express the needed values.

Validation expectations:

- Focused unit or slice test for advisor/runtime settings behavior.
- Existing chat/context tests still pass.

### 1.2 Shell Cancellation Process Cleanup

Issue statement: interrupted shell execution can leave child processes and stdout/stderr reader futures alive because cleanup currently happens on timeout but not consistently on interruption.

Why it needs addressing: Cancellation and interruption are normal operational events. Even without changing security policy, cancelled shell turns must not leak local processes or background readers.

How to address it:

- Wrap process execution in cleanup that destroys the process on interruption/cancellation.
- Bound and cancel stdout/stderr capture futures on all terminal paths.
- Preserve timeout behavior.
- Ensure cancellation is not treated as retryable model/tool work if the local cancellation path can distinguish it.

Code targets:

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java` only if retry classification must distinguish cancellation.

Consolidated overlap notes: The robustness and consolidated reviews mention shell security and cancellation together. This plan includes only resource cleanup and cancellation robustness; shell policy hardening remains out of scope.

Senior guidance: Prefer a small `try/finally` or helper around `Process` lifecycle. Avoid changing command parsing, allowlists, or user-facing shell configuration in this pass.

Validation expectations:

- Focused test that interrupts a long-running shell process and verifies the process/futures are cleaned up.
- Regression test that timeout still destroys the process.

### 1.3 Public Request Validation And Stable Error Mapping

Issue statement: Public controllers allow null, blank, or malformed request payloads to reach deep service/model/persistence paths, producing unstable failures instead of predictable client errors.

Why it needs addressing: Alpha clients need stable 400/404/409 behavior. Clear validation also protects service code from carrying transport-level assumptions.

How to address it:

- Add request DTO validation at controller boundaries for chat, commands, task/workflow create/update/run, orchestration assignment creation, and job creation.
- Normalize blank strings to absent values where appropriate.
- Map known domain validation failures to stable HTTP status codes.
- Add controller tests for null body, blank required fields, invalid ids, invalid enum/status values, missing referenced records, and conflict cases.

Code targets:

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- Orchestration API controllers under `src/main/java/io/mindspice/magenta2/api/web/`
- Existing exception handling or response mapping components, if present.

Consolidated overlap notes: Robustness calls out thin chat validation; cohesion calls out public API contracts; consolidated review lists request validation as high-value hardening. Implement this once through consistent controller validation and error mapping.

Senior guidance: Keep validation close to request DTOs and domain services. Do not let controllers grow large; if validation logic becomes nontrivial, extract small request mappers or service methods.

Validation expectations:

- Focused controller tests for every touched endpoint.
- Service tests for domain-level conflict behavior where HTTP is not involved.

## 2. Runtime Behavior Hardening

### 2.1 Standardize Streaming/SSE Lifecycle Semantics

Issue statement: Chat, task, workflow, and orchestration side-panel streams each hand-roll emitter/subscription/error behavior and currently disagree on client disconnect, timeout, model failure, user cancellation, and execution validation failure.

Why it needs addressing: Streaming is central to the product. Inconsistent lifecycle handling can leave chat history, plan execution status, task status, workflow status, or side-panel state diverged from actual execution.

How to address it:

- Define explicit stream outcomes: completed, client disconnected, timeout, user cancelled, model/tool failure, validation failure, and internal error.
- Extract shared SSE lifecycle support or return `Flux<ServerSentEvent<?>>` where that fits existing code.
- Ensure each stream endpoint uses the same timeout, cancellation, and cleanup policy unless there is a documented product reason to differ.
- Keep transport disconnect separate from logical execution failure for chat and saved-plan execution.
- Ensure active turns/subscriptions are unregistered on every terminal path.

Code targets:

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- Any new small SSE support component under the API or service layer.

Consolidated overlap notes: This merges robustness streaming state concerns, cohesion stream control-flow concerns, simplification SSE duplication, and consolidated streaming semantics findings.

Senior guidance: First write down the intended outcome table in tests or a small internal comment. Then implement the smallest shared helper that removes divergent lifecycle code. Do not hide domain transitions inside a generic emitter utility.

Validation expectations:

- Focused tests for start, progress, completion, model failure, validation failure, timeout, client disconnect, and user cancellation for each stream class touched.
- Playwright MCP validation if browser/live chat or visible streaming behavior changes.

### 2.2 Move Workflow Stream Execution Off Servlet Threads

Issue statement: `WorkflowController.streamRun` starts an SSE emitter but performs synchronous workflow execution before returning, tying long workflow runs to servlet request threads.

Why it needs addressing: Workflow execution can be long-running. The task stream already uses asynchronous execution semantics; workflow streaming should not block request threads or ignore disconnect/cancellation behavior.

How to address it:

- Move workflow execution onto the same style of bounded executor/reactive scheduling used by task streaming.
- Route workflow stream events through the standardized SSE lifecycle support from step 2.1.
- Preserve existing synchronous service behavior for non-stream code paths if they exist and remain useful.

Code targets:

- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/ai/workflow/WorkflowService.java`
- Shared SSE support introduced in step 2.1.

Consolidated overlap notes: This is called out directly in robustness, cohesion, simplification, and consolidated reviews. It should be implemented with the broader stream lifecycle work rather than as a separate one-off.

Senior guidance: Match task stream semantics first. Avoid redesigning workflow execution or adding graph support as part of moving work off the request thread.

Validation expectations:

- Test that `streamRun` returns promptly while execution continues asynchronously.
- Test workflow stream cancellation/timeout behavior matches task stream behavior.

### 2.3 Prevent Duplicate Queued Assignment Submission

Issue statement: The orchestration scheduler can submit the same `QUEUED` assignment multiple times while the background executor is saturated because the assignment remains queued until the worker later acquires a lease.

Why it needs addressing: Duplicate submissions waste executor capacity, create noisy operational behavior, and can create confusing lease/checkpoint races.

How to address it:

- Mark or lease queued assignments before executor submission, or maintain a bounded in-memory submitted set with reliable cleanup.
- Catch and handle executor submission rejection so polling remains healthy.
- Preserve durable lease semantics and avoid losing work if submission fails.

Code targets:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/execution/MagentaWorkExecutor.java`

Consolidated overlap notes: Robustness and consolidated reviews both flag this as a queue correctness issue. It overlaps with orchestration lease behavior but should stay limited to duplicate submission and rejected submission handling.

Senior guidance: Favor a durable state transition if the repository already has the right lock/lease primitives. If using in-memory tracking, make cleanup explicit for success, failure, rejection, and shutdown.

Validation expectations:

- Scheduler test with saturated/backlogged executor proving one queued assignment is submitted once.
- Rejection-path test proving polling survives and work remains eligible.

## 3. Persistence Hardening

### 3.1 Decide SQLite Schema Ownership And Validate Clean/Upgraded Databases

Issue statement: Some tables live in `schema.sql`; other repositories create or alter their own tables. This has already drifted, and SQLite foreign-key behavior is not clearly enabled or tested.

Why it needs addressing: Alpha installs must behave the same on clean databases and upgraded local databases. Orphaned task/workflow rows or drifted schema columns will be painful to diagnose after real usage begins.

How to address it:

- Choose and document the alpha schema policy: central `schema.sql`, repository-owned bootstrapping, or a narrow hybrid with explicit ownership rules.
- Ensure `ai_chat_session_metadata.planning_model` and orchestration/settings/workspace/agent tables are covered by the chosen policy.
- Enable SQLite foreign keys per connection or implement explicit repository cascades where needed.
- Add clean-database startup validation and upgraded-database validation for representative older schemas.

Code targets:

- `src/main/resources/schema.sql`
- `src/main/resources/application.yml`
- Repository `ensureSchema` methods, especially chat metadata, agent jobs, plans, audit, profiles, runtime settings, workspace, and orchestration runtime.
- `TaskRepository` and `WorkflowRepository` delete behavior.

Consolidated overlap notes: Robustness highlights foreign keys; cohesion and simplification highlight fragmented schema ownership; consolidated review recommends schema policy before preserving alpha user data.

Senior guidance: Do not introduce a full migration framework unless the user explicitly accepts that scope. The alpha target is clear ownership, repeatable startup, and tested delete/cascade behavior.

Validation expectations:

- Clean SQLite startup smoke.
- Test that task/workflow deletes do not leave orphaned runs.
- Test or fixture proving an upgraded database with pre-existing tables gets required columns/tables.

### 3.2 Audit Sequence Robustness

Issue statement: Audit sequence numbers are computed by reading max sequence and inserting later, without an apparent uniqueness guarantee or retry behavior.

Why it needs addressing: Concurrent or retried activity can produce ambiguous audit chronology, weakening debugging and accountability during alpha testing.

How to address it:

- Add a unique constraint for `(conversation_id, sequence)` if compatible with existing data.
- Add retry-on-conflict behavior or move to a safer sequence allocation strategy.
- Raise logging visibility for audit write failures if audit is expected to support alpha debugging.

Code targets:

- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AuditRepository.java`
- Audit schema ownership path chosen in step 3.1.

Consolidated overlap notes: Robustness and simplification both flag this as lower priority but useful for alpha operations. Do it with schema work while the schema policy is already being touched.

Senior guidance: Keep the fix local. A perfect global audit event store is not needed; the goal is deterministic per-conversation ordering or clear retry behavior.

Validation expectations:

- Concurrent audit insert test for one conversation.
- Existing audit tests still pass.

## 4. Boundary And Cohesion Cleanup

### 4.1 Extract Controller Workflow And Stream Logic

Issue statement: Controllers own too much orchestration behavior, especially stream lifecycle, event shaping, context mapping, and manual terminal event logic.

Why it needs addressing: Thin controllers are a stated project convention. Keeping workflow behavior in controllers makes similar APIs diverge and raises regression risk.

How to address it:

- Move workflow run orchestration and event mapping into service-level collaborators.
- Keep controllers responsible for request validation, response status, and delegating.
- Use shared stream support from step 2.1 for emitter/subscription cleanup.

Code targets:

- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java` only where stream extraction requires it.
- New focused service/support classes under existing package boundaries.

Consolidated overlap notes: This is the implementation side of the cohesion review's thin-controller finding and overlaps strongly with stream lifecycle work. Do not perform an unrelated controller rewrite.

Senior guidance: Extract along existing behavior. If a private controller method can move unchanged into a support class with tests, prefer that over inventing a new architecture.

Validation expectations:

- Existing controller tests continue to pass.
- New tests cover service/support behavior that moved out of controllers.

### 4.2 Introduce Public API DTOs For Lifecycle-Owned Fields

Issue statement: Some public endpoints accept or return internal records with service-owned fields such as timestamps, statuses, checkpoints, evidence, lease owner, lease expiry, and internal ids.

Why it needs addressing: Public API contracts should not let clients set lifecycle state that services own, and should not freeze persistence shapes as public wire contracts before alpha.

How to address it:

- Introduce request DTOs for task definition create/update, orchestration job create, assignment create, and run requests.
- Introduce response DTOs where internal fields need to be hidden or normalized.
- Keep domain records inside service/repository boundaries.
- Add mappers near the API layer or service boundary.

Code targets:

- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationJobController.java`
- Assignment/orchestration API controllers and request records.
- Domain records only where constructor/factory changes are necessary.

Consolidated overlap notes: This combines cohesion API-contract findings with request validation work. It can be staged endpoint by endpoint.

Senior guidance: Start with create/update inputs because that is where clients can most easily mutate service-owned fields. Response cleanup can follow if changing it would not break current UI clients.

Validation expectations:

- Controller contract tests that client-provided lifecycle fields are ignored or rejected.
- Existing UI/API tests updated to use DTO wire shape.

### 4.3 Move `PlanMode` To A Shared Chat Interaction Package

Issue statement: `PlanMode` lives under `ai.chat.plan`, but now represents shared chat/task interaction modes such as `TASK` and `EXECUTE_TASK`.

Why it needs addressing: Package ownership should reflect domain ownership. Leaving shared interaction state in the plan package encourages more non-plan concepts to accumulate there.

How to address it:

- Move or rename `PlanMode` into a chat-level model package, such as `io.mindspice.magenta2.ai.chat.model`.
- Update imports in task, chat, and plan services.
- Update package guides if the move changes local responsibilities.

Code targets:

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanMode.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/task/TaskService.java`
- Package `AGENTS.md` files near changed packages, if present.

Consolidated overlap notes: Cohesion flags this directly. It is a low-behavior package ownership fix and can be done after stream/validation changes.

Senior guidance: Make this a mechanical move plus tests. Do not change mode semantics in the same commit unless a failing test proves a semantic bug.

Validation expectations:

- Full compile and relevant chat/task/plan tests.
- Package guide updates if ownership text changes.

### 4.4 Move `AgentJobRepository` To Agent Job Ownership

Issue statement: `AgentJobRepository` lives in the chat repository package while persisting `ai.agent.job` domain records consumed by `AgentJobService`.

Why it needs addressing: Repository placement should match domain ownership. The current placement makes chat persistence appear to own agent job data.

How to address it:

- Move the repository to the agent job package or a dedicated agent job persistence package.
- Update imports/configuration.
- Update package guides if they describe repository ownership.

Code targets:

- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AgentJobRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/agent/job/AgentJobService.java`
- Tests using `AgentJobRepository`.

Consolidated overlap notes: Cohesion flags this package mismatch. It pairs naturally with schema ownership work because agent job schema is repository-owned today.

Senior guidance: Keep table names and behavior unchanged. The value is ownership clarity, not persistence redesign.

Validation expectations:

- Existing agent job tests pass.
- Compile confirms import updates.

### 4.5 Incremental `ChatService` Seam Extraction

Issue statement: `ChatService` coordinates chat, plan, task, tool loop, audit, title jobs, model policy, prompt assembly, retry, and runtime settings.

Why it needs addressing: The class is understandable in sections but has become a high-coupling regression point for live chat and execution changes.

How to address it:

- Do not start with a broad split.
- Extract only seams needed by the fixes above, likely request/model resolution, stream/turn lifecycle, tool-loop execution, audit/title side effects, and plan/task execution bridges.
- Preserve behavior with tests before and after each extraction.

Code targets:

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- New collaborators under `io.mindspice.magenta2.ai.chat.service` or a nearby package matching existing conventions.

Consolidated overlap notes: This appears in cohesion, simplification, and consolidated review as an architecture risk. Treat it as an incremental refactor track, not a prerequisite to every correctness fix.

Senior guidance: Every extraction should have a before/after reason tied to an implemented remediation. If an extraction does not reduce risk for a current fix, defer it.

Validation expectations:

- Existing chat, plan, task, tool-loop, and audit tests pass.
- Add focused tests around newly extracted collaborators if behavior becomes independently testable.

## 5. Deferred Or Refactor Candidates

### 5.1 Long-Record Mutation Helpers In Plan/Task Services

Issue statement: `ExecutionPlan` and related task/plan records are repeatedly reconstructed through long record-copy calls.

Why it needs addressing: Long positional reconstruction is error-prone and makes status or metadata changes risky.

How to address it:

- Add local withers, builders, or smaller nested aggregates where mutation patterns are repeated.
- Stage this near code that is already changing plan/task state.

Code targets:

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/ExecutionPlan.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/task/TaskService.java`

Consolidated overlap notes: This is a P1 simplification target but should not outrank correctness fixes unless it directly prevents mistakes in touched code.

Senior guidance: Avoid a project-wide style migration. Add helpers only where they replace repeated, risky reconstruction.

Validation expectations:

- Existing plan/task tests.
- Focused test if helper behavior includes defaults or derived values.

### 5.2 Remove Dead Command Compatibility Code And Stale Utilities

Issue statement: Dead command handlers and apparently unused utilities remain in source, including old chat command compatibility helpers and stale `Option`/`DataService` definitions.

Why it needs addressing: Dead code makes current behavior harder to trace and can mislead future implementers.

How to address it:

- Confirm usages with `rg`.
- Remove dead handlers only if routes no longer expose them.
- Remove stale utilities only if there are no source/test/runtime references.

Code targets:

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/core/util/Option.java`
- `src/main/java/io/mindspice/magenta2/core/DataService.java`

Consolidated overlap notes: Simplification marks this P2. Keep it as cleanup after behavior hardening unless touched code makes it cheap.

Senior guidance: Let the compiler and tests prove removal. Do not remove compatibility behavior that is still reachable by public clients without documenting the contract change.

Validation expectations:

- `rg` shows no removed symbol references.
- `mvn test` passes.

### 5.3 Replace Nullable-Union Stream Event DTO Shape

Issue statement: `ChatStreamEvent` uses mutually exclusive nullable fields via factory methods instead of typed event payloads.

Why it needs addressing: Nullable unions are harder for clients and tests to reason about, especially as stream semantics become more explicit.

How to address it:

- Introduce typed payload records or an explicit event type plus event-specific payload.
- Migrate stream serialization tests and clients.
- Consider this after step 2.1 defines lifecycle semantics.

Code targets:

- `src/main/java/io/mindspice/magenta2/ai/chat/stream/ChatStreamEvent.java`
- Stream serialization and browser client code consuming chat events.

Consolidated overlap notes: Simplification marks this P2. It is valuable if stream lifecycle work already changes event contracts.

Senior guidance: Do not break browser clients casually. If changing the wire shape, update all producers, consumers, and tests together.

Validation expectations:

- Stream serialization tests.
- Browser/live stream validation if wire shape changes.

### 5.4 Workflow, Schedule, And Reaction Alpha Decision

Issue statement: Workflows, schedules, and event reactions exist, but parts remain prototype-shaped or generic map DSLs. It is unclear which surfaces should be alpha-facing.

Why it needs addressing: Alpha users should not encounter half-productized workflow/schedule/reaction behavior unless it is explicitly labeled or hidden.

How to address it:

- Decide per surface: productize now, hide behind dev/experimental controls, or document as v1/experimental.
- If productizing workflow, make stream behavior and API contracts consistent through earlier steps.
- If hiding schedules/reactions, avoid expanding their generic template DSL during alpha hardening.

Code targets:

- `src/main/java/io/mindspice/magenta2/ai/workflow/WorkflowService.java`
- Schedule and orchestration event/reaction services.
- API controllers and UI routes exposing workflow/schedule/reaction surfaces.
- Documentation or `.internal-dev/knowledge` updates for the decision.

Consolidated overlap notes: Simplification and consolidated reviews both recommend an explicit alpha decision. This plan does not force productizing all surfaces.

Senior guidance: Make the smallest honest alpha decision. Hiding an experimental surface is better than hardening a feature that is not part of the alpha promise.

Validation expectations:

- If productized: focused API/UI tests for accepted behavior.
- If hidden: tests or route checks proving alpha users cannot reach it accidentally.
- If documented experimental: docs updated and UI/API text avoids overpromising.

# Validation

Run validation at the end of each implemented remediation group, then again for the complete branch.

- Add focused unit, slice, or integration tests for every implemented remediation group.
- Run targeted tests near the changed packages before broad suite runs.
- Run `mvn test` after grouped fixes.
- Run a bounded Spring Boot startup smoke against an isolated SQLite database, for example:

  ```bash
  timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-non-security-remediation.sqlite'
  ```

- Use Playwright MCP only if implementation touches live chat, SSE, interruption, browser surfaces, workflow/task streaming, or visible stream/client behavior. Before doing so, read and follow `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.
- Validate clean SQLite startup and upgraded SQLite behavior after schema ownership or foreign-key changes.
- Complete `.internal-dev` closeout after implementation: changelog entry, bug records for any out-of-scope discoveries, reusable knowledge updates for durable architecture decisions, and notes for deferred ideas only after confirming they are out of scope.

# Exit Criteria

- Runtime context compaction honors runtime settings.
- Shell interruption/cancellation reliably cleans up child processes and reader futures.
- Public request validation produces stable client errors for malformed inputs and known conflicts.
- Chat, task, workflow, and orchestration side-panel streams have documented and tested lifecycle semantics.
- Workflow streaming no longer blocks servlet request threads.
- Queued orchestration assignments are not submitted more than once while waiting for executor capacity.
- SQLite schema ownership is documented and tested for clean and upgraded databases, including foreign-key/delete behavior.
- Audit sequence behavior is robust under concurrent writes or has a tested retry/constraint strategy.
- Controllers delegate stream/workflow behavior to focused services or support components.
- Public DTOs prevent clients from setting service-owned lifecycle fields on remediated endpoints.
- `PlanMode` and `AgentJobRepository` live under packages that match their domain ownership.
- Any `ChatService` extraction is incremental, behavior-preserving, and tied to a concrete remediation.
- Deferred/refactor candidates are either completed where cheap, explicitly left for later, or captured in `.internal-dev/notes/` after user confirmation.
- Focused tests and `mvn test` pass.
- Bounded startup smoke passes or has an explicit environment blocker.
- Playwright MCP validation is completed for any live chat/SSE/browser-facing changes, or a documented MCP infrastructure blocker plus an accepted fallback is recorded.
- `.internal-dev` closeout artifacts are updated for the implementation work before the plan is archived.
