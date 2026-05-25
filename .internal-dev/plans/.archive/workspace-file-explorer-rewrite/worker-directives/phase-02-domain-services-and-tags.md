# Phase 02 Worker Directive: Domain Services And Tags

## Objective

Prepare backend domain behavior for a details/list file explorer: rich entry metadata, custom file/directory tags, safe operation support flags, and hardened tag/action behavior for copy/move/rename/delete. Do not implement UI in this phase.

## Required Supporting Docs To Read

- `.internal-dev/plans/workspace-file-explorer-rewrite/00-specification-lock.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/01-current-state-analysis.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/02-target-design.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/senior-engineer-guidance.md`
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/validation-matrix.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `docs/technical/workspaces-tools-outputs.md`

## Exact Editable Files/Modules

May edit:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileActionLogRepository.java`
- New records/helpers under `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
- `src/main/resources/schema.sql` only if repository schema changes
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileMetadataServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceFileActionLogRepositoryTest.java`
- New workspace package tests
- `.internal-dev/plans/workspace-file-explorer-rewrite/shared/implementation-notes.md`

## Forbidden Scope

- No web controller or UI edits.
- No CSS/JS edits.
- No docs/focus/changelog closeout edits in this phase except implementation notes.
- No repo-local email ledger ; use direct AgentMail daemon/wait state only.
- No broad workspace runtime redesign.
- No arbitrary filesystem browsing.

## Implementation Sequence

1. Inspect current service records and tests before editing.
2. Add or wrap a richer explorer entry/detail model with file type, size label support, created timestamp optional/unknown, modified timestamp, viewer kind, tags, and operation support flags.
3. Keep created timestamp honest: use filesystem attribute when available, otherwise nullable/unknown.
4. Add service methods for custom tag lifecycle if existing repository methods are not enough.
5. Ensure tags can be assigned to both files and directories.
6. Revalidate tag follow on rename/move, copy on copy, and cleanup on delete.
7. Harden copy/move/delete edge cases if tests expose gaps.
8. Ensure action logging covers mutations with workspace/work area context, source/target, action type, result, and timestamp.
9. Update canonical `schema.sql` only if schema changes.
10. Add focused tests for all changed behavior.
11. Append evidence and decisions to `shared/implementation-notes.md`.

## Acceptance Criteria

- Service can produce row/detail data required by the UI without controller filesystem logic.
- Tags persist for files and directories.
- Custom tags can be created or ensured through service methods.
- Existing `note` and `work-area` semantics remain intact.
- Tags follow rename/move and copy with copy.
- Copy/move/rename/delete remain root-confined and symlink-safe.
- Mutation action logging remains durable and tested.
- Tests cover positive and negative cases.

## Negative Checks

- Fail if controller/UI code is changed.
- Fail if any path operation trusts a UI/view value without backend resolution.
- Fail if tags are file-only.
- Fail if copy drops tags.
- Fail if created timestamp is fabricated.
- Fail if action log is bypassed for a mutation.
- Fail if a root escape, symlink escape, or protected delete succeeds.

## Validation Commands

```bash
mvn test -Dtest=WorkAreaExplorerServiceTest,WorkspaceFileMetadataRepositoryTest,WorkspaceFileMetadataServiceTest,WorkspaceFileActionLogRepositoryTest
git status --short
```

## Stop Conditions

- Existing label schema cannot support required custom file/directory tags without substantial redesign.
- Any root escape or symlink escape is found and cannot be fixed narrowly.
- Domain change requires broad runtime/workspace redesign.
- A repo-local email ledger is recreated or modified by this phase.

## Senior Engineer Notes

Keep the backend model practical. The UI needs enough metadata to render a table and inspect panel, not a generalized filesystem index. Do not add background scanning or reconciliation. External file changes may orphan metadata in v1; document that later rather than designing a watcher here.

## Do Not Close Unless

- [ ] Rich entry/detail data exists or is clearly available through service methods.
- [ ] File and directory tags are tested.
- [ ] Custom tags are tested.
- [ ] Rename/move/copy/delete tag behavior is tested.
- [ ] Path/symlink/protected operation tests pass.
- [ ] Action logging tests pass.
- [ ] `shared/implementation-notes.md` has phase evidence.
- [ ] Validation red-team has passed before Phase 03 starts.
