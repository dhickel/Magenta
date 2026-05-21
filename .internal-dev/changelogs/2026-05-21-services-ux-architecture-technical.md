# Date

2026-05-21

# Change Summary

Technical closeout for the services/frontend/UX architecture refactor. This record captures the implemented service/API/UI contracts that future agents should preserve when extending assignments, projects, jobs, workspaces, outputs, and operational pages.

# Files

Primary implementation areas from phases 01 through 04:

- `ai/orchestration/runtime`: first-class assignment context, assignment summaries, job execution summaries, assignment-routed job recurrence/start behavior, active mutation guards, and workspace-blocked requeue helpers.
- `ai/orchestration/workspaces`: output artifact query/display attribution and collision-safe materialized filenames.
- `ai/orchestration/workflow`: workflow run attribution propagation for output/context views.
- `ai/chat/plan` and `ai/chat/task`: project/effective workspace/job attribution propagation into task output materialization.
- `api/web`: additive API request/response context, output filters, project/job/operator HTMX controls, selector context plumbing, and provenance display.

Closeout documentation files:

- `docs/api/00-index.md`
- `docs/technical/api-reference.md`
- `docs/technical/data-model.md`
- `docs/technical/frontend-htmx.md`
- `docs/technical/orchestration-runtime.md`
- `docs/technical/services.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/end-user/jobs.md`
- `docs/end-user/inbox-outputs-settings.md`
- `.internal-dev/changelogs/2026-05-21-services-ux-architecture-technical.md`
- `.internal-dev/knowledge/services-ux-architecture-rules.md`

# Behavioral Impact

Assignment contract:

- `projectId` is first-class assignment state and selects the effective project workspace.
- `workspaceId` is retained as compatibility metadata only.
- `effectiveWorkspaceId` and `effectiveWorkspaceKind` are persisted for assignment/runs and exposed in operator read models.
- Existing JSON `input.projectId` compatibility remains for older consumers and warm records.

Job contract:

- Public job execution and recurrence create `JOB_RUN` assignments.
- `JobExecutionSummary` bridges definition, assignment, run, agent/project context, effective workspace, persistent job workspace state, child run ids, outputs, and timestamps.
- Persistent job workspaces are opt-in and per assignment at `jobs/<assignmentId>`.
- Job outputs are under `outputs/jobs/<assignmentId>/<jobRunId>`.

Output contract:

- Direct artifact attribution is the primary query/display contract.
- Output queries support direct filters for workspace, plan/workflow id, job assignment, job run, and run type in addition to existing agent/job/project/run/type filters.
- Job route fallback remains compatibility behavior and should not be the reason new filters work.
- Chat files remain separate from orchestration outputs.

UI contract:

- Project/job/agent/plan/workflow submit surfaces should show assignment, project, effective workspace, compatibility workspace, and output provenance where relevant.
- Project membership controls and job execution-affecting edits are guarded while active work references the affected state.
- Standard CRUD and partial refresh behavior remains HTMX-first.

# Risks

- No new auth or permission model was introduced. Do not describe output filters as enforcing project or agent access control.
- Active mutation policy is intentionally conservative until definition snapshot/revision behavior is designed.
- Direct run allocation should stay internal to assignment-owned runner execution.
- Future UI work should not rely on parsing assignment input JSON for project context.

# Follow-up Items

- Consider editor revision checks as a separate planned feature if concurrent multi-operator editing becomes common.
- Decide separately whether loose output artifact discovery should be disabled by default.
- Add any new project/agent permission enforcement only with explicit service-level contracts and tests.
