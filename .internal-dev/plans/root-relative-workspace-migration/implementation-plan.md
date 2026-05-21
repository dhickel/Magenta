# Root-Relative Workspace Migration Implementation Plan

Date: 2026-05-21
Branch: `root-relative-workspace-migration`
Shared notes: `.codex-orchestration/root-relative-workspace-migration/notes.md`

## 1. Objective

Move Magenta to a clean root-owned runtime layout where a fresh install creates and uses a Magenta root, the SQLite database lives inside that root, and newly persisted Magenta-owned filesystem paths are stored relative to the configured data root instead of as host-specific absolute paths.

This is a breaking cleanup, not a full data repair migration. Existing session/chat database state and ordinary chat files must continue to work after the operator copies the existing SQLite database and `chats/<conversationId>/files/` tree into the new root. Existing workspace, output, temp, checkpoint, and active-run files are not carried forward in this phase, and the implementation must not delete old filesystem trees unless the user explicitly asks for a destructive cleanup later.

## 2. Inputs And Assumptions

### Confirmed Inputs

- User decisions from 2026-05-21:
  - SQLite should live inside the Magenta root.
  - Magenta-owned persisted filesystem paths should be root-relative long term.
  - One-time migration command, admin import/API, and startup auto-repair are future documented features, not implemented now.
  - Old roots do not need automatic archive or symlink handling.
  - Existing chat files must be preserved by copying into the new root.
  - Existing workspace files can be dropped during this breaking cleanup.
  - Active runs/checkpoints can be ignored for this cleanup.
  - Sessions/chat UX should continue to look and behave as before.
- Shared orchestration notes: `.codex-orchestration/root-relative-workspace-migration/notes.md`.
- Handoff artifacts:
  - `.internal-dev/plans/root-migration-handoff/handoff-report.md`.
  - `.internal-dev/plans/root-migration-handoff/root-file-database-review.md`.
  - `.internal-dev/plans/root-migration-handoff/migration-options-decision-report.md`.
  - `.internal-dev/plans/root-relative-workspace-migration/orchestration-state.md`.
- Current runtime layout under `AiConfig.dataRoot`:
  - Chat files: `chats/<conversationId>/files/`.
  - Agent workspace: `agents/<agentId>/workspace/`.
  - Project workspace: `projects/<projectId>/workspace/`.
  - Runtime temp: `runtime/task-runs/<runId>/`, `runtime/workflow-runs/<runId>/`.
  - Effective workspace children: `work/`, `outputs/`, `runs/`, `scratch/`, `jobs/`.

### Assumptions To Verify Before Coding

- Use `magenta.root.path` as the single product root property with default `${user.home}/.magenta`.
- Treat `AiConfig.dataRoot` as the filesystem data root. If the AI config omits `dataRoot`, resolve it to `<magenta.root.path>/root`. If it is relative, resolve it against `<magenta.root.path>`, not the process working directory.
- Place SQLite at `<magenta.root.path>/magenta.sqlite` by default.
- Existing warm `spring.datasource.url` overrides remain supported for tests and operators, but the product default changes away from `./chat-memory.db`.
- The implementation should preserve existing table and record field names for compatibility; path storage semantics change from "absolute path string" to "root-relative path string for new writes".

## 3. Scope

### In Scope

- Introduce root configuration/defaults so fresh installs use:
  - Magenta root: `${user.home}/.magenta` unless overridden.
  - Data root: `<magenta.root.path>/root` unless overridden in AI config.
  - SQLite DB: `<magenta.root.path>/magenta.sqlite` by default.
- Ensure the root and data root parent directories are created before SQLite and workspace services need them.
- Add a root-relative path helper for Magenta-owned persisted paths.
- Change new writes to store data-root-relative values in existing path columns:
  - `run_output_artifacts.file_path`.
  - `plan_runs.output_directory`.
  - `plan_runs.temp_workspace_path`.
  - `workflow_runs.workspace_path`.
  - `workflow_runs.output_dir`.
  - `job_runs.workspace_path`.
  - `job_runs.output_dir`.
  - `workspace_links.target` for `PATH` links when the target is under the data root.
- Keep compatibility reads for existing absolute values only when they resolve under the current configured `dataRoot`.
- Keep `workspaces.root_relative_path` relative as-is and normalize new deterministic agent/project records.
- Preserve ordinary chat file behavior by continuing to compute files from `dataRoot/chats/<conversationId>/files`.
- Document the operator copy path for the existing SQLite database and chat files.
- Update docs and `.internal-dev` artifacts required by repo guidance.

### Out Of Scope

- No one-time migration CLI/command.
- No admin import or repair API.
- No startup auto-repair, auto-copy, auto-delete, or old-root discovery.
- No archive or symlink management for old roots.
- No deterministic rewrite of existing database rows in this phase.
- No attempt to preserve old workspace/output files.
- No active run/checkpoint migration or resume guarantee.
- No broad JSON path rewrite for unstructured columns.
- No frontend redesign; sessions/chat UX should remain behaviorally unchanged.

## 4. Current-State Analysis

### Configuration And Root Defaults

- `src/main/resources/application.yml` currently sets:
  - `app.ai.config-path: ./config/ai-config.example.json`.
  - `spring.datasource.url: jdbc:sqlite:./chat-memory.db?foreign_keys=true`.
- `config/ai-config.example.json` currently sets:
  - `"dataRoot": "/home/hickelpickle/.magenta/root"`.
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiUserConfigConfiguration.java` loads AI config with `ExternalAiConfigLoader.load(Path.of(configPath))`.
- `src/main/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoader.java` resolves prompt files relative to the config file, but it does not resolve or default `dataRoot`.
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiConfig.java` stores `Path dataRoot` directly.

Fresh-install gap: SQLite is outside the `.magenta` root by default, and the example data root is host-specific. A brand-new install also depends on the parent directory for a file-backed SQLite URL existing before datasource initialization.

### Path Creation And Resolution

- `WorkspaceDirectoryService`:
  - Requires `AiConfig.dataRoot`.
  - Creates `dataRoot` with `Files.createDirectories(aiConfig.dataRoot()).toRealPath()`.
  - Computes all agent/project/chat/temp/output paths under `dataRoot`.
  - Accepts absolute paths in methods like `existingConfinedDirectory`, `resolveInputPath`, and `deleteTempDir` as long as they are under current `dataRoot`.
- `WorkspaceService`:
  - Requires `AiConfig.dataRoot`.
  - Creates workspace records with relative `root_relative_path`.
  - `agentWorkspace` writes `agents/<agentId>/workspace`.
  - `projectWorkspace` writes `projects/<projectId>/workspace`.
  - `jobWorkspace` currently writes `jobs/<jobId>` even though `WorkspaceDirectoryService.jobWorkspace` uses `jobs/<jobId>/workspace`; treat this as a compatibility oddity and do not expand job workspace behavior unless needed.
  - `workspace_links.target` currently stores the caller-provided string, which may be absolute or relative.

### Chat Files

- `ChatFileService` lists and downloads files from `WorkspaceDirectoryService.chatFiles(conversationId)`.
- Ordinary chat files have no per-file DB table. They are filesystem-led under `chats/<conversationId>/files`.
- `PlanService.persistChatFinalMessage` writes final messages into the same chat file directory.
- This means chat carry-forward is a copy concern, not a DB rewrite concern.

### Output Artifacts

- `schema.sql` table `run_output_artifacts` has `file_path text not null`.
- `RunOutputArtifact.filePath` is a string.
- `OutputArtifactService` currently persists concrete host paths by calling `destination.toString()` or `file.toString()`.
- `OutputArtifactService.loadContent` uses `Path.of(filePath).normalize().toRealPath()` and rejects files outside `WorkspaceDirectoryService.dataRoot()`.
- `OutputArtifactService.discoverLooseArtifacts` and `publishExistingFile` also save concrete paths.

### Plan/Task Runs

- `schema.sql` table `plan_runs` has:
  - `output_directory text`.
  - `temp_workspace_path text`.
- `PlanRun` carries these fields as strings.
- `PlanService.startRun` writes `tempDir.toRealPath().toString()` and `outputDir.toRealPath().toString()`.
- `PlanService.materializeRunOutputs`, `discoverLooseArtifactsForRun`, `cleanupTempForRun`, and `workspaceRuntimeContext` read those strings as path/display values.

### Workflow Runs

- `schema.sql` table `workflow_runs` has:
  - `workspace_path text`.
  - `output_dir text`.
- `WorkflowRun` carries these fields as strings.
- `WorkflowRunner.createRun` currently writes `workspacePath.toString()` and `outputPath.toString()`.
- `WorkflowRunner.outputPathFor` returns `Path.of(run.outputDir())` when present.
- Resume/waiting workflow behavior can depend on `workspace_path`; active/waiting migration is out of scope, but new writes must be root-relative.

### Job Runs

- `schema.sql` table `job_runs` has:
  - `workspace_path text`.
  - `output_dir text`.
- `JobRun` carries these fields as strings.
- `JobService.startRun` stores `wsPath.toRealPath().toString()` and `outPath.toRealPath().toString()`.
- `JobExecutionSummary` displays `run.workspacePath()` and `run.outputDir()`.

### Compatibility-Only Path Carriers

The following can contain path-like strings but should not be rewritten or made authoritative in this phase:

- `plan_runs.output_values_json`, `execution_evidence_json`, `deliverable_evidence_json`.
- `workflow_runs.node_runs_json`, `final_outputs_json`.
- `job_runs.work_item_runs_json`.
- `work_assignments.input_json`, `checkpoint_json`, `output_json`, `evidence_json`.
- User-authored messages, audit text, model outputs, and tool transcripts.

## 5. Target Design

### Root Model

Introduce a small root configuration layer:

- `magenta.root.path`: product-level root directory, default `${user.home}/.magenta`.
- Default data root: `<magenta.root.path>/root`.
- Default SQLite file: `<magenta.root.path>/magenta.sqlite`.

Implementation preference:

- Add `MagentaRootProperties` or a plain root service in `io.mindspice.magenta2.core` or `io.mindspice.magenta2.ai.config.user`.
- Add a datasource configuration that creates the SQLite parent directory before constructing a datasource.
- Preserve operator override of `spring.datasource.url`; if overridden to `jdbc:sqlite::memory:` or another path, do not force it into the root.
- In `AiUserConfigConfiguration`, post-process loaded `AiConfig`:
  - Missing `dataRoot` -> `<magenta.root.path>/root`.
  - Relative `dataRoot` -> `<magenta.root.path>/<dataRoot>`.
  - Absolute `dataRoot` -> preserve exactly, for operator compatibility.

### Root-Relative Path Service

Add one helper responsible for path storage and read compatibility. Suggested name:

- `io.mindspice.magenta2.ai.orchestration.workspaces.RootRelativePathService`

Responsibilities:

- Hold canonical `dataRoot` from `WorkspaceDirectoryService`.
- Convert a Magenta-owned `Path` under `dataRoot` to a normalized slash-separated relative string for persistence.
- Resolve a stored string back to a confined `Path`:
  - Relative value -> `dataRoot.resolve(value).normalize()`.
  - Absolute value -> compatibility path only if normalized/real path is under current `dataRoot`.
  - Absolute value outside current `dataRoot` -> reject with a clear stale-path message; do not attempt old-root lookup.
- Provide display helpers when UI/read models need a human-readable current-root absolute path without persisting one.
- Never delete files and never create old-root directories.

Example API:

```java
public String store(Path path) {
    Path normalized = path.toAbsolutePath().normalize();
    Path root = dataRoot.toAbsolutePath().normalize();
    if (!normalized.startsWith(root)) {
        throw new IllegalArgumentException("Path escapes data root: " + path);
    }
    return root.relativize(normalized).toString().replace('\\', '/');
}

public Path resolve(String storedPath) {
    if (!StringUtils.hasText(storedPath)) {
        throw new IllegalArgumentException("stored path is required");
    }
    Path raw = Path.of(storedPath);
    Path resolved = raw.isAbsolute()
        ? raw.normalize()
        : dataRoot.resolve(storedPath.replace('\\', '/')).normalize();
    if (!resolved.startsWith(dataRoot)) {
        throw new IllegalArgumentException("Stored path escapes current data root: " + storedPath);
    }
    return resolved;
}
```

Use `toRealPath()` only at the point where existence is required, such as content reads/downloads, source file validation, and temp cleanup. Do not require real paths for display-only rows that may legitimately reference dropped historical workspace/output files.

### Persistence Contract

New writes store data-root-relative values:

- `run_output_artifacts.file_path`: relative file path under `dataRoot`.
- `plan_runs.output_directory`: relative output directory under `dataRoot`.
- `plan_runs.temp_workspace_path`: relative temp directory under `dataRoot`.
- `workflow_runs.workspace_path`: relative workflow temp directory under `dataRoot`.
- `workflow_runs.output_dir`: relative output directory under `dataRoot`.
- `job_runs.workspace_path`: relative persistent job workspace under `dataRoot`, when enabled.
- `job_runs.output_dir`: relative output directory under `dataRoot`.
- `workspace_links.target`: relative when `PATH` target resolves under `dataRoot`.

Existing absolute values remain compatibility-read only:

- Accept only if they still resolve under current `dataRoot`.
- If they point to an old root, report stale/missing path in the affected operation and leave rows unchanged.
- Do not rewrite old rows during startup.

Already relative fields stay relative:

- `workspaces.root_relative_path`.

### Chat Carry-Forward Contract

No runtime auto-copy is added. The operator must perform a controlled copy before switching roots:

```text
old data root/chats/ -> new data root/chats/
old ./chat-memory.db -> new Magenta root/magenta.sqlite
```

Only `chats/` is carried forward by policy. Workspace/output/runtime directories are not copied by this implementation plan. Do not delete the old root or old workspace files.

### Error Handling And Observability

- Stale old-root absolute values should fail individual file operations with clear messages, not fail application startup.
- Missing old output/workspace files should not delete DB records.
- Startup should create the Magenta root, SQLite parent, and data root, then lazily create chat/workspace/output directories as existing services do.
- Log root initialization paths once at startup without printing secrets or model API keys.

## 6. Ordered Implementation Plan

### Phase 1: Root Defaults And SQLite Placement

Files likely edited:

- `src/main/resources/application.yml`.
- `config/ai-config.example.json`.
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiUserConfigConfiguration.java`.
- New root/datasource configuration class under `src/main/java/io/mindspice/magenta2/...`.
- Tests under `src/test/java/io/mindspice/magenta2/ai/config/user/` or a new root config test package.

Steps:

1. Add `magenta.root.path` default to `application.yml`.
2. Change default datasource from `jdbc:sqlite:./chat-memory.db?foreign_keys=true` to root-owned SQLite, preferably `jdbc:sqlite:${magenta.root.path}/magenta.sqlite?foreign_keys=true`.
3. Add startup-safe parent directory creation before the datasource connects. If using a custom datasource bean, it must:
   - Use the configured `spring.datasource.url`.
   - Create parent directories only for file-backed SQLite URLs.
   - Ignore in-memory URLs.
   - Preserve `spring.datasource.driver-class-name`.
4. Update AI config loading so missing `dataRoot` resolves to `<magenta.root.path>/root`, and relative `dataRoot` resolves under `magenta.root.path`.
5. Update `config/ai-config.example.json` to remove the host-specific absolute `dataRoot` or replace it with a relative root value.

Validation gate after Phase 1:

- Unit tests for:
  - Missing AI `dataRoot` becomes `<magenta.root.path>/root`.
  - Relative AI `dataRoot` resolves under `<magenta.root.path>`.
  - Absolute AI `dataRoot` remains supported.
  - SQLite parent directory is created for file-backed default URL.
  - In-memory datasource URLs are not modified.
- Focused command:
  - `mvn -Dtest=ExternalAiConfigLoaderTest test`
  - Add and run any new root/datasource config test directly.

Commit point:

- Commit after Phase 1 passes: `feat: root magenta runtime defaults`.

### Phase 2: Root-Relative Path Helper

Files likely edited:

- New `RootRelativePathService` under `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/`.
- `WorkspaceDirectoryService` only if exposing a clean helper or canonical root is needed.
- Tests under `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/`.

Steps:

1. Implement the helper with `store(Path)`, `resolve(String)`, `resolveExistingFile(String)`, and optional `display(String)`.
2. Keep slash-separated stored values for deterministic DB assertions.
3. Reject storage of paths outside current `dataRoot`.
4. Accept absolute compatibility paths only under current `dataRoot`.
5. Reject old-root/outside-root absolute paths with a specific message.
6. Do not create directories during plain `resolve`; callers own creation.

Validation gate after Phase 2:

- Unit tests for relative storage, relative resolution, current-root absolute compatibility, outside-root rejection, missing relative path display, and Windows separator normalization.
- Focused command:
  - `mvn -Dtest=RootRelativePathServiceTest test`

Commit point:

- Commit after Phase 2 passes: `feat: add root-relative path resolver`.

### Phase 3: Output Artifact Path Semantics

Files likely edited:

- `OutputArtifactService`.
- `RunOutputArtifact` only if adding helper methods, not if string field remains enough.
- `WorkspaceRepository` only if tests need no schema change.
- `OutputController` only if it independently resolves `filePath` for downloads; inspect before editing.
- Existing output tests:
  - `OutputControllerTest`.
  - `OutputArtifactServiceAttributionTest`.
  - Add focused path-semantics tests.

Steps:

1. Inject `RootRelativePathService` into `OutputArtifactService`.
2. In every `saveArtifact` call path:
   - Store `rootRelativePathService.store(filePath)` instead of absolute `Path.toString()`.
3. In `loadContent`, resolve with `rootRelativePathService.resolveExistingFile(artifact.filePath())`.
4. In `discoverLooseArtifacts` and `publishExistingFile`, keep source/output confinement checks but persist relative artifact paths.
5. Preserve compatibility reads for existing absolute current-root `run_output_artifacts.file_path` values.
6. Do not rewrite existing rows.

Validation gate after Phase 3:

- Unit tests that:
  - New materialized `file_path` is relative.
  - `loadContent` reads a relative artifact.
  - `loadContent` still reads an absolute artifact under current `dataRoot`.
  - An absolute artifact outside current `dataRoot` is rejected clearly.
  - `discoverLooseArtifacts` saves relative paths.
- Focused command:
  - `mvn -Dtest=OutputArtifactServiceAttributionTest,OutputControllerTest test`
  - Add direct test class if coverage is clearer.

Commit point:

- Commit after Phase 3 passes: `feat: store output artifact paths relative to root`.

### Phase 4: Plan, Workflow, And Job Run Path Semantics

Files likely edited:

- `PlanService`.
- `WorkflowRunner`.
- `JobService`.
- Possibly `JobExecutionSummary` display mapping.
- Tests:
  - `PlanServiceTest`.
  - `WorkflowRunnerTest`.
  - `JobServiceTest`.
  - `OrchestrationRuntimeTest`.

Steps:

1. Inject `RootRelativePathService` where run path strings are persisted.
2. `PlanService.startRun`:
   - Store `temp_workspace_path` as relative.
   - Store `output_directory` as relative.
   - Keep execution contexts/tool contexts using real host paths, not relative strings, when tools need filesystem access during the current run.
3. `PlanService.materializeRunOutputs`, `discoverLooseArtifactsForRun`, `cleanupTempForRun`, and `workspaceRuntimeContext`:
   - Resolve stored paths through the helper before filesystem operations.
   - Display either relative aliases or helper display paths, but do not persist absolute paths.
4. `WorkflowRunner.createRun`:
   - Store `workspace_path` and `output_dir` as relative.
   - Keep async execution context paths absolute/current-run safe where file tools need host paths.
5. `WorkflowRunner.outputPathFor` and resume paths:
   - Resolve stored `output_dir`/`workspace_path` through the helper.
   - Existing absolute current-root rows remain readable.
   - Old-root absolute rows fail on actual filesystem use, which is acceptable because active/waiting migration is out of scope.
6. `JobService.startRun`:
   - Store `job_runs.workspace_path` and `job_runs.output_dir` as relative.
   - Keep `JobExecutionSummary` stable for UX by showing resolved display paths if the UI expects host-looking paths.
7. Do not change table names or column names.

Validation gate after Phase 4:

- Unit/integration tests that:
  - New plan runs store relative `output_directory` and `temp_workspace_path`.
  - Plan output materialization works from relative run paths.
  - Temp cleanup resolves a relative temp path and refuses outside-root absolute paths.
  - New workflow runs store relative `workspace_path` and `output_dir`.
  - Workflow output materialization works from relative output dir.
  - New job runs store relative `workspace_path` and `output_dir`.
  - Job summaries still render meaningful path values.
- Focused command:
  - `mvn -Dtest=PlanServiceTest,WorkflowRunnerTest,JobServiceTest test`
  - Add narrower tests if full classes are too slow during edit gates.

Commit point:

- Commit after Phase 4 passes: `feat: store run workspace paths relative to root`.

### Phase 5: Workspace Links And Deterministic Workspace Roots

Files likely edited:

- `WorkspaceService`.
- `WorkspaceRepository` only if helper methods are needed.
- Tests:
  - `WorkspaceControllerTest`.
  - `WorkspaceRepositorySchemaMigrationTest`.
  - New `WorkspaceService` path test if needed.

Steps:

1. For `WorkspaceService.addLink` with `WorkspaceLinkType.PATH`:
   - Resolve and validate target as today.
   - Persist a root-relative target when the resolved path is under current `dataRoot`.
   - Continue reading old absolute current-root links as compatibility values.
2. Verify `workspaces.root_relative_path` for new agent/project workspace records remains:
   - Agent: `agents/<agentId>/workspace`.
   - Project: `projects/<projectId>/workspace`.
3. Do not auto-normalize warm stale records like `agents/<id>` or `jobs/<id>` in this phase.
4. Document stale warm workspace roots as future repair/import work.

Validation gate after Phase 5:

- Tests that new PATH links persist relative targets.
- Tests that old absolute current-root link targets still validate/read.
- Tests that outside-root absolute targets are rejected.
- Focused command:
  - `mvn -Dtest=WorkspaceControllerTest,WorkspaceRepositorySchemaMigrationTest test`

Commit point:

- Commit after Phase 5 passes: `feat: store workspace link targets relative to root`.

### Phase 6: Chat Carry-Forward Documentation And UX Stability

Files likely edited:

- `docs/technical/configuration-operations.md`.
- `docs/technical/workspaces-tools-outputs.md`.
- `docs/technical/data-model.md`.
- `docs/end-user/chat.md` or `docs/end-user/quickstart.md`.
- Tests:
  - `ChatFileServiceTest`.
  - Any chat session/controller tests affected by datasource/root changes.

Steps:

1. Keep `ChatFileService` behavior unchanged unless root-relative helper integration is required indirectly.
2. Add documentation for manual carry-forward:
   - Stop Magenta.
   - Back up `chat-memory.db` and old root.
   - Create `<magenta.root.path>`.
   - Copy existing DB to `<magenta.root.path>/magenta.sqlite`.
   - Copy `oldDataRoot/chats/` to `<magenta.root.path>/root/chats/`.
   - Do not copy workspace/output/runtime directories for this cleanup unless the operator explicitly wants archival outside Magenta.
3. State clearly that import/repair/admin migration tooling is future work.
4. Confirm session/chat UI docs say behavior remains backed by existing session metadata and chat file discovery.

Validation gate after Phase 6:

- `ChatFileServiceTest` still passes.
- A focused app/browser validation later must confirm chat file listing/download still works after manually seeded copied files.

Commit point:

- Commit after Phase 6 passes: `docs: document root-relative chat carry-forward`.

### Phase 7: Docs, Internal-Dev Closeout, And Final Integration

Files likely edited:

- `.internal-dev/changelogs/<date>-root-relative-workspace-migration.md`.
- `.internal-dev/knowledge/<...>.md` if new reusable root/path facts are discovered.
- `.internal-dev/notes/<...>.md` for confirmed deferred ideas, including migration CLI/admin repair/startup auto-repair.
- `.internal-dev/bugs/<...>.md` only if out-of-scope bugs are found.
- Relevant package `AGENTS.md` files only if ownership/public-surface guidance changes.

Steps:

1. Read `.internal-dev/AGENTS.md` before closeout.
2. Record changelog for the implementation.
3. Record future work notes for:
   - One-time migration CLI with dry-run/apply.
   - Admin import/API.
   - Startup diagnostics or auto-repair, if still desired.
   - Deterministic rewrite of old absolute path rows.
   - Workspace root normalization for warm DBs.
4. If a bug report is created under `.internal-dev/bugs/`, ask the user before filing GitHub Issues.
5. Archive finalized plan/bug artifacts only as directed by `.internal-dev/AGENTS.md`; do not archive this active implementation plan until the implementation is complete.
6. Stage only files owned by this migration and ignore unrelated dirty work.

Final commit point:

- After full validation and closeout, commit implementation/docs/internal-dev artifacts together: `feat: use root-relative magenta workspace storage`.

## 7. Orchestrate-Plan Execution Graph

### Shared Notes

- All agents read and append concise notes to `.codex-orchestration/root-relative-workspace-migration/notes.md`.
- Code-editing subplans run strictly serially.
- Non-mutating review, validation design, and docs review tasks may run in parallel.
- Testing and validation agents use `gpt-5.3-codex` with reasoning effort `medium`, per repo guidance.

### Execution Graph

```text
P0 Planning complete
 |
 +-- R1 non-mutating review: root/config/data-source plan
 +-- R2 non-mutating review: path-column/storage semantics
 +-- R3 non-mutating test-design review
 |
 E1 Root defaults and SQLite placement
 |
 V1 Unit validation gate + root/config review remediation loop
 |
 E2 Root-relative path helper
 |
 V2 Helper unit validation gate + remediation loop
 |
 E3 Output artifact relative persistence
 |
 V3 Output artifact tests + stale-path compatibility review
 |
 E4 Plan/workflow/job run relative persistence
 |
 V4 Run execution tests + smoke targeted review
 |
 E5 Workspace links/root records
 |
 V5 Workspace tests + compatibility review
 |
 E6 Chat carry-forward docs and UX stability pass
 |
 V6 Chat file tests + focused browser/chat validation subagent
 |
 E7 Docs and .internal-dev closeout
 |
 V7 Final validation, startup smoke, closeout review
 |
 Final commit and handoff
```

### Serial Code-Editing Subplans

- `E1`: Root defaults, datasource parent creation, AI config dataRoot defaulting.
- `E2`: Root-relative path helper and direct tests.
- `E3`: Output artifact storage/read paths.
- `E4`: Plan, workflow, and job run path storage/read paths.
- `E5`: Workspace link path storage and deterministic workspace root verification.
- `E6`: Documentation for chat carry-forward and no-runtime-migration policy.
- `E7`: `.internal-dev` closeout, docs consistency, final commit.

### Safe Parallel Non-Mutating Tasks

- `R1`: Review root/config/datasource risks before `E1`.
- `R2`: Review all DB path columns and code read/write call sites before `E2/E3`.
- `R3`: Draft focused test matrix before `E1`.
- After each edit phase, validation agents may inspect changed files and run read-only checks while the main implementation agent waits.
- Final closeout review may inspect docs, `.internal-dev`, shared notes, and git state without editing.

### Validation Gates

- Every edit phase has a validation gate before the next edit starts.
- Failed validation sends work to a remediation subplan that owns only the failed phase's changed files.
- After remediation, rerun the failed checks before proceeding.
- Do not mark any phase complete if startup or execution validation is blocked by missing local services/secrets; stop and ask the user if the blocker is alpha-blocking per repo guidance.

### Remediation Policy

- Prefer local fixes in the phase that introduced the failure.
- Do not broaden scope to migration tooling, auto-repair, or destructive cleanup to make a failing test pass.
- If old absolute paths outside current root are involved, the expected behavior is a clear stale-path failure, not automatic repair.
- Record failed check output and remediation notes in shared notes.

### Phase Commit Points

- Commit after each validated implementation phase:
  - Phase 1 root defaults.
  - Phase 2 path helper.
  - Phase 3 output artifacts.
  - Phase 4 plan/workflow/job paths.
  - Phase 5 workspace links.
  - Phase 6 docs/chat carry-forward.
  - Phase 7 closeout/final validation.
- Each commit must include only files owned by this migration phase plus required docs/internal-dev updates for that phase.

## 8. Validation Plan

### Automated Tests To Add Or Update

- Root/default tests:
  - Missing AI `dataRoot` defaults to `<magenta.root.path>/root`.
  - Relative AI `dataRoot` resolves under `<magenta.root.path>`.
  - Absolute AI `dataRoot` remains supported.
  - Default SQLite parent directory exists before datasource use.
- Path helper tests:
  - Store relative path for files/directories under `dataRoot`.
  - Resolve relative stored path.
  - Resolve absolute current-root compatibility path.
  - Reject absolute old-root/outside-root path.
  - Preserve missing relative historical path as displayable but fail only when existence is required.
- Output artifact tests:
  - Materialized artifacts store relative `file_path`.
  - Artifact content reads from relative path.
  - Compatibility read of current-root absolute `file_path`.
  - Stale outside-root absolute `file_path` rejection.
- Plan/workflow/job tests:
  - New runs persist relative path columns.
  - Execution/materialization resolves relative path columns.
  - Temp cleanup handles relative temp path.
  - Run summaries/UI read models remain meaningful.
- Chat file tests:
  - Existing `ChatFileServiceTest` still passes.
  - Add a seeded root test if necessary to show copied `chats/<conversationId>/files` is discovered from the new data root.
- Schema tests:
  - Existing schema shape remains compatible; no accidental new path columns or dropped columns.

### Commands

Run focused checks after each phase, then full checks before final handoff:

```bash
mvn -Dtest=ExternalAiConfigLoaderTest test
mvn -Dtest=RootRelativePathServiceTest test
mvn -Dtest=OutputArtifactServiceAttributionTest,OutputControllerTest test
mvn -Dtest=PlanServiceTest,WorkflowRunnerTest,JobServiceTest test
mvn -Dtest=WorkspaceControllerTest,WorkspaceRepositorySchemaMigrationTest test
mvn -Dtest=ChatFileServiceTest test
mvn test
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

### Browser/Manual Validation

Because chat/session UX must remain stable:

- Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before browser validation.
- Run Playwright validation in a subagent, not inline.
- Start the app with a temporary Magenta root and a seeded copied chat tree.
- Validate:
  - Existing sessions list still renders.
  - Selecting a session shows prior messages.
  - Chat file panel lists seeded files under `chats/<conversationId>/files`.
  - Chat file download works.
  - Output/project/job pages render even when old output/workspace files were not copied.
- Capture screenshots for agent-side review.

### Acceptance Criteria

- Fresh install creates/uses the configured Magenta root.
- Default SQLite DB file is inside the Magenta root, not `./chat-memory.db`.
- Default data root is under the Magenta root and is created automatically.
- New Magenta-owned path rows are relative to `dataRoot`.
- Existing absolute current-root rows remain readable.
- Old-root absolute rows do not crash startup and fail only at the operation that needs the missing/stale file.
- Existing chat files are preserved when the operator copies `chats/` into the new data root.
- Workspace files are not copied, repaired, or deleted by this implementation.
- No startup migration, admin API, import command, symlink, or archive behavior is introduced.
- `mvn test` and bounded Spring startup smoke pass.

## 9. Handoff Checklist

- [ ] Confirm branch is `root-relative-workspace-migration`.
- [ ] Confirm no unrelated dirty files are staged or modified by implementation agents.
- [ ] Implement root defaults and SQLite-in-root behavior.
- [ ] Implement root-relative path helper.
- [ ] Store new output artifact paths as relative.
- [ ] Store new plan/workflow/job path columns as relative.
- [ ] Store new workspace link PATH targets as relative.
- [ ] Preserve compatibility reads for absolute paths under current `dataRoot`.
- [ ] Reject old-root/outside-root absolute paths clearly without startup failure.
- [ ] Keep chat file discovery behavior unchanged.
- [ ] Document manual DB and chat file copy steps.
- [ ] Document future migration CLI/admin import/startup repair as out of scope.
- [ ] Update relevant technical/end-user docs.
- [ ] Complete `.internal-dev` changelog, knowledge/notes/bugs as required.
- [ ] Ask user before filing GitHub Issues for any bug reports.
- [ ] Run focused tests after each phase.
- [ ] Run `mvn test`.
- [ ] Run bounded Spring startup smoke.
- [ ] Run Playwright chat/session/file validation through a validation subagent.
- [ ] Commit at each validated phase and record commit hashes in shared notes.
- [ ] Final response reports changed files, validation, risks, blockers, and residual future work.

## 10. Risks And Gotchas

- Spring datasource initialization can happen before ordinary beans create directories. Directory creation for the SQLite parent must happen before the datasource connects.
- `ExternalAiConfigLoader` currently does not resolve `dataRoot`; adding defaulting in the Spring configuration layer avoids breaking static loader tests and keeps operator properties available.
- Persisting relative paths while current run contexts still need host paths requires care: store relative strings in DB, but pass resolved absolute/current paths to file/shell/tool execution contexts.
- `Path.of(stored)` will treat relative DB values as process-relative if any call site bypasses the helper. Grep for `Path.of(run.outputDirectory())`, `Path.of(run.outputDir())`, `Path.of(run.workspacePath())`, and `Path.of(artifact.filePath())` during implementation.
- Existing JSON blobs can still contain absolute paths. This is accepted and documented as future repair work.
- Existing old-root output artifact rows may remain visible but unreadable if output files are intentionally not copied. The UI should not crash.
- Workspace files are "dropped" by not carrying them forward; do not delete the old root.
- If the new default root equals the old example root on the developer machine, tests must still simulate distinct old/new roots so stale-path behavior is covered.
