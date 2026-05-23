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
- Planning artifact grep/readback completed locally.
- Closeout review found the plan covers `/avatar` operational redo, SimplyPages row/column editor, Work Areas, explorer/output routing, planner recurrence, orchestration lanes, validation/red-team gates, docs, `.internal-dev`, and commit workflow.

## Remediation Notes

- None.

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
