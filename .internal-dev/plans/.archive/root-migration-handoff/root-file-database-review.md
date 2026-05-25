# Root/File/Database Migration Review

Date: 2026-05-21

## Scope

This is a read-only review of how Magenta currently tracks filesystem roots, chat files, workspace files, output artifacts, temp/scratch paths, project/agent/job workspaces, and database references. No migration, file move, config change, code change, staging, or commit was performed.

Sources inspected:

- Root `AGENTS.md`
- `.internal-dev/notes/current-architecture-focus.md`
- `.internal-dev/plans/root-migration-handoff/agent-notes.md`
- `.internal-dev/plans/root-migration-handoff/handoff-report.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/orchestration-runtime.md`
- `docs/technical/data-model.md`
- `docs/technical/services.md`
- `docs/technical/configuration-operations.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/end-user/jobs.md`
- `.internal-dev/changelogs/2026-05-21-workspace-file-architecture-technical.md`
- `.internal-dev/changelogs/2026-05-21-services-ux-architecture-technical.md`
- `src/main/resources/application.yml`
- `config/ai-config.example.json`
- `src/main/resources/schema.sql`
- Root/path services and repositories under `ai/config/user`, `ai/chat/service`, `ai/chat/tool`, `ai/chat/plan`, `ai/orchestration/workspaces`, `ai/orchestration/workflow`, and `ai/orchestration/runtime`

I also performed read-only SQLite inspection of `chat-memory.db`, limited to table names, path columns, and row counts. I did not read message text or user-authored content.

## Current Root/File Model

### Configuration And Default Roots

Current configuration is split:

- Spring config path: `app.ai.config-path`, default `./config/ai-config.example.json`.
- SQLite database: `spring.datasource.url`, default `jdbc:sqlite:./chat-memory.db?foreign_keys=true`.
- AI/data root: `AiConfig.dataRoot` loaded from the external AI config file.
- Current example data root: `/home/hickelpickle/.magenta/root`.

`ExternalAiConfigLoader` validates models/agents/model defaults but does not itself require `dataRoot`. Workspace/file/shell services do require it:

- `WorkspaceDirectoryService` requires `AiConfig.dataRoot` and creates it with `Files.createDirectories(...).toRealPath()`.
- `WorkspaceService` also requires `AiConfig.dataRoot`.
- `AgentFileToolService` and `AgentShellToolService` require an existing `dataRoot` directory.

On a clean install using the current example config, filesystem data is already rooted under `.magenta/root`, but the SQLite DB still defaults to `./chat-memory.db` beside the app unless the datasource URL is overridden.

### Directory Layout

The current intended layout under `dataRoot` is:

- Chat files: `chats/<conversationId>/files/`
- Agent workspace: `agents/<agentId>/workspace/`
- Project workspace: `projects/<projectId>/workspace/`
- Durable workspace children: `work/`, `outputs/`, `runs/`, `scratch/`, `jobs/`
- Task outputs: `<effective-workspace>/outputs/tasks/<taskId>/<runId>/`
- Workflow outputs: `<effective-workspace>/outputs/workflows/<workflowId>/<runId>/`
- Job outputs: `<effective-workspace>/outputs/jobs/<assignmentId>/<jobRunId>/`
- Persistent job workspace, when enabled: `<effective-workspace>/jobs/<assignmentId>/`
- Task temp: `runtime/task-runs/<runId>/`
- Workflow temp: `runtime/workflow-runs/<runId>/`
- Legacy agent dirs: `agents/<agentId>/home` and `agents/<agentId>/outputs`, with a helper that can move them into `agents/<agentId>/workspace`.

The effective durable workspace rule is:

- `projectId` present: project workspace.
- `projectId` absent: executing agent workspace.
- `workspaceId`: compatibility metadata, not a project selector.

### Chat Files

Ordinary chat files live under:

```text
<dataRoot>/chats/<conversationId>/files/
```

There are no per-file database rows for ordinary chat files. The database references the conversation, not each file:

- `ai_chat_memory.conversation_id`
- `ai_chat_session_metadata.conversation_id`
- `audit_event.conversation_id`
- `agent_jobs.conversation_id`
- `plan_definitions.id = conversation_id` for `SESSION_PLAN`
- `assignment_conversation_links.conversation_id` for assignment transcript linkage

`ChatFileService` computes the directory from `WorkspaceDirectoryService.chatFiles(conversationId)`, recursively lists regular files from the filesystem, and returns descriptors with paths relative to the chat files directory. Downloads accept a path relative to the chat files directory and resolve it with real-path confinement.

Ordinary chat turns install a temporary file-tool context rooted at the conversation file directory. In that context, file tools resolve `workspace/` and `outputs/` to the chat file directory. Anonymous chat plan final messages are written as `final-message.md` or `final-message-N.md` in the same directory.

Path classification for chat files:

- Root: configured `dataRoot`.
- Directory: computed from `conversationId`.
- Listed/download paths: chat-file-directory relative.
- Per-file DB path: none.

### Workspace Files

Workspace records are stored in `workspaces`:

- `owner_type`
- `owner_id`
- `root_relative_path`
- `display_name`
- `metadata_json`

`root_relative_path` is stored relative to `dataRoot`, not absolute. `WorkspaceService` creates records for agents/projects/jobs and creates the configured root directory. `EffectiveWorkspaceResolver` uses the workspace record identity but uses `WorkspaceDirectoryService` to compute the actual agent/project filesystem path.

Workspace links are stored in `workspace_links.target`. For `PATH` links, `WorkspaceService` accepts either an absolute target under `dataRoot` or a target relative to the workspace root, then stores the original string. This means link targets can be relative or absolute depending on input.

Workspace file contents under `work/`, `scratch/`, `jobs/`, and untracked directories are filesystem-only unless explicitly published as output artifacts or referenced in unstructured JSON.

Warm-data divergence found in local `chat-memory.db`: all inspected `workspaces.root_relative_path` values are relative, but some are legacy/inconsistent with the current target layout, for example `agents/<id>` instead of `agents/<id>/workspace`, and `jobs/<id>` instead of `jobs/<id>/workspace`. Current execution paths are still computed by `WorkspaceDirectoryService`, but UI display and workspace summaries can expose those stale relative roots.

### Output Files

Output artifact metadata is stored in `run_output_artifacts`:

- `run_id`
- `plan_id` or workflow id in the `plan_id` column for workflow output materialization
- optional `agent_id`, `job_id`, `job_assignment_id`, `job_run_id`, `project_id`, `workspace_id`, `run_type`
- `output_name`
- `artifact_type`
- `file_name`
- `file_path`
- optional `content_json`
- `created_at`

`file_path` is currently stored as a concrete host path string. Because `WorkspaceDirectoryService` normalizes `dataRoot` to a real path, newly materialized output paths are effectively absolute host paths. Output downloads and inline content reads resolve `file_path` through `Path.of(filePath).toRealPath()` and reject paths outside the current `dataRoot`.

Local DB evidence: `chat-memory.db` currently has one `run_output_artifacts` row, and its `file_path` is absolute under `/home/hickelpickle/.magenta/root/...`.

### Plan/Task Runs

`plan_runs` stores:

- `workspace_id`: effective workspace DB id.
- `output_directory`: concrete output directory path string.
- `temp_workspace_path`: concrete task temp path string.
- `output_values_json`, `execution_evidence_json`, `deliverable_evidence_json`: unstructured values that may include paths when produced by model/tool behavior.

`PlanService.startRun` stores `temp_workspace_path` and `output_directory` from real filesystem paths. Output materialization later reads `run.outputDirectory()` and writes `run_output_artifacts` rows.

### Workflow Runs

`workflow_runs` stores:

- `workspace_path`: workflow temp path string.
- `output_dir`: concrete output directory path string.
- `workspace_id`, project/job/agent attribution, `artifact_ids_json`.
- final output JSON and node run JSON, which may contain path-like values.

`WorkflowRunner` creates `workspace_path` under `runtime/workflow-runs/<runId>` and `output_dir` under the effective workspace output tree when `EffectiveWorkspaceResolver` is available. Resume/waiting workflow execution reuses the stored paths.

### Job Runs

`job_runs` stores:

- `workspace_id`: effective workspace DB id.
- `workspace_path`: concrete persistent job workspace path when persistence is enabled.
- `output_dir`: concrete job output path.
- `work_item_runs_json`: child plan/workflow run ids and per-item output values.

Persistent job workspaces are assignment-scoped under the effective workspace at `jobs/<assignmentId>`.

### Assignment State And Checkpoints

`work_assignments` stores first-class IDs:

- `project_id`
- compatibility `workspace_id`
- `effective_workspace_id`
- `effective_workspace_kind`

It also stores `checkpoint_json`, `input_json`, `output_json`, and `evidence_json`. Those JSON blobs are untyped and can contain path strings. Current job execution checkpoint/evidence can include job workspace/output path values.

## Database/File Source Of Truth Matrix

| File class | Filesystem source of truth | DB source of truth | Stored path form | Notes |
|---|---:|---:|---|---|
| Ordinary chat files | Yes | Conversation existence only | Computed from `dataRoot` + conversation id; returned paths are relative to `chats/<id>/files` | No per-file DB rows. Moving same relative tree to a new root preserves files. |
| Chat final-message files | Yes | Conversation/session plan state only | Computed chat file path | `PlanService.persistChatFinalMessage` writes markdown files into chat files dir. |
| Workspace records | Directory bytes in FS | Yes, for owner/id/root/display/links/leases | `workspaces.root_relative_path` is relative; `workspace_links.target` may be relative or absolute under `dataRoot` | DB owns identity/metadata, FS owns contents. |
| Workspace `work/` files | Yes | No, unless separately referenced | Usually computed by alias/path | Filesystem-only working data. |
| Workspace `scratch/` files | Yes | No, unless separately referenced | Usually computed by alias/path | Filesystem-only durable scratch. |
| Persistent job workspace files | Yes | Job run path exists when enabled | `job_runs.workspace_path` is concrete host path | Contents are not individually indexed. |
| Task output artifacts | Yes, for bytes | Yes, for artifact index/provenance | `run_output_artifacts.file_path` concrete host path; `plan_runs.output_directory` concrete host path | DB and FS are jointly required for normal output browsing/download. |
| Workflow output artifacts | Yes, for bytes | Yes, for artifact index/provenance | `run_output_artifacts.file_path`, `workflow_runs.output_dir` concrete host paths | Workflow `artifact_ids_json` references artifact rows. |
| Job output artifacts | Yes, for bytes | Yes, for artifact index/provenance | `run_output_artifacts.file_path`, `job_runs.output_dir` concrete host paths | Output rows carry job assignment/run attribution when available. |
| Loose files in output dirs | Yes | Only if loose discovery registers them | After discovery, `run_output_artifacts.file_path` concrete host path | Loose discovery is compatibility behavior, not target contract. |
| Task temp dirs | Yes while retained | Plan run stores path | `plan_runs.temp_workspace_path` concrete host path | Cleanup treats temp as disposable after clean terminal completion unless retained. |
| Workflow temp dirs | Yes while active/waiting/retained | Workflow run stores path | `workflow_runs.workspace_path` concrete host path | Waiting/resume depends on stored path. |
| Agent/project symlink materializations | Yes | Mostly no; project/workspace ids in DB | Symlink target computed from current root | External moves can leave stale or broken symlinks in temp/run dirs. |
| File-tool writes outside active context | Yes | No | Relative/absolute under `dataRoot` | Legacy fallback when no orchestration/chat context exists. |
| AI config and prompt files | Host filesystem | No app DB source | Configured by `app.ai.config-path`; prompt paths relative to config file | Outside `dataRoot` unless operator config places them there. |
| SQLite DB | Host filesystem | Itself | `spring.datasource.url` | Current default is not inside `.magenta`. |

Summary: source of truth is shared by file class. Chat files and ordinary workspace contents are filesystem-led. Output artifacts are DB-indexed with filesystem bytes. Workspace identity, leases, assignments, jobs, projects, and run history are DB-led. Temp/run paths are mixed and become fragile for active or waiting work.

## Clean Install Behavior

With current defaults:

- The app reads `./config/ai-config.example.json`.
- The example config points `dataRoot` to `/home/hickelpickle/.magenta/root`.
- The SQLite DB defaults to `./chat-memory.db`.
- `schema.sql` runs on startup and creates the database schema.
- `WorkspaceDirectoryService` creates `dataRoot`.
- Agent/project/workspace/chat/output/temp subdirectories are created lazily as services are used.

The new target layout instantiates correctly for new agent/project/effective-workspace execution:

- Agent profile storage creation calls create `agents/<agentId>/workspace` and `outputs`.
- Project creation creates `projects/<projectId>/workspace` and a workspace record.
- Effective workspace resolution creates `work/`, `outputs/`, `runs/`, and `scratch/`.
- Chat file listing/download creates `chats/<conversationId>/files` as needed.
- Task/workflow/job execution creates their run temp/output directories as needed.

Caveats:

- A clean install does not eagerly create every top-level directory.
- `dataRoot` is required by workspace/file/shell services; a config without it can fail application wiring when those beans instantiate.
- The example `dataRoot` is absolute and user-specific. It is not a portable product default.
- The DB default remains outside `.magenta`, so a "clean `.magenta` root" is incomplete unless the datasource location is also decided.
- Warm databases can retain stale relative workspace roots, even though new execution computes current paths.

## Populated Root Move Failure Modes

### Moving Or Deleting The Root While Config Still Points At The Old Root

If the populated root is moved away or deleted and config still points to the old path:

- Startup may recreate an empty old `dataRoot`, making the failure look like missing data rather than a hard startup error.
- Chat file panels can show zero files because `chats/<conversationId>/files` is recomputed and recreated empty.
- Workspace DB rows still exist, but actual workspace contents under `work/`, `scratch/`, `outputs/`, and `jobs/` are gone from the configured root.
- Output artifact rows still query by DB metadata, but content/download fails because `file_path` cannot be resolved.
- Agent/project workspace pages can display workspace records while byte counts and file-derived summaries are wrong or zero.
- New runs may write into newly created empty directories, mixing fresh state with orphaned historical DB metadata.
- Active/waiting workflow runs that need `workflow_runs.workspace_path` can fail when the stored temp path is gone.
- Active task/job/workflow tool contexts can fail because file/shell tools call `toRealPath()` on stored paths.

### Moving The Root And Updating Config To The New Root Without Rewriting DB Paths

If files are copied/moved to a new root and `AiConfig.dataRoot` is updated:

- Chat files work if `chats/<conversationId>/files` exists at the same relative location under the new root.
- Workspace records mostly work because `root_relative_path` is relative, but legacy/stale relative values can still display incorrectly.
- New agent/project execution works because current paths are computed from IDs and the new root.
- Existing output downloads/content fail if `run_output_artifacts.file_path` still contains the old absolute root. If the old path no longer exists, `toRealPath()` fails. If it exists but resolves outside the new `dataRoot`, confinement rejects it.
- Existing `plan_runs.output_directory`, `plan_runs.temp_workspace_path`, `workflow_runs.workspace_path`, `workflow_runs.output_dir`, `job_runs.workspace_path`, and `job_runs.output_dir` continue to point at the old root.
- Resuming waiting workflows or jobs with stored paths can fail or write to the wrong root unless repaired.
- Path strings inside JSON columns can still point at old locations.
- Symlinks created in assignment temp dirs can point at the old project workspace target.

### Deleting The Root

Deleting the root is data loss for all filesystem-only file classes:

- Chat files
- Workspace `work/` and `scratch/`
- Persistent job workspace contents
- Temp/run state
- Unindexed loose files
- Output bytes

The DB can still retain conversations, sessions, assignments, runs, projects, jobs, and output metadata, but all file-backed reads become missing/orphaned. There is no current repair path that can reconstruct bytes from metadata, except for output rows with `content_json` for some JSON artifacts.

## Migration Options

### Option 1: Offline Root Copy Plus Absolute Path Rewrite

Copy the old populated root to the new `.magenta` root, update `AiConfig.dataRoot`, optionally move SQLite into `.magenta`, then rewrite known old-root absolute prefixes in DB path columns to the new-root prefix.

Known columns to rewrite:

- `run_output_artifacts.file_path`
- `plan_runs.output_directory`
- `plan_runs.temp_workspace_path`
- `workflow_runs.workspace_path`
- `workflow_runs.output_dir`
- `job_runs.workspace_path`
- `job_runs.output_dir`
- `workspace_links.target` only where it is an absolute path under the old root
- Known path strings inside JSON columns only if a safe structured rewrite is implemented

Pros:

- Smallest complete migration.
- Preserves existing DB identities and artifact metadata.
- Easy to dry-run by counting affected rows.
- Works well when the whole old root is copied intact.

Cons:

- Keeps absolute path storage, so the next root move has the same problem.
- Easy to miss unstructured JSON path strings.
- Needs an offline/quiesced app to avoid active runs writing during copy/rewrite.
- Does not clean up stale relative `workspaces.root_relative_path` values unless added to scope.

Risk: medium. Operationally simple, but incomplete as a long-term fix.

### Option 2: Convert Owned Path Columns To Root-Relative Storage

Add path-resolution behavior so owned paths under `dataRoot` are persisted root-relative, while compatibility reads accept older absolute paths and normalize them. Migrate existing absolute paths under the old root to root-relative values. Keep external/absolute workspace links explicit and separately classified.

Suggested root-relative columns or value forms:

- Artifact file path relative to `dataRoot`.
- Plan/workflow/job output/temp paths relative to `dataRoot`.
- Persistent job workspace path relative to `dataRoot`.
- Workspace links store either relative targets or typed external targets.

Pros:

- Solves the root-move class of bug for Magenta-owned paths.
- Aligns storage with the existing `workspaces.root_relative_path` pattern.
- Makes future `.magenta` moves mostly config-only when relative trees are preserved.
- Reduces confinement ambiguity because resolver always anchors owned paths to current `dataRoot`.

Cons:

- Larger code/schema behavior change.
- Requires careful compatibility for existing absolute rows.
- Must distinguish Magenta-owned paths from deliberate external absolute links.
- Requires tests across output downloads, content reads, file tools, run resume, temp cleanup, and UI display.

Risk: medium-high implementation risk, low long-term operational risk.

### Option 3: Import/Reindex/Repair Command

Implement an explicit admin/CLI repair operation with dry-run and apply modes. It should inspect a candidate root and database, then report and optionally repair:

- Chat file trees present/missing by conversation id.
- Workspace records whose relative roots are missing or stale.
- Legacy agent roots such as `agents/<id>` or `agents/<id>/home`/`outputs`.
- Output artifact rows whose files exist under old root, new root, or neither.
- Plan/workflow/job run path columns that can be re-derived from IDs.
- Active/waiting assignments/runs that must block migration or be marked needs-review.
- Broken project symlinks in runtime temp dirs.

It can also reindex discovered output files from expected output directories, but only with explicit operator approval because loose discovery is compatibility behavior and can over-index incidental files.

Pros:

- Best diagnostics and operator safety.
- Handles partial migrations and unknown filesystem state.
- Can produce a missing/orphaned report instead of silently recreating empty roots.
- Useful beyond this one migration.

Cons:

- More scope than a one-time path rewrite.
- Reindexing cannot perfectly reconstruct artifact semantics when DB rows are absent.
- Needs strong dry-run output and tests to avoid accidental metadata churn.

Risk: medium-high scope, but high value as a safety layer.

### Option 4: Compatibility Symlink From Old Root To New Root

Move/copy the root to `.magenta`, update config, and leave a symlink at the old absolute root pointing to the new root.

Pros:

- Fastest way to keep old absolute DB paths resolving.
- Can reduce immediate breakage while a proper migration is prepared.
- If `toRealPath()` resolves through the symlink into the new root, current confinement checks can still pass with the new `dataRoot`.

Cons:

- Depends on filesystem symlink support and permissions.
- Masks stale DB state.
- Not portable across hosts/containers.
- Fails if the old path is recreated as a real directory or cannot be symlinked.
- Does not solve stale JSON paths or display values.

Risk: low implementation effort, medium operational fragility.

### Option 5: Clean Root With Chat-Only Migration

Create a clean `.magenta` root and migrate only `chats/<conversationId>/files`. Archive old workspaces/outputs separately and do not try to preserve historical output downloads in the active DB.

Pros:

- Smallest low-risk data copy.
- Preserves ordinary chat files, which are the easiest and cleanest file class to move.
- Avoids complex path rewrites and stale active-run problems.

Cons:

- Existing output artifact downloads and workspace contents are intentionally not live.
- DB output metadata becomes misleading unless archived, deleted, or marked missing.
- Does not satisfy users who expect historical outputs/jobs/projects to continue working.

Risk: low technical risk, high product/data-continuity tradeoff.

## Recommended Option

Recommended path: Option 2 plus a constrained Option 3 repair tool, executed as an offline migration.

The durable fix is to make Magenta-owned paths root-relative long term. That matches the existing workspace record model and makes future root moves a matter of moving the preserved relative tree and updating `dataRoot`, rather than rewriting host-specific absolute strings every time.

Recommended sequence:

1. Quiesce the application. Require no active/running/waiting assignments unless the operator explicitly chooses to mark them blocked/needs-review.
2. Define a single Magenta home, for example `~/.magenta`.
3. Define filesystem data root under it, for example `~/.magenta/root`.
4. Decide whether SQLite also moves under it, for example `~/.magenta/magenta.sqlite`. Current defaults do not do this.
5. Copy the full old `dataRoot` tree to the new root, preserving relative paths.
6. Run a dry-run repair/migration command that reports path columns, stale workspace relative roots, missing files, active runs, symlinks, and orphaned artifact rows.
7. Migrate Magenta-owned absolute path columns under the old root to root-relative storage, or at minimum normalize them through a compatibility resolver that stores/reports root-relative owned paths going forward.
8. Normalize stale workspace relative roots where owner/type clearly determines the current path:
   - agent: `agents/<agentId>/workspace`
   - project: `projects/<projectId>/workspace`
   - legacy/runtime job root: decide whether to keep, archive, or map to current effective-workspace job assignment layout before rewriting.
9. Leave workspace links alone unless they are absolute paths under the old root and the operator approves rewriting them.
10. Start against the new config and run validation.

Why not Option 1 alone: it is the fastest viable one-time move, but it preserves the same root-coupling defect.

Why not startup auto-repair: root migration changes data ownership and can mask data loss. It should be explicit, dry-run first, and operator-approved.

Should Magenta implement import/reindex/repair? Yes, but as an explicit admin/CLI operation, not automatic startup behavior. It should:

- Refuse to apply while active assignments or nonterminal runs exist, unless a forced blocked-state mode is explicitly selected.
- Produce a dry-run report with row counts and example paths.
- Verify old/new roots by real path.
- Rewrite only Magenta-owned paths under the old root.
- Normalize workspace relative roots by owner type/id where deterministic.
- Verify each artifact file exists after rewrite.
- Mark or report missing artifacts instead of silently deleting rows.
- Reindex output directories only in an explicit mode, and label discovered artifacts as repaired/discovered.
- Report chat conversations with missing or empty file directories.
- Report unstructured JSON columns containing old-root strings for manual review or structured migration.

## Required Tests And Validation

Automated tests for the chosen implementation should include:

- Clean install with a temp `dataRoot` creates expected agent/project/chat/output/temp directories lazily.
- Fresh agent workspace record uses `agents/<id>/workspace`.
- Fresh project workspace record uses `projects/<id>/workspace`.
- Warm legacy agent filesystem dirs migrate from `home`/`outputs` into `workspace` without overwriting existing workspace data.
- Chat file listing/download still works after copying `chats/<conversationId>/files` to a new root with the same relative path.
- Chat file listings expose relative paths only.
- Output artifact content/download works after root-relative migration.
- Output artifact content/download rejects paths outside current `dataRoot`.
- Existing absolute `run_output_artifacts.file_path` rows under old root are compatibility-read and migrated.
- Existing absolute `run_output_artifacts.file_path` rows outside old root are not rewritten without explicit operator approval.
- `plan_runs.output_directory` and `temp_workspace_path` migrate or compatibility-resolve.
- `workflow_runs.workspace_path` and `output_dir` migrate or block resume with a clear diagnostic when missing.
- `job_runs.workspace_path` and `output_dir` migrate or report missing.
- Workspace links with relative targets are preserved.
- Workspace links with absolute old-root targets are reported and optionally rewritten.
- Workspace links with external absolute targets are reported and left unchanged.
- Work assignment JSON/checkpoint old-root strings are detected in dry-run.
- Migration dry-run makes no database or filesystem changes.
- Migration apply is idempotent.
- Migration refuses or clearly blocks active/running/waiting work unless an explicit mode is selected.
- Missing artifact files are reported without deleting metadata.
- Stale `workspaces.root_relative_path` values are reported and deterministic agent/project roots can be normalized.

Validation commands/checks after implementation:

- Focused unit/repository tests for path resolver, migration/repair, chat file service, output artifact service, workspace service, plan/workflow/job run path handling.
- `mvn test` or targeted package tests followed by full test run if migration touches shared path services.
- Bounded Spring startup smoke against a temp migrated database/root.
- Focused UI/browser validation for chat file panel, outputs list/download/content, project workspace page, job runs page, and workspace summaries.
- `git diff --check`.

Manual/operator validation:

- Backup DB and old root before apply.
- Dry-run report reviewed before apply.
- Verify row counts before/after.
- Verify representative chat file download.
- Verify representative output artifact content/download.
- Verify representative project and agent workspace display.
- Verify no active assignment is left pointing at old-root temp/output paths.

## Open Questions

- Should the SQLite database move under `.magenta` as part of this effort, or should only `AiConfig.dataRoot` move?
- What should the canonical product default be: `~/.magenta/root`, `$MAGENTA_HOME/root`, or a Spring property with a fallback?
- Should config files/prompts also live under `.magenta`, or remain repo/local-operator files?
- Should output path columns be changed in place to store relative values, or should new columns be added while old absolute columns remain compatibility-only?
- How aggressively should migration scan and rewrite unstructured JSON columns?
- Should missing historical artifacts remain as metadata rows with a missing status, be hidden from default output views, or remain visible with download errors?
- Should stale `workspaces.root_relative_path` rows be normalized automatically for agents/projects?
- What should happen to legacy job workspace records rooted at `jobs/<jobId>` when current user-facing jobs use effective workspace `jobs/<assignmentId>` for persistent job workspaces?
- Should loose artifact discovery stay enabled during/after repair, be disabled by default, or require explicit operator action?
- What is the desired policy for active/waiting workflow runs during migration: block migration, mark needs-review, or attempt path repair and resume?
- Should old roots be left untouched, archived, or symlinked after successful migration?
