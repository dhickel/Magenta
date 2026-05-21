# Scope

Non-mutating test-design review for the root-relative workspace migration, covering Phases 1-7 from `.internal-dev/plans/root-relative-workspace-migration/implementation-plan.md`.

This review defines required tests, exact assertions, and validation commands. It does not edit production code. All tests must use isolated roots under `/tmp` or JUnit `@TempDir`; no command should read from or mutate the normal `./chat-memory.db`, `${user.home}/.magenta`, or any operator data root.

# Existing Test Coverage

- `ExternalAiConfigLoaderTest` already verifies JSON/YAML loading and prompt path resolution. It does not cover `magenta.root.path`, missing `dataRoot`, relative `dataRoot`, absolute `dataRoot`, or datasource parent creation.
- `PublicApiRouteBindingTest` already uses an isolated `/tmp` SQLite file and `/tmp` data root through `@DynamicPropertySource`. It is the best existing Spring context/API smoke candidate for seeded chat file carry-forward and route binding.
- `ChatFileServiceTest`, `ChatFileControllerTest`, and `ChatServiceTest#listSessionsIncludesChatFileOutputCount` already verify `chats/<conversationId>/files`, relative descriptors, download confinement, and session output counts.
- `OutputArtifactServiceAttributionTest` already covers materialization, loose artifact discovery, publish-existing-file, path traversal rejection, symlink escape rejection, and missing/broken file handling. Several assertions currently assume `artifact.filePath()` is directly usable as an absolute `Path`.
- `OutputControllerTest` covers output content and attribution filters. It currently constructs `OutputArtifactService` directly and will need the root-relative helper in setup once injected.
- `PlanServiceTest` has strong coverage for task run output allocation, effective workspace resolution, chat execution context propagation, temp cleanup, loose artifact detection, and chat file separation. Many assertions currently call `Path.of(run.outputDirectory())` or `Path.of(run.tempWorkspacePath())`; after migration these should assert persisted relative values and separately resolve them for filesystem checks.
- `WorkflowRunnerTest` covers durable workflow output directories, async task context propagation, delegation child runs, waiting/resume behavior, and workflow temp cleanup. It also directly calls `Path.of(finished.workspacePath())` and `Path.of(finished.outputDir())`.
- `JobServiceTest` covers job output allocation, persistent job workspace allocation, project scoped job workspaces, execution summary display, recurrence, cancel, and assignment ownership. Current assertions are substring-oriented and can be adapted to relative strings, but filesystem assertions need explicit resolution.
- `WorkspaceControllerTest` has light workspace/link coverage. There is no focused `WorkspaceServiceTest` for path-link persistence semantics.
- `WorkspaceRepositorySchemaMigrationTest` verifies schema shape and legacy workspace root migration. It should remain schema-focused; path semantics belong in service tests.

# Required New Tests

## Phase 1: Root Defaults And SQLite Placement

Add `AiUserConfigConfigurationTest` or `MagentaRootConfigurationTest` under `src/test/java/io/mindspice/magenta2/ai/config/user/` or `src/test/java/io/mindspice/magenta2/core/`.

Required assertions:

- Missing AI `dataRoot` resolves to `<magenta.root.path>/root`.
  - Arrange: temp root `/tmp/.../magenta-root`, AI config JSON with no `dataRoot`.
  - Assert: bean/config post-processing returns `config.dataRoot().normalize()` equal to `root.resolve("root").normalize()`.
- Relative AI `dataRoot` resolves under Magenta root, not process working directory.
  - Arrange: `"dataRoot": "custom-data"`.
  - Assert: `config.dataRoot()` equals `root.resolve("custom-data")`; assert it is not `Path.of("custom-data").toAbsolutePath().normalize()` unless cwd coincidentally matches the temp root.
- Absolute AI `dataRoot` remains supported.
  - Arrange: `"dataRoot": "/tmp/.../external-data"`.
  - Assert: returned path equals that absolute path.
- Default datasource URL is root-owned.
  - Assert `application.yml` effective default contains `jdbc:sqlite:${magenta.root.path}/magenta.sqlite?foreign_keys=true` or the equivalent configured default, and no longer contains `jdbc:sqlite:./chat-memory.db`.
- File-backed SQLite parent directory is created.
  - Arrange: temp root whose parent exists but `root` does not, `spring.datasource.url=jdbc:sqlite:/tmp/.../root/magenta.sqlite?foreign_keys=true`.
  - Assert: before datasource access parent does not exist; after datasource bean/config initialization `Files.isDirectory(root)` is true.
- In-memory SQLite URL is not converted or directory-created.
  - Arrange: `jdbc:sqlite::memory:?foreign_keys=true`.
  - Assert: configured URL remains exactly in-memory and no root child is created only because of datasource setup.
- Fresh install Spring context uses isolated `/tmp` root.
  - Add or extend a Spring context test with `--magenta.root.path=/tmp/.../fresh-root`, no datasource override unless testing default placement.
  - Assert root directory exists, `root/root` data directory exists after `WorkspaceDirectoryService` initialization, and `root/magenta.sqlite` exists after schema init.

Update `ExternalAiConfigLoaderTest` only if defaulting is intentionally placed in the loader. If defaulting stays in Spring configuration, keep loader tests focused on raw file loading and add a separate configuration test.

## Phase 2: Root-Relative Path Helper

Add `RootRelativePathServiceTest` under `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/`.

Required assertions:

- `store` persists a data-root child as slash-separated relative text.
  - Arrange: data root `/tmp/.../data`, file path `data/agents/a/workspace/outputs/report.txt`.
  - Assert: `store(path)` equals `agents/a/workspace/outputs/report.txt`; assert it does not contain `dataRoot.toString()`.
- `store` normalizes redundant path segments.
  - Arrange: `data/agents/a/workspace/../workspace/out.txt`.
  - Assert: stored value is `agents/a/workspace/out.txt`.
- `store` rejects outside-root paths.
  - Arrange: `/tmp/.../outside.txt`.
  - Assert: `IllegalArgumentException` message contains `escapes data root`.
- `resolve` maps relative stored paths under current data root without requiring existence.
  - Arrange: stored `runtime/task-runs/run-1`.
  - Assert: resolved path equals `dataRoot.resolve("runtime/task-runs/run-1").normalize()`.
- `resolveExistingFile` requires existence and regular file when intended.
  - Arrange: existing `outputs/a.txt`, missing `outputs/missing.txt`, directory `outputs/dir`.
  - Assert: existing resolves to real path under data root; missing and directory throw clear messages.
- Current-root absolute compatibility is accepted.
  - Arrange: existing absolute file under data root.
  - Assert: resolve/load helper returns that path.
- Old-root/stale absolute path is rejected.
  - Arrange: absolute `/tmp/old-root/root/chats-or-outputs/file.txt` while service data root is `/tmp/new-root/root`.
  - Assert: exception message contains `outside current data root` or `stale` and does not create anything under old root.
- Windows separator normalization is deterministic.
  - Arrange: stored `agents\\a\\workspace\\out.txt`.
  - Assert: resolved path equals `dataRoot.resolve("agents/a/workspace/out.txt")`; stored output from `store` uses `/`.
- Traversal in stored relative values is rejected.
  - Arrange: `../outside.txt`, `agents/a/../../outside.txt`.
  - Assert: exception contains `escapes current data root`.

## Phase 3: Output Artifact Path Semantics

Update `OutputArtifactServiceAttributionTest` and `OutputControllerTest`. Add `OutputArtifactPathSemanticsTest` if this becomes too dense.

Required assertions:

- Materialized text/json/user-message artifacts persist relative `file_path`.
  - Assert `artifact.filePath()` equals `outputs-direct/summary.txt` or the expected data-root-relative suffix.
  - Assert `artifact.filePath()` does not start with `/`, does not contain `dataRoot.toString()`, and uses `/`.
  - Resolve through the helper for `Files.readString`.
- `FILE_PATH` outputs copied into output dir persist the destination relative path.
  - Existing `copiesValidFilePathInsideDataRootIntoOutputDirectory` should assert `artifact.filePath()` equals `agents/agent-1/workspace/outputs/run-copy/result.txt` or equivalent, and the resolved path content equals source content.
- `loadContent` reads relative artifact paths.
  - Assert `service.loadContent(artifact.id(), 10_000)` returns the file content.
- `loadContent` reads legacy absolute paths only when under current data root.
  - Insert or save a `RunOutputArtifact` with `filePath=dataRoot.resolve("legacy/current.txt").toString()`.
  - Assert content loads.
- `loadContent` rejects stale absolute old-root values.
  - Insert an artifact with `filePath=oldRoot.resolve("outputs/old.txt").toString()` where `oldRoot != dataRoot`.
  - Assert `IllegalArgumentException` contains `data root`, `stale`, or `outside current data root`.
  - Assert no file or directory is created under the old root.
- `discoverLooseArtifacts` persists relative paths.
  - Assert each discovered `artifact.filePath()` starts with the relative output dir and does not contain data root.
- `publishExistingFile` persists relative destination path and still copies when source is outside output dir but inside data root.
- Existing symlink escape, broken symlink, missing file, and outside-root source tests remain; update expected messages only if helper centralizes wording.
- `OutputControllerTest#querySupportsDirectAttributionFilters` should assert the content endpoint still returns content when artifact paths are relative.
- `OutputControllerTest#jobFallbackDoesNotMaskMissingDirectAssignmentAttribution` should resolve `run.outputDir()` before passing it to materialization if job run rows now store relative output dirs.

## Phase 4: Plan, Workflow, And Job Run Path Semantics

Update `PlanServiceTest`, `WorkflowRunnerTest`, and `JobServiceTest`. Prefer helper methods in each test class:

- `assertStoredRelative(String value, String expectedPrefix)`:
  - not null;
  - not absolute (`Path.of(value).isAbsolute()` false);
  - starts with expected slash-separated prefix;
  - does not contain `dataRoot.toString()`.
- `resolveStored(String value)`:
  - `dataRoot.resolve(value.replace('\\', '/')).normalize()` until the production helper is available to tests.

Plan tests:

- `agentContextAllocatesOutputUnderAgentDirectory`
  - Assert `run.outputDirectory()` starts with `agents/agent-1/workspace/outputs/`.
  - Assert resolved output directory exists.
  - Assert `run.tempWorkspacePath()` equals or starts with `runtime/task-runs/<run.id()>`.
- `chatExecutionWithAgentContextUpdatesHolderWithRunScopedOutputPath`
  - Assert persisted `run.outputDirectory()` is relative to project workspace output.
  - Assert `OrchestrationTaskContextHolder.current().hostWorkspacePath()`, `hostOutputPath()`, and `hostRunPath()` remain absolute/resolved host paths for tools, not persisted relative strings.
  - Assert project symlink exists under resolved temp workspace.
- `startRunUsesEffectiveWorkspaceTaskOutputDirectory`
  - Assert project and agent run persisted output dirs are relative.
  - Resolve before `toRealPath()`.
- `systemContextAllocatesOutputUnderSystemDirectory`
  - Assert persisted output dir starts with `agents/system/workspace/outputs/`; resolve before directory check.
- `completeRunCleansTempDirectoryAndDetectsLooseArtifacts`
  - Resolve `run.tempWorkspacePath()` before cleanup assertion.
  - Resolve `run.outputDirectory()` before writing loose artifact.
  - Assert temp resolved path no longer exists and discovered artifact file path is relative.
- Add stale cleanup rejection test.
  - Seed or update a plan run `temp_workspace_path` to an absolute path under `/tmp/old-root/root/runtime/task-runs/run-x`.
  - Complete/cancel cleanup path should throw or record a clear stale-path failure and must not delete that old-root directory.
- Add current-root absolute compatibility test for plan run operations if old absolute rows are expected to finish cleanup/materialization.

Workflow tests:

- `finalOutputsMaterializeIntoDurableWorkflowOutputDirectory`
  - Assert `finished.workspacePath()` starts with `runtime/workflow-runs/` and is relative.
  - Assert `finished.outputDir()` starts with `agents/system/workspace/outputs/workflows/<workflowId>/<runId>` and is relative.
  - Resolve both before `Files` and `deleteTempDir` checks.
  - Assert artifact file path is relative and resolves under resolved output dir.
- `taskNodesInheritCallerOrchestrationContextAcrossAsyncExecution`
  - Persisted workflow paths should be relative, but context seen by task node must carry absolute/resolved `hostWorkspacePath`, `hostRunPath`, and `hostOutputPath`.
- `delegationChildRunUsesActiveEffectiveWorkspaceContext`
  - Resolve child plan run output directory before filesystem assertion; assert child persisted output directory is relative.
- Waiting/resume tests:
  - Assert waiting run paths are relative and resolved directories exist before approval response.
  - Resume must work from relative `workspace_path` and `output_dir`.
- Add legacy current-root absolute compatibility test:
  - Create a waiting run with absolute `workspace_path`/`output_dir` under current data root and assert resume or output materialization still works.
- Add stale old-root rejection test:
  - Create/update a run with absolute old-root `output_dir`; assert filesystem use fails clearly and does not rewrite the row.

Job tests:

- `startRunDefaultsToNoPersistentWorkspaceAndWritesJobOutputUnderEffectiveWorkspace`
  - Keep `workspacePath()` null.
  - Assert `outputDir()` is relative, starts with `agents/system/workspace/outputs/jobs/<assignment>/<run>`, and resolves to a directory.
- `persistentJobWorkspaceIsExplicitAndAssignmentIsolated`
  - Assert `workspacePath()` and `outputDir()` are relative strings with expected prefixes and different assignment suffixes; resolve paths for directory checks.
- `projectScopedJobUsesProjectEffectiveDurableWorkspace`
  - Assert relative project prefixes and resolved directories.
- `executionSummaryBridgesPendingAssignmentAndCreatedRun`
  - Decide expected UX contract: if summary displays host paths, assert `summary.outputDirectory()` and `summary.persistentJobWorkspacePath()` are absolute/resolved and under data root; if summary displays stored values, assert they are relative and clearly meaningful. Do not leave this ambiguous.
- Add stale absolute output/workspace path summary test:
  - Seed job run with old-root absolute values.
  - Assert summary either marks stale/unavailable clearly or returns stored value without attempting filesystem mutation; no old-root directory creation/deletion.

## Phase 5: Workspace Links And Deterministic Workspace Roots

Add `WorkspaceServicePathTest` under `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/`. Keep `WorkspaceControllerTest` for controller not-found/filter behavior.

Required assertions:

- New agent workspace root remains `agents/<agentId>/workspace`.
- New project workspace root remains `projects/<projectId>/workspace`.
- Existing job workspace compatibility oddity remains unchanged unless deliberately changed: `WorkspaceService.jobWorkspace` stores `jobs/<jobId>`.
- New PATH link with relative target under workspace persists data-root-relative value.
  - Arrange: agent workspace `agents/a/workspace`, add link target `docs`.
  - Assert saved link target equals `agents/a/workspace/docs`, not just `docs`, if target normalization is data-root-relative as planned.
- New PATH link with absolute target under data root persists relative value.
  - Arrange: target `dataRoot.resolve("agents/a/workspace/docs").toString()`.
  - Assert saved link target equals `agents/a/workspace/docs`.
- Old absolute current-root link targets still validate/read.
  - Seed repository link directly with absolute current-root target.
  - Assert `workspaceService.links(workspace.id())` returns it or returns a display-normalized equivalent according to chosen compatibility contract.
- Absolute outside-root PATH link is rejected.
  - Assert message contains `escapes data root`.
- Relative traversal PATH link is rejected.
  - Target `../../outside`; assert rejection.
- Non-PATH link targets are not forced through filesystem normalization unless existing behavior already does so.

## Phase 6: Chat Carry-Forward Documentation And UX Stability

Update/add tests without adding runtime auto-copy:

- `ChatFileServiceTest` seeded carry-forward test.
  - Arrange: temp `newDataRoot/chats/<conversationId>/files/nested/seeded.md` as if copied by operator.
  - Assert `countFiles` is `1`, listing relative path is `nested/seeded.md`, and download resolves to that seeded file.
- `ChatFileControllerTest` should retain `listsDescriptorsWithoutAbsolutePaths`; assert seeded copied file paths never expose data root.
- `ChatServiceTest#listSessionsIncludesChatFileOutputCount` should remain and use a seeded copied file under temp data root.
- `PublicApiRouteBindingTest#chatRoutesBindWithoutCallingModelBackedExecution` already covers seeded file listing/download through API; update its dynamic properties to use `magenta.root.path=/tmp/.../magenta-root`, default data root if possible, and default SQLite path in one dedicated fresh-install route-binding test.
- Add documentation assertion test only if project has doc tests; otherwise validate by command-level grep:
  - docs mention copying old `chat-memory.db` to `<magenta.root.path>/magenta.sqlite`.
  - docs mention copying old `chats/` to `<magenta.root.path>/root/chats/`.
  - docs explicitly say workspace/output/runtime directories are not auto-carried-forward in this cleanup.

## Phase 7: Closeout And Integration

Required validation assertions are mostly process/integration:

- `.internal-dev/changelogs/<date>-root-relative-workspace-migration.md` exists and contains behavioral impact.
- `.internal-dev/notes/` records deferred migration CLI/admin import/startup repair only after user confirmation or if already confirmed in plan notes.
- Docs updated:
  - `docs/technical/configuration-operations.md` covers root, datasource default, override behavior, and manual carry-forward.
  - `docs/technical/workspaces-tools-outputs.md` covers relative persisted paths and stale absolute path handling.
  - `docs/technical/data-model.md` documents path columns storing data-root-relative values for new writes.
  - `docs/end-user/chat.md` or `docs/end-user/quickstart.md` covers chat file carry-forward.
- No finalized active plan should be archived until the implementation is complete.
- Git status check before commit must stage only migration-owned files and not touch unrelated dirty files listed before this review.

# Per-Phase Validation Commands

Use isolated `/tmp` roots. For tests that instantiate Spring, pass fresh root/database values per run.

Phase 1:

```bash
mvn -Dtest=ExternalAiConfigLoaderTest,MagentaRootConfigurationTest,AiUserConfigConfigurationTest test
```

If the datasource test uses Spring context:

```bash
mvn -Dtest=MagentaRootFreshInstallSpringTest test -Dmagenta.root.path=/tmp/magenta-root-phase1-$(date +%s)
```

Phase 2:

```bash
mvn -Dtest=RootRelativePathServiceTest test
```

Phase 3:

```bash
mvn -Dtest=OutputArtifactServiceAttributionTest,OutputArtifactPathSemanticsTest,OutputControllerTest test
```

Phase 4:

```bash
mvn -Dtest=PlanServiceTest,WorkflowRunnerTest,JobServiceTest test
```

Phase 5:

```bash
mvn -Dtest=WorkspaceServicePathTest,WorkspaceControllerTest,WorkspaceRepositorySchemaMigrationTest test
```

Phase 6:

```bash
mvn -Dtest=ChatFileServiceTest,ChatFileControllerTest,ChatServiceTest,PublicApiRouteBindingTest test
```

Phase 7 focused regression:

```bash
mvn -Dtest=ExternalAiConfigLoaderTest,RootRelativePathServiceTest,OutputArtifactServiceAttributionTest,OutputControllerTest,PlanServiceTest,WorkflowRunnerTest,JobServiceTest,WorkspaceServicePathTest,WorkspaceControllerTest,ChatFileServiceTest,ChatFileControllerTest,ChatServiceTest,PublicApiRouteBindingTest test
```

Final unit/integration sweep when time permits:

```bash
mvn test
```

Doc/static checks:

```bash
rg -n "chat-memory.db|magenta.sqlite|dataRoot|root-relative|workspace/output/runtime|chats/" docs config src/main/resources/application.yml
```

# Browser Validation Plan

Browser validation should be run by a validation subagent using Playwright MCP, after Phase 6 and again after Phase 7 if docs/UI/API wiring changed. Use a fresh `/tmp` root and database. Do not use the normal user database.

Start command:

```bash
MAGENTA_TEST_ROOT=/tmp/magenta-browser-root-$(date +%s)
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=18080 --magenta.root.path=${MAGENTA_TEST_ROOT} --app.ai.config-path=${MAGENTA_TEST_ROOT}/ai-config.json --magenta.executor.chat-threads=4"
```

Before startup, create `${MAGENTA_TEST_ROOT}/ai-config.json` with minimal local/stub model config and either no `dataRoot` or `"dataRoot": "root"` so the browser pass validates root defaulting. If local model services are unavailable, use a deterministic OpenAI-compatible stub as documented in `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`.

Focused browser assertions:

- Open `http://localhost:18080/chat`; assert chat root, form, input, history, model selectors, and planning panel render.
- Seed a conversation row into `${MAGENTA_TEST_ROOT}/magenta.sqlite` or create it through API; seed `${MAGENTA_TEST_ROOT}/root/chats/<conversationId>/files/seeded.md`; verify `/api/chat/sessions` reports `outputCount=1`.
- In browser, switch/open that session and verify the Outputs panel lists `seeded.md` with a relative path only.
- Click/download the seeded chat file and assert response body matches; assert no absolute root path appears in DOM or JSON.
- Exercise `/outputs` after creating a small output artifact through an API/test fixture; assert content view/download works for relative artifact paths.
- Run one short chat stream if model/stub is available; assert named SSE events and persisted history as described in the live-chat workflow guide.
- Capture screenshots of `/chat` with the seeded output panel and `/outputs` with an artifact row.
- Capture console and network logs; unexpected 500s or JavaScript exceptions block sign-off.

# Startup Smoke Plan

Run a bounded startup smoke after Phase 1, Phase 4, Phase 6, and final Phase 7. Use unique `/tmp` roots and do not rely on `./chat-memory.db`.

Default-root behavior smoke:

```bash
MAGENTA_ROOT=/tmp/magenta-smoke-root-$(date +%s)
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=0 --magenta.root.path=${MAGENTA_ROOT}"
test -d "${MAGENTA_ROOT}"
test -d "${MAGENTA_ROOT}/root"
test -f "${MAGENTA_ROOT}/magenta.sqlite"
```

Relative AI `dataRoot` smoke:

```bash
MAGENTA_ROOT=/tmp/magenta-smoke-relative-$(date +%s)
# ai config contains "dataRoot": "custom-root"
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=0 --magenta.root.path=${MAGENTA_ROOT} --app.ai.config-path=${MAGENTA_ROOT}/ai-config.json"
test -d "${MAGENTA_ROOT}/custom-root"
```

Datasource override smoke:

```bash
MAGENTA_ROOT=/tmp/magenta-smoke-override-$(date +%s)
DB=/tmp/magenta-smoke-db-$(date +%s)/override.sqlite
timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=0 --magenta.root.path=${MAGENTA_ROOT} --spring.datasource.url=jdbc:sqlite:${DB}?foreign_keys=true"
test -f "${DB}"
```

If startup fails because model provider secrets/services are unavailable, record the exact missing dependency. Do not mark final validation complete without user-approved blocker handling.

# Risks And Gaps

- Constructor churn is likely. `OutputArtifactService`, `PlanService`, `WorkflowRunner`, `JobService`, and `WorkspaceService` are directly instantiated in many tests, so adding `RootRelativePathService` injection will require broad test setup updates.
- Existing tests often use persisted path strings as host paths. These must be converted carefully so the tests do not accidentally force production code to keep storing absolute paths.
- Stale absolute path rejection needs direct tests. Without them, compatibility support for current-root absolutes can easily become unsafe old-root access.
- SQLite parent creation must be tested against file-backed URLs, not only `:memory:`. Otherwise fresh install behavior can regress while unit tests stay green.
- Browser validation can be blocked by local model availability or Playwright profile locks. The validation plan requires either MCP recovery or a documented deterministic stub; a skipped browser pass is a blocker unless the user explicitly approves.
- Current docs and `config/ai-config.example.json` include host-specific values and at least one API key-like value. This review does not change it, but Phase 1/6 docs/config updates should remove host-specific defaults and avoid leaking secrets in examples.
- No test should delete old roots. Tests for stale absolute paths should create old-root sentinel files/directories under `/tmp` and assert they remain after failure.

# Follow-ups

- Add a small shared test helper for `@TempDir` data root plus root-relative resolver setup to reduce repeated constructor changes.
- Consider a Spring `@DynamicPropertySource` fresh-install integration test as the canonical guard for root, data root, and SQLite placement.
- After implementation, update this review or append validation results to orchestration notes if any required assertion is intentionally changed by design.
