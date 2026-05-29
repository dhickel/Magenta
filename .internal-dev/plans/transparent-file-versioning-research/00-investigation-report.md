# Transparent File Versioning Investigation

Date: 2026-05-29

## Executive Recommendation

Magenta should implement transparent file versioning as a Magenta-owned versioned file service for all controlled mutation paths, backed by content-addressed immutable blobs plus SQLite metadata, and pair it with scoped pre/post snapshots for less controlled mutation paths such as shell/process execution. Do not make Borg, restic, Git/JGit, filesystem snapshots, FUSE, or Apache Commons VFS the primary product ledger.

The recommended path is a hybrid:

1. Add a `VersionedWorkspaceFileService` under the workspace domain. It becomes the write/delete/move/copy facade for Work Areas, projects, chat files, run-output promotion, future widgets, and agent file tools.
2. Store immutable file bytes in a Magenta-owned content-addressed blob store under the data root, with SQLite metadata for version chains, path history, operation batches, actor/run/task attribution, retention class, and rollback state.
3. Keep filesystem files as the live working tree so existing tools, previews, downloads, and shell commands still work.
4. Capture shell and raw process mutation with staged execution boundaries: create a manifest snapshot before execution, rescan after execution, store changed/deleted/new file versions, and optionally require leases or operation scopes for rollbackable work.
5. Use Borg/restic/filesystem snapshots as optional backup/export/defense-in-depth integrations, not as the user-facing undo ledger.
6. Treat Git/JGit as an optional project-repository integration for code-like projects and explicit commits, not the transparent file-versioning substrate for all Magenta files.

This is the practical route because Magenta already has centralized workspace layout helpers, Work Areas, project leases, confined file tools, and output-promotion services. Controlled writes can be made atomic and user-attributed inside those services. Raw host writes cannot be captured reliably by wrapping Java APIs alone; they need either OS-level capture, filesystem snapshots, a virtual filesystem, or a pre/post diff strategy.

## Current Architecture Meaning

Transparent versioning in Magenta means users can inspect and roll back accidental file changes without needing to know whether the change came from a person, an agent tool, backend output promotion, or a future widget.

It should cover these logical surfaces differently:

| Surface | Versioning meaning | Rollback expectation |
| --- | --- | --- |
| Work Areas | Primary user-visible files under stable DB-owned Work Area ids. | File-level, directory-level, and operation-batch rollback from UI and API. |
| Projects | Shared durable workspaces under `projects/<projectId>`, protected by writable leases. | Project-scoped snapshots and file history, respecting lease ownership and future collaboration permissions. |
| Agent execution roots | Agent-owned durable workspace root under `workspace/<agentWorkspaceId>/`. | Version `home/`, user-created Work Areas, and controlled mutable regions; treat run staging separately. |
| Run-local outputs | Staged model-facing `runs/<runId>/outputs/` during execution. | Capture at execution boundaries and promotion time; rollback normally targets promoted final outputs, not transient staging. |
| Promoted outputs | Backend-owned final output destinations. | Immutable-ish artifact lineage plus explicit "restore/copy back" flows; avoid silent overwrite of audited outputs. |
| Chat/output artifacts | Chat files live under `chats/<conversationId>/files/`; output artifacts have metadata rows. | Preserve chat file history and artifact file history separately, with links back to conversation/run. |
| Scripted/custom widgets | Future widget edits should use the same versioned file API. | Widgets should receive file-version handles and must not write raw host paths when rollback is promised. |

Relevant current contracts:

- `architecture.md` defines data-root children as `workspace/`, `chats/`, `agents/`, and `projects/`; agent execution roots live under `workspace/<agentWorkspaceId>/`; Work Areas use stable DB ids; run outputs stage under `runs/<runId>/outputs/`; final output promotion is backend-owned.
- `WorkspacePathLayout` centralizes structural path constants and aliases such as `workspace`, `projects`, `chats`, `home`, `workareas`, `outputs`, and `run` (`src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspacePathLayout.java`).
- `WorkspaceDirectoryService` owns directory creation, data-root confinement, chat file roots, project workspace roots, run staging cleanup, and symlink project materialization (`WorkspaceDirectoryService.java`).
- `WorkAreaExplorerService` owns user-facing Work Area browse, preview, save, create, rename, move, copy, delete, labels, and action logs (`WorkAreaExplorerService.java`).
- `AgentFileToolService` owns model-visible file read/write/append/replace/list/search inside active workspace scopes and records active runtime paths (`AgentFileToolService.java`).
- `AgentShellToolService` executes allowed commands with `ProcessBuilder` in a confined working directory but cannot observe per-file writes performed by that process (`AgentShellToolService.java`).
- `OutputArtifactService` materializes, publishes, discovers, and promotes output files with data-root and symlink confinement (`OutputArtifactService.java`).
- `ChatFileService` lists and downloads conversation-scoped chat files separately from output artifacts (`ChatFileService.java`).

## Mutation Paths

Known current mutation paths:

- Work Area explorer save text, create text/markdown file, create directory, rename, move, copy, delete, label metadata, and nested Work Area marking.
- Avatar and agent-detail Work Area browser routes that delegate to `WorkAreaExplorerService`.
- Agent file tools: `file_write`, `file_append`, and `file_replace`.
- Agent shell tool: allowed command execution in a confined working directory. This can mutate any writable file reachable under the process working tree, and Java does not see individual write calls.
- Plan/task execution setup creates run directories and output directories.
- Output materialization writes text/json/user-message output files or copies `file_path` outputs.
- Output loose discovery registers existing files in output staging.
- Output promotion copies run-local staged files to final destinations.
- Chat file directories are created on listing/counting, and future upload/import paths would write there.
- Workspace migration/compatibility paths can move legacy directories into current layout.
- Project workspace materialization creates/removes symlinks under assignment workspaces.
- Workspace cleanup deletes run-local staging.
- Future widgets/plugins/tools are likely to write files, import files, generate outputs, or edit project files unless constrained to a versioned API.

Likely future mutation paths:

- UI file upload/import and drag/drop.
- Rich text/code editor saves.
- Direct project Git operations.
- Widget-generated files and scripted plugin helpers.
- Agent-side validation/promotion tools.
- Container-backed execution writing mounted project/workspace paths.
- Admin/diagnostic filesystem browser.

## Capture Boundary

Wrapping Magenta file APIs can reliably capture:

- Work Area browser/editor mutations.
- Agent file-tool mutations.
- Backend output materialization/promotion.
- Chat file import/upload when routed through services.
- Future widgets/plugins if they receive a versioned file service rather than raw `Path`.
- Metadata-only operations such as labels, descriptions, and logical moves if modeled as file version events.

Wrapping Magenta file APIs cannot reliably capture:

- `ProcessBuilder` commands that write files directly.
- External tools invoked by agents that create temp files, rename files atomically, or rewrite whole directories.
- Containerized processes writing mounted volumes unless the mount is observed or diffed.
- Manual operator edits on the host filesystem outside Magenta.
- Any library code that receives a `Path` and calls `Files.write`, `Files.move`, or native IO outside the versioned service.
- Symlink target changes if allowed in a raw process path.

Therefore "transparent" must be defined as transparent inside Magenta-controlled surfaces plus best-effort or boundary-scoped capture for raw execution. Full OS-level transparency requires filesystem-native snapshots, FUSE/overlay, fanotify/inotify-style monitoring, or container/storage-layer integration, each with platform and trust tradeoffs.

## Approach Comparison

### 1. App-Level Versioned File Service With Content-Addressed Blobs And SQLite Metadata

Summary: Magenta stores every controlled pre-image/post-image as immutable blobs named by digest, and stores version records in SQLite. The live filesystem remains the current working tree.

Fit: Best primary architecture.

Core model:

- `versioned_file_blobs`: digest, size, media type hint, storage path, compression/encryption metadata.
- `file_version_events`: event id, scope type/id, path before/after, operation type, actor type/id, run id, assignment id, conversation id, tool call id, batch id, old blob digest, new blob digest, metadata JSON, timestamp.
- `file_live_state`: scope type/id, path, current blob digest, file kind, size, mtime seen, version head, deleted flag.
- `file_operation_batches`: one user click, tool call, output promotion, shell execution, widget action, or import.

Rollback granularity: File version, directory tree at a point in time, or operation batch. Directory rollback is computed from path-history events under a scope/prefix.

Atomicity: Strong for controlled writes if service uses temp files plus atomic move where available, and records SQLite transaction before/after with compensation on IO failure. Cross-file operations need operation batches and staged application.

Concurrency: Use existing project workspace leases for project writes. Add per-scope/path optimistic concurrency with expected version id for UI/API/tool writes. Use `file_live_state` unique keys for SQLite conflict detection.

Storage growth: Dedup by digest; optional compression; retention policies by scope and event type. Text files rewritten often dedup only identical full files unless a delta layer is added later.

Binary/large files: Store full blob for controlled changes up to configured thresholds. For very large files, snapshot only at operation boundaries, use chunked blobs, or mark as external-large with retention warnings.

Symlink/path confinement: Keep current realpath confinement rules. Do not version symlink targets as normal file contents unless a future explicit symlink policy is accepted. Controlled services should reject symlink mutation on user surfaces just as Work Area explorer does today.

Retention/pruning: Scope policies such as keep all user-visible Work Area history for N days, keep important/manual checkpoints indefinitely, prune run staging aggressively, keep promoted output lineage longer.

Indexing/search: Version metadata in SQLite; optional full-text index for current files first, historical text later. Search results can link to version id.

Offline portability: Good if blobs and SQLite stay under Magenta root and paths remain root-relative.

Backup/export: Can export a scope at version id as tar/zip; can feed blob store and metadata into Borg/restic as backup.

Security/privacy: Blobs contain all historical secrets accidentally written. Needs retention controls, hard purge, encryption-at-rest decision, and clear UI warnings.

Platform assumptions: Pure Java/Spring/SQLite plus normal filesystem. Works on Linux/macOS/Windows within current constraints.

Failure modes: DB row without blob, blob without row, interrupted live write after version recorded, digest collision paranoia, disk-full, rollback clobbering newer edits, current file manually changed outside live state.

User trust: Strong because UI can show actor, operation, before/after, and rollback preview. Must explicitly label raw shell/process captures as boundary snapshots, not perfect syscall capture.

### 2. Git/JGit Repository Per Work Area/Project/Scope

Summary: Initialize a Git repository for each Work Area/project/agent scope and commit changes. Use JGit for in-process Java Git operations.

Primary source: Eclipse JGit is described by Eclipse as a pure Java implementation of Git: https://projects.eclipse.org/projects/technology.jgit/governance and its user guide documents high-level API support in `org.eclipse.jgit.api`: https://help.eclipse.org/latest/topic/org.eclipse.egit.doc/help/JGit/User_Guide/Reference.html

Fit: Good optional integration for code-like project workspaces; poor universal transparent versioning substrate.

Rollback granularity: Commit, file checkout, branch/tag. Operation batch maps naturally to commits.

Atomicity: Git commits are atomic at repository level, but staging working-tree mutations is separate. Cross-scope operations are hard.

Concurrency: Git has its own index locks and merge/conflict semantics. This is useful for projects but not friendly for simple Work Area files or concurrent UI/tool saves.

Storage growth: Git delta compression is strong for text, worse for frequently changing large binary files unless Git LFS-like behavior is introduced.

Binary/large files: Poor default fit for large generated artifacts, images, archives, and model output files.

Symlink/path confinement: Git tracks symlinks as entries. Magenta must still enforce no symlink escape in live filesystem. Git does not replace confinement.

Retention/pruning: Git history pruning is possible but product-hostile. Rewriting history undermines user trust and audit.

Indexing/search: Good for code history and diffs, less direct for arbitrary Work Area metadata, labels, chat/run attribution, and output lineage.

Offline portability: Excellent where users know Git.

Backup/export: Excellent for code/project repos, but not all Magenta scopes.

Security/privacy: Secrets committed to Git are hard to truly purge without rewriting history and backups.

Platform assumptions: Java-native with JGit; no native Git required for core operations.

Failure modes: Repository corruption, `.git` exposure to agents/users, conflicted states, history rewrites, accidental versioning of internal Magenta files.

User trust: Familiar for technical users, confusing for non-code files. "Rollback" can imply Git checkout semantics rather than Magenta's actor-attributed undo.

Recommendation: Do not use as default. Add later as explicit "project source control" for code projects and maybe as an export target from Magenta version history.

### 3. Event-Sourced Change Journal With Snapshots/Deltas

Summary: Store logical operations such as write, append, replace, move, copy, delete, plus snapshots every N operations.

Fit: Useful metadata/event layer, risky as the only storage layer.

Rollback granularity: Excellent for controlled logical edits and operation batches.

Atomicity: Strong if all writes are service-owned. Replay must be deterministic.

Concurrency: Needs expected-version checks. Concurrent append/replace can be modeled precisely; raw filesystem changes cannot.

Storage growth: Deltas can be compact for text. Binary deltas are harder and can make validation complex.

Binary/large files: Full snapshots are simpler than custom binary deltas. Delta chains can become fragile.

Symlink/path confinement: Same as app-level service.

Retention/pruning: Snapshot compaction can drop old deltas while preserving restore points.

Indexing/search: Good for current state and logical history. Historical full-text search requires reconstructing snapshots or indexing each version.

Offline portability: Requires Magenta tooling to replay.

Backup/export: Export reconstructed trees and event logs.

Security/privacy: Same historical-secret problem as blobs, with added risk of sensitive deltas surviving compaction.

Platform assumptions: Pure Java/SQLite.

Failure modes: Replay bugs, corrupted delta chain, semantic mismatch with actual live file after external mutation.

User trust: Good if UI shows operations, but users care about recoverable bytes more than elegant event theory.

Recommendation: Use an event journal as metadata around blob snapshots, not instead of content-addressed pre/post blobs.

### 4. Borg/Restic-Style External Snapshot Engine Integration

Summary: Run external backup tools against Magenta roots or scopes at schedule/operation boundaries.

Primary sources:

- Borg documentation describes Borg as a deduplicating archiver with content-defined chunking, optional compression, and authenticated encryption: https://borgbackup.readthedocs.io/
- Restic documentation describes snapshots and repository objects such as blobs, packs, trees, and snapshots, with encrypted repository data: https://restic.readthedocs.io/en/stable/design.html
- Restic's manual documents snapshot restore and forget/prune lifecycle: https://restic.readthedocs.io/en/stable/manual_rest.html

Fit: Strong backup and disaster recovery layer; not sufficient as primary transparent undo.

Rollback granularity: Snapshot restore by path/time. Good for "restore this file from yesterday"; poor for "undo this exact agent tool call".

Atomicity: Snapshot consistency depends on quiescing writers or filesystem semantics. App-level transactions are not automatically aligned with external snapshots.

Concurrency: External snapshot can race with active writes unless coordinated through leases/checkpoints.

Storage growth: Excellent dedup/chunking. Borg/restic are designed for backup retention.

Binary/large files: Stronger than Git and app full-file snapshots because chunking dedups large files.

Symlink/path confinement: Backup tools can preserve symlinks; Magenta must avoid restoring unsafe symlinks into active roots without validation.

Retention/pruning: Mature retention/prune tooling, but policies are backup-centric rather than product-event-centric.

Indexing/search: Not app-native. Need a Magenta index mapping product events to snapshot ids.

Offline portability: Good if users have the tool and credentials.

Backup/export: Excellent.

Security/privacy: Encryption is mature, but key/passphrase management becomes product responsibility. Restores can reintroduce secrets or unsafe paths.

Platform assumptions: External binaries, repository configuration, background jobs, operational monitoring. Borg is strongest on Unix-like deployments; restic has broad deployment support, but both are external operational dependencies.

Failure modes: Missing binary, repo lock, key loss, failed prune, partial backup, slow restore, repository corruption, stale snapshots not matching DB state.

User trust: Strong for "backup" trust if restores are tested; weaker for exact in-app undo unless Magenta links snapshot ids to operations.

Recommendation: Add as optional backup/export integration after app-level versioning exists. A Borg/restic snapshot id can be attached to operation batches for high-risk runs, but it should not be the only rollback source.

### 5. Filesystem-Native Snapshots: btrfs/zfs

Summary: Use copy-on-write filesystem snapshots for roots/subvolumes/datasets.

Primary sources:

- Btrfs documentation describes subvolumes and snapshots, with snapshots sharing common data cheaply: https://btrfs.readthedocs.io/en/latest/btrfs-man5.html
- OpenZFS documents `zfs snapshot`, and related rollback/diff/send/receive commands: https://openzfs.github.io/openzfs-docs/man/v2.3/8/zfs-snapshot.8.html

Fit: Excellent deployment-level primitive where available; poor cross-platform product baseline.

Rollback granularity: Subvolume/dataset snapshot; file restore is possible by copying from snapshot. Whole-dataset rollback can be too broad for user mistakes.

Atomicity: Very strong at filesystem snapshot boundary.

Concurrency: Snapshot can be instant, but DB and file tree need consistent checkpointing if both must roll back together.

Storage growth: Efficient copy-on-write, especially for large files.

Binary/large files: Strong.

Symlink/path confinement: Snapshot captures whatever exists, including symlinks. Restore must pass Magenta confinement.

Retention/pruning: Mature external tools exist, but product-level policy still needed.

Indexing/search: Not app-native.

Offline portability: Tied to filesystem.

Backup/export: zfs send/receive and btrfs send/receive are powerful but operationally specialized.

Security/privacy: Snapshot retention keeps deleted secrets. Admins must understand storage policy.

Platform assumptions: Host filesystem and privileges. Not guaranteed on a remote host, Docker volume, ext4, APFS, Windows, or managed cloud filesystem.

Failure modes: Snapshot command unavailable, no subvolume/dataset boundary per Work Area, insufficient privileges, restore too broad, DB/file mismatch.

User trust: High if surfaced as "system checkpoint"; not enough for fine-grained "undo this file edit".

Recommendation: Support as optional checkpoint provider for installations that opt in. Do not require it.

### 6. FUSE/Overlay/Virtual Filesystem Layer

Summary: Put a userspace filesystem or overlay between agents/processes and the real Magenta data root.

Primary source: libfuse is the reference implementation for the Linux FUSE interface; it lets a userspace program export a filesystem to the kernel and receive filesystem callbacks: https://github.com/libfuse/libfuse

Fit: The most transparent capture model for raw writes, but too much operational and correctness risk for the first implementation.

Rollback granularity: Potentially every filesystem operation, depending on implementation.

Atomicity: Hard. Filesystem semantics for rename, fsync, mmap, hard links, file locks, permissions, and crash recovery are complex.

Concurrency: Very hard. Needs correct locking and cache semantics under concurrent processes.

Storage growth: Depends on design; can be content-addressed and CoW.

Binary/large files: Possible but performance-sensitive.

Symlink/path confinement: Can enforce centrally, but bugs become filesystem bugs.

Retention/pruning: App-defined.

Indexing/search: App-defined.

Offline portability: Low unless mounted filesystem is exportable or backed by a portable store.

Backup/export: Backing store can be exported; live mount is platform-specific.

Security/privacy: A bug could expose or corrupt data. FUSE mount permissions and `allow_other` policy need careful operations.

Platform assumptions: Kernel/module support and mount permissions. macOS/Windows need different stacks.

Failure modes: Dead mount, process crash, stale kernel cache, poor performance, incomplete POSIX semantics, data corruption.

User trust: If perfect, very high; if flaky, catastrophic.

Recommendation: Defer. Consider only for a future dedicated agent execution sandbox where Magenta owns the mount lifecycle and can test filesystem semantics heavily.

### 7. Hybrid Staged Execution With Pre/Post Snapshots

Summary: Before shell/process/container execution, scan a scoped tree manifest. After execution, scan again, detect changed/added/deleted files, and store versions/events for changed paths.

Fit: Required companion for the recommended app-level service.

Rollback granularity: Operation batch for a shell command/run, plus individual file versions for detected changes.

Atomicity: Boundary-level, not syscall-level. A command that runs while other writers mutate the same tree can produce ambiguous attribution unless leases or expected versions are enforced.

Concurrency: Needs execution-scoped leases or write fences. Project workspaces already have writable leases; Work Area/agent roots need similar operation scopes for high-trust rollback.

Storage growth: Manifest hashes are cheap; changed large files can be expensive unless chunked.

Binary/large files: Hash-and-copy changed files. For huge files, use chunked blobs or external snapshot provider.

Symlink/path confinement: Manifest walker must not follow symlinks by default and must record symlink presence as unsupported/skipped unless policy changes.

Retention/pruning: Batch retention can be shorter for command-generated scratch changes and longer for user-visible Work Area/project changes.

Indexing/search: Store changed path list and actor/run/tool attribution.

Offline portability: Good with app-level blob store.

Backup/export: Good.

Security/privacy: Same as app-level plus risk of capturing generated secrets.

Platform assumptions: Pure Java file walking. Accuracy depends on mtime/size/digest policy.

Failure modes: Missed transient files that are created and deleted during execution, ambiguous concurrent writes, slow scans, partial post-scan after crash, files changed while being hashed.

User trust: Good if described honestly: "Captured changes made during this command/run" rather than "observed every write syscall."

Recommendation: Use for shell/process/container/tool execution. Pair with operation scope locks and post-run validation.

### 8. Java Library/Protocol Options

#### Java NIO `FileSystemProvider`

Primary source: The JDK `FileSystemProvider` API supports pluggable file system providers and notes the default provider can be overridden through `java.nio.file.spi.DefaultFileSystemProvider`: https://docs.oracle.com/en/java/javase/24/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html

Fit: Useful if Magenta builds an internal virtual filesystem abstraction or test provider. Not sufficient for external processes because raw OS paths bypass Java providers.

Recommendation: Use NIO-compatible abstractions internally where helpful, but do not rely on provider replacement as transparent capture.

#### Jimfs

Primary source: Jimfs is an in-memory Java NIO filesystem implementation: https://google.github.io/jimfs/

Fit: Excellent for tests of path/version logic. Not a production versioned filesystem.

Recommendation: Use in unit tests for filesystem edge cases where compatible, not as runtime storage.

#### Apache Commons VFS

Primary sources: Apache Commons VFS provides a single API for different filesystems and supports local, RAM, temp, archive, HTTP/S, SFTP, WebDAV and other providers: https://commons.apache.org/vfs/ and https://commons.apache.org/vfs/filesystems.html

Fit: Useful abstraction for importing/exporting from remote/archive sources. It does not provide transparent versioning or capture raw host writes.

Recommendation: Optional import/export helper only.

#### Kawa-Adjacent Possibility

Kawa itself is not a filesystem versioning substrate. If Magenta later supports JVM-hosted scripting/widgets, scripts should receive a constrained versioned file API rather than raw `java.nio.file.Path`. That is an API design concern, not a reason to choose a Kawa-specific storage model.

## Recommended Architecture

### Service Boundaries

Add a new workspace-domain service surface:

- `VersionedWorkspaceFileService`: controlled create/write/append/replace/delete/move/copy/import/restore/checkpoint operations.
- `FileVersionRepository`: SQLite persistence for blobs, version events, live state, batches, retention policy, restore records.
- `FileBlobStore`: immutable content-addressed byte storage under data root, with digest verification, temp-write-plus-atomic-move, optional compression, and future encryption hooks.
- `FileManifestService`: scoped tree scan for pre/post execution snapshots.
- `FileRollbackService`: rollback planning, conflict detection, preview, and application.
- `FileVersionPolicyService`: retention, large-file policy, excluded paths, and scope rules.

Keep `WorkspaceDirectoryService` as path-layout authority. The versioning services should ask it for roots/scopes and should never hand-concatenate structural paths.

### Integration Points

Controlled write integrations:

- `WorkAreaExplorerService.saveText`, create file/directory, rename, move, copy, delete: route through `VersionedWorkspaceFileService` and keep the existing service as the user-facing policy owner.
- `AgentFileToolService.write`, `append`, `replace`: route through versioned service with actor `AGENT_TOOL`, tool call metadata, current context, and expected version when available.
- `OutputArtifactService.materialize*`, `publishExistingFile`, `publishDirectoryContents`, `promoteDirectoryContents`: record backend-owned version events and artifact lineage.
- `ChatFileService` future upload/import/delete routes: route through versioned service with actor `USER` or `SYSTEM`.
- `WorkspaceDirectoryService` migrations and cleanup: explicitly mark as system maintenance batches; cleanup may delete unversioned run staging if policy excludes it.
- Future widgets/plugins: expose a `VersionedFileHandle` API. Do not pass raw `Path` for rollbackable writes.

Less controlled integrations:

- `AgentShellToolService.exec`: before execution, create manifest for the resolved scope; after execution, diff and record changed/deleted/created files as a single operation batch.
- Container execution: snapshot mounted scopes before/after the container run. Prefer mounting only scoped Work Area/project/run directories, not the whole data root.
- Project leases: require a write lease for project-scoped shell/process mutation and attach the lease id to the version batch.

### Metadata Model

Minimum persisted fields:

- Scope: `scope_type` (`WORK_AREA`, `PROJECT`, `AGENT_WORKSPACE`, `CHAT_FILES`, `RUN_OUTPUTS`, `FINAL_OUTPUTS`) and `scope_id`.
- Path: root-relative path inside scope, plus old/new path for renames/moves.
- Version: monotonically increasing per path or per scope, current head id, previous version id.
- Content: old/new blob digest, size, MIME/media hint, executable bit if supported, line-ending hint for text.
- Operation: create, write, append, replace, delete, move, copy, import, materialize, promote, shell_batch, restore.
- Actor: user id/session where available, agent id, assignment id, run id, conversation id, tool name, widget id.
- Batch: operation batch id, started/completed timestamps, command/display summary, validation status.
- Trust flags: controlled API, pre/post diff, external snapshot, skipped symlink, large-file skipped, conflict detected.
- Retention: class, expires_at, legal/user pin flag.

Blob storage:

- Store under a non-user-facing root such as `<dataRoot>/versions/blobs/sha256/ab/cd/<digest>`.
- Write to temp path, fsync where practical, then atomic move into blob path.
- Verify digest on read and during periodic check.
- Store root-relative blob path through `RootRelativePathService` for portability.

### Tool API

Agent-facing tools should remain simple, but version-aware:

- File write/append/replace responses should include `versionId`, `batchId`, `previousVersionId`, and `rollbackAvailable`.
- Add read-only tools later: `file_history(path, limit)`, `file_diff(versionA, versionB)`, `file_restore_preview(path, versionId)`.
- Restoration should usually require user approval or a dedicated validator/tool policy unless the agent is explicitly asked to roll back.
- Shell tool response should include a changed-file summary from the post-scan, skipped files, and a batch id.

### API Implications

Add routes after service design:

- `GET /api/work-areas/{id}/files/history?path=...`
- `GET /api/work-areas/{id}/files/versions/{versionId}`
- `GET /api/work-areas/{id}/files/diff?from=...&to=...`
- `POST /api/work-areas/{id}/files/rollback-preview`
- `POST /api/work-areas/{id}/files/rollback`
- Project equivalents under project file APIs when those are first-class.
- Output artifact lineage route: `GET /api/outputs/{artifactId}/lineage`.
- Batch audit route: `GET /api/file-version-batches/{batchId}`.

Controller rule: controllers should stay thin and delegate path and rollback policy to workspace services.

### UI Affordances

Work Area browser:

- Add compact history indicator in inspector.
- Add "History" panel for selected file: version list with actor, time, operation, size, and source (`User`, `Agent`, `Shell command`, `Output promotion`).
- Add diff view for text/markdown/json.
- Add restore preview showing files to change, conflicts, deletions, and skipped unsupported items.
- Add operation-batch history in recent actions, not only single-file actions.
- For shell/process batches, show "Captured by pre/post scan" and any skipped symlinks/large files.

Outputs:

- Show whether an artifact is promoted from run staging, copied from an existing file, or generated from structured output.
- Offer "copy this version into Work Area" rather than mutating historical output in place by default.

Agent/tool transcript:

- Show batch id and changed-file summary after versioned mutations so users can undo from the same area.

### Rollback UX

Default rollback should be preview-first:

1. User selects a file version or operation batch.
2. Magenta computes planned changes against current live state.
3. Conflicts are explicit: current head differs from version expected by rollback, target path moved/deleted, new file exists, skipped large/symlink item, active lease/running assignment.
4. User chooses restore mode:
   - Restore in place.
   - Restore as copy.
   - Revert entire operation batch.
   - Restore selected files from batch.
5. Magenta applies rollback as a new versioned operation, not by erasing history.

Never silently remove history during rollback. Rollback itself is an auditable version event.

### Audit Events

Every versioned mutation should emit durable audit information:

- Actor and source surface.
- Scope/path and before/after version ids.
- Batch id and originating run/tool/widget/controller route when known.
- Controlled vs pre/post diff capture mode.
- Policy flags: skipped, failed, large-file threshold, symlink blocked, conflict.
- Restore actions and approvals.

This should integrate with existing workspace file action logs rather than replacing them immediately. The current action log can become a recent-events projection over version batches.

## Risks And Failure Cases

High-risk areas:

- Shell/process writes cannot be perfectly attributed without OS/filesystem instrumentation.
- Disk-full during blob capture can leave live file changed but history incomplete unless write ordering is carefully designed.
- Rollback can destroy newer work if expected-version checks are weak.
- Large binary files can cause storage blowups.
- Historical secrets persist even after users delete live files.
- Symlinks and hard links complicate identity, confinement, and rollback semantics.
- Project leases serialize project writes today, but Work Area/agent-root writes may need additional operation locks.
- Run staging cleanup must not prune blobs needed by version history.
- SQLite transactions cannot atomically include arbitrary filesystem writes without compensating checks.
- External Borg/restic/native snapshot integrations can drift from app metadata unless checkpointed deliberately.

Design mitigations:

- For controlled writes, write blob first, write live file through temp+atomic move, then commit metadata with verification, or use a pending event state that is finalized after live write verification.
- Use operation batches and expected head ids.
- Add consistency checker: live state vs filesystem digest vs blob availability.
- Default to rejecting symlink mutation on user-visible rollbackable surfaces.
- Add large-file thresholds and chunked blob support before promising large binary rollback.
- Provide hard-purge/admin retention tooling with clear warnings.
- Make pre/post shell capture explicit in UI and tool transcripts.

## Phased Implementation Plan

### Phase 0: Specification Lock

- Define product language for "transparent" vs "controlled" vs "pre/post captured".
- Decide retention defaults and whether historical blobs are encrypted at rest.
- Decide large-file thresholds and chunking requirement.
- Decide whether rollback should require explicit user approval when requested by an agent.

### Phase 1: Core Version Store

- Add blob store, repository schema, and version event model.
- Add path/scope abstractions using `WorkspaceDirectoryService` and `RootRelativePathService`.
- Add consistency checker and repair/report command.
- Unit-test digest storage, root-relative portability, and corruption detection.

### Phase 2: Controlled Work Area Versioning

- Route Work Area save/create/rename/move/copy/delete through versioned service.
- Preserve existing Work Area confinement, protected path, symlink, text-size, and active-reference guards.
- Add history/restore service APIs, but keep UI minimal if necessary.
- Add service/controller tests for rollback and conflicts.

### Phase 3: Agent File Tools And Output Promotion

- Route `AgentFileToolService` write/append/replace through versioned service.
- Add version ids/batch ids to tool responses.
- Record output materialization and promotion as version events.
- Add agent-tool contract tests and output lineage tests.

### Phase 4: Shell/Process Boundary Capture

- Add manifest pre/post diff for `AgentShellToolService`.
- Attach changed-file summary to shell result.
- Add operation-scope locks or expected-head checks.
- Validate concurrent edit and large tree behavior.

### Phase 5: UI Rollback UX

- Add Work Area inspector history.
- Add diff and restore preview.
- Add operation-batch rollback from recent actions/transcripts.
- Add focused Playwright browser validation on desktop/mobile Work Area surfaces.

### Phase 6: Backup/Export Providers

- Add optional Borg/restic or native snapshot checkpoint providers.
- Attach external snapshot ids to version batches.
- Add restore drills and health checks.
- Keep provider failure non-fatal for core app-level versioning unless configured as required.

### Phase 7: Future Widgets/Plugins Contract

- Require rollbackable widgets/plugins to use versioned file handles.
- Add plugin/script capability policy for raw filesystem access.
- Expose explicit "unversioned write" warnings only for admin/debug surfaces.

## Validation Criteria

Unit tests:

- Blob digest, duplicate storage, missing/corrupt blob detection.
- Root-relative blob and version path storage.
- Controlled write/create/delete/move/copy event chains.
- Expected-version conflict handling.
- Retention policy selection and prune eligibility.
- Large-file threshold and chunked/skip behavior.

Integration tests:

- Work Area save, create, rename, move, copy, delete followed by rollback.
- Directory rollback with nested files.
- Project workspace mutation under active write lease.
- Chat file import/history once upload/import exists.
- Output materialization/promotion lineage.
- Run staging cleanup does not delete required blobs.
- Migration compatibility with existing unversioned files: first write should capture pre-image from live filesystem.

Corruption and recovery tests:

- Missing blob referenced by version row.
- Blob digest mismatch.
- Interrupted live write after pending version event.
- DB row committed but live file unchanged.
- Manual host edit detected before next controlled write.

Security/path tests:

- `..` traversal rejected.
- Absolute paths rejected where scope-relative required.
- Symlink file, symlink directory, broken symlink, and destination symlink rollback blocked.
- Hard link policy explicitly tested once decided.
- Project materialized symlink is not copied into output/version stores as a normal directory.
- Restore cannot write outside scope root.

Concurrency tests:

- Two UI saves with stale expected version.
- Agent file tool write racing with UI save.
- Shell pre/post capture while project lease prevents another write.
- Rollback conflict when current head changed after selected version.

Large/binary tests:

- Image/binary version capture and restore.
- Large file threshold warning/skip/chunk behavior.
- Repeated large-file writes storage growth.

Browser/UI tests:

- Work Area file history panel opens and shows actor/source/version ids.
- Text diff is readable and scrollable.
- Restore preview catches conflicts and protected paths.
- Rollback updates list, inspector, preview, and recent actions.
- Desktop and mobile screenshots for Work Area browser history/restore flows.

Agent-tool contract tests:

- File tools return version/batch ids.
- Shell tool reports changed/created/deleted/skipped files from pre/post scan.
- Tool path aliases preserve existing `workspace`, `root`, `outputs`, `run`, and `projects/<projectId>` behavior.
- Agent cannot restore without explicit approved tool/policy when user did not request rollback.

External provider validation if implemented:

- Borg/restic binary missing, repo locked, wrong passphrase, failed prune.
- Snapshot id attached to batch only after successful checkpoint.
- Restore from external snapshot passes Magenta path/symlink validation before writing live files.
- Native snapshot provider unavailable falls back or fails according to configured policy.

## Open Decisions Needing Product Sign-Off

- Does "transparent" mean every host write must be captured, or is Magenta-controlled plus shell pre/post capture acceptable for alpha?
- Which scopes are in v1: Work Areas only, Work Areas plus project workspaces, or all data-root user-visible files?
- Should historical blobs be encrypted at rest separately from the host disk?
- What is the default retention policy for Work Areas, projects, chat files, run staging, and promoted outputs?
- What is the large-file threshold, and is chunked storage required before launch?
- Can users hard-purge historical versions containing secrets, and what audit remains after purge?
- Are symlinks always blocked in rollbackable user-visible scopes?
- Should project Git/JGit support be part of the same initiative or a separate deferred project-source-control feature?
- Should Borg/restic/native snapshots be required for deployments that enable shell/container execution, or optional defense-in-depth?
- What agent permissions are required to initiate rollback?
- How should rollback interact with active assignments, waiting workflows, and project leases?
- Should output artifacts be immutable, restored as copies, or rollbackable in place?

## Final Recommendation

Build the app-level version ledger first and make it boring: immutable blobs, SQLite metadata, service-owned file operations, strict path confinement, expected-version conflicts, and rollback-as-new-event. That gives Magenta product-level undo for the places users actually see and trust: Work Areas, projects, agent file tools, chat files, and outputs.

Then add pre/post capture for shell and container execution. Be honest in UI and audit logs that this is boundary capture, not syscall-level surveillance. Keep Borg/restic/filesystem snapshots as optional backup/checkpoint providers and keep Git/JGit as explicit project-source-control functionality rather than the universal history layer.

This recommendation preserves Magenta's current architecture direction: centralized path layout, service-owned filesystem policy, thin controllers, durable Work Areas, project leases, run-local output staging, backend output promotion, and root-relative portability.
