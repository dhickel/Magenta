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
  - `.internal-dev` changelog.
  - Relevant docs updates under `docs/`.
  - Reusable knowledge notes if new operational facts are learned.
  - Phase commits after validation gates.
  - Startup smoke after backend wiring.

## Final Validation Status

- Not started.

## Handoff Notes

- Preserve unrelated dirty work in the repo. Stage only files owned by this migration/refactor.
- Implementation plan chooses new writes as data-root-relative, old absolute paths as current-root compatibility reads only, and no startup/import/admin repair in this pass.
- Plan defines Magenta root default as `${user.home}/.magenta`, data root as `<magenta.root.path>/root`, and SQLite default as `<magenta.root.path>/magenta.sqlite`.
- Phase 2 added `RootRelativePathService` in the workspace package. It stores slash-separated paths relative to the canonical `WorkspaceDirectoryService.dataRoot()`, resolves stored relative/current-root absolute values without requiring existence, rejects traversal and stale/outside-root absolute paths, and provides existence-specific file/directory helpers plus display resolution.
- Phase 2 validation passed: `mvn -Dtest=RootRelativePathServiceTest test` (10 tests).
