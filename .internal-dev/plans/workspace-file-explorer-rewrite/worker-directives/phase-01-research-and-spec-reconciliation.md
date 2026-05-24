# Phase 01 Worker Directive: Research And Spec Reconciliation

## Objective

Confirm the current branch, source drift, plan supersession, SimplyPages dependency/source state, and validation fixture requirements before production code changes begin. This phase is planning/support only and must not edit production code, tests, schemas, runtime config, or behavior.

## Required Supporting Docs To Read

- `.internal-dev/plans/workspace-file-explorer-rewrite/00-specification-lock.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/01-current-state-analysis.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/02-target-design.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/senior-engineer-guidance.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/validation-matrix.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/focus/AGENTS.md`
- `docs/technical/frontend-htmx.md`
- `docs/technical/workspaces-tools-outputs.md`

## Exact Editable Scope

May edit only:

- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md`

If criteria are wrong or ambiguous, stop and request a replan. Do not repair criteria by editing other plan files unless the orchestrator explicitly dispatches a planning revision.

## Forbidden Scope

- No production Java edits.
- No test edits.
- No docs edits outside `shared/implementation-notes.md`.
- No repo-local email ledger; use direct AgentMail daemon/wait state only.
- No archiving/moving the old `.internal-dev/plans/workspace-file-explorer/` suite.
- No SimplyPages upstream mutation.

## Implementation Sequence

1. Run `git status --short --branch`.
2. Verify branch is `feature/workspace-file-explorer`; if not, stop and report.
3. Verify current dirty state and distinguish plan-suite changes from unrelated worktree changes.
4. Re-open the old plan directory file list and record that it is superseded but not archived.
5. Check Maven dependency for `io.mindspice:simplypages` version and whether checked-out SimplyPages source contains the file explorer module sources.
6. Record any dependency/source drift in `shared/implementation-notes.md`.
7. Draft fixture inventory for validators: folder, Markdown, plain text, image, binary, invalid UTF-8, large file, symlink, tagged file, tagged directory.
8. Append Phase 01 status and evidence to `shared/implementation-notes.md`.

## Acceptance Criteria

- Branch and dirty-state facts are recorded.
- Direct AgentMail daemon/wait workflow is named; no repo-local email ledger is created.
- Prior plan supersession is recorded without moving/archive changes.
- SimplyPages dependency/source drift is recorded.
- Validation fixture list is recorded.
- No production/test/runtime behavior changed.

## Negative Checks

- Fail if any production code, tests, schemas, runtime config, or normal docs are modified.
- Fail if a repo-local email ledger is recreated.
- Fail if the old plan is moved or archived.

## Validation Commands

```bash
git status --short --branch
git diff -- .internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md
find .internal-dev/plans/workspace-file-explorer -maxdepth 2 -type f | sort
rg -n "simplypages" pom.xml
```

Expected evidence: only `shared/implementation-notes.md` changed by this phase unless a criteria defect requires replan.

## Stop Conditions

- Branch is not `feature/workspace-file-explorer`.
- Dirty files overlap the next phase's production targets.
- Criteria conflict with current source in a way that changes product/API/data design.
- SimplyPages dependency/source state forces an upstream strategy decision.

## Senior Engineer Notes

This phase prevents the old failure mode where implementation raced ahead of current-state facts. Keep it narrow. Your job is not to improve the plan or start coding; it is to make sure the next worker has clean situational awareness and no hidden dirty-state trap.

## Do Not Close Unless

- [ ] `git status --short --branch` evidence is recorded.
- [ ] Direct AgentMail daemon/wait workflow is named in the notes.
- [ ] Prior plan is marked superseded in notes, not archived.
- [ ] SimplyPages dependency/source state is recorded.
- [ ] Fixture inventory is recorded.
- [ ] No production/test/schema/runtime file changed.
