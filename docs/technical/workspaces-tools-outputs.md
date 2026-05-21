# Workspaces, Tools, and Outputs

Workspace and output behavior is owned by [`ai/orchestration/workspaces`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces). Tool behavior is owned by [`ai/chat/tool`](../../src/main/java/io/mindspice/magenta2/ai/chat/tool).

## Data Root and Directory Layout

Workspace paths are confined under the configured data root managed by `WorkspaceDirectoryService`. The current layout includes:

- Agent workspace root: `agents/<id>/workspace/`
- Project workspace root: `projects/<projectId>/workspace/`
- Effective workspace shared directories: `work/`, `outputs/`, `runs/`, `scratch/`, and `jobs/`.
- Task outputs: `<effective-workspace>/outputs/tasks/<taskId>/<runId>/`
- Workflow outputs: `<effective-workspace>/outputs/workflows/<workflowId>/<runId>/`
- Job outputs: `<effective-workspace>/outputs/jobs/<assignmentId>/<jobRunId>/`
- Opt-in persistent job workspaces: `<effective-workspace>/jobs/<assignmentId>/`
- Agent project links: `agents/<id>/workspace/projects/<projectId>`
- Agent scratch: `agents/<id>/workspace/scratch/`
- Task temp directories under runtime task-run space.
- Workflow temp directories under `runtime/workflow-runs/<runId>/`.
- Persistent chat files: `chats/<conversationId>/files/`

Legacy `agents/<id>/home` and `agents/<id>/outputs` directories are deprecated. `WorkspaceDirectoryService` can migrate warm data roots into the current `workspace/` layout when old directories exist and new counterparts do not.

`EffectiveWorkspaceResolver` centralizes durable workspace selection:

- If `projectId` is present, the project workspace is the effective durable workspace.
- Otherwise, the executing agent workspace is the effective durable workspace.
- `workspaceId` is retained as compatibility metadata and is not interpreted as a project id.

Projects are shared durable workspace and visibility abstractions. They are not executable work units, and `ownerAgentId` is nullable legacy compatibility metadata.

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
- Optional agent id, job id, job assignment id, job run id, project id, workspace id, and run type.
- Output name and artifact type.
- File name and absolute file path.
- Optional content JSON.
- Created timestamp.

API routes:

- `GET /api/outputs`
- `GET /api/outputs/{artifactId}/content`
- `GET /api/outputs/{artifactId}/download`

`GET /api/outputs` currently accepts `agentId`, `jobId`, `projectId`, `runId`, `type`, and `limit`. Output rows also store workspace, plan, job assignment, and job run attribution for internal correlation and future filtering work, but those fields are not all exposed as public query parameters on this route yet.

The controller limits inline content/downloads to 10 MB. Download resolves the real path and rejects files outside the output service data root. Text, JSON, and user-message artifacts are returned inline when safe; other artifacts direct callers to download.

## Ordinary Chat Files

Ordinary `/chat` conversations also have persistent chat-scoped files under `chats/<conversationId>/files/`. These files are created by chat file tools and anonymous chat plan execution context. They are not `run_output_artifacts`, are not indexed in the output artifact table, and are not moved into agent/job output directories.

`ChatFileService` lists regular files recursively from that directory, returns relative-path descriptors, derives simple format labels from extensions, and resolves downloads through real-path confinement. `/api/chat/sessions` exposes `outputCount` for the session card, while `/api/chat/{conversationId}/files` and `/api/chat/{conversationId}/files/download?path=...` power the chat page outputs panel.

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

During task, workflow, or job execution, file and shell tools resolve runtime aliases from the active orchestration context:

- `workspace/`: effective durable workspace root.
- `work/`: effective workspace `work/`.
- `outputs/`: current run output directory.
- `run/`: current run temp/execution directory.
- `scratch/`: effective workspace `scratch/`.
- `job/`: current persistent job workspace, only when the active job assignment/run has one.

Project-scoped runs therefore expose project files through `workspace/`, while agent-scoped runs expose the agent workspace. Existing project-link access under `projects/<projectId>/...` remains compatibility support for older prompts and run contexts.

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
2. The effective workspace resolver selects the project workspace when `projectId` is present, otherwise the executing agent workspace.
3. Workspace directory service provides a work-unit output directory under `outputs/tasks/...`, `outputs/workflows/...`, or `outputs/jobs/...`.
4. `OutputArtifactService` copies or writes materialized content into that output directory.
5. Metadata is inserted into `run_output_artifacts`.
6. Output services and operational pages use run, agent, job, project, workspace, plan, job assignment, job run, or type attribution as needed. Public `GET /api/outputs` exposes the filtered subset documented above.

Temp workspaces can be cleaned up on terminal run states, but output directories persist.

Loose artifact discovery exists only as compatibility behavior. It is gated by service policy, confined by real paths under the configured data root and expected run output directory, and should not be used as the primary contract for new work. New code should publish explicit outputs through output materialization or `OutputArtifactService.publishExistingFile(...)`.

## Temp Retention

`RuntimeSettings.retainTempWork` controls cleanup for task temp work:

- `true`: never auto-delete temp run directories.
- `false`: delete temp directories only after clean completion.

When validation or output materialization detects missing required output, missing referenced file deliverables, or missing final-message deliverables, Magenta keeps the temp directory and marks the run for review instead of silently completing it. Persistent chat files are separate from temp work and are never auto-deleted by this cleanup path.

Waiting workflow runs keep their runtime temp directory so approval and resume state remains available. Durable output directories and persistent job workspaces are not deleted by run temp cleanup.
