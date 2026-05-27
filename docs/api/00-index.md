# API Documentation Index

This index owns route, payload, streaming, and integration contracts for Magenta APIs. The detailed contributor reference is [`../technical/api-reference.md`](../technical/api-reference.md).

Security summary: alpha routes are currently open at the application layer. Request safety currently relies on route semantics, controller/service validation, and bounded runtime/tooling safeguards. See [`../technical/security.md`](../technical/security.md).

## API Families

| Family | Purpose | Source |
| --- | --- | --- |
| `/api/chat` | Chat turns, SSE streaming, sessions, history, chat file listing/downloads, commands, interrupts, and anonymous conversation plan controls. | [`ChatController`](../../src/main/java/io/mindspice/magenta2/api/web/ChatController.java), [`ChatFileController`](../../src/main/java/io/mindspice/magenta2/api/web/ChatFileController.java) |
| `/api/fragments` | HTMX chat transcript/session/planning fragments. | [`FrontendFragmentController`](../../src/main/java/io/mindspice/magenta2/api/web/FrontendFragmentController.java) |
| `/api/plans` | Saved plan/task-like definitions, saved plan chat, submit-to-agent, and plan run reads. | [`PlanController`](../../src/main/java/io/mindspice/magenta2/api/web/PlanController.java) |
| `/api/tasks` | Task definitions, chat-backed task drafts, draft approval, and task run reads/submission. | [`TaskController`](../../src/main/java/io/mindspice/magenta2/api/web/TaskController.java) |
| `/api/workflows`, `/api/workflow-runs`, `/api/users/inbox` | Workflow definitions, validation, assignment submission, run reads/resume, and workflow approval inbox. | [`WorkflowController`](../../src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java) |
| `/api/jobs`, `/api/job-runs` | Job definitions, ordered job items, assignment submission, runs, cancellation, outputs, events, and recurrence. | [`JobController`](../../src/main/java/io/mindspice/magenta2/api/web/JobController.java) |
| `/api/agents` | Agent profile CRUD and agent workspace status. | [`AgentProfileController`](../../src/main/java/io/mindspice/magenta2/api/web/AgentProfileController.java) |
| `/api/agents/{agentId}` | Agent inbox, assignments/history/lifecycle, schedules, event reactions, and agent side-panel chat SSE. | [`AgentOrchestrationController`](../../src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java) |
| `/api/projects` | Projects, memberships, network view, events, workspace summary, and workspace release requests. | [`ProjectController`](../../src/main/java/io/mindspice/magenta2/api/web/ProjectController.java) |
| `/api/skills` | Agent Skills metadata, diagnostics, root-confined file operations, and agent assignment add/remove/list. | [`SkillController`](../../src/main/java/io/mindspice/magenta2/api/web/SkillController.java) |
| `/api/workspaces` | Workspace records, active leases, and workspace links. | [`WorkspaceController`](../../src/main/java/io/mindspice/magenta2/api/web/WorkspaceController.java) |
| `/api/work-areas` | Agent/project Work Area metadata plus confined browse/preview, image view, text save policy, create/move/copy/rename/delete modal steps, labels, recent action rows, and mark controls. | [`WorkAreaController`](../../src/main/java/io/mindspice/magenta2/api/web/WorkAreaController.java) |
| `/api/outputs` | Output artifact query, inline content, and confined downloads. | [`OutputController`](../../src/main/java/io/mindspice/magenta2/api/web/OutputController.java) |
| `/avatar` | Server-rendered Avatar shell and HTMX fragments for tabs, dashboard layout editing, compact Avatar chat, outputs, and Work Area explorer flows including icon toolbar navigation, typed tag selector options, and move/copy directory-picker popovers. | [`AvatarDashboardController`](../../src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java), [`Avatar dashboard fragments`](../technical/avatar-dashboard-fragments.md) |
| `/skills` | Server-rendered Agent Skills browser/editor with HTMX list, detail, diagnostics, file editing, guided creation, and assignment fragments. | [`SkillFragments`](../../src/main/java/io/mindspice/magenta2/api/web/SkillFragments.java) |
| `/api/dashboard/summary` | Operational dashboard read model. | [`DashboardController`](../../src/main/java/io/mindspice/magenta2/api/web/DashboardController.java) |
| `/api/runtime/status` | Runtime status summary. | [`RuntimeController`](../../src/main/java/io/mindspice/magenta2/api/web/RuntimeController.java) |
| `/api/settings/runtime` | Persisted runtime settings read/update. | [`RuntimeSettingsController`](../../src/main/java/io/mindspice/magenta2/api/web/RuntimeSettingsController.java) |
| `/api/models` | Configured model summaries. | [`ModelController`](../../src/main/java/io/mindspice/magenta2/api/web/ModelController.java) |
| `/selectors` | Read-only entity selector options/selected/validation fragments used by operational UI. | [`selector`](../../src/main/java/io/mindspice/magenta2/api/web/selector) |

## Streaming Endpoints

| Route | Stream Purpose | Events |
| --- | --- | --- |
| `POST /api/chat/stream` | Live chat turn. | `start`, `chunk`, `tool`, `system`, `interrupt`, `context`, `done`, `error` |
| `POST /api/chat/{conversationId}/plan/execute/stream` | Anonymous session plan execution stream. Accepts optional `clearContext=true`. | Chat/plan execution events from `ChatController` |
| `POST /api/plans/{planId}/runs/stream` | Submit saved plan/task-like definition to an agent assignment. | `submitted`, `failed` |
| `POST /api/tasks/{taskId}/runs/stream` | Submit saved task to an agent assignment. | `submitted`, `failed` |
| `POST /api/workflows/{workflowId}/runs/stream` | Submit saved workflow to an agent assignment. | `submitted`, `failed` |
| `POST /api/agents/{agentId}/chat/stream` | Agent-scoped side-panel chat. | `start`, `done`, `error` |

Anonymous session plan execution reaches `COMPLETED` only after validator-gated `plan_complete` passes. If completion cannot be verified after retries, the final `done` event carries `planState.status=NEEDS_REVIEW` with review evidence/feedback available through normal history and plan state reloads.

The public plan/task/workflow stream routes acknowledge durable assignment submission; they do not stream inline model execution.

## Payload Anchors

- Chat payloads: [`ChatRequest`](../../src/main/java/io/mindspice/magenta2/ai/chat/model/ChatRequest.java), [`ChatResponse`](../../src/main/java/io/mindspice/magenta2/ai/chat/model/ChatResponse.java), [`ChatStreamEvent`](../../src/main/java/io/mindspice/magenta2/ai/chat/model/ChatStreamEvent.java), [`ChatFileListing`](../../src/main/java/io/mindspice/magenta2/ai/chat/model/ChatFileListing.java), [`ChatFileSummary`](../../src/main/java/io/mindspice/magenta2/ai/chat/model/ChatFileSummary.java).
- Plans: [`PlanDefinition`](../../src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanDefinition.java), [`PlanRun`](../../src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanRun.java).
- Tasks: [`TaskDefinition`](../../src/main/java/io/mindspice/magenta2/ai/chat/task/TaskDefinition.java), [`TaskDraft`](../../src/main/java/io/mindspice/magenta2/ai/chat/task/TaskDraft.java), [`TaskRun`](../../src/main/java/io/mindspice/magenta2/ai/chat/task/TaskRun.java).
- Workflows: [`WorkflowDefinition`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowDefinition.java), [`WorkflowRun`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRun.java), [`WorkflowValidator.ValidationResult`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java).
- Runtime assignments/jobs/projects: [`WorkAssignment`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/WorkAssignment.java), [`JobDefinition`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobDefinition.java), [`Project`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/Project.java).
- Job execution summaries: [`JobExecutionSummary`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobExecutionSummary.java).
- Workspaces/outputs: [`Workspace`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/Workspace.java), [`WorkspaceLease`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLease.java), [`WorkArea`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkArea.java), [`RunOutputArtifact`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/RunOutputArtifact.java).

Assignment-returning submit routes expose first-class project/effective workspace context. `projectId` selects project workspace execution; `workspaceId` is compatibility metadata. Submit payloads can also carry `selectedWorkAreaId`, `outputRouteType`, `outputWorkAreaId`, `outputDirectRelativePath`, and `runDisplayName` for non-job task/workflow work; plan-chat routes do not accept these controls. During execution, model-facing `outputs/` resolves to run-local staging, and output query routes expose promoted artifact filters for agent, job, job assignment, job run, project, workspace, plan/workflow id, run id, run type, artifact type, and limit.

## Error Conventions

Controllers generally map:

- Invalid request/body/lifecycle input to `400`.
- Missing records to `404`.
- Invalid lifecycle conflicts to `409`.

HTMX failures typically return fragment-friendly HTML for in-panel rendering; JSON/API failures return JSON or Spring error responses depending on the controller path.
