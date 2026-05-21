# Backend Services Review

## Scope

Read-oriented review of backend service/API behavior for projects, jobs, assignments, task/plan runs, workflow runs, output artifacts, and workspaces against the services/frontend/UX architecture target:

- Agents execute work.
- Projects are shared durable workspace and visibility abstractions.
- Tasks/plans and workflows are bounded executable work units.
- Jobs are assignable orchestration/work-unit hybrids with optional per-assignment persistent job workspaces.
- Effective durable workspace is project workspace when `projectId` is present, otherwise agent workspace.

No production source, tests, docs, or shared notes were modified.

## Findings

1. Assignment project context is not first-class durable state.
   `AssignmentRequest` accepts `projectId`, but `WorkAssignment` only persists `workspaceId` plus JSON input/checkpoint/output fields. `AssignmentService.create` merges `projectId` into `input_json` instead of persisting a dedicated assignment column, and `OrchestrationRunnerService.resolveProjectId` later recovers it from `assignment.input()` or job definition fallback.

   Evidence:
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentRequest.java:15`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/WorkAssignment.java:15`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java:84`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java:542`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java:135`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:316`

   Impact: project-scoped UX queries, filtering, diagnostics, and lease attribution must parse opaque JSON or infer from job definitions. This is fragile for displaying "project work", assigning jobs/tasks/workflows to projects, and distinguishing project-attached work from legacy workspace metadata.

2. Project service still exposes legacy ownership as a primary read surface.
   `ProjectService.createProject` accepts `ownerAgentId`, stores it as compatibility metadata, auto-adds that agent as role `owner`, and `workspaceSummary` returns `ownerAgentId`. `ProjectRepository.findByOwnerAgent` and `ProjectService.listProjectsByOwner` still expose owner-based project listing.

   Evidence:
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:63`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:67`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:82`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:222`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java:264`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectRepository.java:46`

   Impact: the API can still lead UX toward "projects owned by agents" instead of "projects visible to/member agents". Membership exists, but service/read-model naming still makes the legacy owner path easy to misuse.

3. Job assignment identity exists at run time but is not queryable as an assignment concept.
   `JobRun` stores `jobAssignmentId`, `workspaceId`, `workspacePath`, and `outputDir`; `JobService.startRun` uses the `jobAssignmentId` or `runId` as the persistent workspace key. However, job definitions still use `ownerAgentId`, and there is no service method/read model for "job assignments" independent of raw `WorkAssignment` rows.

   Evidence:
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:77`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:179`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:183`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:193`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:221`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRepository.java:157`
   - `src/main/java/io/mindspice/magenta2/api/web/JobController.java:149`

   Impact: persistent job workspace flags are technically usable, but UX/service integration cannot cleanly show "this job assignment for agent X/project Y owns workspace Z" without joining job runs, work assignments, output artifacts, and checkpoint JSON.

4. Direct job recurrence bypasses assignment execution semantics.
   `JobService.fireDueRecurrences` calls `startRun(rec.jobId())`, which allocates a job run directly using `def.ownerAgentId()` or `"system"` and does not create a `WorkAssignment`, acquire a project lease, or route through an executing agent. A separate `ScheduleService` path does create assignments, but this recurrence API remains live in `JobService`.

   Evidence:
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:175`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:184`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java:382`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java:152`

   Impact: the architecture says jobs cannot execute outside an agent runtime path. This service path can create job runs without assignment identity, lease boundaries, assignment status, or agent-visible queue/history.

5. Workflow runs do not persist effective workspace/project/agent attribution on the run record.
   `WorkflowRunner` resolves output paths through `EffectiveWorkspaceResolver`, but `WorkflowRun` persistence stores filesystem `workspacePath`/`outputDir` only. Output artifact context is sourced from thread-local `OrchestrationTaskContext`, whose `workspaceId` is the assignment's legacy `workspaceId`, not the resolved effective workspace id. Backfill later uses the same assignment workspace id.

   Evidence:
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java:112`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java:702`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java:766`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:258`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:793`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java:480`

   Impact: workflow output files land under the correct effective root, but service metadata can be incomplete or misleading for project/workspace-filtered output panels, workflow run detail pages, and later handoff behavior.

6. Project workspace leases are acquired for any project-scoped assignment, even read-only or wait-heavy work, and release is unconditional at method exit.
   `OrchestrationRunnerService.executeWithLease` acquires a writable project workspace lease before switching on assignment type and releases it in `finally`. A workflow that enters `WAITING` returns while releasing the project lease; resume reacquires later.

   Evidence:
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:220`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:235`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:271`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:287`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java:291`

   Impact: this is safe for coarse write exclusion, but it may unnecessarily block independent read/status work and it does not model waiting workflow ownership over pending workspace state. Future UX may need "blocked by lease" states that distinguish mutating work from read-only inspection.

7. Output querying has compatibility fallbacks that can hide attribution gaps.
   `OutputController.query` first queries direct artifact attribution. If no direct results exist and no `runId` filter is present, it falls back to job definitions/runs and fetches artifacts by run id. This makes job output pages work even when artifacts are missing `jobId`, `projectId`, or `workspaceId`.

   Evidence:
   - `src/main/java/io/mindspice/magenta2/api/web/OutputController.java:40`
   - `src/main/java/io/mindspice/magenta2/api/web/OutputController.java:57`
   - `src/main/java/io/mindspice/magenta2/api/web/OutputController.java:61`
   - `src/main/java/io/mindspice/magenta2/api/web/OutputController.java:167`

   Impact: UX can appear correct for job pages while global project/agent/workspace output filters remain incomplete. This should be treated as a compatibility bridge, not the primary integration contract.

8. Loose artifact discovery remains enabled by default.
   `OutputArtifactService` defaults `looseArtifactDiscoveryEnabled` to `true`, and `PlanService.completeRun` always invokes loose discovery after explicit materialization.

   Evidence:
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java:41`
   - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java:212`
   - `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java:1104`
   - `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java:1954`

   Impact: this diverges from the target "explicit outputs only" behavior. It is confined to the output directory and gated by constructor flag, but default behavior still indexes incidental direct files.

## Risk Assessment

- High: Lack of first-class `project_id` on assignments will make project dashboards, project history, project output filters, and assignment/project joins brittle.
- High: Direct job recurrence can create runs outside assignment/agent execution semantics, undermining queue visibility and lease safety.
- Medium: Workflow run and output metadata can point to correct files but lack reliable effective workspace attribution.
- Medium: Legacy owner fields on projects/jobs can leak into UX language and filtering.
- Medium: Coarse project writable leases are conservative but can block non-conflicting work and need clearer service boundaries before multi-agent project use.
- Low/Medium: Loose artifact discovery is bounded and confined, but it conflicts with explicit-output semantics and can mask missing publication calls.

## Recommendations

1. Make assignment context first-class.
   Add `project_id` and effective `workspace_id`/`workspace_owner_type` semantics to work assignments, keep JSON input as compatibility data, and add repository/service queries by project, job, assignment type, and status.

2. Introduce job assignment read models.
   Keep `WorkAssignment` as the execution queue row, but expose service methods that return job assignment summaries with job definition, assignment id, agent id, project id, effective workspace id, persistent job workspace path/id, latest job run, status, and output counts.

3. Route all job recurrence through assignments.
   Deprecate or narrow `JobService.fireDueRecurrences` so recurring jobs use `ScheduleService`/assignment creation and always execute through an agent with queue/history/lease behavior.

4. Persist effective context on workflow runs and artifact contexts.
   Add workflow run attribution or a companion read model for agent/project/effective workspace/job assignment. Ensure `WorkflowRunner.outputContext()` uses the resolved effective workspace id, not only `OrchestrationTaskContext.workspaceId`.

5. Clarify owner compatibility in service/API naming.
   Keep nullable `ownerAgentId` for migration, but prefer membership-based project APIs and "default/assigned agent" wording for jobs. Avoid using owner as the primary project visibility axis.

6. Split lease boundaries by operation intent.
   Keep current coarse write lease for first implementation safety, but introduce service-level lease intent hooks so read-only displays, waiting states, and future subtree locking can evolve without changing every runner path.

7. Turn output fallback behavior into explicit compatibility mode.
   Make direct attribution the primary output query contract and test that project/job/agent filters work without route-level run-id fallback. Decide separately whether loose discovery remains default, opt-in, or test-only.

## Follow-ups

- Add service/repository tests for project-scoped assignments, assignment queries by project, and output filtering by project/job/agent/workspace.
- Add tests for multiple assignments of the same persistent-workspace-enabled job producing distinct `jobs/<assignmentId>/` paths.
- Add tests proving direct job recurrence does not bypass assignment execution, or remove that path from operational use.
- Add workflow run tests for project-scoped execution metadata and final output artifact attribution.
- Add lease conflict tests at the runner/service boundary: project-scoped task waits on conflicting lease, resumes after release, and does not lose assignment context.
- Add an output compatibility test covering loose artifact discovery so implementation can safely switch it off or gate it.
