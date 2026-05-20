# Service Architecture System Map

## Scope

This artifact maps the reviewed Magenta service architecture and shows the major integration paths between API/controllers, chat, tools, plans/tasks, runtime assignments, workflows, jobs, workspaces, outputs, settings, and persistence.

## Findings

### High-Level Domain Map

- `api.web`: REST/SSE/HTMX transport, selectors, stream helpers, browser shell and fragments.
- `ai.chat`: chat turns, prompt/context assembly, memory, audit, model routing, tools, plan/task chat hooks.
- `ai.chat.plan` and `ai.chat.task`: session plans, saved task templates, plan/task runs, validation/evidence state.
- `ai.chat.tool`: registered Spring AI tool callbacks, file/shell/web/plan/task/question tools, transcript retention.
- `ai.execution`: active turn registry, per-conversation coordination, shared executor concepts.
- `ai.orchestration.runtime`: assignments, jobs, schedules, reactions, events, projects, direct-line inbox.
- `ai.orchestration.workflow`: workflow definitions, graph validation, runs, node runs, workflow inbox, runner.
- `ai.orchestration.workspaces`: workspace records, confined filesystem paths, links, leases, output artifacts.
- `ai.orchestration.settings` and `ai.config.user`: runtime defaults plus file-backed model/provider configuration.
- Repositories and `schema.sql`: SQLite persistence shape and warm database compatibility bootstrap.

### Chat Turn / Context / Tool / Audit Flow

```mermaid
flowchart TD
  UI[Browser/API client] --> CC[ChatController SSE]
  CC --> RR[RequestResolver]
  RR --> RS[RuntimeSettingsService]
  RR --> CM[ChatMemoryRepository]
  RR --> PA[PromptContextAssembler]
  PA --> PS[PlanService/TaskService]
  CC --> AT[ActiveTurnRegistry]
  CC --> CS[ChatService]
  CS --> TR[ChatModelRouter]
  CS --> TG[ToolAccessPolicy + ChatToolRegistry]
  TG --> TCM[ToolCallingManager]
  TCM --> CT[File/Shell/Web/Plan/Task/Question tools]
  CT --> WS[Workspace or chat-file context]
  TCM --> TTS[ToolTranscriptService]
  CS --> CM
  CS --> AUD[AuditService/AuditRepository]
  CC --> SSE[SSE message/tool/context/error events]
  AUD --> AE[(audit_event)]
  CM --> MEM[(ai_chat_memory)]
```

### Assignment / Job / Workflow Execution Flow

```mermaid
flowchart TD
  API[Plan/Task/Workflow/Job/Agent APIs + HTMX] --> AS[AssignmentService]
  SCH[Schedules/Reactions] --> AS
  AS --> WA[(work_assignments)]
  POLL[Runtime poller] --> ORS[OrchestrationRunnerService]
  ORS --> WA
  ORS --> CTX[OrchestrationTaskContext]
  ORS --> CHAT[ChatService task execution]
  ORS --> WF[WorkflowService/WorkflowRunner]
  WF --> WR[(workflow_runs + workflow_node_runs)]
  ORS --> JS[JobService]
  JS --> JR[(job_runs)]
  JS --> AS
  ORS --> WLS[WorkspaceLeaseService]
  WLS --> WL[(workspace_leases)]
  ORS --> OUT[OutputArtifactService]
  OUT --> ROA[(run_output_artifacts)]
  ORS --> EV[OrchestrationEventService]
  EV --> RX[EventReactionService]
  RX --> AS
```

### Workspace / Output / Artifact Flow

```mermaid
flowchart TD
  AG[Agent profile] --> WSS[WorkspaceService]
  JOB[Job/assignment] --> WSS
  PROJ[Project] --> WSS
  WSS --> W[(workspaces)]
  WSS --> WD[WorkspaceDirectoryService]
  WD --> FS[(dataRoot filesystem)]
  WSS --> LINKS[(workspace_links)]
  ASSIGN[Assignment/workflow/task run] --> LEASE[WorkspaceLeaseService]
  LEASE --> WL[(workspace_leases)]
  ASSIGN --> TEMP[taskTemp/workflowTemp/jobWorkspace]
  TEMP --> FS
  ASSIGN --> OCTX[OutputArtifactContext]
  OCTX --> OAS[OutputArtifactService]
  OAS --> OUTDIR[durable output dir]
  OAS --> ROA[(run_output_artifacts)]
  API[OutputController/UI] --> OAS
  OAS --> DL[confined inline/download read]
```

### Persistence Ownership Map

```mermaid
flowchart LR
  CHAT[ai.chat] --> M[(ai_chat_memory)]
  CHAT --> S[(ai_chat_session_metadata)]
  CHAT --> A[(audit_event)]
  PLAN[ai.chat.plan/task] --> PD[(plan_definitions)]
  PLAN --> PR[(plan_runs)]
  PLAN --> PCM[(plan_chat_messages)]
  WF[ai.orchestration.workflow] --> WFD[(workflow_definitions)]
  WF --> WFR[(workflow_runs)]
  WF --> WNR[(workflow_node_runs)]
  WF --> INB[(inbox_messages)]
  RT[ai.orchestration.runtime] --> AP[(agent_profiles)]
  RT --> WA[(work_assignments)]
  RT --> ACL[(assignment_conversation_links)]
  RT --> SCHED[(agent_schedules/schedule_firings)]
  RT --> RX[(agent_event_reactions/orchestration_events)]
  RT --> J[(job_definitions/job_runs/job_recurrences)]
  RT --> AINB[(agent_inbox_messages)]
  WS[ai.orchestration.workspaces] --> WSP[(workspaces)]
  WS --> WLINK[(workspace_links)]
  WS --> WLEASE[(workspace_leases)]
  WS --> ART[(run_output_artifacts)]
  CFG[settings/config] --> RS[(runtime_settings)]
```

## Risk Assessment

The diagrams show healthy package separation, but the runtime contracts are not equally explicit. Execution, workspace, output, and audit state cross package boundaries through records and JSON payloads without enough validation at the handoff points.

The highest-risk edges are:

- `WorkflowRunner` called from assignment execution while also owning async execution.
- `AssignmentService` accepting `workspaceId` without workspace validation.
- `ChatService` plain and tool streaming paths persisting different state.
- `OrchestrationController` reconstructing records instead of delegating complete domain mutations.
- Repository delete/purge paths relying on callers or FKs inconsistently.

## Recommendations

- Treat the system maps as the baseline contract for remediation plans.
- Add narrow integration tests around every red-edge handoff: assignment to workflow, assignment to job, workflow to outputs, chat to audit, runtime settings to tools, and owner delete to workspace cleanup.
- Keep workflow and runtime inbox tables separate; that split is sound and should not be collapsed.

## Follow-ups

- Convert these diagrams into permanent technical docs after remediation decisions are made.
- Add a table-level owner appendix to `docs/technical/data-model.md` once schema cleanup is complete.
