# Root-Relative Workspace Migration Notes

## Global Assumptions

- User decisions on 2026-05-21:
  - The SQLite database should live inside the Magenta root and use root-relative path behavior where paths are Magenta-owned.
  - One-time migration/admin import/startup auto-repair should be documented as a future feature, not implemented in this phase.
  - Long-term absolute path columns should become root-relative where practical.
  - Old roots do not need automatic archive or symlink handling.
  - Existing chat files should be copied into the new root.
  - Existing workspace files can be dropped as part of this breaking cleanup.
  - Active runs/checkpoints can be ignored for this migration cleanup.
- Implementation and planning agents use `gpt-5.5` with high reasoning per user request.
- Testing/validation agents use `gpt-5.3-codex` with medium reasoning per repo validation instructions.
- Code-editing subplans run serially. Non-mutating review and planning may run in parallel.

## Active Agents

- `019e4b3e-e6dc-75d3-bb93-de99a204a4c6` / Sartre: high-reasoning planning agent. Scope: write `.internal-dev/plans/root-relative-workspace-migration/implementation-plan.md` and append concise notes here.

## Completed Work

- Created branch `root-relative-workspace-migration`.
- Created shared orchestration notes.
- Created beginning commit `6473c48` (`chore: start root-relative workspace migration`).
- Planning agent created `.internal-dev/plans/root-relative-workspace-migration/implementation-plan.md`.
- Phase 1 implemented root defaults and SQLite placement:
  - Added `magenta.root.path` default `${user.home}/.magenta`.
  - Moved default SQLite URL to `${magenta.root.path}/magenta.sqlite?foreign_keys=true`.
  - Added early SQLite parent-directory creation for plain file-backed SQLite URLs while ignoring in-memory and URI memory URLs.
  - Resolved/defaulted `AiConfig.dataRoot` before workspace/tool beans: missing -> `<magenta.root.path>/root`, relative -> `<magenta.root.path>/<relative>`, absolute unchanged.
  - Kept prompt files resolved relative to the AI config file directory.
  - Updated `config/ai-config.example.json` to omit host-specific `dataRoot` and replace real-looking credentials.

## Validation Results

- Planning artifact only; no production code validation run.
- R3 test-design review completed: `.internal-dev/reviews/2026-05-21-root-relative-testing-review.md`.
- High-priority test gaps before implementation sign-off: isolated `/tmp` fresh-install SQLite/root behavior, root-relative helper stale absolute rejection, relative persisted path assertions for output/plan/workflow/job rows, seeded chat file carry-forward, and browser validation against an isolated root/database.
- Phase 1 focused tests passed: `mvn -Dtest=ExternalAiConfigLoaderTest,MagentaRootConfigurationTest,AiUserConfigConfigurationTest test` (17 tests).
- Phase 1 bounded startup smoke passed: app started with temp `--magenta.root.path=/tmp/magenta-phase1-smoke-1779380199373764712`, created `magenta.sqlite` and `root/`, then was stopped by `timeout` (exit 124 after successful startup).
- Independent Phase 1 validation passed: `mvn -Dtest=ExternalAiConfigLoaderTest,MagentaRootConfigurationTest,AiUserConfigConfigurationTest test` (17 tests) and bounded startup smoke with temp root `/tmp/magenta-phase1-smoke-WCiG78`; `magenta.sqlite` and `root/` were created. Phase 1 may proceed to Phase 2.
- Phase 2 focused tests passed: `mvn -Dtest=RootRelativePathServiceTest test` (10 tests).
- Independent Phase 2 validation passed: `mvn -Dtest=RootRelativePathServiceTest test` (10 tests). Phase 2 may proceed to Phase 3.
- Phase 3 focused tests passed: `mvn -Dtest=OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest,OutputControllerTest test` (25 tests) plus a bounded isolated-root startup smoke from the implementation worker.
- Independent Phase 3 validation passed: `mvn -Dtest=OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest,OutputControllerTest test` (25 tests). Phase 3 may proceed to Phase 4.
- Phase 4 focused tests passed: `mvn -Dtest=PlanServiceTest,WorkflowRunnerTest,JobServiceTest test` (75 tests) plus bounded startup smoke with isolated `/tmp` root from the implementation worker.
- Independent Phase 4 validation passed: `mvn -Dtest=PlanServiceTest,WorkflowRunnerTest,JobServiceTest test` (75 tests). Phase 4 may proceed to Phase 5.
- Phase 5 focused tests passed: `mvn -Dtest=WorkspaceServicePathTest,WorkspaceControllerTest,WorkspaceRepositorySchemaMigrationTest test` (18 tests) plus bounded isolated-root startup smoke from the implementation worker.
- Independent Phase 5 validation passed: `mvn -Dtest=WorkspaceServicePathTest,WorkspaceControllerTest,WorkspaceRepositorySchemaMigrationTest test` (18 tests). Phase 5 may proceed to Phase 6.
- Phase 6 focused tests passed: `mvn -Dtest=ChatFileServiceTest,ChatFileControllerTest,ChatServiceTest,PublicApiRouteBindingTest test` (24 tests).
- Independent Phase 6 validation passed: same 24-test chat/API suite and `git diff --check HEAD~1..HEAD`. Phase 6 may proceed to closeout/UX remediation.
- Phase 5 focused tests passed: `mvn -Dtest=WorkspaceServicePathTest,WorkspaceControllerTest,WorkspaceRepositorySchemaMigrationTest test` (18 tests).
- Phase 5 bounded startup smoke passed with isolated root `/tmp/magenta-phase5-smoke-2384505`; app started on ephemeral port 38965 and was stopped by `timeout 30s` (exit 124 after successful startup).

## Remediation Notes

- R1 root/config/SQLite review completed: `.internal-dev/reviews/2026-05-21-root-config-sqlite-review.md`.
- Phase 1 constraints to carry forward: create SQLite parent before datasource/SQL init opens a connection; resolve/default `AiConfig.dataRoot` before workspace/tool beans consume it; ignore in-memory SQLite URLs; keep relative `dataRoot` rooted at `magenta.root.path`, not cwd or config file directory; do not touch old roots or `./chat-memory.db`.
- R2 path-column review completed: `.internal-dev/reviews/2026-05-21-root-relative-path-storage-review.md`.
- R2 key constraints: replace every direct `Path.of(storedDbValue)` reader for path columns, keep `OrchestrationTaskContext.host...` values resolved host paths, and avoid `toRealPath()` for display-only stale rows.
- R2 highest-risk call sites: `OutputController.download`, `OutputArtifactService.loadContent`, `PlanService` run output/temp readers, `WorkflowRunner` context/output readers, `JobService`/`OrchestrationRunnerService` job path handoff, and `WorkspaceService.addLink`.

## Blockers

- None yet.

## Closeout Work

- Required before final sign-off:
  - `.internal-dev` changelog. Done: `2026-05-21-root-relative-workspace-migration.md`, `2026-05-21-root-relative-workspace-technical.md`, plus phase-specific chat carry-forward changelog.
  - Relevant docs updates under `docs/`. Done in Phase 6.
  - Reusable knowledge notes if new operational facts are learned. Done: `.internal-dev/knowledge/root-relative-workspace-storage.md`.
  - Deferred ideas note for future migration tooling. Done: `.internal-dev/notes/root-migration-future-tooling.md`.
  - Phase commits after validation gates. Done through Phase 6 plus UX remediation.
  - Startup smoke after backend wiring.

## Final Validation Status

- Not started.

## Handoff Notes

- Preserve unrelated dirty work in the repo. Stage only files owned by this migration/refactor.
- Implementation plan chooses new writes as data-root-relative, old absolute paths as current-root compatibility reads only, and no startup/import/admin repair in this pass.
- Plan defines Magenta root default as `${user.home}/.magenta`, data root as `<magenta.root.path>/root`, and SQLite default as `<magenta.root.path>/magenta.sqlite`.
- Phase 2 added `RootRelativePathService` in the workspace package. It stores slash-separated paths relative to the canonical `WorkspaceDirectoryService.dataRoot()`, resolves stored relative/current-root absolute values without requiring existence, rejects traversal and stale/outside-root absolute paths, and provides existence-specific file/directory helpers plus display resolution.
- Phase 2 validation passed: `mvn -Dtest=RootRelativePathServiceTest test` (10 tests).
- Phase 3 implemented root-relative output artifact storage:
  - `OutputArtifactService` now stores new artifact `file_path` values through `RootRelativePathService` for materialized text/json/user_message/file_path outputs, loose discovery, and publish-existing-file.
  - Artifact content/download path access is centralized through service resolution, supporting relative rows and legacy absolute rows under the current data root while rejecting stale/outside-root absolute rows.
  - `AgentWorkspaceStatusService` resolves artifact paths through the output service for byte counts so relative rows do not crash or undercount.
  - Focused validation passed: `mvn -Dtest=OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest,OutputControllerTest test` (25 tests).
  - Bounded startup smoke passed: app started with isolated `/tmp` `magenta.root.path` and was stopped by `timeout 30s` after successful startup.
- Phase 4 implemented root-relative run workspace/output path storage:
  - New `plan_runs.output_directory` and `plan_runs.temp_workspace_path` values are stored data-root-relative; plan materialization, loose artifact discovery, temp cleanup, and output attribution resolve through `RootRelativePathService`.
  - New `workflow_runs.workspace_path` and `workflow_runs.output_dir` values are stored data-root-relative; execution/resume contexts and legacy task-node executor handoff receive resolved host paths.
  - New `job_runs.workspace_path` and `job_runs.output_dir` values are stored data-root-relative; job runtime context resolves the persistent job workspace before installing it.
  - Assignment checkpoint JSON now retains the stored job path values (relative for new runs, legacy absolute for old rows) as display/resume metadata; runtime context installation resolves through the helper before exposing `hostJobWorkspacePath`.
  - Focused validation passed: `mvn -Dtest=PlanServiceTest,WorkflowRunnerTest,JobServiceTest test` (75 tests).
  - Bounded startup smoke passed with isolated root `/tmp/magenta-phase4-smoke-py4dbq`; app started on an ephemeral port and was stopped by `timeout 30s` (exit 124 after successful startup).
- Phase 5 implemented root-relative workspace PATH link target storage:
  - `WorkspaceService.addLink` now resolves relative PATH link targets against the workspace root, rejects relative traversal and absolute outside-root targets, and persists new PATH links through `RootRelativePathService` as data-root-relative slash paths.
  - Non-PATH workspace link targets remain unchanged, and directly seeded legacy absolute current-root PATH links are still listed without rewrite.
  - Agent and project workspace root rows remain `agents/<agentId>/workspace` and `projects/<projectId>/workspace`; the existing `WorkspaceService.jobWorkspace` `jobs/<jobId>` compatibility behavior remains unchanged.
  - Focused validation passed: `mvn -Dtest=WorkspaceServicePathTest,WorkspaceControllerTest,WorkspaceRepositorySchemaMigrationTest test` (18 tests).
  - Bounded startup smoke passed with isolated root `/tmp/magenta-phase5-smoke-2384505`; app started on an ephemeral port and was stopped by `timeout 30s` (exit 124 after successful startup).
- Phase 6 documented root-relative carry-forward and chat UX stability:
  - Technical and end-user docs now describe `magenta.root.path`, default SQLite `<magenta.root.path>/magenta.sqlite`, default data root `<magenta.root.path>/root`, relative/absolute AI `dataRoot` behavior, manual DB/chat-file carry-forward, and explicitly out-of-scope migration tooling.
  - Docs now state new Magenta-owned path columns store data-root-relative values, current-root absolute rows remain compatibility-readable, and stale old-root absolute values fail when used.
  - Chat file tests now seed `new-magenta-root/root/chats/<conversationId>/files/nested/seeded.md` and verify list/count/download behavior without exposing absolute paths.
  - `PublicApiRouteBindingTest` now uses an isolated `magenta.root.path`, omitted AI `dataRoot`, and a root-owned SQLite database path for route binding.
  - Focused validation passed: `mvn -Dtest=ChatFileServiceTest,ChatFileControllerTest,ChatServiceTest,PublicApiRouteBindingTest test` (24 tests).
