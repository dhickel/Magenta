# Root Migration Handoff Agent Notes

## Global Assumptions

- Branch: `root-migration-handoff-planning`.
- This task is planning/reporting only until the user chooses a migration strategy.
- Do not move existing files, change configured roots, or implement import/migration behavior in this phase.
- Existing chat files should be preserved.
- The user wants a new root under `.magenta` and a plan for migrating normal chat files, workspace files, and database-backed references safely.
- We must determine whether the filesystem is source of truth, the database is source of truth, or whether specific file classes are jointly tracked.

## Active Agents

- Root/file/database review agent completed read-only review.

## Completed Work

- Created planning branch `root-migration-handoff-planning`.
- Created this shared notes file.
- Wrote `.internal-dev/plans/root-migration-handoff/root-file-database-review.md`.
- Reviewed current root controls, chat file behavior, workspace/output path persistence, clean install behavior, populated-root move failure modes, and migration options.

## Validation Results

- Read-only validation only. Inspected current code/schema/docs plus path-only/count-only SQLite queries against `chat-memory.db`.
- No automated tests were run because this phase is review/planning only and no implementation changes were made.

## Remediation Notes

- Top risk: Magenta-owned output/run path columns store concrete host paths, so moving a populated root without DB repair breaks output content/downloads and active/waiting run resume paths.
- Chat files are easier to migrate because per-file DB rows do not exist; files are discovered from `chats/<conversationId>/files` under the configured `dataRoot`.
- Workspace records use relative roots, but warm DB rows may still carry stale/legacy relative roots such as `agents/<id>` instead of `agents/<id>/workspace`.
- Recommended direction is explicit offline migration to root-relative owned paths plus a dry-run repair/report command; avoid startup auto-repair.

## Blockers

- No implementation should begin until the user selects a migration approach.

## Closeout Work

- Produce handoff report.
- Produce root/file/database review report with several migration options.
- Commit reports only, if appropriate, while excluding unrelated dirty files.

## Final Validation Status

- Root/file/database review artifact produced. Awaiting user decision on migration strategy before implementation planning.

## Handoff Notes

- The review should explicitly cover clean new installs and the edge case of moving a configured root with a populated database.
