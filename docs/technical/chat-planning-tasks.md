# Chat, Planning, and Tasks

This page explains the implemented chat/planning/task stack. Source anchors are [`ai/chat`](../../src/main/java/io/mindspice/magenta2/ai/chat), [`ChatController`](../../src/main/java/io/mindspice/magenta2/api/web/ChatController.java), [`PlanController`](../../src/main/java/io/mindspice/magenta2/api/web/PlanController.java), and [`TaskController`](../../src/main/java/io/mindspice/magenta2/api/web/TaskController.java).

## Conversation State

Conversation content is stored in `ai_chat_memory` through `ChatMemoryRepository` and exposed as `ChatHistory`. Session metadata is stored in `ai_chat_session_metadata` through `ChatSessionMetadataRepository` and exposed as `ChatSession`/`ChatSessions`.

Session metadata includes:

- Current model and planning model.
- Title, favorite, and archived state.
- Active task run id.
- Origin and agent id for agent-scoped conversations.
- Updated timestamp.

`AuditService` writes a separate append-only audit trail in `audit_event`, including messages, tool calls, compaction/context snapshots, and errors. This is used for diagnostics and transcript visibility separate from the user-facing chat memory.

## Chat Turn Lifecycle

`ChatController` receives `ChatRequest.MsgRequest` or `ChatRequest.CmdRequest`. `RequestResolver` normalizes conversation/model context into `ResolvedChatRequest`. `ChatService` then:

1. Records user input.
2. Resolves model settings through `ChatModelRouter`.
3. Assembles prompt context and context usage through `ContextManagementAdvisor`.
4. Applies tool access policy and approved tool registry.
5. Streams model output and tool activity.
6. Persists assistant messages, audit events, context usage, and any plan/task state changes.

Streaming events are represented by `ChatStreamEvent` and sent over `/api/chat/stream`. Expected event names are `start`, `chunk`, `tool`, `system`, `interrupt`, `context`, `done`, and `error`.

## Anonymous Chat Planning

The `/chat` planning path is anonymous in-chat planning. It is conversation-scoped, uses `PlanMode` and `ChatPlanState` in `ai.chat.model`, and persists a `SESSION_PLAN` keyed by the chat conversation id. It is not a saved `/plans` definition and cannot be saved or submitted as a task template.

`PlanService` owns:

- Session plan creation and retrieval.
- Three backend-seeded opening questions for goal, assumptions/details/constraints/approach, and expected deliverables.
- Pending follow-up planning questions and current question index.
- Plan answer recording.
- Plan approval, continue, cancel, anonymous execution, deletion, and final message state.
- Execution evidence and validation feedback.
- Persistent chat file directory resolution under `data/chats/<conversationId>/files/`.

Anonymous plan prompts do not expose structured inputs or outputs. They focus on goal, assumptions, expected deliverables, ordered steps, and validation. Approval actions are continue planning, approve and execute with conversation context, approve and execute clean, and cancel.

Anonymous execution installs a chat-scoped file context when there is no assignment context. File tools then resolve to the active chat file directory instead of a broad data-root fallback. Validator-approved final anonymous execution messages are persisted as markdown files in that directory. Clean execution is prompt-scoped: it omits stored chat messages from the model prompt for that run, while preserving the persisted transcript and appending the execution turn afterward.

Anonymous execution completion is gated by `plan_complete`. `PlanCompletionService` records the latest execution report, checks per-criterion evidence coverage, carries forward artifact paths previously recorded through `plan_report`, and asks the configured planning validator model to compare the approved plan, evidence, artifact contents, prior feedback, and proposed final message. Validator input sections are framed as untrusted data and the validator request is isolated from broad chat history. Durable validation feedback records the validator model used, records that model validation was skipped when a fail-closed preflight rejected completion before the model call, or fails closed if no planning validator model can resolve. Completion validation does not fall back to the execution model. On pass, the plan becomes `COMPLETED` and the stored final message is the only trusted user-facing completion. On failure, remediation stays in the execution tool loop; if the model exhausts completion repair without a validator-passed `plan_complete`, `ChatService` marks the plan `NEEDS_REVIEW` and persists a controlled review message instead of ordinary assistant text.

Anonymous execution is single-flight per conversation. `ActiveTurnRegistry` rejects a second saved-plan execution request for the same conversation while an execution turn is active, and the `/api/chat/{conversationId}/plan/execute` and `/api/chat/{conversationId}/plan/execute/stream` routes return `409 Conflict` before marking the plan executing.

Tool-call argument JSON is preflight validated before Spring AI tool execution. If any tool call in a model batch has malformed argument JSON, none of the tools in that batch execute. Magenta persists and streams compact synthetic tool diagnostics, records audit detail for the rejected calls, adds a system control message telling the model which call failed parsing, and continues the tool loop so the model can retry. These recovered parser failures do not directly update plan status, execution evidence, or validation feedback.

`NEEDS_REVIEW` is an execution-review state, not draft planning. `PlanService.mode(...)` resolves it as `NORMAL` while `ChatPlanState.status` remains `NEEDS_REVIEW`, so clients can show evidence and validation feedback without reinstalling PLAN-mode prompts, tools, or planning controls.

## Saved Plan Chat

Saved task planning happens in `/plans` and is separate from `/api/chat`, `ai_chat_memory`, and `ai_chat_session_metadata`. It is plan-scoped under `/api/plans`, stores messages in `plan_chat_messages`, and produces durable `TASK_TEMPLATE` definitions for later submission.

`SavedPlanChatService` owns:

- Creating a `TASK_TEMPLATE` draft directly for new saved plan chats.
- Persisting plan-scoped chat messages.
- Seeding four backend questions in a fixed order: runtime inputs, goal, high-level deliverables, and structured outputs.
- Passing the opening answers to a plan-id-scoped saved-plan model turn as seed context, not final field values.
- Continuing saved-plan chat turns through saved-plan-specific tools that update typed inputs, typed outputs, deliverables, assumptions, steps, and validation details on the saved draft.
- Enforcing saved-plan planning terminal states: either queued follow-up questions or a draft marked ready for approval.

Saved plan model turns do not use `/api/chat`, `ai_chat_memory`, or `ai_chat_session_metadata`. They use `plan_chat_messages`, the current `PlanDefinition`, and saved-plan-specific tool callbacks scoped by plan id.

`PlanDefinition` is the durable plan contract for saved definitions. It contains title, summary, goal, notes, deliverables, structured inputs/outputs, assumptions, ordered steps, validation criteria, evidence, feedback, planning/execution model choices, settings overrides, pending questions, and conversation linkage.

The `/api/chat/{conversationId}/plan/*` routes operate on anonymous session plans. The `/api/plans` routes operate on saved definitions and plan-scoped saved chats.

## Task Drafts

Task drafts are chat-backed task creation sessions owned by `TaskService`.

Routes:

- `POST /api/tasks/drafts/{conversationId}` starts a draft.
- `GET /api/tasks/drafts/{conversationId}` returns the active draft.
- `POST /api/tasks/drafts/{conversationId}/answers` records an answer.
- `POST /api/tasks/drafts/{conversationId}/approve` promotes the draft into a `TaskDefinition`.

Draft state is represented by `TaskDraft` and `TaskDraftStatus`. Draft answers are captured as structured task definition fields rather than freeform blob-only text.

## Saved Definitions

Saved plans and task-like definitions are durable records used by chat, operational pages, and assignment submission.

- `PlanController` exposes the unified saved plan/task API under `/api/plans`.
- `TaskController` exposes task-specific definition/draft/run routes under `/api/tasks`.
- Public run controls submit saved definitions to an agent assignment. They do not directly run arbitrary model execution from the controller.

Plan/task fields use record types such as `PlanFieldDefinition`, `TaskFieldDefinition`, `PlanStep`, and `TaskStep`. Field value types come from `PlanFieldType` and `TaskValueType`.

## Runs and Evidence

Plan runs and task runs preserve execution history:

- `PlanRun` stores input values, output values, definition snapshot, workspace/output paths, evidence, validation feedback, deliverable evidence, final/error messages, status, and timestamps.
- `TaskRun` stores task-specific run state and is used by task execution paths.

Runs snapshot their definitions so edits after execution do not rewrite historical meaning. Outputs are materialized through workspace/output services and indexed in `run_output_artifacts`.

## Submit-To-Agent Semantics

Public plan/task run routes now submit durable assignments:

- `POST /api/plans/{planId}/submit`
- `POST /api/plans/{planId}/runs/stream`
- `POST /api/tasks/{taskId}/runs/stream`

The controller resolves an active agent when no `agentId` is supplied, defaults priority to `9`, builds `AssignmentRequest`, and calls `AssignmentService.create` with `AssignmentType.TASK_RUN`. The SSE stream returns `submitted` or `failed` and then completes.

Actual execution is handled later by orchestration runner services. This keeps HTTP request handling short and makes queued work observable/cancellable.

## Model and Tool Routing

Model choices come from both file-backed AI config and runtime settings:

- File config in `ai.config.user` defines model endpoints and legacy agent defaults.
- `RuntimeSettingsService` provides default model, planning model, summary/compaction model, system chat settings, and tool defaults.
- `ChatModelRouter` resolves concrete model clients for chat turns.

Tools are controlled by `ChatToolRegistry` and per-agent approved tools. Shell execution also consults agent shell command allowlists and the explicit unsafe wildcard override from file config.

## Interruption and Active Turns

`ConversationTurnCoordinator`, `ActiveTurnRegistry`, and related `ai.execution` records track active work and interrupt status. `/api/chat/turns/{turnId}/interrupt` accepts a conversation id, interrupt token, and message to request interruption of an active turn.

The orchestration runtime can also force-interrupt agent queue work through operational routes, but durable assignment state remains owned by `AssignmentService`.
