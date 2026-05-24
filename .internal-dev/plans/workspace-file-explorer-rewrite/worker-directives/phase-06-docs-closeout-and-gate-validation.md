# Phase 06 Worker Directive: Docs Closeout And Gate Validation

## Objective

Complete docs, `.internal-dev` workflow, phase evidence, supersession handling, final validation readiness, and commit hygiene after implementation phases pass. Do not add new feature behavior in this phase.

## Required Supporting Docs To Read

- `.internal-dev/AGENTS.md`
- `.internal-dev/focus/AGENTS.md`
- `.internal-dev/focus/current-focus.md`
- `.internal-dev/focus/unfinished-work.md`
- `.internal-dev/focus/architecture-focus.md`
- `.internal-dev/focus/decisions.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/00-specification-lock.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/validation-matrix.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/frontend-htmx.md`

## Exact Editable Files

May edit:

- `docs/end-user/projects-and-workspaces.md`
- `docs/end-user/avatar-dashboard.md` if present
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `docs/technical/frontend-htmx.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md` only if web route responsibility changed
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md` only if workspace responsibility changed
- `.internal-dev/changelogs/2026-05-24-workspace-file-explorer-rewrite.md`
- `.internal-dev/knowledge/workspace-file-explorer-details-list-rewrite.md`
- `.internal-dev/focus/unfinished-work.md` for actual deferred/blocking items
- `.internal-dev/focus/architecture-focus.md` and `.internal-dev/focus/decisions.md` only for durable architecture/process changes
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/closeout-report-plan.md` only if final evidence requires filling placeholders
- Prior `.internal-dev/plans/workspace-file-explorer/` archive marker or move only if all implementation/validation passes and orchestrator authorizes closeout archival.

## Forbidden Scope

- No new production behavior.
- No test logic changes except evidence-only correction if orchestrator approves.
- No repo-local email ledger ; use direct AgentMail daemon/wait state only.
- No hiding failed validation by weakening docs.
- No archiving old plan before final validation passes.

## Implementation Sequence

1. Review phase evidence in `shared/implementation-notes.md`.
2. Update end-user docs with actual explorer behavior and limitations.
3. Update technical docs with route/fragment/schema/tag/action-log behavior.
4. Update frontend HTMX docs only if new reusable patterns or JS justification need recording.
5. Write changelog with files, behavior impact, validation, risks, follow-ups.
6. Write knowledge note for reusable details/list explorer lessons.
7. Update focus files only for real deferred/blocking items or durable direction changes.
8. If out-of-scope bugs were found, ensure local bug reports exist and GitHub Issues are mirrored.
9. Mark prior plan superseded; archive only after orchestrator confirms final validation pass.
10. Run docs/source checks and targeted tests.
11. Append final phase evidence to implementation notes.

## Acceptance Criteria

- Docs accurately reflect implemented behavior.
- `.internal-dev` changelog exists.
- Reusable knowledge exists.
- Focus files are updated only when required.
- Prior plan relationship is recorded.
- No production behavior changed.
- Validation evidence is complete enough for final quality review.

## Negative Checks

- Fail if docs claim unimplemented features.
- Fail if old plan is archived before validation.
- Fail if bug reports are local-only in a repo with GitHub issues when mirroring is required.
- Fail if focus files are silently rewritten for strategy.
- Fail if a repo-local email ledger is recreated.

## Validation Commands

```bash
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkAreaControllerTest,AvatarDashboardControllerTest
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
git status --short --branch
```

If startup cannot run, record the concrete dependency/secrets blocker and stop for user approval.

## Stop Conditions

- Full tests fail.
- Startup fails for an unknown or fixable reason.
- Required Playwright evidence from UI phases is missing.
- Docs would need to describe a blocker as complete.
- GitHub issue mirroring is required but unavailable.

## Senior Engineer Notes

Closeout is where incomplete work often gets laundered into success. Do the opposite: make docs and changelog precise, name residual risk plainly, and leave follow-up items visible. The final quality-review subagent depends on your evidence trail being honest.

## Do Not Close Unless

- [ ] Docs updated.
- [ ] Changelog written.
- [ ] Knowledge note written.
- [ ] Focus files reviewed and updated only if needed.
- [ ] Previous plan supersession recorded.
- [ ] Targeted tests pass.
- [ ] Full `mvn test` passes or blocker is user-approved.
- [ ] Bounded startup passes or blocker is user-approved.
- [ ] Playwright evidence from UI phases is present.
- [ ] Validation red-team has passed.
