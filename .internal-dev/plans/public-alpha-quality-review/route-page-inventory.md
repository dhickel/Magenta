# Route and Page Inventory

## Agent

- Agent: main Codex campaign coordinator
- Model / reasoning: current parent Codex session
- Scope: public pages, HTMX fragments, REST and SSE controller route groups
- Command: `rg -n "@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)" src/main/java/io/mindspice/magenta2/api src/main/java/io/mindspice/magenta2/ai -g '*.java'`

## Public Pages Required by Campaign

- `/`
- `/chat`
- `/dashboard`
- `/plans`
- `/workflows`
- `/jobs`
- `/projects`
- `/projects/{projectId}`
- `/inbox`
- `/outputs`
- `/agents`
- `/agents/{agentId}`
- `/settings`

## Public Fragment Groups

- Dashboard: `/dashboard/_stats`, `_active-work`, `_open-projects`, `_agents`, `_side-inbox`, `_side-outputs`, `_side-events`.
- Plans: `/plans/_list`, `_editor/_new`, `_editor/_draft`, `_editor/{planId}`, editor collection routes for inputs/outputs/deliverables/steps/validation/assumptions/evidence/feedback/questions, submit and runs fragments.
- Workflows: `/workflows/_list`, `_editor/_new`, `_editor/_draft`, `_editor/{workflowId}`, node/route CRUD fragments, validation, submit, runs, resume.
- Jobs: `/jobs/_list`, `/jobs/{jobId}`, editor/list/detail/runs/outputs/events/recurrence fragments.
- Projects: `/projects/_list`, editor/detail/jobs/agents/outputs/network/workspace release fragments.
- Inbox: `/inbox/_agent-selector`, `_user`, `_agent`, approve/reject/read/handled actions.
- Outputs: `/outputs/_list`, `/outputs/_content/{artifactId}`.
- Agents: `/agents/_list`, `/agents/_create`, detail dashboard/queue/profile/diagnostics/transcript/inbox/jobs/schedules/reactions/workspace/outputs/exec/history/submit, lifecycle and editor routes.
- Settings: `/settings`, `PUT /settings`.

## REST and SSE API Groups

- Chat: `/api/chat`, `/api/chat/stream`, `/api/chat/{conversationId}/plan/execute/stream`, session/history/plan actions.
- Plans: `/api/plans`, `/api/plans/{planId}/submit`, `/api/plans/{planId}/runs/stream`.
- Tasks: `/api/tasks`, draft routes, runs, `/api/tasks/{taskId}/runs/stream`.
- Workflows: `/api/workflows`, validation, runs, `/api/workflows/{workflowId}/runs/stream`, `/api/workflow-runs/{runId}/resume`, `/api/users/inbox`.
- Agents: `/api/agents`, `/api/agents/{agentId}/workspace`, `/api/agents/{agentId}/assignments`, schedules, event reactions, side-panel chat stream.
- Jobs/projects/workspaces/outputs/settings/runtime: `/api/jobs`, `/api/projects`, `/api/workspaces`, `/api/outputs`, `/api/settings/runtime`, `/api/runtime/status`.

## Static Frontend Assets

- CSS: `src/main/resources/static/css/magenta.css`, `src/main/resources/static/css/orchestration.css`.
- Chat JS: `src/main/resources/static/js/chat-client.js`, `src/main/resources/static/js/magenta-tools.js`.
- Orchestration JS modules: `dashboard.js`, `plans.js`, `workflows.js`, `jobs` behavior in shared modules if present, `projects.js`, `inbox.js`, `outputs.js`, `agents.js`, `agent-chat.js`, `api.js`, `dom.js`.
