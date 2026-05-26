# Phase 04 Worker Directive: API, UI, And Browser Surfaces

## Objective

Update API/controller/UI surfaces so users submit named non-job runs, browse/edit Work Areas and projects as the MVP filesystem UX, and no normal browser surface presents internal workspace roots or job-owned directories as user-managed spaces.

## Editable Files

- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/api/web/JobController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OutputController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkspaceController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendFragmentController.java`
- `src/main/java/io/mindspice/magenta2/api/web/selector/*`
- Static assets under `src/main/resources/static/` only if required by changed UI behavior.
- Controller/UI tests under `src/test/java/io/mindspice/magenta2/api/web/`

## Forbidden Scope

- Do not redesign unrelated Avatar/dashboard/chat surfaces.
- Do not add advanced unrestricted filesystem browser.
- Do not implement project git behavior.
- Do not turn browser UX into internal root management.
- Do not delete unrelated Playwright artifacts.

## Supporting Docs To Read

- Phase 01-03 worker reports and validator results.
- `AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
- `.internal-dev/knowledge/workspace-api-list-and-agent-tab-operational-pattern.md`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before final Playwright dispatch.
- Relevant SimplyPages docs/demo if changing Work Area layout structure.

## Experience Contract

- Work Area and project browsing/editing are the first-class MVP file UX.
- Internal agent workspace root, run staging, output internals, and agent metadata are not normal edit targets. If visible, they are diagnostic/read-only and visually de-emphasized.
- Run submission controls require a clear run-name field for non-job task/workflow submissions.
- UI should keep Magenta's operational-console style: dense panels, compact controls, thin borders, small radii, stable table columns, and predictable HTMX fragments.
- Browser validation must capture desktop and mobile screenshots of affected pages and critique alignment, density, scan hierarchy, text wrapping, overflow, and whether available space is used coherently.
- Standard CRUD/filter/form/row actions should use HTMX unless existing narrow JavaScript is the path of least resistance.

## Implementation Steps

1. Add/validate `runDisplayName` in non-job task/workflow public submission flows and carry it to assignment/run summaries.
2. Update output/job/workspace display paths to target semantics and remove active UI affordances for job-owned workspaces.
3. Update Work Area/project browser route assumptions so selected Work Areas/projects are browsable/editable while internal roots stay out of MVP management.
4. Ensure selectors and labels distinguish project, Work Area, and compatibility `workspaceId`.
5. Update controller tests for payload validation, status codes, fragments, and changed labels/path assumptions.
6. Prepare a precise Playwright checklist for validator handoff.

## Acceptance Criteria

- Non-job submissions without `runDisplayName` are rejected with clear validation.
- Browser surfaces no longer imply jobs own directories or that users manage internal workspace roots.
- Project directories remain fully browsable/editable.
- Work Area browser remains service-confined and HTMX-compatible.
- Controller/API docs and tests align with payload changes.

## Negative Checks

```bash
rg -n "persistent job workspace|job workspace|jobs/.*/workspace|outputs/jobs|runtime/task-runs|runtime/workflow-runs|scratch|workspace root" src/main/java/io/mindspice/magenta2/api/web src/test/java/io/mindspice/magenta2/api/web docs
```

Remaining hits must be legacy/diagnostic or deliberately changed later.

## Validation Commands

```bash
mvn -Dtest='TaskControllerTest,WorkflowControllerTest,JobControllerTest,OutputControllerTest,WorkspaceControllerTest,WorkAreaControllerTest,OrchestrationControllerTest,FrontendControllerTest,EntitySelectorControllerTest,EntityLookupServiceTest' test
```

## Stop Conditions

- Stop if UI needs a new broad filesystem browser to satisfy the request.
- Stop if a browser surface still depends on job-owned workspace paths after Phase 03.
- Stop if SimplyPages behavior is unclear and docs/demo inspection does not resolve it.

## Senior Guidance

Do not over-design the UX. Users need opinionated Work Area/project browsing now; internal run/output/root views can wait.

## Do Not Close Unless

- Controller tests pass.
- Playwright checklist is included in the worker report.
- Any JavaScript use is justified.
- Screenshots are planned for validator/Playwright execution.

