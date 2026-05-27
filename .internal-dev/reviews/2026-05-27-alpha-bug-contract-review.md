# Scope

Reviewer 2 read-only pre-alpha bug and contract review for `/home/hickelpickle/Code/Java/magenta2`.

Required context was read before judging contracts: `AGENTS.md`, `.internal-dev/AGENTS.md`, `.internal-dev/specifications/AGENTS.md`, and relevant current specifications for architecture, services, API, schema, workflow, web, SimplyPages, and service graph. Domain-matching knowledge files were used only where they matched the reviewed surface. Production code was inspected directly; prior reviews and changelogs were not treated as proof that an issue still exists.

Focus was correctness and contract adherence across chat, SSE, task/workflow submission, workflow runtime, persistence, and browser-visible runtime boundaries. No production code, tests, specs, docs, knowledge, bugs, changelogs, or plans were intentionally modified. No tests were run because this assignment is a read-only review artifact.

# Findings

## Finding 1: SSE error callbacks can leave active chat/plan execution state registered

- Issue: `ChatController.streamResolved` treats `emitter.onError` as a transport disconnect and records it, but it does not dispose the subscription guard or complete the active turn. Cleanup only runs from `onCompletion` when `clientConnected` is still true or plan execution is already finalized.
- Target: `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`, `src/main/java/io/mindspice/magenta2/api/web/SseStreamLifecycle.java`, `src/main/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistry.java`
- Evidence: `domainCleanup` disposes the guard and completes the active turn at `ChatController.java:142-145`. `onCompletion` conditionally calls cleanup at `ChatController.java:158-162`, while `onError` only calls `recordTransportDisconnect` at `ChatController.java:166-168`. The lifecycle helper documents that client disconnects and model/tool failures should dispose subscriptions and complete active turns at `SseStreamLifecycle.java:21-39`, and its callback helper does dispose on error at `SseStreamLifecycle.java:141-155`. Active plan execution conflicts are held in `ActiveTurnRegistry` until `complete()` removes them at `ActiveTurnRegistry.java:28-48`.
- Scope: Chat SSE streams, saved-plan execution streams, explicit active-turn tracking, and any browser/client path where `onError` fires without a subsequent cleanup-bearing `onCompletion`.
- Impact: A disconnect or stream transport error can leak an active turn and, for plan execution, keep the conversation in the active-plan map. That can block later `/plan/execute/stream` attempts with a false active-execution conflict, leave model work running after the client is gone, and make final plan state depend on servlet callback ordering rather than domain state.
- Severity: high
- Confidence: medium
- Contract: `.internal-dev/knowledge/plan-execution-stream-finalization.md:13-22`; `SseStreamLifecycle.java:21-39`
- Mitigation Notes: The obvious triage point is the terminal callback path; avoid changing plan lifecycle semantics before confirming actual servlet callback ordering in the target container.

## Finding 2: Plain streaming turns advertise an interrupt token but cannot accept interrupts

- Issue: Plain streaming chat registers an `ActiveTurn` and sends `turnId`/`interruptToken`, but the plain model path never calls `ActiveTurn.phase(...)`. `ActiveTurn.acceptsInterrupts` defaults false, so `/api/chat/turns/{turnId}/interrupt` returns queued-after-turn for normal non-tool streaming model calls.
- Target: `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`, `src/main/java/io/mindspice/magenta2/ai/execution/ActiveTurnRegistry.java`, `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- Evidence: Stream start payload includes the active turn id and token at `ChatController.java:171-180`. `ActiveTurn.acceptsInterrupts` is false until `phase(...)` sets it at `ActiveTurnRegistry.java:89-122`. The tool-capable path sets phases around model/tool execution at `ChatService.java:1559-1647`, but the plain path falls through to `plainStream(request)` at `ChatService.java:617-639`, and `plainStream` itself does not receive or update the active turn at `ChatService.java:641-655`.
- Scope: Normal `/chat` SSE turns where no approved tools are used, or where a model is remembered as tool-unsupported and falls back to plain streaming.
- Impact: The API exposes an interrupt capability that silently degrades for the most common streaming path. Browser or API clients can receive a valid interrupt token, call the documented route, and still be unable to influence the active model call.
- Severity: medium
- Confidence: high
- Contract: `.internal-dev/specifications/api.md:24`; `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md:48-54`
- Mitigation Notes: Triage should confirm whether best-effort cancellation is intended only for tool turns or whether the start event should stop advertising interrupt semantics for non-interruptible phases.

## Finding 3: Some TASK_RUN assignment entry points bypass the required user-visible run name

- Issue: The active API contract requires non-job task/workflow submissions to include a user-visible `runDisplayName`, but multiple TASK_RUN assignment paths still use older `AssignmentRequest` constructors that set `runDisplayName` to null. The lower-level assignment validator only checks `taskId`/`workflowId`/`jobId`, not the non-job display-name contract.
- Target: `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`, `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`, `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`, `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentTemplateParser.java`
- Evidence: `TaskController` and `WorkflowController` enforce `requireRunDisplayName` before creating assignments at `TaskController.java:185-193` and `WorkflowController.java:116-124`. In contrast, `PlanController.submitToAgent` creates a non-job `TASK_RUN` with the constructor that omits `runDisplayName` at `PlanController.java:170-196`, and `PlanController.streamRun` does the same at `PlanController.java:300-319`. The generic agent assignment endpoint also omits `runDisplayName` at `AgentOrchestrationController.java:128-148`, and its request record has no field for it at `AgentOrchestrationController.java:401-415`. `AssignmentService` persists `normalize(request.runDisplayName())` without requiring it at `AssignmentService.java:141-164`; `AssignmentTemplateParser.validate` requires only task/workflow/job identifiers at `AssignmentTemplateParser.java:25-40`.
- Scope: Saved plan/task submission APIs and generic agent assignment creation, especially clients still using `/api/plans/{planId}/submit`, `/api/plans/{planId}/runs/stream`, or `/api/agents/{agentId}/assignments`.
- Impact: Task runs can enter queue/history/output attribution without the required user-visible name. Browser history and downstream run records can appear blank or inconsistent even though sibling task/workflow submission routes reject the same missing field.
- Severity: medium
- Confidence: high
- Contract: `.internal-dev/specifications/api.md:25`
- Mitigation Notes: Keep triage centered on the request boundary and shared assignment contract; do not infer that all older compatibility routes should remain public without product confirmation.

## Finding 4: PASS_THROUGH workflow routes are validated and executed as single-port mappings

- Issue: The route contract says `PASS_THROUGH` forwards all source node outputs as a map and does not use source/target ports, but validation requires both ports for all non-control data routes and runtime copies only one named source value to one target port.
- Target: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRouteType.java`
- Evidence: Route knowledge defines `PASS_THROUGH` as forwarding all source outputs and shows `fromOutputName`/`toInputName` as null for `PASS_THROUGH` at `.internal-dev/knowledge/workflow-route-model.md:20-40`. The enum comment also says source output is forwarded unchanged at `WorkflowRouteType.java:11-19`. `WorkflowValidator` nevertheless requires `sourcePort()` and `targetPort()` for every non-control route at `WorkflowValidator.java:170-185`, and `WorkflowRunner.resolveNodeInputs` handles `PASS_THROUGH` by checking a single `sourcePort` and writing a single `targetPort` at `WorkflowRunner.java:1127-1135`.
- Scope: Workflow v2 graph validation and execution for adapter-chain or branch workflows using `PASS_THROUGH` routes.
- Impact: A workflow matching the documented pass-through shape is rejected before submission. If users provide ports to satisfy validation, runtime semantics still collapse pass-through into one field instead of forwarding the source output map, so downstream nodes can receive incomplete or incorrectly shaped inputs.
- Severity: high
- Confidence: high
- Contract: `.internal-dev/knowledge/workflow-route-model.md:20-40`; `.internal-dev/knowledge/workflow-v2-graph-composer-runtime-contract.md:10-20`
- Mitigation Notes: Triage should compare saved workflow JSON from the HTMX authoring surface against the route model before changing executor behavior.

## Finding 5: DELEGATION workflow nodes can fabricate completed child plan runs

- Issue: `WorkflowRunner.executeDelegationNode` starts a child `PlanRun` and immediately completes it with empty outputs and a fixed final message. It does not execute through chat-backed task execution, model instructions, or `task_complete`.
- Target: `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`, `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- Evidence: `WorkflowNodeType` includes `DELEGATION` at `WorkflowNodeType.java:11-23`, and the workflow UI node form exposes every enum value at `OrchestrationController.java:2953-2958`. The runner handles the node by calling `planService.startRun(...)` and then `planService.completeRun(childRun.id(), Map.of(), "Delegated run completed", List.of())` at `WorkflowRunner.java:537-543`. `PlanService.completeRun` marks a running plan complete after required-output checks and output materialization at `PlanService.java:1140-1168`; the actual chat-backed execution path is separate (`startChatExecution`) at `PlanService.java:1066-1075`.
- Scope: Workflow v2 runs containing `DELEGATION` nodes, especially delegated plans/tasks with no required outputs or outputs that can be empty.
- Impact: Workflow history can show a child plan run as completed even though no delegated work occurred. This recreates the fake-output class of defect the task execution contract is supposed to prevent, and it can make workflow completion evidence materially untrustworthy.
- Severity: high
- Confidence: medium
- Contract: `.internal-dev/knowledge/task-execution-placeholder-test-gap.md:15-31`; `.internal-dev/knowledge/workflow-v2-graph-composer-runtime-contract.md:10-20`
- Mitigation Notes: Triage should first decide whether `DELEGATION` is an active public-alpha node type or should be treated as unsupported until real execution semantics exist.

## Finding 6: Pending chat FIFO ordering can race under concurrent enqueue

- Issue: Pending chat enqueue computes `max(message_order) + 1` and inserts the row without a uniqueness constraint or per-conversation write lock. Concurrent POSTs for the same conversation can select the same next order and persist duplicate ordering keys.
- Target: `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatPendingMessageRepository.java`, `src/main/resources/schema.sql`
- Evidence: `enqueue` reads `coalesce(max(message_order), 0) + 1` for the conversation at `ChatPendingMessageRepository.java:41-49`, then inserts that value at `ChatPendingMessageRepository.java:50-68`. Claims select the oldest pending message with `order by message_order asc limit 1` at `ChatPendingMessageRepository.java:215-229`. The schema defines `message_order integer not null` but only indexes `(conversation_id, status, message_order)` and `(conversation_id, message_order)`; it does not enforce uniqueness at `schema.sql:13-32`, and the repository bootstrap mirrors that at `ChatPendingMessageRepository.java:235-259`.
- Scope: Browser chat mid-turn queue when multiple normal messages are submitted quickly, duplicate tabs post to the same conversation, or background drain/retry behavior overlaps with user actions.
- Impact: FIFO drain order can become nondeterministic for duplicate `message_order` rows. A green service test that exercises single-thread enqueue does not prove the real browser/API boundary preserves user order under concurrent requests.
- Severity: medium
- Confidence: medium
- Contract: `.internal-dev/specifications/services.md:25`; `.internal-dev/specifications/schema.md:20`; `.internal-dev/specifications/web.md:25`
- Mitigation Notes: Later triage should reproduce with concurrent same-conversation POSTs rather than assuming SQLite transaction serialization is enough.

# Risk Assessment

The highest alpha risk is lifecycle divergence between visible browser/API state and backend runtime state. The SSE cleanup gap can strand active executions; workflow pass-through and delegation can make saved workflow runs disagree with the documented graph model; and missing run names create queue/history records that violate the user-facing run contract.

The medium risks are mostly concurrency and boundary gaps: explicit interrupt semantics are advertised more broadly than they work, and pending-message FIFO depends on non-concurrent enqueue behavior. These are likely to surface as intermittent or confusing user-facing behavior rather than deterministic unit failures.

Existing green tests should not be treated as sufficient for these findings because several issues require exercising the real controller/service/runtime boundary, SSE callback ordering, or concurrent request behavior.

# Recommendations

Prioritize independent triage of terminal lifecycle behavior, workflow route/delegation execution semantics, and non-job assignment request validation. Keep each triage narrow and evidence-driven against the current API and service contracts.

Use focused runtime or controller-level tests for any later fixes. Unit-only checks are unlikely to catch the SSE, workflow execution, and browser queue failure modes described here.

# Follow-ups

- Inspect actual servlet callback ordering for `/api/chat/stream` and `/plan/execute/stream` on client disconnect, timeout, and send failure.
- Exercise `/api/chat/turns/{turnId}/interrupt` against plain streaming, tool streaming, and tool-unsupported fallback models.
- Inventory all public or UI-reachable `AssignmentRequest` creation paths for `TASK_RUN` and `WORKFLOW_RUN` and compare them to `API-20260526-01`.
- Compile a minimal workflow fixture with a documented `PASS_THROUGH` route and verify save validation, runtime inputs, and persisted node outputs.
- Decide whether `DELEGATION` nodes are public-alpha supported; if they are, validate them with a task requiring real model/tool completion evidence.
- Run a concurrent pending-message enqueue/claim probe against SQLite with the same conversation id and inspect persisted order plus browser drain order.
