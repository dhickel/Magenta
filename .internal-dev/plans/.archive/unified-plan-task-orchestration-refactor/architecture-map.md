# Architecture Map

## Current State

- `ai.chat.plan` has the strongest behavior: plan mode, execution-mode prompt injection, approval, evidence, validation, and durable plan state.
- `ai.chat.task` has useful typed inputs/outputs and completion validation, but is separate from plan persistence and lost explicit deliverables.
- `ai.chat.workflow` is a thin two-to-three-step MVP chaining tasks synchronously.
- `ai.orchestration.runtime` has durable assignments, job items, inboxes, schedules, leases, and SSE integration, but much of the behavior is wrapper-level and should be reapproached.
- `ai.orchestration.workspaces` confines paths under `dataRoot` and should remain the path-safety baseline.
- The UI is split between `/chat` and orchestration pages; keep that route boundary.

## Target Ownership

- `io.mindspice.magenta2.ai.chat.plan`
  - Owns in-session planning, executable task definitions, plan/task runs, model-facing plan/task tools, and validation state.
  - Keep chat-plan concepts here because they are part of chat behavior and already tested.
- `io.mindspice.magenta2.ai.orchestration.runtime`
  - Owns workflow, job, project, assignment, inbox, schedule, approval, and event state.
  - Owns durable queue/resume at run or workflow-step boundaries, not token-level resume.
- `io.mindspice.magenta2.ai.orchestration.workspaces`
  - Owns agent homes, job/project persistent spaces, temp task/workflow spaces, output directories, and workspace leases.
- `io.mindspice.magenta2.ai.orchestration.docker`
  - New package for Docker image/container/exec lifecycle and runtime health checks.
- `io.mindspice.magenta2.api.web`
  - Thin HTTP/SSE adapters only. Controllers validate request shape and delegate to services.

## Core Domain Model

- `PlanDefinition`
  - `id`, `kind`, `status`, `title`, `summary`, `goal`, `notes`
  - `deliverables`, `steps`, `validationCriteria`, `assumptions`
  - `inputs`, `outputs`
  - `promptProfile`, `planningModel`, `executionModel`, `settingsOverrideJson`
  - timestamps
- `PlanKind`
  - `SESSION_PLAN`: in-chat plan draft or approved executable plan.
  - `TASK_TEMPLATE`: reusable finalized task.
- `PlanStatus`
  - Keep existing semantics where possible: `DRAFT`, `READY_FOR_APPROVAL`, `APPROVED`, `SAVED_TASK`, `EXECUTING`, `NEEDS_REVIEW`, `COMPLETED`.
- `PlanFieldDefinition`
  - `name`, `type`, `array`, `description`, `required`, `schema`, `example`
  - Types: `user_message`, `string`, `file_path`, `number`, `json`.
  - Inputs may be optional. Outputs are declared expectations and must all be submitted at completion.
- `PlanRun`
  - `id`, `planId`, `status`, `inputValuesJson`, `outputValuesJson`, `planSnapshotJson`, `workspaceId`, `outputDirectory`, `executionEvidence`, `validationFeedback`, `finalMessage`, `errorText`, timestamps.

## Workspace Layout

- Agent home: `data/agents/{agentId}/home`
- Agent outputs: `data/agents/{agentId}/outputs/{slug}-{runId}/`
- Job workspace: `data/jobs/{jobId}/workspace`
- Job outputs: `data/jobs/{jobId}/outputs/{slug}-{runId}/`
- Project workspace: `data/projects/{projectId}/workspace`
- Task temp: `data/runtime/task-runs/{runId}`
- Workflow temp: `data/runtime/workflow-runs/{runId}`

Task temp directories are deleted after terminal completion. Workflow temp directories persist between workflow steps, then are deleted after terminal workflow completion. Outputs that matter to users must be copied or written into the output directory before completion is accepted.

## Docker Runtime

- Add `com.github.docker-java:docker-java:3.7.1`.
- Add Java-managed Docker lifecycle:
  - verify Docker daemon availability at startup;
  - verify or build the configured agent image;
  - create one container per running agent assignment or reusable stopped container per agent only if lifecycle is explicit;
  - mount agent home always;
  - mount task/workflow temp workspace;
  - mount job/project workspace only through a workspace lease;
  - mount output directory writable.
- Mandatory behavior: task/workflow/job execution fails fast if Docker is unavailable.
- Test image should include Python and common shell tools. Prompt the agent to use virtual environments for project-specific Python dependencies.

## Workflow Model

- `WorkflowDefinition` owns ordered `WorkflowNode` records.
- Node types:
  - `TASK`: runs a finalized plan/task.
  - `USER_APPROVAL`: sends user inbox approval and waits.
  - `AGENT_APPROVAL`: sends agent inbox approval and waits.
  - `USER_MESSAGE`: sends a user inbox message.
  - `AGENT_MESSAGE`: sends an agent inbox message.
  - `DELEGATION`: starts child tasks/workflows and gathers outputs.
  - `REPORT`: writes a structured report/message output.
- Node output bindings read from prior node outputs, not assistant prose.
- Gate nodes persist `WAITING` status and checkpoint fields: `workflowRunId`, `nodeIndex`, `waitingMessageId`, `resumePolicy`, `nextNodeIndex`.

## Jobs, Projects, and Networks

- Jobs are large units of work that own persistent workspaces and orchestrate tasks/workflows.
- Projects are thin top-level tracking and data-space wrappers with one owner agent.
- Agents can belong to multiple projects, but agents in one project must share a project network.
- Network membership enables inbox messaging between agents assigned to the same project.
- Project agents and job management agents use management prompt profiles; they should schedule, inspect, and coordinate work, not bypass task/workflow completion rules.

## API Targets

- Unified plan/task:
  - `GET /api/plans`
  - `POST /api/plans`
  - `GET /api/plans/{planId}`
  - `PUT /api/plans/{planId}`
  - `DELETE /api/plans/{planId}`
  - `POST /api/plans/{planId}/finalize-task`
  - `POST /api/plans/{planId}/runs/stream`
  - `GET /api/plans/{planId}/runs`
  - `GET /api/plan-runs/{runId}`
- Workflow:
  - `GET/POST/PUT/DELETE /api/workflows`
  - `POST /api/workflows/{workflowId}/runs/stream`
  - `POST /api/workflow-runs/{runId}/resume`
- Inboxes and approvals:
  - `GET /api/users/inbox`
  - `POST /api/users/inbox/{messageId}/respond`
  - `GET /api/agents/{agentId}/inbox`
  - `POST /api/agents/{agentId}/inbox/{messageId}/respond`
- Jobs/projects:
  - `GET/POST/PUT/DELETE /api/jobs`
  - `POST /api/jobs/{jobId}/runs`
  - `GET /api/jobs/{jobId}/outputs`
  - `GET/POST/PUT/DELETE /api/projects`
  - `POST /api/projects/{projectId}/agents`
  - `GET /api/projects/{projectId}/network`

## Gotchas

- Do not remove `/chat` behavior while refactoring orchestration.
- Preserve non-blocking SSE: return `SseEmitter` immediately and subscribe work asynchronously.
- Keep controllers thin. Validation and persistence belong in services/repositories.
- Use structured JSON values for field values. Do not parse final assistant text for outputs.
- Keep workspace paths confined under `dataRoot`.
- Lease extension must be guarded by current owner and running status.
- Approval/wait nodes must save `WAITING`, not fail the assignment.
- Browser fixtures must use actual enum wire values.

