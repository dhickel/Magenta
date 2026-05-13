# Phase 01 - Contract Repair And Data Model Normalization

## Context

Current UI modules are wired against inconsistent or missing backend contracts. This phase makes the API and data model stable before any major UI redesign.

## Goal

Make dashboard, plans, workflows, jobs, projects, agents, inbox, and outputs consume canonical contracts. After this phase, every visible UI control must call an existing endpoint with payload fields that match the backing records and service validation.

## In Scope

- Normalize project DTOs and add project workspace endpoint.
- Converge job APIs and job models.
- Add dashboard summary API.
- Add output search/list API.
- Repair inbox response contracts.
- Preserve `/chat` and existing task/plan execution API compatibility.
- Add tests proving UI/API contract alignment.

## Out of Scope

- New visual dashboard layout.
- Advanced workflow graph model.
- Autonomous job/workflow creation by chat.
- Direct code removal of legacy `/api/tasks` unless it is a pure compatibility wrapper change.

## Implementation Steps

1. Create a contract inventory test before changing behavior.
   - Add a focused controller contract test class under `src/test/java/io/mindspice/magenta2/api/web`.
   - Assert these pages render their expected `data-orchestration-page` values: `/dashboard`, `/plans`, `/workflows`, `/jobs`, `/projects`, `/agents`, `/inbox`, `/outputs`, `/settings`.
   - Add API smoke tests for endpoints the current UI calls today (existing JS and HTMX interactions). Mark current missing endpoints as expected failures in the plan notes, not ignored tests.

2. Normalize project public fields.
   - Preferred canonical fields: `id`, `name`, `description`, `ownerAgentId`, `gitRepoUrl`, `promptProfile` or `workTypeProfile`, `model`, `settingsOverrideJson`, timestamps.
   - Update project UI interactions to use `name` and `description`, not `title` and `summary`. Prefer HTMX form posts/puts for create/update flows; keep JS only for behavior that is clearly awkward in HTMX.
   - In `ProjectController`, accept a temporary compatibility alias for `title` and `summary` only on request records if existing clients need it.
   - Add `GET /api/projects/{projectId}/workspace` returning the project workspace metadata. Keep filesystem path exposure minimal; return workspace id, owner, root kind, display path, and link count where possible.
   - Add project controller tests for create/update/detail with an owner agent.

3. Converge job APIs.
   - Choose `JobDefinition` as the public "job definition" model only if it gains owner agent, project id, workspace id, status, and item endpoints. Otherwise restore a controller for `OrchestrationJob` and make `JobDefinition` private.
   - Recommended target:
     - `GET /api/jobs?agentId=&projectId=&status=` returns summaries.
     - `POST /api/jobs` can create a draft job with no items, because manual creation needs an empty starting point.
     - `GET /api/jobs/{jobId}` returns the full editable definition.
     - `PUT /api/jobs/{jobId}` updates definition metadata.
     - `GET /api/jobs/{jobId}/items` returns ordered items.
     - `POST /api/jobs/{jobId}/items` adds an item.
     - `PUT /api/jobs/{jobId}/items/{itemId}` edits an item.
     - `DELETE /api/jobs/{jobId}/items/{itemId}` removes an item.
     - `GET /api/jobs/{jobId}/events` returns job events.
     - `GET /api/jobs/{jobId}/outputs` returns an array of output artifacts, not a single map.
   - Update job editing interactions to use `PUT /api/jobs/{jobId}` for save, not `POST /api/jobs`. Implement via HTMX requests by default.
   - Update cancel semantics: cancel run uses `/api/job-runs/{runId}/cancel`; delete job uses `DELETE /api/jobs/{jobId}`. Do not label delete as cancel.

4. Add dashboard summary API.
   - Add a thin controller endpoint such as `GET /api/dashboard/summary`.
   - Service response should be a record, not `Map<String,Object>`.
   - Suggested response:

```java
public record DashboardSummary(
    List<ProjectSummary> openProjects,
    List<WorkSummary> activeWork,
    List<AgentSummary> agents,
    InboxSummary userInbox,
    List<OutputSummary> recentOutputs,
    SystemStats stats,
    Instant generatedAt
) {}
```

   - `activeWork` should include active tasks, workflow runs, job runs, and assignments if those remain separate.
   - `stats` should include counts for running/pending jobs, workflows, assignments, waiting approvals, failed items, and agents by status.

5. Add output artifact query API.
   - Introduce `GET /api/outputs?agentId=&jobId=&projectId=&runId=&type=&limit=`.
   - Back it with `OutputArtifactService`/workspace artifact metadata where possible.
   - Make `/api/jobs/{jobId}/outputs` delegate to the same service and return the same item shape.
   - Replace ad hoc output discovery with HTMX-driven query/refresh on `/outputs`. Use JS only for least-resistance client affordances (for example debounce or local formatting) if needed.

6. Repair inbox response contracts.
   - Decide whether agent approvals are supported now.
   - If yes, add `POST /api/agents/{agentId}/inbox/{messageId}/respond`.
   - If no, remove approve/reject buttons from agent inbox rows and render "read/handled" actions only.
   - Ensure response payload includes `messageId`, `approved`, `workflowRunId` where applicable, and updated run status if a workflow was resumed.

7. Normalize plan/task terminology in public docs and labels.
   - Keep `TaskService` as compatibility facade if needed.
   - In controllers and UI, prefer:
     - `Plan` for session planning artifact.
     - `Task Template` for reusable executable definition.
     - `Workflow` for routed composition.
     - `Job` for agent-owned orchestration package.
   - Avoid showing raw enum fields where a user choice should be constrained by the current screen.

8. Add contract-level validation.
   - Controller tests for all new/changed endpoints.
   - Browser fixture tests or Playwright probes that call the same endpoints pages call, including HTMX-driven interactions.
   - Negative tests:
     - project create without owner agent returns clear 400;
     - job item with no plan/workflow id returns clear 400;
     - output query with unknown id returns empty list, not 500;
     - agent inbox unsupported response action is not rendered.

## Validation

- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Browser smoke:
  - `/projects` create form sends `name`, `description`, and selected owner agent via HTMX.
  - `/projects/{id}` loads workspace without 404.
  - `/jobs` create/save/add item calls existing endpoints via HTMX by default.
  - `/outputs` loads through `/api/outputs` and HTMX refreshes.
  - `/inbox` does not render dead agent approval buttons unless endpoint exists.

## Exit Criteria

- No orchestration page calls an endpoint that does not exist.
- No UI create/update payload uses field names absent from its controller request DTO.
- Job and project pages can create, edit, reload, and display their records.
- Dashboard summary endpoint exists and returns enough data for the Phase 02 dashboard redesign.
