# Scope

Final read-only architecture/code review for the `root-relative-workspace-migration` branch as of commit `1681afa`.

Reviewed inputs:

- `.internal-dev/plans/root-relative-workspace-migration/implementation-plan.md`
- `.codex-orchestration/root-relative-workspace-migration/notes.md`
- Prior reviews for root/config/SQLite, path storage, testing, and services UX Playwright validation
- Recent branch commits from `6473c48` through `1681afa`
- Production code for root defaults, datasource parent setup, AI `dataRoot` defaulting, root-relative path storage/resolution, output downloads/content, plan/workflow/job run path handling, workspace links, and CSS UX remediation
- Closeout docs, changelogs, knowledge note, deferred tooling note, and focused test evidence

No production code, tests, user-facing docs, staging, commits, or reverts were changed by this review.

# Findings

## Medium - Legacy stale absolute workspace PATH links are still returned and displayed raw

`WorkspaceService.links(...)` returns repository rows directly after only validating that the workspace exists:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:81`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:82`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:83`

The root-relative/stale-path handling only runs for new writes in `normalizeLinkTarget(...)`:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:182`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:190`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:203`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:206`

Read-side consumers then treat the raw target as meaningful:

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AgentWorkspaceStatusService.java:82`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AgentWorkspaceStatusService.java:83`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AgentWorkspaceStatusService.java:86`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java:6359`

This leaves one stale absolute path surface after an operator copies the old SQLite database into the new Magenta root: `workspace_links.target` rows from an old absolute data root can still be listed, displayed as old host paths, and substring-counted as linked projects. That does not auto-copy or repair old workspace files, but it does not meet the migration contract that legacy absolute path values are compatibility-readable only under the current data root and otherwise become clear stale-path failures/markers when used.

Recommended remediation: route PATH-link read/display through a small helper that accepts relative/current-root absolute values, rejects or marks stale old-root absolute values without requiring file existence, and does not rewrite rows. Non-`PATH` links should remain unchanged.

## Medium - Committed tests still assume run path columns are host-absolute

`OutputControllerTest#jobFallbackDoesNotMaskMissingDirectAssignmentAttribution` now passes a stored `job_runs.output_dir` value into `Path.of(...)` and then into output materialization:

- `src/test/java/io/mindspice/magenta2/api/web/OutputControllerTest.java:180`
- `src/test/java/io/mindspice/magenta2/api/web/OutputControllerTest.java:181`
- `src/test/java/io/mindspice/magenta2/api/web/OutputControllerTest.java:187`

After Phase 4, `JobService.startRun(...)` stores `outputDir` data-root-relative, so this test is constructing a process-relative path instead of resolving it under the test data root. The same stale assumption remains in `OrchestrationRuntimeTest`:

- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1114`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1115`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1150`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1152`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1326`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java:1327`

The phase validation evidence did not rerun these paths after the run-path migration. A full `mvn test` is likely to fail, and these tests no longer assert the intended root-relative storage contract.

Recommended remediation: update these tests to assert stored strings are relative and resolve them against `WorkspaceDirectoryService.dataRoot()` or `RootRelativePathService` before filesystem assertions/materialization.

## Low - SQLite parent creation skips all `jdbc:sqlite:file:` URI forms

`MagentaRootConfiguration.sqliteFilePath(...)` strips query parameters, then returns empty for any SQLite location beginning with `file:`:

- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootConfiguration.java:55`
- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootConfiguration.java:56`
- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootConfiguration.java:57`
- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootConfiguration.java:58`

The default root-owned URL is unaffected because it uses the plain file path form. The edge case is an operator override such as a file-backed SQLite URI with a missing parent directory. That override will not get the early parent-directory creation that the docs/changelog describe for file-backed SQLite URLs generally.

Recommended remediation: either document parent preparation as applying only to plain `jdbc:sqlite:/path/to.db` URLs, or distinguish URI-memory forms from file-backed `file:` URI forms and add test coverage for the intended contract.

# Risk Assessment

The core production migration path is mostly aligned:

- Default SQLite moved to `<magenta.root.path>/magenta.sqlite`.
- Omitted AI `dataRoot` resolves to `<magenta.root.path>/root`; relative `dataRoot` resolves under `magenta.root.path`; absolute values remain supported.
- `RootRelativePathService` centralizes storage/resolution for output, plan, workflow, and job path columns.
- Output content and download now share service-side artifact path resolution.
- Plan/workflow/job runtime contexts resolve stored values before passing host filesystem paths into execution/tool contexts.
- Manual chat carry-forward is documented; no auto-copy/import/repair/delete behavior was added.

The remaining production risk is localized to workspace PATH-link read/display semantics and the SQLite URI override edge. The remaining validation risk is broader because committed tests still contain pre-migration absolute-path assumptions and final integrated validation has not run.

# Recommendations

- Fix PATH-link read/display stale handling before final sign-off, or explicitly document that stale legacy workspace links are tolerated as inert display-only rows.
- Update stale tests and rerun at least:
  - `mvn -Dtest=OutputControllerTest,OrchestrationRuntimeTest test`
  - the already listed focused suites for output, plan, workflow, job, workspace, chat, and root config
- Run a final bounded startup smoke after the last backend-affecting change and closeout commit.
- Decide whether file-backed `jdbc:sqlite:file:` URI overrides are supported by early parent creation; align docs/tests/code accordingly.

# Follow-ups

- UX remediation is appropriately small and low risk for the reported mobile sidebar/table containment issues. The committed CSS anchors the mobile sidebar as a fixed drawer and adds mobile horizontal containment for `dashboard-table` surfaces. The deeper job-summary redesign remains correctly deferred.
- Closeout artifacts are present: normal and technical changelogs, chat carry-forward changelog, root-relative knowledge note, future migration tooling note, and docs in `docs/`.
- `.codex-orchestration/root-relative-workspace-migration/notes.md` still lists final validation as not started and startup smoke after backend wiring as an open closeout requirement.
