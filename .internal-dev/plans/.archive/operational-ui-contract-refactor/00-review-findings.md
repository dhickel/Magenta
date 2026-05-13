# Current-State Review Findings

## Context

The current checkout has a partially unified plan/task runtime and multiple new orchestration UI/API surfaces. The code has enough backend shape to support a larger operational UI, but several page modules are currently wired against contracts that either do not exist or use different field names than the API.

This review focuses on refactor targets, UI/UX structure, and contract adherence between frontend and backend.

## Goal

Identify the concrete defects and design risks that must shape the refactor plan. Findings are ordered by implementation risk and user-visible impact.

## In Scope

- Dashboard, plans, workflows, jobs, projects, agents, inbox, outputs.
- Plan/task naming and model-facing terminology.
- Manual editor inputs vs backing data structures.
- API contract mismatch between static JS and controllers.
- SimplyPages usage patterns relevant to the redesign.

## Out of Scope

- Runtime security review.
- Performance tuning beyond UI/API contract risks.
- Code edits outside `.internal-dev` artifacts.

## Findings

### P0 - Job UI and backend are split across incompatible job models

Evidence:

- The UI calls `/api/jobs?agentId=...`, `/api/jobs/{jobId}/items`, `/api/jobs/{jobId}/events`, and creates jobs with `{ ownerAgentId, title, summary, status }` in `src/main/resources/static/js/orchestration/dashboard.js:242-319`.
- `JobController` exposes `/api/jobs`, `/api/jobs/{jobId}`, `/api/jobs/{jobId}/runs`, `/api/jobs/{jobId}/outputs`, recurrence endpoints, and no `/items` or `/events` endpoints in `src/main/java/io/mindspice/magenta2/api/web/JobController.java:34-160`.
- `JobService.saveDefinition` requires `JobDefinition.items()` to be non-empty in `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:48-75`, so the UI's empty "New Job" create request cannot satisfy the service contract.
- A second `OrchestrationJobService` model has `ownerAgentId`, `workspaceId`, `items`, and `events` support in `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationJobService.java:38-108`, but the controller exposing those routes is deleted in the current working tree.

Impact:

- Job pages are not implementable as designed until the repository chooses one canonical job API.
- Outputs and project-job linkage are unreliable because the UI is querying filters the active controller ignores.

Required direction:

- Converge `JobDefinition` and `OrchestrationJob` into one public job contract, or make one a private runtime implementation. Do this before redesigning the job UI.

### P0 - Project UI is currently broken by DTO field mismatch and missing workspace endpoint

Evidence:

- Project creation UI sends `{ title, summary }` in `src/main/resources/static/js/orchestration/projects.js:14-20`.
- `ProjectController.CreateProjectRequest` expects `name`, `description`, `ownerAgentId`, and `gitRepoUrl` in `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java:37-46` and `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java:131-136`.
- `ProjectService.createProject` requires a nonblank owner agent ID in `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:50-57`.
- Project detail UI reads and writes `project.title` and `project.summary` in `src/main/resources/static/js/orchestration/projects.js:43-79`, but `Project` exposes `name` and `description` in `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/Project.java:9-20`.
- Project UI calls `/api/projects/{projectId}/workspace` in `src/main/resources/static/js/orchestration/projects.js:131-139`, but `ProjectController` has no workspace route in `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java:32-153`.

Impact:

- Project creation cannot succeed from the UI.
- Project detail displays blank fields even when data exists.
- Workspace overview cannot load.

Required direction:

- Repair project DTOs, add explicit project workspace read endpoint, and make the UI use `name`/`description` or intentionally alias `title`/`summary`.

### P0 - Dashboard is a navigation card grid, not an operational overview

Evidence:

- `/dashboard` renders `dashboard-summary-grid` with eight `summaryCard` links in `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:87-107`.
- No dashboard API aggregates open projects, active tasks/workflows/jobs, agent status, inbox counts, recent outputs, or statistics. `dashboard.js` has no dashboard-specific overview loader beyond generic page dispatch in `src/main/resources/static/js/orchestration/dashboard.js:4-15`.

Impact:

- The dashboard does not answer "what is happening in the system right now".
- Users have to click into separate modules before seeing state, which prevents the dashboard from acting as the operational home.

Required direction:

- Add a dashboard summary API and redesign the first viewport around system state, exceptions, and next actions, with chat at the top reserved for future dashboard-aware tools.

### P0 - Plan and workflow UI expose direct run affordances the target explicitly rejects

Evidence:

- Plan editor includes `Run` button and a run panel in `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:175-190`.
- Plan JS streams `/api/plans/{planId}/runs/stream` and renders raw SSE log text in `src/main/resources/static/js/orchestration/plans.js:209-231`.
- Workflow editor includes `Run` button and run panel in `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:225-239`.
- Workflow JS streams `/api/workflows/{workflowId}/runs/stream` in `src/main/resources/static/js/orchestration/workflows.js:171-190`.

Impact:

- The UI encourages raw execution instead of assigning work to an agent.
- Users see run logs rather than operational assignment state.

Required direction:

- Remove direct run controls from plan/workflow pages. Add "Submit to agent" controls that create `WorkAssignment` records with enough context to continue in an agent chat or queue view.

### P1 - Manual editors flatten structured fields into CSV, JSON, or newline text

Evidence:

- Agent approved tools and shell allowlist are comma-separated text inputs in `src/main/resources/static/js/orchestration/dashboard.js:159-187`.
- Agent assignment input is raw JSON in `src/main/resources/static/js/orchestration/dashboard.js:190-210`.
- Workflow input bindings are a raw JSON textarea in `src/main/resources/static/js/orchestration/workflows.js:33-40` and `src/main/resources/static/js/orchestration/workflows.js:43-56`.
- Plan steps, validation criteria, and assumptions are newline textareas in `src/main/resources/static/js/orchestration/plans.js:19-22` and `src/main/resources/static/js/orchestration/plans.js:140-157`.
- Plan field `description`, `schema`, and `example` are all inline single-line inputs in `src/main/resources/static/js/orchestration/plans.js:31-42`.

Impact:

- Users edit data in formats that do not match the backing structure.
- Validation is delayed until save or hidden by silent parse failures.
- The UI is fragile for long descriptions, schemas, deliverables, validation criteria, tool lists, and workflow bindings.

Required direction:

- Build reusable structured editors: list editor, schema field editor, tool picker, model selector, binding editor, assignment payload editor, and expandable edit panels.

### P1 - Workflow backend remains ordered-list execution and does not model multi-route graph semantics

Evidence:

- `WorkflowDefinition` is "ordered list of workflow nodes" in `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowDefinition.java:8-32`.
- `WorkflowNode` has `inputBindings` on the node, not edge/routing records, in `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowNode.java:19-36`.
- `WorkflowRunner.executeFromCheckpoint` iterates `for (int i = currentNodeIndex; i < nodes.size(); i++)` and executes sequentially in `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java:206-387`.
- Compatibility validation checks bindings against any node key, not prior-only graph reachability, in `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java:69-80` and `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java:101-151`.

Impact:

- The model cannot faithfully express one node with multiple output routes, fan-out to multiple downstream nodes, pass-through/log behavior, or branch visibility.
- A visual or tree-link UI would have to invent semantics the backend cannot execute.

Required direction:

- Introduce explicit workflow routes/edges, a graph validator, and a route-aware runner before building the advanced workflow builder.

### P1 - Plan/task terminology remains model-facing and user-facing ambiguous

Evidence:

- `PlanDefinition` claims unified plan/task definition with `SESSION_PLAN` and `TASK_TEMPLATE` kinds in `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanDefinition.java:8-17`.
- `PlanController` routes `/api/plans` but `list()` returns `planService.listTasks()` in `src/main/java/io/mindspice/magenta2/api/web/PlanController.java:45-48`.
- `PlanService.getTask` throws "Task not found" while operating on `PlanDefinition` in `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java:464-467`.
- `TaskService` is a compatibility facade over `PlanService` in `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java:24-32`.

Impact:

- Developers and model prompts can confuse saved reusable tasks, in-session plans, and execution plans.
- UI labels such as "Plans & Tasks", "Finalize Task", and raw `kind/status` fields force implementation vocabulary onto users.

Required direction:

- Use user-facing terms consistently: "Plan" for in-session planning artifact, "Task Template" for reusable executable unit, "Workflow" for routed task/gate composition, "Job" for agent-owned orchestration package. Keep compatibility facades private and documented.

### P1 - Prompt profile exists as a string but has no visible profile contract

Evidence:

- Plan definitions store `promptProfile` as a string in `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanDefinition.java:34-36`.
- `PromptProfile` enum exists with generic values in `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/PromptProfile.java:8-16`.
- Prompt assembly replaces or appends mode prompts in `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java:58-81`, but no worktype profile instructions are appended.
- PlanService normalizes and persists prompt profile at save time in `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java:469-505`.

Impact:

- Users see an opaque "Prompt Profile" text field.
- Model behavior cannot be reliably steered by coding/data/research worktype defaults.

Required direction:

- Replace UI label and public DTO with `workTypeProfile`, backed by deterministic profiles: `CODING_CENTRIC`, `DATA_CENTRIC`, `RESEARCH_CENTRIC`.

### P2 - Agent pages are shallow and do not expose enough operational state

Evidence:

- Agent detail dashboard only renders inbox and queue counts in `src/main/resources/static/js/orchestration/dashboard.js:213-240`.
- Agent detail tabs render inbox/queue/workspace as JSON `pre` blocks in `src/main/resources/static/js/orchestration/dashboard.js:224-239`.
- Agent profile editor uses one large form with raw prompt/tool fields in `src/main/resources/static/js/orchestration/dashboard.js:159-187`.
- Docker runtime client verifies daemon/image at startup in `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/DockerRuntimeClient.java:69-100`, but there is no web/API status surface for agents.

Impact:

- Agents cannot be monitored closely from their dashboard.
- Editing prompt/tool/runtime shape is error-prone.
- Docker failures are not visible in the place users expect to diagnose agent execution.

Required direction:

- Build an agent operational dashboard with assignment state, inbox, job/workflow links, workspace/output activity, model/tool profile, Docker health, and expandable structured editors.

### P2 - Inbox and outputs are partial surfaces and should be treated as dependencies of dashboard redesign

Evidence:

- Agent inbox response endpoint is left blank in the JS for approve action, so agent inbox approvals cannot work from the current UI in `src/main/resources/static/js/orchestration/inbox.js:96-143`.
- Outputs UI gathers `URLSearchParams` but does not call a unified search endpoint; it tries job outputs, agent outputs, and plan runs ad hoc in `src/main/resources/static/js/orchestration/outputs.js:57-113`.
- `/api/jobs/{jobId}/outputs` returns a single `Map`, but outputs UI expects arrays and maps over `outputs` in `src/main/java/io/mindspice/magenta2/api/web/JobController.java:104-119` and `src/main/resources/static/js/orchestration/dashboard.js:288-292`.

Impact:

- Dashboard "recent outputs" and "user inbox" cannot be robust until inbox/output APIs are made queryable.

Required direction:

- Add explicit summary/search APIs for inbox and output artifacts, then have dashboard, agent, job, and project pages consume those APIs.

## Risk Assessment

Highest risk:

- Building the visual redesign before resolving job/project/workflow contracts will cause churn and hidden broken controls.
- Graph workflow UI requires backend graph semantics. A UI-only rewrite will create definitions that the sequential runner cannot execute correctly.
- Continuing to expose raw JSON/CSV editors will make validation and support worse as models and users produce more complex definitions.

Medium risk:

- Renaming prompt profile to worktype profile can break persisted data or DTO compatibility if not phased.
- Removing direct run controls can frustrate developers unless assignment submission gives clear queue feedback.

Low risk:

- The SimplyPages component model supports the needed dashboard layout, structured forms, side nav, and edit workflows. The main challenge is extracting reusable domain modules rather than using raw inline JS strings everywhere.

## Recommendations

1. Repair API contracts first: jobs, projects, outputs, inbox, and dashboard summary endpoints.
2. Introduce a UI component contract for operational surfaces: summary strip, exception lane, split list/detail, expandable edit sections, schema field editor, list editor, and activity timeline.
3. Redesign dashboard around system state rather than navigation.
4. Redesign plan editor around structured fields and worktype profiles, with "Submit to agent" replacing direct runs.
5. Redesign workflows as a route-aware graph/tree builder backed by explicit routes and validation.
6. Converge job/project models before building their advanced overview pages.
7. Redesign agents as the most detailed operational dashboard, including Docker status.

## Follow-ups

- See phase files in this directory for implementation plans.
- See `.internal-dev/notes/future_features.md` for deferred agent-chat and larger product capabilities discovered during review.

