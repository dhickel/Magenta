# Topic

Magenta alpha architecture flow map for chat, planning, task execution, workflows, and durable orchestration.

# Source References

- `src/main/java/io/mindspice/magenta2/api/web/ChatController.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/ChatMemoryRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/resources/schema.sql`

# Key Takeaways

## Chat Flow

Chat starts at `ChatController.chat` for blocking calls or `ChatController.stream` for SSE. The controller passes `ChatRequest.MsgRequest` to `ChatService.resolve`, which determines:

- conversation id, generating a UUID when absent
- whether this is a new conversation
- selected model from request, stored metadata, runtime settings, or AI config
- planning model when the conversation is in plan/task design mode

Blocking chat goes through `ChatService.chat`. If `ConversationTurnCoordinator` is present, it submits a per-conversation `CHAT_TURN` work item to `MagentaWorkExecutor`; otherwise it runs immediately.

Streaming chat registers an `ActiveTurn` in `ActiveTurnRegistry`, sends a `start` SSE event with `turnId` and interrupt token, then subscribes to `ChatService.stream`. Streaming uses a per-conversation semaphore to reject concurrent streams for the same conversation.

## Model And Memory Flow

`ChatService` chooses between a tool-capable path and a plain chat path.

Plain path:

1. Save/audit user message through Spring AI chat memory advisor.
2. Build a `ChatClient` from `ChatModelRouter`.
3. Attach `ContextManagementAdvisor` using `ChatMemory.CONVERSATION_ID`.
4. Call the selected model.
5. Persist assistant output through the advisor/repository path.
6. Maintain context usage and enqueue title job for first turns.

Tool path:

1. Resolve approved tools from runtime settings or default agent config through `ChatToolRegistry`.
2. Build prompt instructions with effective system prompt plus user message.
3. Prepare context via `ContextManagementAdvisor.preparePrompt`.
4. Call the selected model through `ChatModelRouter`.
5. While the model returns tool calls, execute them through `ToolCallingManager`.
6. Convert tool responses into Magenta-owned transcript messages via `ToolTranscriptService`.
7. Persist tool transcript messages and audit tool executions.
8. Check interrupt queue at tool checkpoints.
9. Compact/checkpoint tool-loop context when needed.
10. Continue model calls until final assistant output or validated plan/task completion.

Chat messages persist in `ai_chat_memory`. Conversation metadata persists in `ai_chat_session_metadata`. Audit events persist in `audit_event`.

## Interrupt And Concurrency Boundaries

`ActiveTurnRegistry` stores active stream turns by turn id. Interrupt requests must provide conversation id, turn id, interrupt token, and message. Accepted interrupts are queued and consumed during tool checkpoints.

`MagentaWorkExecutor` provides bounded priority lanes:

- `CHAT_TURN`
- `DELEGATION`
- `BACKGROUND_JOB`

`ConversationTurnCoordinator` serializes blocking chat turns per conversation. Streaming chat uses a semaphore instead of the coordinator queue.

Cancellation is cooperative. Stream disposal can interrupt reactive work, but model calls and tool calls are not uniformly hard-cancelled. Durable orchestration cancellation is checked at assignment/job-item boundaries.

## Plan Flow

Plan mode state persists in `ai_chat_plans` and `ai_chat_plan_steps`.

Plan entry:

1. `/api/chat/commands` with `/plan` calls `ChatService.beginPlan`.
2. `PlanService.beginPlan` creates or replaces persisted plan state.
3. Chat continues with a plan-mode system prompt and plan edit tools.

Plan editing:

- `PlanSaveTools` mutate keyed plan fields through `PlanService`.
- `ask_user_questions` queues clarification questions.
- `plan_ready_for_approval` marks the plan ready after validation.

Plan execution:

1. `ChatService.resolveSavedPlanExecution` clears chat context for execution.
2. `PlanService.markExecuting` switches mode to `EXECUTE_PLAN`.
3. The execution prompt instructs the model to complete the saved plan.
4. `plan_report` records evidence.
5. `plan_complete` validates completion through `PlanCompletionService`.
6. Successful validation exits execution mode and stores final message/evidence.
7. If model execution returns without valid completion, the plan is marked needs-review.

## Task Flow

Task design is parallel to plan design but creates reusable task definitions.

Task state tables:

- `ai_task_drafts`
- `ai_task_definitions`
- `ai_task_runs`

Draft flow:

1. `TaskService.beginDraft` creates a conversation-scoped draft.
2. Task tools mutate goal, inputs, outputs, assumptions, steps, and validation criteria.
3. `task_ready_for_approval` requires a complete draft.
4. `TaskService.approveDraft` saves an `ai_task_definitions` row.

Execution flow:

1. `TaskController.streamRun`, `OrchestrationRunnerService`, or `WorkflowService` calls into chat-backed task execution.
2. `ChatService.resolveTaskExecution` creates a run with task snapshot and input values.
3. `TaskService.registerExecutionContext` stores active run context in memory and `ai_chat_session_metadata.active_task_run_id`.
4. The model receives task runtime instructions and must call `task_complete`.
5. `TaskService.completeRun` validates required outputs and stores evidence/final message.
6. If the model returns without completion, the run becomes `NEEDS_REVIEW`; exceptions fail the run.

Durable orchestration never fabricates task outputs; it delegates task output creation back through chat-backed task execution.

## Workflow Flow

Workflow v1 is linear with two or three steps.

Workflow state tables:

- `ai_workflow_definitions`
- `ai_workflow_runs`

Workflow execution:

1. `WorkflowService.startRun` snapshots workflow definition and creates pending step runs.
2. For each step, `resolveInputs` binds literal values or prior step outputs.
3. Each step executes a task through `ChatService.executeTaskBlocking`.
4. Completed step output is stored in `step_runs_json`.
5. The last completed step's output becomes final workflow outputs.
6. Any failed/non-completed task step fails the workflow run.

## Durable Orchestration Flow

Durable runtime state is stored by `OrchestrationRuntimeRepository`:

- `orchestration_jobs`
- `orchestration_job_items`
- `work_assignments`
- `agent_inbox_messages`
- `agent_schedules`
- `schedule_firings`
- `agent_event_reactions`
- `orchestration_events`

Assignment creation:

1. Controllers or services create `AssignmentRequest`.
2. `AssignmentService.create` validates assignment type, agent id, job id, and required input.
3. A `WorkAssignment` is saved with `QUEUED` status.

Runner flow:

1. `OrchestrationRunnerService.pollQueuedWork` periodically finds queued assignments.
2. Background executor work calls `runAssignment`.
3. `runAssignment` acquires a lease and starts a heartbeat.
4. Stale running leases are recovered to `INTERRUPTED`.
5. Assignment types dispatch to task, workflow, job, agent message, wait, or report handlers.

Job flow:

1. `JOB_RUN` loads ordered job items.
2. Each item runs with retry policy and optional continue-on-failure.
3. The assignment checkpoints `nextItemIndex`, outputs, and evidence after each item.
4. `WAIT_FOR_MESSAGE` moves the assignment to `WAITING`.
5. `TASK_RUN` and `WORKFLOW_RUN` items delegate to chat-backed task/workflow execution.
6. `AGENT_MESSAGE` sends inbox messages.

Schedules and reactions:

- `ScheduleService` finds due schedules, creates idempotent schedule firing rows, and enqueues assignment templates.
- `OrchestrationEventService` saves events and applies enabled event reactions to enqueue assignment templates.
- `InboxService.send` persists inbox messages and publishes `INBOX_MESSAGE_RECEIVED`.

# Engine Relevance

The main engine invariant is that task results come from model-backed task execution through `ChatService` and `TaskService`. Orchestration provides durable wrapping, leasing, retries, checkpoints, and queueing, but does not invent outputs.

The strongest durability boundaries are task runs, workflow step runs, job items, assignments, schedule firings, inbox messages, and events. The engine is not designed for token-level resume or mid-tool resume.

The highest-risk runtime boundaries are streaming disconnects, tool execution cancellation, web fetch host validation, and assignment cancellation while model-backed execution is active.

# Open Questions

- Should streaming chat use the same `ConversationTurnCoordinator` queue as blocking chat?
- Should assignment cancel/pause interrupt active model-backed task/workflow execution or remain job-item-boundary-only for alpha?
- Should `schema.sql` become the complete clean-install schema, or should repositories remain the source of schema creation?
- Should workflows, schedules, and event reactions be alpha-facing or explicitly experimental?
- Should public assignment/job APIs hide lease/checkpoint/evidence internals behind response DTOs?
