# Phase 05: Backend/UI Parity Audit — Evidence

## Summary

Comprehensive audit of 22 controller files and 10 key service files across all 7 domains. Result: ~249 total endpoints, ~46% fully exposed in UI, ~24% partially exposed, ~15% missing, ~15% dual-path (REST+HTML).

## Capability Matrix

### Docker/Runtime Controls
| Capability | Entry Point | UI | Classification |
|---|---|---|---|
| Docker daemon health | `RuntimeController.GET /api/runtime/docker/status` | Agent dashboard via docker-status fragment | partial |
| Start/stop/restart/refresh agent container | `OrchestrationController.POST /agents/_docker/{id}/start\|stop\|restart` | Agent list + dashboard buttons | fully |
| Enable/disable agent | `OrchestrationController.POST /agents/_lifecycle/{id}/enable\|disable` | Agent list + dashboard buttons | fully |
| Archive/delete agent | `OrchestrationController.POST /agents/_lifecycle/{id}/archive-and-disable\|hard-delete` | Delete confirmation panel | fully |
| Container exec | `AgentContainerRuntimeService.execInAgent()` | No UI | **missing** |
| Container idle TTL / reconciliation | `AgentContainerRuntimeService` scheduled tasks | No UI | **missing** (internal) |

### Agents and Profiles
- Agent list, detail, all 10 tabs, profile editor, submit work: fully exposed via HTMX
- Assignment cancel/pause/resume: **missing** from UI (REST endpoints exist, no controls)
- Agent chat: JS-backed SSE (justified)

### Plans/Tasks
- Plan CRUD, editor, field management: fully exposed via HTMX
- Plan runs listing, streaming execution: **missing** from UI
- Task CRUD (`/api/tasks`): **missing** from UI (plans page covers partially)

### Workflows, Gates, Inbox
- Workflow CRUD, node/route editor, validation: fully exposed via HTMX
- Workflow run monitoring (start, stream, list, resume): **missing** from UI
- Inbox (user + agent): fully exposed via HTMX

### Jobs, Projects
- Jobs/Projects CRUD, editor, detail tabs: fully exposed via HTMX
- Job run start/cancel, recurrence: **missing** from UI
- Project network, workspace: **missing** from UI

### Workspaces, Links, Leases, Outputs
- Workspace info, links: partially exposed (agent workspace tab)
- Output listing, content view, download: fully exposed
- Lease management (acquire, extend, release): **missing** from UI

### Model Overrides and Chat
- Settings page: HTMX + JS mixed (dual save paths)
- Chat: JS-required (SSE streaming, 1468 lines)
- Agent chat: JS-required (SSE streaming, 97 lines)
- No REST endpoint for available models list: **missing**

## JavaScript Usage Review
- Pure HTMX: All CRUD, tabs, filters, forms, Docker controls — no JS needed ✓
- JS-required: Chat SSE (1468 lines), Agent chat SSE (97 lines), Output viewer (115 lines) — justified
- Mixed/questionable: Inbox polling (192 lines) — could use HTMX polling; Settings (dashboard.js) — dual save paths

## Key Gaps (17 total)
See issue ledger in Phase 08 for prioritized list.

## Assessment
**PASS WITH GAPS** — The UI covers all major operational surfaces. The 17 identified gaps are all feature-level (not architecture-level). The most impactful are: no workflow run monitoring UI, no plan/task run history UI, and no assignment cancel/pause/resume controls.
