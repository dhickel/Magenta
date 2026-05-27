# Workspaces, Tools, and Outputs

Workspace and output behavior is owned by [`ai/orchestration/workspaces`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces). Tool behavior is owned by [`ai/chat/tool`](../../src/main/java/io/mindspice/magenta2/ai/chat/tool).

## Data Root and Directory Layout

Workspace paths are confined under the configured data root managed by `WorkspaceDirectoryService`. If AI config omits `dataRoot`, the default is `<magenta.root.path>/root`; relative AI `dataRoot` values resolve under `magenta.root.path`, and absolute values remain supported. Static structural path segments are application-owned constants/helpers, not operator configuration. The intended layout is:

- Data-root children: `workspace/`, `chats/`, `agents/`, and `projects/`.
- Agent execution root: `workspace/<agentWorkspaceId>/`.
- Agent Home Work Area: `workspace/<agentWorkspaceId>/home/`.
- User Work Areas: `workspace/<agentWorkspaceId>/workareas/<workAreaId>/`.
- Run staging: `workspace/<agentWorkspaceId>/runs/<runId>/`.
- Model-facing execution outputs: `workspace/<agentWorkspaceId>/runs/<runId>/outputs/`.
- Persistent chat files: `chats/<conversationId>/files/`.

`agents/` stores agent metadata/internal structures, not execution workspaces. `projects/` stores shared project directories. Legacy agent home/output directories, scratch directories, runtime temp paths, and job-owned workspace/output paths may remain compatibility-readable for old records, but they are not current layout contracts.

`EffectiveWorkspaceResolver` centralizes durable workspace selection:

- If `projectId` is present, the project workspace is the effective durable workspace.
- Otherwise, the executing agent workspace is the effective durable workspace.
- `workspaceId` is retained as compatibility metadata and is not interpreted as a project id.

Run output staging and final output promotion are separate concepts. During task, workflow, or job execution, model-facing `outputs/` resolves to the current run-local `runs/<runId>/outputs/`. On successful backend completion, validation, or promotion, declared outputs are copied or materialized to final destinations: jobless task/workflow outputs promote to the agent workspace final `outputs/`, while job-bound task/workflow/job outputs promote to the bound Work Area or project output destination.

Projects are shared durable workspace and visibility abstractions. They are not executable work units, and `ownerAgentId` is nullable legacy compatibility metadata.

## Runtime AGENTS.md Guidance

Magenta runtime `AGENTS.md` support follows the external `agents.md` format baseline: plain Markdown with no required schema, root and nested file placement, and explicit user prompt precedence.

For Magenta runtime behavior, the contract is:

- Starter generation is first-create-only for new agent execution workspaces. A starter `AGENTS.md` is written once at workspace-root creation time and is never overwritten, regenerated, normalized, or compared by hash later.
- Starter guidance is hard-coded for this phase and describes workspace-root expectations, `home/` persistence, `runs/` staging, `<runId>/outputs` staging semantics, `workareas/` user-controlled areas, and project/job binding expectations.
- Resolution is confined to the bound root for the current run context (project root, selected Work Area root when narrowed, or effective agent workspace root). Runtime resolution must fail closed for traversal, symlink escape, or absolute paths outside that root.
- Applicable files are layered from bound root toward the active path. Ancestor guidance remains active context; the closest applicable file has precedence only when instructions conflict. This ancestor retention is Magenta runtime policy; the external site only states nearest-file precedence and explicit prompt override.
- File and shell runtime tools update the active path from their own confined target resolution. Subsequent tool-loop model prompts use that updated path so sibling nested `AGENTS.md` context follows actual `workspace/...`, `root/...`, `outputs/...`, `run/...`, or current-project tool targets instead of a stale workspace root.
- Prompt/context injection runs only for model-backed agent runtime contexts (assignment/agent-bound orchestration context with an agent id). If the turn has no bound root or is ordinary chat without that runtime binding, runtime `AGENTS.md` resolution is omitted.

## Workspace Records and Links

`WorkspaceService` owns `Workspace` and `WorkspaceLink` records:

- `workspaces` has owner type/id, root relative path, display name, metadata JSON, and timestamps.
- `workspace_links` has workspace id, label, link type, target, readable/writable flags, and timestamps.

New `PATH` links are stored as data-root-relative paths. Listing links normalizes legacy absolute targets under the current data root to the same representation and omits stale absolute targets outside the current root.

API routes:

- `GET /api/workspaces`
- `GET /api/workspaces/{workspaceId}`
- `GET /api/workspaces/{workspaceId}/links`
- `POST /api/workspaces/{workspaceId}/links`
- `DELETE /api/workspaces/{workspaceId}/links/{linkId}`

Project and agent pages consume the same services for workspace summaries.

## Work Areas

`WorkAreaService` owns durable Work Area metadata for confined directories inside an agent or project workspace root. A Work Area is the primary user-facing workspace abstraction. New Work Areas use stable DB ids as disk segments under `workareas/<workAreaId>/`; display names stay in the database. The default `home/` Work Area is system-owned, created on demand, and is the v1 default selection point for new assignment work.

Schema:

- `work_areas.id`
- `owner_type` and `owner_id`
- `workspace_id`
- `root_relative_path`
- `area_relative_path`
- `display_name`
- `system_flag`, `home_flag`, and `active_flag`
- `metadata_json`
- `created_at` and `updated_at`

Service behavior:

- `ensureHome(...)` creates `<owner-workspace>/home/` and persists it as a system/Home Work Area.
- New user Work Areas are created under `workareas/<workAreaId>/`; compatibility marking of pre-existing directories must remain explicit.
- The owner workspace root itself cannot be marked as a Work Area.
- Existing inactive Work Areas are reactivated instead of creating duplicate rows.
- `unmark(...)` deactivates user-created Work Areas but refuses Home/system Work Areas.
- Unmarking refuses Work Areas referenced by queued/running assignments or output targets when those assignment metadata columns exist.
- Path checks normalize traversal and use real paths so symlink escapes outside the workspace root are rejected.

Current Work Area persistence is used by assignment runtime routing and the Avatar Work Areas/file explorer surface.

Assignment records now carry first-class Work Area/output routing metadata:

- `selected_work_area_id`: the Work Area selected for assignment execution. New assignments default to the owner `home/` Work Area when Work Area services are available.
- `output_route_type`: `DEFAULT`, `WORK_AREA`, or `DIRECT_DIRECTORY`.
- `output_work_area_id`: the output Work Area when `output_route_type` is `WORK_AREA`.
- `output_direct_relative_path`: an existing owner-root-relative directory when `output_route_type` is `DIRECT_DIRECTORY`.

The metadata is validated at assignment creation. `WORK_AREA` targets must be active Work Areas owned by the same agent/project root. `DIRECT_DIRECTORY` targets must already exist under the owner root and pass the same traversal and symlink confinement policy as Work Areas.

Operational submit forms use the shared HTMX entity selector for selected Work Area and output Work Area fields. Agent-specific submit panels pass the agent owner context into the selector; broader plan/workflow/job submit panels can search active Work Areas and still rely on assignment creation validation for final ownership checks. Direct output routing remains an existing-directory relative path field.

Runtime alias and output directory resolution consume these columns during task, workflow, and job run allocation:

- `workspace/` resolves to the selected Work Area path when present.
- `root/` resolves to the broader owner workspace root.
- `outputs/` resolves to the active run-local `runs/<runId>/outputs/` staging directory.
- `DEFAULT` promotes completed outputs to the default final destination.
- `WORK_AREA` promotes completed outputs to the selected output Work Area.
- `DIRECT_DIRECTORY` promotes completed outputs to the existing owner-root-relative directory.

`WorkAreaExplorerService` provides the backend contract for the Avatar Work Areas/file explorer surface. It supports confined directory listings, rich row/inspect metadata, safe text/Markdown preview and save, image preview/download routing, bounded downloads, directory creation, `.txt` and `.md` creation, sibling rename, copy, move, custom file/directory tags, note labels, recursive delete with typed confirmation, and marking nested directories as Work Areas.

The Avatar UI renders these operations as a Magenta-local HTMX details/list explorer with a separate inspector panel, not as a file card grid. Work Area cards are direct click targets, the toolbar uses icon controls, and the inspector supports collapsed/expanded modes while preserving selected entry context. Filesystem access, path validation, persistence, tags, and audit logging stay in workspace services. Explorer path resolution rejects absolute paths, traversal, symlink path components, unsafe text-edit extensions, oversized text saves, Home/system roots, marked Work Area descendants, and Work Areas referenced by queued/running assignment or output routing metadata.

Row metadata is intentionally lighter than preview: list/inspect classification uses extension, size, and a bounded UTF-8 probe, while preview/save routes perform full text validation. Directory creation validates symlink ancestors before any filesystem write so a rejected path cannot create directories outside the Work Area.

Viewer behavior:

- Markdown opens with the rendered tab active and a Text tab for raw editing.
- Markdown render failure is non-fatal and leaves raw text accessible.
- Plain text opens raw-only without a rendered Markdown tab.
- Images render through the confined inline image route and expose a download link.
- Unsupported/binary files do not expose a row View action and stale viewer requests return a safe fallback.

Copy and move are driven by an HTMX directory-picker popover/module. The user selects a destination directory inside the current Work Area scope, and the service validates confinement, traversal/symlink safety, destination type, collisions, and descendant rules.

Tag editing is modal-driven from the inspector. The Tag Editor modal renders both directory and file groups, each with tag name, target type, and description metadata. Creation and assignment still persist through `workspace_file_labels` and `workspace_file_label_assignments`, with type metadata in `workspace_file_labels.metadata_json.targetType` and optional description text in `workspace_file_labels.metadata_json.description`. Server-side assignment still rejects mismatched typed labels.

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
- File name and stored file path. New rows store data-root-relative slash-separated paths; legacy absolute paths under the current data root remain compatibility-readable.
- Optional content JSON.
- Created timestamp.

API routes:

- `GET /api/outputs`
- `GET /api/outputs/{artifactId}/content`
- `GET /api/outputs/{artifactId}/download`

`GET /api/outputs` accepts `agentId`, `jobId`, `jobAssignmentId`, `jobRunId`, `projectId`, `workspaceId`, `runId`, `planId`, `runType`, `type`, and `limit`. Direct stored attribution is the primary query contract; job output fallback through known job run ids remains compatibility behavior only when direct-only filters are not supplied.

When materialized output filenames collide in the same output directory, `OutputArtifactService` writes or copies the later artifact with a stable numeric suffix instead of overwriting the existing file.

The controller limits inline content/downloads to 10 MB. Download resolves the stored path to a real path and rejects files outside the output service data root. Stale absolute paths from an old root fail at operation time; Magenta does not search old roots or rewrite those rows. Text, JSON, and user-message artifacts are returned inline when safe; other artifacts direct callers to download.

## Ordinary Chat Files

Ordinary `/chat` conversations also have persistent chat-scoped files under `chats/<conversationId>/files/`. These files are created by chat file tools and anonymous chat plan execution context. They are not `run_output_artifacts`, are not indexed in the output artifact table, and are not moved into agent/job output directories.

`ChatFileService` lists regular files recursively from that directory, returns relative-path descriptors, derives simple format labels from extensions, and resolves downloads through real-path confinement. `/api/chat/sessions` exposes `outputCount` for the session card, while `/api/chat/{conversationId}/files` and `/api/chat/{conversationId}/files/download?path=...` power the chat page outputs panel.

For root carry-forward, operators preserve ordinary chat files by copying old `chats/` into `<magenta.root.path>/root/chats/` before starting Magenta on the new root. Workspace, output, and runtime directories are not carried forward automatically and should only be archived outside Magenta when needed.

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

- `workspace/`: selected Work Area when present; otherwise effective durable workspace root.
- `root/`: effective durable owner root, useful when `workspace/` is narrowed to a Work Area.
- `outputs/`: current run output directory.
- `run/`: current run staging directory.

Legacy aliases such as scratch or job workspace are compatibility-only when still accepted by older callers. New prompts and tool guidance should not advertise them as current workflow paths.

Project-scoped runs therefore expose project files through `workspace/`, while agent-scoped runs expose the agent workspace. Existing project-link access under `projects/<projectId>/...` remains compatibility support for older prompts and run contexts.

Web tools:

- Source: [`AgentWebToolService`](../../src/main/java/io/mindspice/magenta2/ai/chat/tool/web/AgentWebToolService.java).
- Provide bounded web search/fetch results based on configured web search settings.
- Truncate large response bodies and return structured metadata.

Plan/task/question tools:

- Plan save tools update persisted plan state.
- Task tools create or operate on task definitions/runs.
- Interaction question tools support model-driven clarification workflows.

Operational agent tools:

- Source: [`AgentOperationalTools`](../../src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalTools.java).
- Normal agent tools use the `agent_` prefix and operate from the active `OrchestrationTaskContext` agent identity. They expose bounded workspace status, queue, assignment lifecycle, inbox, schedule, job, project, and output inspection or mutation.
- Avatar supervisor tools use the `avatar_` prefix and require the durable Avatar profile identity plus explicit per-tool approval. They expose cross-agent and cross-project operational views intended for the personal assistant surface.
- Agent side-panel chat uses `ChatService.chatAsAgent(...)` so model selection, approved tools, chat session origin, and orchestration context are scoped to the selected agent even when the turn runs through the shared chat queue.

Avatar organizer tools:

- Source: [`AvatarAssistantTools`](../../src/main/java/io/mindspice/magenta2/ai/chat/tool/avatar/AvatarAssistantTools.java).
- These tools also use the `avatar_` prefix and require the active orchestration context to be the configured Avatar supervisor agent id, plus explicit approval of the exact tool name on that profile.
- Organizer records are stored through `AvatarService` and the separate `avatar.sqlite` persistence boundary. Tool code must not bypass that service into raw SQL or the primary orchestration repository.
- Current assistant tools cover todos, daily tasks, local calendar items, notes, task submission, research-oriented task submission, and output artifact list/read. They return compact JSON records rather than HTML.
- `avatar_submit_task` and `avatar_submit_research_assignment` create `TASK_RUN` assignments through `AssignmentService`; they require an existing task id and rely on existing assignment validation for agent state, project context, workspace compatibility, and model override behavior.
- `avatar_list_outputs` and `avatar_read_output` use `OutputArtifactService`, including existing query limits, path confinement, and bounded content reads.
- Email processing remains deferred. Do not add a public Avatar email-ingress endpoint in this sprint. Future mail handling should enter through the scripting API, internal messaging, or agents using approved tools to add messages, then publish internal events after endpoint lockdown and redaction rules are designed. Do not store raw email body content or ingress tokens in Avatar persistence, runtime events, logs, prompts, or UI.

## Tool Approval

Agents carry approved tool names in `agent_profiles.approved_tool_names_json`. `ChatToolRegistry` validates configured names and resolves approved tools for chat/model calls. Runtime settings also carry system chat approved tools for the system chat path.

Controllers and settings pages should not accept arbitrary tool execution; they should persist intended allowlists and let chat/tool services enforce them.

Operational tools should be approved by exact name. Do not use wildcard approval for agent or Avatar operational profiles. `ToolAccessPolicy` keeps operational `agent_` and `avatar_` tools out of PLAN/TASK drafting modes; ordinary and execution turns can use them only when the current profile approval and runtime identity checks pass.

Example normal-agent approvals:

```json
[
  "agent_workspace_status",
  "agent_queue_list",
  "agent_assignment_get",
  "agent_assignment_transcript",
  "agent_inbox_list",
  "agent_output_list",
  "agent_output_read"
]
```

Example Avatar supervisor approvals:

```json
[
  "avatar_system_overview",
  "avatar_agent_list",
  "avatar_agent_status",
  "avatar_assignment_list",
  "avatar_project_list",
  "avatar_job_list",
  "avatar_schedule_list",
  "avatar_output_list",
  "avatar_output_read",
  "avatar_todo_list",
  "avatar_todo_upsert",
  "avatar_todo_complete",
  "avatar_daily_task_list",
  "avatar_daily_task_upsert",
  "avatar_daily_task_complete",
  "avatar_calendar_list",
  "avatar_calendar_upsert",
  "avatar_calendar_delete",
  "avatar_note_append",
  "avatar_note_search",
  "avatar_submit_task",
  "avatar_submit_research_assignment",
  "avatar_list_outputs",
  "avatar_read_output"
]
```

## Output Materialization Flow

Typical task/plan/workflow output flow:

1. Execution service produces structured output values and optional file paths.
2. The effective workspace resolver selects the project workspace when `projectId` is present, otherwise the executing agent workspace.
3. Workspace directory service provides run-local output staging under `runs/<runId>/outputs/`.
4. Backend completion, validation, or promotion copies or writes declared outputs to the final destination.
5. Metadata is inserted into `run_output_artifacts`.
6. Output services and operational pages use run, agent, job, project, workspace, plan, job assignment, job run, or type attribution as needed. Public `GET /api/outputs` exposes the filtered subset documented above.

Run staging is retained for at least one day and can be cleaned only by a retention-aware cleanup path. Final promoted output destinations persist.

Loose artifact discovery exists only as compatibility behavior. It is gated by service policy, confined by real paths under the configured data root and expected run output directory, and should not be used as the primary contract for new work. New code should publish explicit outputs through output materialization or `OutputArtifactService.publishExistingFile(...)`.

Task completion can optionally copy retained run-staging files into the final output publication by passing `includeTempWithOutput=true` through `task_complete`. The copy runs after declared output materialization and before loose artifact discovery and terminal persistence. It copies regular files from confined run staging into `copied-temp/` inside the final output destination, registers each copied file as a `copied_temp/...` output artifact, skips symlinks including project workspace links, and fails completion if the requested publication cannot be completed safely.

## Temp Retention

Run staging cleanup is retention-aware. Staging must be retained for at least one day; settings may make retention longer, but terminal completion must not immediately delete staging.

When validation or output materialization detects missing required output, missing referenced file deliverables, or missing final-message deliverables, Magenta keeps run staging and marks the run for review instead of silently completing it. Persistent chat files are separate from run staging and are never auto-deleted by this cleanup path.

Waiting workflow runs keep their run staging so approval and resume state remains available. Final output destinations are not deleted by run staging cleanup.
