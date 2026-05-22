# Frontend And UX Architecture Review

Date: 2026-05-21

## Scope

Read-oriented review of Magenta's operational UI for the services/UX architecture refactor. I inspected:

- Web UI routes and HTMX fragments in `OrchestrationController`.
- Selector infrastructure under `api/web/selector`.
- Static JS/CSS for orchestration pages.
- Operational controller tests and end-user/technical docs.
- Data records where needed to compare visible UI against available project/job/workspace/output metadata.

No production source, tests, docs, screenshots, or shared notes were modified. This artifact is the only file written.

## Findings

1. **Agent submit flow cannot attach project or workspace context.**
   - Evidence: the agent detail tab exposes the primary agent-side work submission surface through `/agents/_detail/{agentId}/submit` and `/agents/_submit-form/{agentId}` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:6333`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:6915`).
   - The form only asks for assignment type, target id, priority, and model override (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:6926`).
   - Submission creates `AssignmentRequest` with `projectId = null` and `workspaceId = null` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:6974`).
   - This blocks the target architecture's most direct user mental model: "send this task/workflow/job to this agent under this project workspace." Plan and workflow editor submit panels have project/workspace selectors, but the agent page does not.

2. **Job submit/start surfaces hide effective project/workspace context and cannot override it at run time.**
   - Evidence: the job submit panel only exposes agent, model override, and priority (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3690`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3701`).
   - `submitJob` silently copies `job.projectId()` and `job.workspaceId()` into the assignment (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3729`).
   - `Start Run` silently uses the job owner or first active agent and copies job project/workspace fields (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4029`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4046`).
   - The public job API already supports run-time `projectId` and `workspaceId` overrides (`src/main/java/io/mindspice/magenta2/api/web/JobController.java:149`, `src/main/java/io/mindspice/magenta2/api/web/JobController.java:154`), so the UI is behind the contract.

3. **Persistent job workspace support exists in data/API shape but is not configurable or visible in the job UI.**
   - Evidence: `JobDefinition` carries `persistentWorkspaceEnabled` (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobDefinition.java:21`).
   - Job create/update handlers read `persistentWorkspaceEnabled` from params (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3478`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3509`).
   - The job editor form renders title, summary, owner agent, project, status, manager type, and model, but no persistent workspace control (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3833`).
   - Existing job advanced metadata shows workspace ID only, not persistent workspace enabled/status/path (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3856`).
   - Job runs have assignment id, effective workspace id, job workspace path, and output directory fields (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobRun.java:26`), but the runs table only shows run, status, created, and action (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4004`).

4. **Project UI still over-emphasizes owner/initial agent and lacks membership editing.**
   - Evidence: project model comments define `ownerAgentId` as nullable legacy compatibility metadata (`src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/Project.java:5`).
   - The project editor labels that field as `Initial Agent` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4467`), workspace/network panels repeat `Initial agent` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4487`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4580`), and dashboard project cards still say `Owner` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:457`).
   - Project selector fallback detail also says `Owner ...` (`src/main/java/io/mindspice/magenta2/api/web/selector/EntityLookupService.java:279`).
   - The project page lists members but exposes no add/remove role controls, despite API support for membership mutation (`src/main/java/io/mindspice/magenta2/api/web/ProjectController.java:85`, `src/main/java/io/mindspice/magenta2/api/web/ProjectController.java:100`).

5. **Project active-job links target a container that does not exist on the project page.**
   - Evidence: `projectJobsFragment` renders job links with `hx-target="#job-editor-container"` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4368`).
   - The project page only renders `#project-editor-container` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4231`).
   - Result: clicking a project job from the project detail likely swaps nothing or fails silently. Since the link `href` is `#`, it also does not navigate to `/jobs/{jobId}`.

6. **Run, assignment, and output tables do not expose enough context to verify effective workspace behavior.**
   - Evidence: plan runs show only run/status/start/completion (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:1969`); workflow runs show run/status/current node/start/action (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:2821`); job runs show run/status/created/action (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4004`).
   - Agent queue/history tables do not show project id or effective workspace id (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:6363`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:6247`).
   - Assignment diagnostics show linked runs but omit project/workspace/output path fields (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:6505`).
   - Users cannot confirm whether a submitted task/workflow/job is agent-scoped or project-scoped without reading raw service state.

7. **Outputs views under-display artifact attribution.**
   - Evidence: `RunOutputArtifact` records agent, job, job assignment, job run, project, workspace, run type, path, and artifact metadata (`src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/RunOutputArtifact.java:9`).
   - The outputs table shows only output/type/run/plan/created/action (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4817`).
   - The content panel shows type/file/created only (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4858`).
   - Project/job/agent output panels similarly show compact names and run ids only (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:4419`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:3798`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:6157`).
   - This makes outputs discoverable by filters but not self-explanatory once displayed.

8. **Dashboard "Active Work" is job-definition centric and misses assignment-level work.**
   - Evidence: dashboard stats and active work derive from `jobService.listDefinitions()` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:377`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:405`).
   - Active work rows hard-code the type chip as `JOB` (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:420`).
   - `DashboardController` does the same for API summary active work (`src/main/java/io/mindspice/magenta2/api/web/DashboardController.java:44`).
   - Task/workflow/job assignments that are queued/running under agents are not represented as first-class active work, even though assignments are the execution substrate.

9. **Schedules and reactions expose workspace compatibility but no project context.**
   - Evidence: schedule and reaction forms include `workspaceId`, model, priority, assignment type, and input JSON, but no `projectId` selector (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:5734`, `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:5795`).
   - Assignment templates only store agentId, jobId, assignmentType, priority, modelOverride, workspaceId, and input (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:5917`).
   - Because schedules require `jobId` regardless of selected assignment type (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:5855`), the form can imply broader assignment routing than it actually supports.

10. **Selector infrastructure is reusable, but current wrapper usage loses context.**
    - Evidence: `EntityLookupService.jobs` can filter by `agentId`, `projectId`, and status context (`src/main/java/io/mindspice/magenta2/api/web/selector/EntityLookupService.java:144`).
    - The controller helper always creates selectors with `Map.of()` context (`src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:7201`).
    - The selected-option HTMX URL includes only name/value/required (`src/main/java/io/mindspice/magenta2/api/web/selector/EntitySelectorComponents.java:75`).
    - This is a gotcha for project-aware job pickers: the backend can filter by context, but the rendered controls currently do not preserve or supply that context.

## Risk Assessment

- **Architecture clarity risk: high.** Users can create/select projects and project-scoped submissions in some places, but the agent and job routes, run tables, and outputs do not make effective workspace resolution observable.
- **Operational routing risk: high.** Start/submit actions can silently fall back to an owner/first active agent or stored job project. That is easy to miss during cross-agent/project work.
- **Job persistence risk: high.** A backend-supported job workspace flag is not exposed. Users cannot intentionally enable the job workspace behavior the architecture depends on.
- **HTMX integration risk: medium.** Most CRUD is HTMX-first and aligned with SimplyPages policy, but the project job link targets a missing container and selector context is not threaded through reusable helpers.
- **Copy/navigation risk: medium.** "Owner" and "Initial Agent" copy can make projects feel agent-owned rather than shared workspace contexts.
- **Validation gap: medium.** Existing controller tests assert HTMX surfaces and some current copy, but there are no browser-level checks for project-scoped submission, project job navigation, output provenance, or persistent job workspace controls.

## Recommendations

1. **Phase 1: Align submission controls.**
   - Add project selector and compatibility workspace selector to the agent submit tab.
   - Add visible effective-context summary to plan, workflow, job, and agent submit result fragments.
   - For job submit/start, show the job's current project/workspace and allow explicit override where API support already exists.

2. **Phase 2: Fix job workspace UX.**
   - Add a persistent workspace toggle to the job editor.
   - Show persistent workspace enabled/disabled, effective workspace id, job assignment id, job workspace path, and output directory in job run panels.
   - In assignment-created panels, show whether the job will use project workspace, agent workspace, and/or a persistent job workspace.

3. **Phase 3: Reframe projects as shared workspace contexts.**
   - Rename visible project labels from "Owner"/"Initial Agent" toward "Legacy initial agent" or move the field into Advanced compatibility metadata.
   - Add project membership add/remove role controls using the existing project membership API.
   - Fix project active job links to navigate to `/jobs/{jobId}` or load a dedicated project-local job summary into an existing target.

4. **Phase 4: Make run/output provenance discoverable.**
   - Add project, agent, job, assignment, workspace, output path, and run type columns/details where space allows.
   - Add "View outputs" affordances from plan/workflow/job run tables and assignment diagnostics.
   - Keep chat files separate in copy and navigation so output artifacts are not conflated with conversation files.

5. **Phase 5: Improve selector context and copy.**
   - Extend `entitySelector` helper to accept context params.
   - Use context-aware job selectors on project/agent-specific surfaces.
   - Avoid type-agnostic target search on agent submit when assignment type is selected, or dynamically scope validation/options by selected type.

## Follow-ups

- Playwright validation needed after implementation:
  - Create an ownerless project, verify project page copy treats it as a shared workspace, add/remove members if implemented, and verify workspace summary renders.
  - Submit a plan from `/plans` with a project selected; verify result shows agent, project, effective workspace, assignment id, and queue visibility.
  - Submit a workflow with project context; verify validation errors, submit result context, queue row context, and run/output provenance.
  - From an agent detail submit tab, submit a task/workflow/job with project context; verify assignment request is project-scoped and UI shows the context.
  - Create a job with persistent workspace enabled, submit it under a project, and verify job run panels show job assignment id, effective workspace, persistent job workspace path/status, outputs path, and cancellation behavior.
  - Open a project's active jobs section and verify job navigation/HTMX target works.
  - Browse `/outputs` filtered by project, job, agent, and run; verify rows and content panels expose provenance and downloads still work.
  - Check mobile/desktop layouts for the added selectors and provenance columns so form rows do not overflow.
- Controller/unit tests to add or update:
  - Agent submit form renders `projectId`/`workspaceId` and creates `AssignmentRequest` with those fields.
  - Job submit/start fragments expose and pass project/workspace overrides.
  - Job editor renders and persists `persistentWorkspaceEnabled`.
  - Project jobs fragment targets an existing container or navigates to `/jobs/{jobId}`.
  - Output fragments display project/job/agent/workspace attribution.
