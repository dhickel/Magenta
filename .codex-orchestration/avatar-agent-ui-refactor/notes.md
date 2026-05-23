# Avatar Agent UI Refactor Shared Notes

## Global Assumptions

- Branch: `feature/avatar-dashboard-sprint` at planning time.
- Worktrees are not used.
- This notes file is coordinator-owned; workers append concise lane notes only when assigned.
- Current planning pass creates the contract and does not modify production code.

## Active Agents

- Main thread: planning and repo workflow coordinator.

## Completed Work

- Phase 01 Avatar layout persistence implemented: row/widget schema, records, repository/service operations, compatibility seeding from legacy layout, focused tests, technical doc, and changelog.
- Phase 03 Work Area persistence foundation implemented: `work_areas` schema, `WorkArea` repository/service, Home Work Area creation, confined mark/list/unmark behavior, active-use guard checks, docs, package guide, and changelog.
- Assignment Work Area metadata slice implemented: `work_assignments` selected/output route columns, request/record fields, assignment creation validation, docs, and changelog.
- Runtime Work Area routing slice implemented: selected Work Area runtime path allocation, output redirect resolution, `root/` alias support, docs, package guide, and changelog.
- Submit Work Area routing slice implemented: operational submit forms and direct submit DTOs now propagate selected Work Area/output route metadata while plan-chat routes remain unchanged.
- Submit Work Area picker slice implemented: operational submit forms use the shared HTMX entity selector for selected/output Work Area controls instead of raw ID-only fields.
- Work Area explorer backend slice implemented: `/api/work-areas` metadata/explorer routes, confined browse/preview/download/edit/directory/rename/delete/mark behavior, delete guardrails, docs, tests, and changelog.
- Avatar layout editor UI slice implemented: `/avatar` can render persisted row/widget layouts, `/avatar/_edit` exposes row/widget add/move/resize/remove actions, and layout mutations autosave with HTMX plus OOB grid refresh.
- Avatar Work Area explorer UI slice implemented: Work Areas widget lists agent-owned Work Areas and opens HTMX modal fragments for browse, preview/download, safe text edit, create directory, delete, and mark nested Work Area.
- Avatar planner organizer slice implemented: durable planner task/subtodo/note-link/projection persistence, friendly recurrence projection, and HTMX Organizer modal tabs for planner, todos, calendar, and notes.
- Plan suite created under `.internal-dev/plans/avatar-agent-ui-refactor/`.
- Non-mutating closeout review completed by subagent `019e5351-f450-7690-90eb-aab7eab5c054`; it found no missing required items.
- SimplyPages UI reviewer completed; recommended splitting layout/editor/catalog components out of `AvatarDashboardComponents`, using SimplyPages `Row`/`Column`, stable OOB containers, compact decorator controls, and narrow raw HTML fallbacks only.
- Runtime reviewer completed; confirmed Work Area persistence must precede runtime metadata/routing, and identified alias/output routing touchpoints in runtime, plan, workflow, file/shell tools, and output services.
- SimplyPages upstream review lane started for email report.
- Submit-surface reviewer completed; immediate submits, plan/workflow/job submits, schedule/reaction templates, and direct APIs all need explicit Work Area/output route propagation later, while planning-chat routes must remain unchanged.
- SimplyPages upstream review was emailed to Dwight via AgentMail message `<0100019e535770f8-3b4d2ed3-2ba2-48f8-b18e-2d0af4a3e3d9-000000@email.amazonses.com>`.

## Validation Results

- Phase 01 focused tests: `mvn -Dtest='io.mindspice.magenta2.avatar.*Test' test` passed with 17 tests, 0 failures, 0 errors.
- Phase 03 focused tests: `mvn -Dtest='io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepositorySchemaMigrationTest' test` passed with 14 tests, 0 failures, 0 errors.
- Assignment metadata focused tests: `mvn -Dtest='io.mindspice.magenta2.ai.orchestration.runtime.AssignmentContextServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkspaceRepositorySchemaMigrationTest' test` passed with 22 tests, 0 failures, 0 errors.
- Runtime routing focused tests: `mvn -Dtest='io.mindspice.magenta2.ai.orchestration.workspaces.OutputDirectoryServiceTest,io.mindspice.magenta2.ai.chat.plan.PlanServiceTest,io.mindspice.magenta2.ai.chat.tool.file.AgentFileToolServiceTest,io.mindspice.magenta2.ai.chat.tool.shell.AgentShellToolServiceTest' test` passed with 103 tests, 0 failures, 0 errors. Follow-up targeted route/alias test pass also passed with 31 tests, 0 failures, 0 errors.
- Submit routing focused tests: `mvn -Dtest='io.mindspice.magenta2.api.web.OrchestrationControllerTest,io.mindspice.magenta2.api.web.AgentOrchestrationControllerTest,io.mindspice.magenta2.api.web.PublicRunSubmissionControllerTest' test` passed with 132 tests, 0 failures, 0 errors.
- Submit Work Area picker focused tests: `mvn -Dtest='io.mindspice.magenta2.api.web.OrchestrationControllerTest,io.mindspice.magenta2.api.web.selector.*Test,io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaServiceTest' test` passed with 114 tests, 0 failures, 0 errors.
- Submit Work Area picker Playwright validation subagent `019e53cf-3dfb-7341-b2c2-7e16da1c0f0c` passed desktop picker behavior but found mobile Submit tab/sidebar interception.
- Submit Work Area picker mobile recheck subagent `019e53dd-2f16-70c0-8af2-a0a7fbc2fc05` passed desktop/mobile Submit tab loading, Browse Work Areas option loading, output route/direct directory controls, and no sidebar intercept. Screenshots saved under `target/playwright-submit-work-area-pickers-mobile-recheck/`.
- Work Area explorer backend focused tests: `mvn -Dtest='io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaServiceTest' test` passed locally after explorer guardrail hardening.
- Avatar layout editor focused tests: `mvn -Dtest='io.mindspice.magenta2.api.web.AvatarDashboardControllerTest,io.mindspice.magenta2.avatar.*Test' test` passed with 23 tests, 0 failures, 0 errors.
- Avatar layout editor compile check: `mvn -DskipTests compile` passed.
- Playwright validation subagent `019e5398-8502-72f0-8df0-ce5be067377b` passed `/avatar` desktop/mobile and layout-editor interaction checks on port 18080. Screenshots saved under `target/playwright-avatar-layout-editor/`.
- Avatar Work Area explorer UI focused tests: `mvn -Dtest='io.mindspice.magenta2.api.web.AvatarDashboardControllerTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaExplorerServiceTest,io.mindspice.magenta2.ai.orchestration.workspaces.WorkAreaServiceTest' test` passed with 17 tests, 0 failures, 0 errors.
- Avatar Work Area explorer UI compile check: `mvn -DskipTests compile` passed.
- Playwright validation subagent `019e53a2-e8d3-7802-ac4f-6f02087e315d` passed Work Area create-directory/create-file/edit/preview/download-link flow with one ambiguous `Save` click issue; follow-up subagent `019e53ab-4dbf-7731-a9d5-c4bc2203364f` passed the renamed `Save File` normal-click recheck. Screenshots saved under `target/playwright-avatar-workarea-explorer/`.
- Avatar planner organizer focused tests: `mvn -Dtest='io.mindspice.magenta2.avatar.*Test,io.mindspice.magenta2.api.web.AvatarDashboardControllerTest' test` passed with 26 tests, 0 failures, 0 errors.
- Avatar organizer Playwright validation subagent `019e53b8-5005-7ea1-925d-c6a926f39751` initially passed organizer function but found desktop widget slivers and organizer modal/button ambiguity.
- Avatar organizer Playwright recheck subagent `019e53bd-dd5c-7682-9c7f-ef109014ed86` confirmed modal/button fixes and isolated remaining desktop row-grid sliver issue.
- Avatar row-grid Playwright recheck subagent `019e53c5-cf3e-7e52-a1ac-bf4bfc7570b1` passed desktop widget readability, organizer modal id, planner task/subtodo creation, and mobile no-overflow checks. Screenshots saved under `target/playwright-avatar-row-grid-recheck/`.
- Planning artifact grep/readback completed locally.
- Closeout review found the plan covers `/avatar` operational redo, SimplyPages row/column editor, Work Areas, explorer/output routing, planner recurrence, orchestration lanes, validation/red-team gates, docs, `.internal-dev`, and commit workflow.

## Remediation Notes

- Playwright validation found one UX friction: when every first-party widget exists, the add-widget catalog is all disabled entries until a user removes a widget elsewhere. This is expected by the v1 single-instance rule and was recorded in `.internal-dev/focus/ideas-inbox.md`.
- Organizer validation found and remediated two issues: the Organizer modal now uses `#avatar-organizer-modal` with specific submit labels, and Avatar row-grid CSS overrides SimplyPages global `.col-*` max-width/flex behavior inside `.avatar-dashboard-row` so widgets do not collapse into slivers.
- Submit picker validation found and remediated a mobile sidebar override that kept the sidebar open over agent-detail content. The orchestration stylesheet now leaves the mobile sidebar hidden until framework JS applies `mobile-open`.

## Blockers

- None.

## Closeout Work

- Add changelog and focus/decision updates for the planning artifact.
- Commit and push explicit paths.

## Final Validation Status

- Planning artifact validation passed.

## Handoff Notes

- Implementation should begin from `implementation-plan.md` and `orchestration.md`.
- Runtime reviewer flagged that `workspace/` alias drift across prompt text, file tools, shell tools, and output routing is the largest compatibility risk.
- Submit-surface reviewer listed `OrchestrationController`, `AgentOrchestrationController`, `JobController`, `WorkflowController`, `TaskController`, and `PlanController` as later propagation targets; do not add Work Area controls to planning-chat routes.
