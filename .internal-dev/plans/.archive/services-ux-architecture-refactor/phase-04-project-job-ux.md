# Phase 04: Project, Job, And Operator UX

## Context

The operational UI currently exposes projects, jobs, assignments, and outputs, but it hides or mislabels effective execution context. Some controls are missing, and selector helpers often drop useful agent/project context.

## Goal

Expose project/job/workspace/output context clearly in the UI using the service contracts from phases 1-3. Standard CRUD, filtering, row actions, and form submissions should remain HTMX-first and should follow SimplyPages patterns.

## In Scope

- Agent submit project/workspace context controls.
- Plan/workflow/job submit result context display.
- Job editor persistent workspace toggle.
- Job execution summary rendering.
- Project shared-workspace copy and membership controls backed by mutation policy.
- Project active job navigation fix.
- Output provenance filters, rows, and detail display.
- Selector context plumbing.
- Focused controller tests and Playwright scenarios.

## Out of Scope

- A full visual redesign of all orchestration pages.
- Replacing SimplyPages/HTMX flows with JavaScript transport.
- New output authorization model.
- Optimistic two-tab editor conflict UX unless already implemented in services.

## Implementation Steps

1. Read relevant SimplyPages docs and demos before editing reusable UI components, selectors, forms, or modal/fragment behavior.
2. Extend entity selector helpers so callers can pass context params such as `agentId`, `projectId`, status, and type.
3. Update selected-option and lookup HTMX URLs to preserve context.
4. Update agent submit tab:
   - add project selector.
   - keep compatibility workspace selector only where useful and label it clearly.
   - submit `projectId` and `workspaceId` separately.
   - render assignment id, project, effective workspace, and compatibility workspace in result fragments.
5. Update plan and workflow submit fragments:
   - show assignment id.
   - show project.
   - show effective workspace id/kind/path.
   - show compatibility workspace only as compatibility metadata.
6. Update job editor:
   - render persistent workspace toggle.
   - persist the flag through existing create/update paths.
   - show mutation-policy errors for active job definitions.
7. Update job submit/start surfaces:
   - show saved job project/workspace context.
   - allow explicit project override where service/API supports it.
   - keep workspace override labeled as compatibility metadata.
   - show assignment context immediately after submit.
8. Update job runs panel to render `JobExecutionSummary`:
   - assignment id.
   - job run id.
   - status.
   - agent.
   - project.
   - effective workspace.
   - persistent workspace enabled/path/presence.
   - output directory.
   - output count.
   - cancel/requeue actions where valid.
9. Update project UI:
   - replace prominent "Owner" copy with shared workspace/member language.
   - move legacy initial agent into advanced compatibility metadata.
   - add membership add/remove role controls after phase 1 mutation checks are wired.
   - show active lease/release requested state without implying immediate unlock.
10. Fix project active job links:
    - target an existing container, or
    - navigate to `/jobs/{jobId}` with a real link.
11. Update assignment queue/history/diagnostics:
    - display project and effective workspace context.
    - display workspace-blocked `WAITING` state and requeue action when eligible.
12. Update outputs page/fragments:
    - add filters for new query params where useful.
    - show project, agent, job, assignment, job run, run type, workspace, path, and created timestamp.
    - show provenance in content pane.
    - keep download behavior unchanged.
13. Check mobile and desktop layouts for added selectors and columns. Prefer compact detail rows over overflowing wide tables.

## Validation

Run:

```bash
mvn test -Dtest=OperationalUiContractControllerTest,PublicApiRouteBindingTest,PublicRunSubmissionControllerTest
git diff --check
```

Add/extend controller tests for:

- agent submit form renders and submits project/workspace fields.
- submit result fragments show project and effective workspace context.
- job editor renders and persists `persistentWorkspaceEnabled`.
- job run panel renders execution summary fields.
- active project/job mutation errors render clearly.
- project membership controls call service policy.
- project active job links target a real route/container.
- output fragments render provenance and new filters.
- selector lookup URLs preserve context.

Playwright subagent validation must cover:

- `/projects`: create ownerless project, inspect shared-workspace copy, membership controls if implemented, active jobs link, workspace/lease display.
- `/jobs`: create/edit job with persistent workspace enabled, submit under project, inspect assignment/run summary and output context.
- plan submit: choose project, submit, inspect result and queue context.
- workflow submit: choose project, submit, inspect result and output/run context where available.
- agent detail submit: choose task/workflow/job with project context, inspect history row.
- `/outputs`: filter by project, job, agent, workspace/run fields, open content pane, download still works.
- desktop and mobile screenshots for changed surfaces.

## Exit Criteria

- Users can see and choose project context on primary submit flows.
- Users can distinguish project effective workspace from compatibility workspace metadata.
- Persistent job workspace configuration and per-run state are visible.
- Project membership/job mutation controls respect active-work policy.
- Output provenance is visible without inspecting raw JSON or database rows.
- Playwright validation passes or blockers are recorded and approved by the user.
