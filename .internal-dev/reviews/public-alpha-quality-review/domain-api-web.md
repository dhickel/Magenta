# Domain API/Web Review

## Agent

- Agent: domain-api-web
- Agent id: `019e371c-6209-7051-940e-08a583a1f81e`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed `src/main/java/io/mindspice/magenta2/api/web/**` plus static JS/CSS resources that public web routes load.

## Files and Routes Reviewed

- Controllers: `FrontendController`, `OrchestrationController`, `ChatController`, `PlanController`, `WorkflowController`, `TaskController`, `JobController`, `ProjectController`, `WorkspaceController`, `AgentProfileController`, `AgentOrchestrationController`, `OutputController`, `RuntimeController`.
- Static resources: `src/main/resources/static/js/**`, `src/main/resources/static/css/**`.
- Routes: public pages, `/api/chat/**`, `/api/plans/**`, `/api/tasks/**`, `/api/workflows/**`, `/api/jobs/**`, `/api/projects/**`, `/api/workspaces/**`, `/api/agents/**`, `/api/outputs/**`.

## Commands and Probes

- `find .. -name AGENTS.md`
- Targeted `sed`/`nl` reads of AGENTS, controller, JS, and CSS files
- `rg` route inventory
- `rg` JS/fetch/HTMX cross-checks
- `rg` auth/CSRF/security scan
- `rg` stale Docker references

## Findings

- Critical: unauthenticated public mutation plus shell-exec surface is alpha-blocking if the portal is exposed beyond trusted localhost. Evidence includes missing Spring Security/auth configuration plus public shell execution, hard delete, profile/job/project/workspace mutations.
- High: `/workflows` persists and renders unescaped workflow/node data through raw `innerHTML`, creating stored XSS risk.
- High: workflow run API drops orchestration context. `WorkflowRunRequest` carries agent/job/workspace/model/priority fields, but streaming direct-run ignores them.
- Medium: `/workflows` server-rendered HTMX editor is overwritten by a JS-only composer.
- Medium: agent detail Delete/Archive targets missing `#agent-docker-status-{agentId}` element.
- Low: agent detail event log uses static placeholder events.
- Low: stale Docker naming remains in public UI/resource internals.

## Explicitly Ruled Out

- No active HTMX WebJar shadow route was found.
- Output downloads perform path-confinement checks before reads.
- Chat title/favorite/archive client routes match controller mappings.
- `magenta-tools.js` references stale workflow routes, but no current page import was found in this pass.
