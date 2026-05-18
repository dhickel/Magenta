# Scope

Review-only backend/API/domain contract pass for the completed operational UI contract refactor. Scope was limited to production code and tests in the web API, plan, orchestration runtime, workflow, workspace, and Docker runtime areas. No production code was edited.

Reviewed contract inputs:

- `.internal-dev/AGENTS.md`
- `.internal-dev/plans/operational-ui-contract-refactor/README.md`
- `.internal-dev/plans/operational-ui-contract-refactor/01-contract-repair-and-data-model.md`
- `.internal-dev/plans/operational-ui-contract-refactor/03-plan-editor-and-worktype-profiles.md`
- `.internal-dev/plans/operational-ui-contract-refactor/04-workflow-builder-redesign.md`
- `.internal-dev/plans/operational-ui-contract-refactor/05-jobs-projects-operational-surfaces.md`
- `.internal-dev/plans/operational-ui-contract-refactor/06-agent-dashboard-docker-runtime.md`
- `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md`

Reviewed implementation evidence:

- `src/main/java/io/mindspice/magenta2/api/web/*Controller.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/*`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/*`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/*`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/*`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/*`
- `src/main/resources/schema.sql`
- Focused tests under `src/test/java/io/mindspice/magenta2/api/web`, `src/test/java/io/mindspice/magenta2/ai/orchestration`, and `src/test/java/io/mindspice/magenta2/ai/chat/plan`

# Findings

## 1. Agent dashboard JOB_RUN submission does not bridge canonical jobs to legacy assignment validation

Severity: High

The Phase 05 handoff notes explicitly say public jobs now live in `JobDefinition`/`job_definitions`, while `AssignmentService` still validates `JOB_RUN` assignments through legacy `OrchestrationJobService`/`orchestration_jobs`. The job detail submit path handles that with `ensureLegacyJob(job)` before creating the assignment (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2342-2366`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2384-2405`).

The agent dashboard submit path does not. It accepts `JOB_RUN`, places the target id into both `jobId` and `input.jobId`, then calls `assignmentService.create(...)` directly (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3805-3832`). `AssignmentService.create` validates any nonblank `jobId` by calling the legacy `OrchestrationJobService.get(...)` (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java:44-45`). A canonical `JobDefinition` created by the `/jobs` API or HTMX job editor will therefore fail from the agent dashboard unless it has previously been submitted through the job detail page and created a shadow legacy row.

Test evidence: current `OrchestrationControllerTest` checks that the agent submit form renders `JOB_RUN` (`src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java:487-492`), but there is no test that posts a canonical job id through `/agents/_submit/{agentId}` and verifies the assignment is created.

Impact: Phase 06's "Submit work panel" is broken for normal canonical jobs from the agent dashboard, and beta users will see an error for a first-class operation.

## 2. Workflow graph validation is not enforced on save, and the public REST validation contract is not structured

Severity: High

Phase 04 requires route-aware validation for cycles, required task inputs, route endpoints, route types, and type compatibility. The validator exists and returns `WorkflowValidator.ValidationResult(errors, warnings)` (`src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java:28-45`, `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java:49-76`). The HTMX fragment endpoint uses it (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:1682-1703`).

However, `WorkflowService.saveDefinition(...)` does not call `validator.validate(...)` or reject validation errors. It only checks TASK nodes have a plan id, referenced plans exist, legacy binding source nodes exist, and route source/destination node keys exist (`src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java:63-122`). This allows persisted workflows with cycles, missing required task inputs, null route types, and type mismatches that the validator would flag.

The public REST validation endpoint also does not return the structured result required by Phase 04. `POST /api/workflows/{workflowId}/validate` returns `List<String>` from `compatibilityWarnings(...)` (`src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java:80-86`), and `compatibilityWarnings(...)` flattens structured errors into strings prefixed with `"ERROR: "` (`src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java:241-248`). The structured `validateGraph(...)` method is present but not exposed by the REST controller (`src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java:251-256`).

Impact: clients can save invalid graphs, the REST API cannot reliably distinguish errors from warnings without string parsing, and workflow execution can fail later despite a supposedly contract-correct builder.

## 3. Job item references are only checked for nonblank ids, not existence

Severity: High

Phase 05 requires job items to validate selected plan/workflow references before save. The current `JobService.saveDefinition(...)` only checks that PLAN items have a nonblank `planId` and WORKFLOW items have a nonblank `workflowId` (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:55-71`). `JobService` has no dependency on `PlanService` or `WorkflowService`, so it cannot verify that the referenced definitions exist (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:26-33`).

The contract test unintentionally documents the gap: it successfully adds a PLAN job item with `planId = "plan-1"` without creating any plan definition (`src/test/java/io/mindspice/magenta2/api/web/OperationalUiContractControllerTest.java:79-98`). The negative test only covers the missing-id case (`src/test/java/io/mindspice/magenta2/api/web/OperationalUiContractControllerTest.java:101-114`).

Impact: job definitions can persist references that cannot execute. The UI will appear to save a valid ordered item, but the later assignment/run path will fail when it tries to resolve a missing plan or workflow.

## 4. Project ownership accepts nonexistent agents and persists orphan project memberships

Severity: Medium

The project contract requires owner-agent-backed project creation and Phase 05 says the owner agent select must load real agents. The backend only validates that `ownerAgentId` is nonblank (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:50-57`), then persists the project and auto-adds the same id as an owner membership (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:61-69`). There is no `AgentProfileService` dependency in `ProjectService` (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:24-31`).

The schema also does not enforce an agent foreign key for `projects.owner_agent_id` or `project_agent_memberships.agent_id` (`src/main/resources/schema.sql:509-534`). Current tests create projects with `"agent-1"` without first creating an agent row (`src/test/java/io/mindspice/magenta2/api/web/OperationalUiContractControllerTest.java:43-64`, `src/test/java/io/mindspice/magenta2/api/web/OperationalUiContractControllerTest.java:117-155`).

Impact: dashboards and project detail pages can contain owner/member agent ids that do not resolve through agent APIs, breaking links, assignment defaults, and operational trust in project ownership data.

## 5. Workflow node removal can fail when a route has no source node

Severity: Medium

The workflow route add endpoint explicitly allows a blank `fromNodeKey` and converts it to `null` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:1635-1655`). The node removal endpoint then filters routes with `!r.fromNodeKey().equals(nodeKey)`, which dereferences `fromNodeKey()` without a null check (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:1576-1590`).

Impact: removing a node from a workflow that contains a route with a null source returns an error fragment instead of completing the delete. This is a broken HTMX endpoint in the workflow builder surface.

# Risk Assessment

The backend is close to the intended shape, but the remaining risks are operational rather than cosmetic:

- Canonical job definitions and legacy orchestration jobs still split the system. One submit path bridges the split; another does not.
- Workflow validation exists but is not the persistence gate. Invalid graphs can become durable state.
- Reference integrity for jobs and projects depends on caller discipline instead of service validation.
- Tests heavily cover rendering and happy-path contracts, but several tests use fake ids without creating backing records. That makes contract holes look intentional.

Beta-readiness risk is medium-high until findings 1-3 are fixed. Findings 4-5 are smaller but will create confusing operational failures once users start creating real projects and editing workflows repeatedly.

# Recommendations

1. Move canonical `JOB_RUN` assignment validation to `JobService` or make `AssignmentService` accept `JobDefinition` ids directly. Until that migration is complete, reuse the same bridge behavior in every submit path that can create a `JOB_RUN`.
2. Make workflow validation a service-level persistence gate for blocking errors. Keep warnings nonblocking, but reject cycles, missing required inputs, missing route endpoint names, null route types, and unknown endpoint references before saving.
3. Change `POST /api/workflows/{workflowId}/validate` to return `WorkflowValidator.ValidationResult` or a stable response record with `errors`, `warnings`, and `valid`.
4. Inject reference validators into `JobService` or add narrow validation collaborators so PLAN items verify `PlanService.getTask(planId)` and WORKFLOW items verify `WorkflowService.getDefinition(workflowId)`.
5. Inject `AgentProfileService` into `ProjectService` or validate owner/member ids at the controller/service boundary. Add negative tests for unknown owner id and unknown added member id.
6. Fix route cleanup to be null-safe when removing workflow nodes.

# Follow-ups

- Add controller/service tests for `/agents/_submit/{agentId}` with `JOB_RUN` using a canonical `JobDefinition` id and no preexisting legacy `OrchestrationJob`.
- Add workflow persistence tests proving invalid graphs are rejected on save, not only rendered as validation errors.
- Add REST validation tests asserting structured workflow validation response shape.
- Add job item tests for unknown `planId` and unknown `workflowId`.
- Add project tests for unknown owner agent and unknown membership agent.
- Consider retiring the legacy `orchestration_jobs` validation path before beta rather than continuing to grow bridge code.
