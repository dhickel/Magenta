# Scope

Alpha milestone cohesion, style, and contract review. Reviewed whether controllers remain thin, services own use-case behavior, repositories own persistence, code paths share control flow, package responsibilities are clear, public APIs avoid leaking internals, and backend behavior aligns with the package guides.

# Findings

## High: Controllers Own Too Much Workflow Behavior

`ChatController.streamResolved` owns active turn registration, SSE lifecycle, event shaping, plan-execution failure recording, context maintenance, and last-message recovery in one controller method (`ChatController.java:90-266`). `TaskController.streamRun` builds orchestration context, chooses execution path, maps domain events into SSE payloads, and manages subscription lifecycle (`TaskController.java:135-245`). `WorkflowController.streamRun` owns run orchestration and manual SSE event construction (`WorkflowController.java:95-153`).

This violates the web package guide's thin-controller expectation. It also causes similar API surfaces to behave differently for cancellation, timeout, and error handling.

## High: `ChatService` Has Become The Central Cross-Domain Coordinator

`ChatService` depends on chat memory, metadata, rendering, AI config, context management, model routing, tool calling, tool registry, tool transcripts, plan service, task service, agent job service, turn coordinator, audit repository, object mapper, and runtime settings (`ChatService.java:141-160`). It owns:

- normal and streaming chat execution (`ChatService.java:268-418`)
- plan lifecycle and saved-plan execution (`ChatService.java:470-616`)
- task execution bridging (`ChatService.java:618-698`)
- tool loop orchestration (`ChatService.java:938-1211`)
- model/tool policy (`ChatService.java:1589-1649`)
- prompt assembly (`ChatService.java:1660-1728`)
- audit and title-job side effects (`ChatService.java:1253-1287`, `:1653-1658`)
- retry/snapshot recovery (`ChatService.java:1902-1977`)

The class is understandable in small sections, but it is now a coupling point for chat, plan, task, tool, audit, title jobs, settings, and orchestration. This raises regression risk for every change to live chat or execution.

## Medium: `PlanMode` Escapes Its Package Contract

`PlanMode` lives under `ai.chat.plan`, but includes `TASK` and `EXECUTE_TASK`. `TaskService` imports and returns it for task draft/execution state, and `ChatService` uses it as the common interaction-mode switch for tool allowlists and prompt assembly.

This is no longer plan-owned state. It is a shared chat interaction mode. Leaving it in the plan package makes package ownership harder to reason about and encourages future task/workflow concepts to accrete under plan naming.

## Medium: API Contracts Expose Internal Domain And Persistence Shapes

Several controllers accept and return internal records directly:

- `TaskController.create/update` accepts `TaskDefinition`, including timestamps.
- `OrchestrationJobController.create` accepts `OrchestrationJob`, including status/timestamps.
- assignment endpoints return `WorkAssignment`, including checkpoint, evidence, lease owner, and lease expiry.

This is fast to build but turns runtime and persistence details into public API. It also allows clients to provide fields that services should own, such as `createdAt`, status, checkpoint maps, and internal ids.

## Medium: Repository And Schema Ownership Are Split

`schema.sql` defines core chat/task/workflow tables, while many repositories create or alter their own tables at startup:

- `ChatMemoryRepository.ensureMetadataColumn`
- `ChatSessionMetadataRepository.ensureSchema`
- `AgentJobRepository.ensureSchema`
- `ChatPlanRepository.ensureSchema`
- `AuditRepository.ensureSchema`
- `AgentProfileRepository.ensureSchema`
- `RuntimeSettingsRepository.ensureSchema`
- `WorkspaceRepository.ensureSchema`
- `OrchestrationRuntimeRepository.ensureSchema`

There is already drift: `schema.sql` omits `ai_chat_session_metadata.planning_model`, while `ChatSessionMetadataRepository` adds it. Orchestration/settings/workspace/agent tables are absent from `schema.sql` entirely.

## Medium: Agent Job Repository Lives In The Chat Repository Package

`AgentJobRepository` is in `ai.chat.repository`, but it persists `ai.agent.job` domain records and is consumed by `AgentJobService`. This crosses the package guide boundaries and makes chat repositories own non-chat persistence.

## Low/Medium: Task And Workflow Run Streams Have Inconsistent Control Flow

`TaskController.streamRun` uses a Reactor stream scheduled on bounded elastic. `WorkflowController.streamRun` performs synchronous execution in the request thread. Both manually compare enum names as strings to choose terminal event names.

This inconsistency is likely to produce different behavior for similar user workflows and makes future UI/API clients harder to write predictably.

# Risk Assessment

The backend is functionally cohesive at the product-flow level: chat owns model turns, task execution goes through chat, workflows compose tasks, orchestration wraps durable work assignments. The code-level boundaries are weaker. Controllers and `ChatService` are absorbing behavior that should live in focused services, and internal records are becoming public contracts before those contracts are deliberately designed.

This is not a reason to stop alpha work, but it is a reason to put boundary cleanup on the alpha-hardening path. Every new live chat, plan, task, or orchestration feature will otherwise make the current coupling harder to unwind.

# Recommendations

1. Extract stream orchestration from controllers into focused services or a small SSE support component.
2. Split `ChatService` by current seams: request/model resolution, chat turn execution, tool-loop execution, plan coordination, task execution bridge, session/history metadata, audit/title side effects.
3. Move or rename `PlanMode` to a chat-level package such as `ai.chat.model` or split task execution state from plan state.
4. Introduce API DTOs for task, workflow, job, and assignment create/update responses where lifecycle fields should be service-owned.
5. Move `AgentJobRepository` under `ai.agent.job` or create a dedicated job persistence package.
6. Choose a schema policy: central `schema.sql` plus migrations, or repository-owned schema creation. Avoid drifting between both.

# Follow-ups

- Add controller contract tests for bad request bodies and consistent error status mapping.
- Add package dependency tests for API -> service -> repository direction.
- Add tests proving task stream and workflow stream semantics match for start, progress, completion, failure, and cancellation.
- Update package `AGENTS.md` files if `PlanMode`, agent jobs, or schema ownership move.
