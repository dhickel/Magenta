# Root Migration Decision Report

Date: 2026-05-21

## Purpose

This report synthesizes the handoff report and the read-only root/file/database review into an operator decision point. It does not authorize or perform any file move, database rewrite, import, root cleanup, or code implementation.

## Decision Summary

Magenta should treat the upcoming root migration as both a data move and a path-model repair. Moving files alone is not enough because ordinary chat files are filesystem-led, while outputs and run history are indexed by database rows that currently store concrete host paths.

Recommended direction:

1. Move toward root-relative storage for Magenta-owned paths.
2. Add an explicit dry-run/apply repair command or admin operation.
3. Copy the existing root tree intact into the chosen `.magenta` root.
4. Preserve chat files by copying `chats/<conversationId>/files/` with the same relative layout.
5. Repair or compatibility-resolve existing absolute output/run paths before marking the move complete.
6. Keep migration offline and refuse apply while active/running/waiting work exists unless the operator explicitly selects a blocked/needs-review mode.

## Source Of Truth Findings

Chat files:

- Filesystem is the source of truth for file bytes and per-file listing.
- Database stores conversations and session metadata, not individual chat file rows.
- Preserving the same relative `chats/<conversationId>/files/` tree under a new `dataRoot` should preserve ordinary chat files.

Workspace contents:

- Filesystem owns `work/`, `scratch/`, job workspace contents, and loose files.
- Database owns workspace identity, owner metadata, display fields, links, leases, assignments, projects, and jobs.
- `workspaces.root_relative_path` is already relative to `dataRoot`, but warm records can contain stale legacy relative paths.

Output artifacts and run history:

- Filesystem owns artifact bytes.
- Database owns artifact index, provenance, run identity, and download/content metadata.
- `run_output_artifacts.file_path`, `plan_runs.output_directory`, `plan_runs.temp_workspace_path`, `workflow_runs.workspace_path`, `workflow_runs.output_dir`, `job_runs.workspace_path`, and `job_runs.output_dir` currently store concrete path strings.
- Moving the root without repairing those rows can break output downloads, content reads, resume behavior, cleanup, and historical display.

SQLite placement:

- The current default SQLite database is `./chat-memory.db`.
- The example AI data root is `/home/hickelpickle/.magenta/root`.
- A complete product-level `.magenta` home decision should include whether SQLite also moves under `.magenta`, for example `~/.magenta/magenta.sqlite`.

## Behavior And Feature Changes To Carry Forward

- Projects are shared durable workspace and visibility contexts, not work units.
- Agents execute work and own default agent workspaces.
- Tasks/plans and workflows are bounded work units.
- Jobs are hybrid orchestration/work units and can have persistent assignment-scoped workspaces when enabled.
- `projectId` explicitly selects project-scoped execution.
- `workspaceId` remains compatibility metadata and should not be used as a project selector.
- Effective workspace resolution is project workspace when `projectId` is present, otherwise executing agent workspace.
- Outputs now carry direct provenance by agent, project, workspace, assignment, job, job run, plan/workflow id, run id, and run type.
- Chat files remain separate from run output artifacts.

## Option Comparison

### Option A: Root Copy Plus DB Path Rewrite

Copy the populated root to the new `.magenta` root, update config, and rewrite known old-root absolute prefixes in DB path columns.

Use when: the goal is the smallest one-time move with existing behavior mostly preserved.

Main drawback: it keeps absolute path storage, so future root moves have the same failure mode.

### Option B: Root-Relative Owned Paths Plus Compatibility Reads

Persist Magenta-owned paths relative to `dataRoot`, while compatibility reads accept and normalize old absolute rows under known roots.

Use when: the goal is to fix the root-move class of bugs rather than only moving this installation.

Main drawback: broader code and test surface across output download/content, run resume, cleanup, file tools, workflow/job execution, and UI display.

### Option C: Explicit Import/Reindex/Repair Tool

Add a dry-run/apply operation that inspects the filesystem and database, reports risks, and repairs only approved deterministic records.

Use when: operators need safe diagnostics for populated databases, partial moves, missing artifacts, stale workspace roots, and old absolute paths.

Main drawback: larger implementation scope, especially if output reindexing from loose files is included.

### Option D: Compatibility Symlink

Copy or move the root and leave a symlink from the old absolute root to the new one.

Use when: short-term continuity is needed while a proper path migration is prepared.

Main drawback: masks stale database paths and is fragile across hosts, containers, permissions, and cleanup.

### Option E: Clean Root With Chat-Only Migration

Start a clean `.magenta` root and migrate only `chats/<conversationId>/files/`.

Use when: preserving ordinary chat files matters, but historical workspaces/outputs can remain archived outside active Magenta.

Main drawback: output and workspace continuity is intentionally sacrificed unless additional import work follows.

## Recommended Implementation Strategy

Implement Option B with a constrained Option C repair tool. Option A can be used as a tactical fallback only if the user chooses a smaller one-time migration.

Recommended implementation phases:

1. Introduce a root-owned path resolver that can store root-relative paths and compatibility-resolve old absolute paths under a known previous root.
2. Refactor artifact/run path reads and writes to use the resolver for Magenta-owned paths.
3. Add a migration dry-run that reports path columns, affected row counts, missing files, stale workspace roots, active/waiting work, and old-root strings in known JSON columns.
4. Add a migration apply mode that copies or verifies the root, rewrites deterministic Magenta-owned paths to root-relative form, verifies artifact existence, and emits a repair report.
5. Add workspace root normalization for deterministic agent/project records, with legacy job roots reported unless their mapping is unambiguous.
6. Add operator-facing docs for backup, dry-run, apply, validation, rollback/old-root archive, and symlink fallback.

## Gating And Risk Mitigation

Required gates before apply:

- Database backup exists.
- Old root exists and resolves inside the operator-approved source path.
- New root exists or can be created and is distinct from the old root.
- No active/running/waiting assignments or nonterminal runs exist, unless forced blocked-state mode is selected.
- Dry-run report has been reviewed.
- Expected chat file tree and representative output artifacts are present in the copied root.

Required gates after apply:

- Chat file listing and download work for representative conversations.
- Output artifact content and download work for representative task, workflow, and job outputs.
- Project and agent workspace pages resolve under the new root.
- Missing artifacts are reported, not silently deleted.
- `mvn test` passes.
- Spring startup smoke passes against the migrated configuration.
- Focused browser validation covers chat files, outputs, project workspace, and job run surfaces.

Primary risks:

- Absolute paths embedded in JSON columns may survive deterministic column rewrites.
- Active or waiting workflow/job state can depend on old temp paths.
- Loose artifact discovery can over-index incidental files if used too broadly.
- Stale workspace relative paths can confuse UI summaries even when execution computes current paths.
- A startup auto-repair could mask data loss by recreating empty directories.

Mitigations:

- Make migration explicit and offline.
- Prefer dry-run first, with row counts and examples.
- Rewrite only Magenta-owned paths under approved roots.
- Treat unstructured JSON path rewrites as report-first unless a structured transform is implemented.
- Leave missing artifacts visible as missing/needs-review instead of deleting rows.
- Avoid startup auto-repair.

## Decisions Needed From User

1. Should SQLite move under `.magenta` as part of this effort, or should only `dataRoot` move?
2. Should the implementation prioritize the long-term root-relative repair, or a smaller one-time root copy plus DB path rewrite?
3. Should active/waiting runs block migration completely, or should migration be allowed to mark them blocked/needs-review?
4. Should old roots be archived, symlinked, or left untouched after a successful migration?
5. Should loose output artifact discovery be available during repair, or deferred until the deterministic path migration is complete?
