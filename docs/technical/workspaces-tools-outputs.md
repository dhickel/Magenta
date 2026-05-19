# Workspaces, Tools, and Outputs

Workspace and output behavior is owned by [`ai/orchestration/workspaces`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces). Tool behavior is owned by [`ai/chat/tool`](../../src/main/java/io/mindspice/magenta2/ai/chat/tool).

## Data Root and Directory Layout

Workspace paths are confined under the configured data root managed by `WorkspaceDirectoryService`. The current layout includes:

- Agent workspace root: `agents/<id>/workspace/`
- Agent outputs: `agents/<id>/workspace/outputs/<slug>-<runId>/`
- Agent project links: `agents/<id>/workspace/projects/<projectId>`
- Agent scratch: `agents/<id>/workspace/scratch/`
- Task temp directories.
- Workflow temp directories.
- Persistent chat files: `chats/<conversationId>/files/`
- Job workspaces.
- Project workspaces.
- Agent/job output directories.

Legacy `agents/<id>/home` and `agents/<id>/outputs` directories are deprecated. `WorkspaceDirectoryService` can migrate warm data roots into the current `workspace/` layout when old directories exist and new counterparts do not.

## Workspace Records and Links

`WorkspaceService` owns `Workspace` and `WorkspaceLink` records:

- `workspaces` has owner type/id, root relative path, display name, metadata JSON, and timestamps.
- `workspace_links` has workspace id, label, link type, target, readable/writable flags, and timestamps.

API routes:

- `GET /api/workspaces`
- `GET /api/workspaces/{workspaceId}`
- `GET /api/workspaces/{workspaceId}/links`
- `POST /api/workspaces/{workspaceId}/links`
- `DELETE /api/workspaces/{workspaceId}/links/{linkId}`

Project and agent pages consume the same services for workspace summaries.

## Workspace Leases

`WorkspaceLeaseService` owns `WorkspaceLease` records in `workspace_leases`.

The key invariant is one active writable lease per workspace. SQLite enforces this with `idx_workspace_leases_active_write` on unreleased `WRITE` leases. Service behavior includes:

- Reconcile expired leases before acquisition.
- Acquire leases for holders such as assignments/jobs/projects.
- Extend leases only when holder ownership matches.
- Request release through `release_requested` for graceful drain.
- Mark release completion with `released_at`.

Workspace leases are separate from assignment queue leases in `work_assignments`.

## Output Artifacts

`OutputArtifactService` owns `RunOutputArtifact` records in `run_output_artifacts`.

Artifacts include:

- Run id and plan id.
- Optional agent id, job id, project id, workspace id, and run type.
- Output name and artifact type.
- File name and absolute file path.
- Optional content JSON.
- Created timestamp.

API routes:

- `GET /api/outputs`
- `GET /api/outputs/{artifactId}/content`
- `GET /api/outputs/{artifactId}/download`

The controller limits inline content/downloads to 10 MB. Download resolves the real path and rejects files outside the output service data root. Text, JSON, and user-message artifacts are returned inline when safe; other artifacts direct callers to download.

## Tool Boundaries

Tool services are chat-facing capabilities registered through `ChatToolRegistry`.

File tools:

- Source: [`AgentFileToolService`](../../src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java).
- Provide bounded list/read/search/write/append/replace behavior.
- Resolve paths through scoped roots and normalize path traversal.
- Return structured result records rather than raw side effects only.

Shell tools:

- Source: [`AgentShellToolService`](../../src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java).
- Resolve working directories, enforce command allowlists, capture bounded stdout/stderr, and report exit status.
- Per-agent allowlists come from durable profiles seeded from file config. Wildcard shell command allowance requires the explicit unsafe file-config override.

Web tools:

- Source: [`AgentWebToolService`](../../src/main/java/io/mindspice/magenta2/ai/chat/tool/web/AgentWebToolService.java).
- Provide bounded web search/fetch results based on configured web search settings.
- Truncate large response bodies and return structured metadata.

Plan/task/question tools:

- Plan save tools update persisted plan state.
- Task tools create or operate on task definitions/runs.
- Interaction question tools support model-driven clarification workflows.

## Tool Approval

Agents carry approved tool names in `agent_profiles.approved_tool_names_json`. `ChatToolRegistry` validates configured names and resolves approved tools for chat/model calls. Runtime settings also carry system chat approved tools for the system chat path.

Controllers and settings pages should not accept arbitrary tool execution; they should persist intended allowlists and let chat/tool services enforce them.

## Output Materialization Flow

Typical task/plan/workflow output flow:

1. Execution service produces structured output values and optional file paths.
2. Workspace directory service provides a run output directory.
3. `OutputArtifactService` copies or writes materialized content into that output directory.
4. Metadata is inserted into `run_output_artifacts`.
5. Output APIs and operational pages query by run, agent, job, project, workspace, or type.

Temp workspaces can be cleaned up on terminal run states, but output directories persist.

## Temp Retention

`RuntimeSettings.retainTempWork` controls cleanup for task temp work:

- `true`: never auto-delete temp run directories.
- `false`: delete temp directories only after clean completion.

When validation or output materialization detects missing required output, missing referenced file deliverables, or missing final-message deliverables, Magenta keeps the temp directory and marks the run for review instead of silently completing it. Persistent chat files are separate from temp work and are never auto-deleted by this cleanup path.
