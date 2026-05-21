# Workspace, Services, And UX Refactor Handoff Report

Date: 2026-05-21

## Purpose

This handoff summarizes the behavior changes from the completed workspace/file architecture refactor and the services/frontend/UX refactor. It also frames the next decision: how to move Magenta to a clean `.magenta` root while preserving existing chat files, workspace files, output artifacts, and database-backed references.

No root migration, file move, import, or code implementation has been performed in this planning pass.

## Completed Refactor Branches And Commits

Workspace/file architecture closeout:

- `3f447ae chore: close workspace file architecture refactor`
- `e6dfe87 chore: record workspace file closeout commit`

Services/frontend/UX architecture closeout:

- `0a92caa feat: add first-class assignment workspace context`
- `ca6c0c5 feat: add job execution summaries`
- `47877a9 feat: expose output provenance filters`
- `5c6aff1 feat: align project and job operator ux`
- `1d97983 chore: close services ux architecture refactor`
- `a1b2fad chore: record services ux closeout commit`

Current planning branch:

- `root-migration-handoff-planning`

## Architecture Behavior Now In Place

### Core Abstractions

- Agents execute work and own default agent workspaces.
- Projects are shared durable workspace and visibility contexts. They are not executable work units.
- Tasks/plans and workflows are bounded work units.
- Jobs are hybrid records: a job definition describes repeatable work, a job assignment requests execution, and a job run records execution.
- `projectId` is now the explicit project-scoping field for execution.
- `workspaceId` remains compatibility metadata and must not be treated as a project id.

### Effective Workspace Rule

Every work-unit run resolves one effective durable workspace:

- `projectId` present: use the project workspace.
- `projectId` absent: use the executing agent workspace.

That rule now drives task, workflow, and job output paths, tool aliases, operator display, and output attribution.

### File Layout

The current durable layout under `dataRoot` is:

- Agent workspace: `agents/<agentId>/workspace/`
- Project workspace: `projects/<projectId>/workspace/`
- Shared workspace directories: `work/`, `outputs/`, `runs/`, `scratch/`, `jobs/`
- Task outputs: `<effective-workspace>/outputs/tasks/<taskId>/<runId>/`
- Workflow outputs: `<effective-workspace>/outputs/workflows/<workflowId>/<runId>/`
- Job outputs: `<effective-workspace>/outputs/jobs/<assignmentId>/<jobRunId>/`
- Persistent job workspace, opt-in: `<effective-workspace>/jobs/<assignmentId>/`
- Chat files: `chats/<conversationId>/files/`

Legacy agent `home` and `outputs` directories can be migrated into `agents/<id>/workspace/` by existing service helpers when the warm data root still uses old directories.

### Tool Runtime Aliases

File and shell tools resolve scoped runtime aliases:

- `workspace/`: current effective durable workspace root.
- `work/`: current effective workspace `work/`.
- `outputs/`: current run output directory.
- `run/`: current run temp/execution directory.
- `scratch/`: current effective workspace `scratch/`.
- `job/`: current persistent job workspace when enabled for the active job assignment/run.

### Output Artifact Behavior

Output artifacts now prefer direct attribution rather than discovery by fallback:

- `run_output_artifacts` stores run, plan/workflow, agent, job, job assignment, job run, project, workspace, run type, output name, artifact type, file name, absolute path, size, and timestamps.
- `/api/outputs` accepts filters for `agentId`, `jobId`, `jobAssignmentId`, `jobRunId`, `projectId`, `workspaceId`, `runId`, `planId`, `runType`, `type`, and `limit`.
- Job fallback lookup still exists for compatibility but is bypassed when direct attribution filters are supplied.
- Duplicate materialized output filenames are suffixed instead of overwritten.
- Chat files remain separate from output artifacts and are not indexed into `run_output_artifacts`.

### Assignment And Job Behavior

- `WorkAssignment` now has first-class `projectId`, `effectiveWorkspaceId`, and `effectiveWorkspaceKind`.
- Existing JSON `input.projectId` remains compatibility data for warm records.
- Assignment creation resolves effective workspace identity but does not acquire write leases.
- Runner execution repairs missing effective workspace fields on legacy assignments.
- Project-scoped execution acquires project workspace write leases during execution.
- Workspace-blocked assignments can be requeued after the blocking lease clears.
- Job start and recurrence paths enqueue `JOB_RUN` assignments rather than directly creating user-facing runs.
- `JobExecutionSummary` bridges job definition, assignment, run, agent/project context, effective workspace, persistent job workspace, output directory/count, and timestamps.
- Job run allocation is assignment-idempotent and persists checkpoint state before item execution.

### Operator UX Behavior

- Project UI frames projects as shared workspace contexts.
- Project membership add/remove controls exist and are guarded by active-work policy.
- Agent, plan, workflow, and job submit surfaces show project context and compatibility workspace metadata distinctly.
- Job editor exposes persistent job workspace configuration.
- Job run tables show assignment id, run id, agent/project, effective workspace, compatibility workspace, persistent job workspace, output directory, and output count.
- Output views show expanded provenance and filters.
- Selector context is namespaced as `selectorContext.*`, so selector helper state cannot overwrite real business fields such as submitted `agentId`.

## Execution Environment Impact

### What Changed For Existing Runs

The execution environment is now less ambiguous:

- Project-scoped work no longer implicitly relies on an agent owner field. It uses project workspace context through `projectId`.
- Agent-scoped work continues to use the executing agent workspace.
- Outputs are placed under durable output directories inside the effective workspace.
- Temp directories are still run-scoped and should not be treated as durable output storage.
- Chat files keep their own conversation file tree and are not converted into output artifacts.

### Existing Files After The Refactor

Existing files can remain valid if the configured `dataRoot` still points to the root that contains them. The new code computes many locations from ids and relative workspace paths, but some database rows also store concrete paths.

Important classes of existing files:

- Chat files: currently filesystem-discovered under `chats/<conversationId>/files/`.
- Workspace roots: database `workspaces.root_relative_path` points at relative roots under `dataRoot`.
- Output artifacts: database `run_output_artifacts.file_path` stores file paths used for reads/downloads.
- Plan/workflow/job run records: some rows store workspace/output/temp path fields for historical and resume behavior.

Because of those stored path fields, moving the root externally can break some reads even if the directory tree itself is copied correctly.

## Current Root Situation

Known from the current code/config scan:

- `application.yml` default database: `jdbc:sqlite:./chat-memory.db?foreign_keys=true`
- Example AI config path: `./config/ai-config.example.json`
- Example AI config `dataRoot`: `/home/hickelpickle/.magenta/root`
- `WorkspaceDirectoryService`, file tools, shell tools, chat files, output artifacts, workspace services, and input resolution all use `AiConfig.dataRoot`.

That means a clean install can already be configured to use a `.magenta` filesystem root, while the SQLite database may still live beside the running application unless `spring.datasource.url` is also changed.

## Root Move Risk Summary

Moving only files is not enough if the populated database still contains path fields pointing at the old root.

Likely safe without DB rewrite:

- Moving chat files if the new `dataRoot` contains the same `chats/<conversationId>/files/` relative tree, because chat file listing is filesystem-derived by conversation id.
- Moving workspace records if `workspaces.root_relative_path` stays relative and the same relative directories exist under the new root.

Likely unsafe without DB rewrite/import/repair:

- Output artifact downloads if `run_output_artifacts.file_path` contains absolute old-root paths.
- Plan/workflow/job historical output/temp fields if stored as absolute paths and later used for display, resume, validation, or download.
- Active/incomplete assignments whose checkpoints contain absolute paths.
- Symlinked project materializations if links point at the old root.

## Migration Decision Needed

Before implementation, choose one of these directions after the root/file/database review report is complete:

1. **Root copy plus database path rewrite**
   Copy the existing root to `.magenta/<new-root>`, update config, and rewrite known absolute old-root paths in SQLite to the new root.

2. **Root import/reindex command**
   Treat the moved filesystem as imported data. Rebuild or repair file/path rows from filesystem state where possible, preserving DB identities.

3. **Compatibility alias/symlink**
   Move the root but leave a filesystem symlink from old root to new root so old absolute DB paths continue to resolve, then gradually repair paths.

4. **New root with chat-file-only migration**
   Start clean for workspaces/outputs and migrate only `chats/<conversationId>/files/`, leaving old workspace/output data archived and optionally importable later.

The review agent is assigned to inspect the current database/file source-of-truth model and propose the safest option set before any implementation.

## Validation Requirements For The Future Migration

Any chosen migration implementation should validate:

- Clean install creates expected `.magenta` root structure.
- Existing populated database starts against moved root without startup failure.
- Chat file listing/download works after moving `chats/<conversationId>/files/`.
- Existing output artifact content/download works or is explicitly marked missing/import-needed.
- Workspace summaries resolve project and agent workspace roots under the new root.
- Active/incomplete assignment behavior is either migrated safely or blocked with a clear diagnostic.
- `mvn test` and Spring startup smoke pass.
- A focused browser pass confirms chat file panels, project/workspace pages, outputs, and job/project surfaces still render.

## Current Open Questions

- Should the SQLite database also live inside `.magenta`, or only the filesystem `dataRoot`?
- Do we want a one-time migration command, an admin API/import function, or startup auto-repair?
- Should absolute path columns be converted to root-relative storage long term?
- Should old roots be archived automatically, symlinked, or left untouched until operator confirmation?
- How should active runs/checkpoints be handled during root migration: migrate, fail closed, or require no active work?
