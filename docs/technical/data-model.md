# Data Model

Primary Magenta SQLite schema is initialized from [`schema.sql`](../../src/main/resources/schema.sql). Avatar user-centric data is initialized separately from [`avatar-schema.sql`](../../src/main/resources/avatar-schema.sql) into `avatar.sqlite`. Repositories also create tables defensively and add compatibility columns for warm local databases, so code inspection should include the relevant schema file and owning repository before changing schema assumptions.

## Avatar Personal Data

Source package: [`avatar`](../../src/main/java/io/mindspice/magenta2/avatar).

Avatar personal dashboard and organizer state is stored in a separate `<magenta.root.path>/avatar.sqlite` database through the `avatarDataSource` and `avatarJdbcTemplate` beans. This keeps user-centric Avatar data out of the primary `magenta.sqlite` runtime database and avoids cross-database foreign keys.

Tables in `avatar.sqlite`:

- `avatar_profile`: singleton Avatar personal profile row.
- `avatar_preferences`: key/value preference records with JSON values.
- `avatar_dashboard_layout`: singleton dashboard widget layout JSON.
- `avatar_todos`: personal todo records with status, priority, optional due time, and optional project/task/output references.
- `avatar_daily_tasks`: date-scoped daily task rows.
- `avatar_calendar_items`: local calendar entries.
- `avatar_notes`: notes with tag JSON and optional source references.
- `avatar_facts`: durable facts keyed by namespace and key.
- `avatar_events`: append-only Avatar event records.

The backing `agent_profiles` row for `Avatar` remains in the primary Magenta database because agent profiles are part of the existing orchestration/runtime surface. Phase 01 reserves that profile as disabled and direct-line-off without changing runtime defaults.

## Chat, Sessions, and Audit

Source packages: [`ai/chat/repository`](../../src/main/java/io/mindspice/magenta2/ai/chat/repository), [`ai/agent/job`](../../src/main/java/io/mindspice/magenta2/ai/agent/job).

- `ai_chat_memory`: ordered chat messages by `conversation_id` and `message_order`, including message type, text, and metadata JSON.
- `ai_chat_session_metadata`: per-conversation model, title, active task run, planning model, favorite/archive flags, origin, agent id, and updated timestamp.
- `plan_chat_messages`: saved `/plans` planning chat messages by `plan_id`, separate from `/api/chat` session memory.
- `audit_event`: append-only conversation event log for user/assistant messages, tool execution, compaction/context snapshots, errors, token usage, and result previews.
- `agent_jobs`: background chat jobs such as conversation title generation, with selected model, input/result JSON, status, and timestamps.

Compatibility notes:

- `ChatMemoryRepository` can add `message_metadata_json`.
- `ChatSessionMetadataRepository` can add title, favorite, archived, updated, planning model, active task run, origin, and agent columns.
- `AuditRepository` can add audit columns as the audit event shape evolves.

## Plans, Tasks, and Runs

Source package: [`ai/chat/plan`](../../src/main/java/io/mindspice/magenta2/ai/chat/plan). Task-facing records live in [`ai/chat/task`](../../src/main/java/io/mindspice/magenta2/ai/chat/task).

- `plan_definitions`: unified saved plan/task definitions. It stores kind, status, title, summary, goal, notes, deliverables, inputs, outputs, assumptions, steps, validation criteria, execution evidence, validation feedback, prompt/work profile, planning/execution model, settings overrides, planning task text, pending questions/index, plan start order, final message, conversation id, and timestamps.
- `plan_runs`: execution history for saved definitions. It snapshots the full definition, input/output values, effective workspace/output paths, temp workspace path, evidence, validation feedback, deliverable evidence, final/error messages, status, and timestamps.

Important invariants:

- `SESSION_PLAN` uses `id = conversation_id`.
- Task templates use UUID ids and may carry conversation id for draft tracking.
- Saved plan chats are keyed by saved `plan_id`; anonymous `/chat` planning remains keyed by conversation id and does not use `plan_chat_messages`.
- Runs snapshot definitions at start time so later edits do not change historical run meaning.
- Project-scoped runs use the project workspace as the effective durable workspace; agent-scoped runs use the agent workspace.
- Task outputs are stored under `<effective-workspace>/outputs/tasks/<taskId>/<runId>/`.
- `PlanRepository` adds `plan_runs.temp_workspace_path`, output path, workspace, and project compatibility columns for warm databases as needed.
- New `plan_runs.output_directory` and `plan_runs.temp_workspace_path` values are stored relative to the configured data root. Legacy absolute values under the current data root remain compatibility-readable; stale old-root absolute values fail when filesystem operations use them.

## Workflows

Source package: [`ai/orchestration/workflow`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workflow).

- `workflow_definitions`: v2 workflow definitions with schema version, title, summary, max concurrency, nodes JSON, routes JSON, UI layout JSON, and timestamps.
- `workflow_runs`: workflow execution records with current node index, denormalized node runs JSON, temp workspace path, durable output path, nullable agent/job/job-assignment/job-run/project/workspace/run-type attribution, workflow snapshot JSON, final outputs, artifact ids, final/error messages, status, and timestamps.
- `workflow_node_runs`: queryable per-node run rows denormalized from `workflow_runs.node_runs_json`.
- `inbox_messages`: workflow-owned inbox messages for user approvals, workflow agent approvals, notifications, and run-output delivery.

Compatibility notes:

- `WorkflowRepository` adds schema version, max concurrency, UI layout, node/route JSON, run output fields, current node index, node run JSON, workspace/output fields, snapshot, final/error messages, and timestamps when missing.
- Workflow inbox messages are intentionally separate from runtime direct-line agent inbox messages.
- Workflow durable outputs are stored under `<effective-workspace>/outputs/workflows/<workflowId>/<runId>/`; runtime execution state stays under workflow temp space and remains available while a run is `WAITING`.
- New `workflow_runs.workspace_path` and `workflow_runs.output_dir` values are stored relative to the configured data root. Legacy absolute values under the current data root remain compatibility-readable; stale old-root absolute values fail at operation time.

## Agents, Assignments, Runtime Jobs, Inbox, Schedules, Reactions

Source packages: [`ai/orchestration/agents`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/agents), [`ai/orchestration/runtime`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/runtime).

- `agent_profiles`: durable agent profiles with name, status, default model, system prompt, approved tools JSON, shell allowlist JSON, direct-line flag, and timestamps.
- `orchestration_jobs` and `orchestration_job_items`: legacy/runtime orchestration job records used by runtime internals.
- `work_assignments`: durable assignment queue. Fields include agent id, job/job item ids, assignment type, priority, status, model override, compatibility workspace id, first-class project id, effective workspace id/kind, current item index, checkpoint/input/output/evidence JSON, error, lease owner/expires, progress/heartbeat timestamps, lifecycle timestamps.
- `assignment_conversation_links`: durable mapping from assignment ids to chat conversation ids so transcripts remain visible even if checkpoint output is incomplete.
- `agent_inbox_messages`: runtime direct-line agent/operator inbox messages with read/handled flags.
- `agent_schedules` and `schedule_firings`: cron-like assignment scheduling and de-duplication of due firings.
- `agent_event_reactions`: event-to-assignment reaction rules.
- `orchestration_events`: append-only runtime event records with handled timestamp.

Compatibility notes:

- `OrchestrationRuntimeRepository` creates runtime tables and can add retry/continue fields to job items plus project/effective-workspace/progress/heartbeat fields to work assignments.
- New assignments keep `input_json.projectId` for compatibility, but first-class `project_id` is the assignment context source for new UI/API reads.
- Runtime direct-line inbox and workflow approval inbox use different tables and lifecycle models.

## User-Facing Jobs

Source package: [`ai/orchestration/runtime`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/runtime).

- `job_definitions`: user-facing jobs with owner agent runtime hint, project, compatibility workspace, `persistent_workspace_enabled`, status, title, summary, ordered `items_json`, prompt profile, model, settings override JSON, and timestamps.
- `job_runs`: execution records with job assignment id, effective workspace id, work item run JSON, optional persistent job workspace path, output path, final/error messages, status, and timestamps.
- `job_recurrences`: one recurrence per job, with cron expression, timezone, next fire time, enabled flag, and timestamps.

Compatibility notes:

- `JobRepository` can add owner agent, project, workspace, persistent workspace, assignment id, and status columns to older job tables.
- Public job definitions can be saved as empty `DRAFT` rows before items are added.
- Persistent job workspace is opt-in and assignment-scoped under `<effective-workspace>/jobs/<assignmentId>`.
- Job outputs are stored under `<effective-workspace>/outputs/jobs/<assignmentId>/<jobRunId>`.
- Job execution summaries are service read models, not separate tables. They join job definition, assignment, run, workspace, project/agent labels, child run ids, and output counts.
- New `job_runs.workspace_path` and `job_runs.output_dir` values are stored relative to the configured data root. Legacy absolute values under the current data root remain compatibility-readable; stale old-root absolute values fail at operation time.

## Projects

Source package: [`ai/orchestration/runtime`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/runtime).

- `projects`: project metadata, nullable legacy owner agent id, optional git repo URL, prompt profile, model, settings override JSON, and timestamps.
- `project_agent_memberships`: unique project/agent membership rows with roles.
- `project_events`: append-only project event log.

Projects are shared workspace and visibility records, not executable work units. Project services also coordinate with workspace tables for project workspace summaries and release requests.

## Workspaces, Leases, Links, Outputs

Source package: [`ai/orchestration/workspaces`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces).

- `workspaces`: owner type/id, root relative path, display name, metadata JSON, and timestamps.
- `workspace_links`: labeled readable/writable links from a workspace to external targets.
- `workspace_leases`: writable/read leases with holder, mode, expiry, release request flag, released timestamp, and timestamps.
- `run_output_artifacts`: output artifact metadata including run id, plan id, optional agent/job/job-assignment/job-run/project/workspace ids, run type, output name, artifact type, file name/path, content JSON, and timestamp.

Important constraints:

- `idx_workspaces_owner` enforces one workspace per owner.
- `idx_workspace_leases_active_write` enforces at most one unreleased `WRITE` lease per workspace.
- Output artifact queries are indexed by run, agent, job, project, and workspace.
- New `workspace_links.target` values for `PATH` links and new `run_output_artifacts.file_path` values are stored relative to the configured data root. Non-`PATH` link targets are not filesystem-normalized. Legacy absolute current-root values remain compatibility-readable; stale old-root absolute values fail when used.

Compatibility notes:

- `WorkspaceRepository` can add output scoping columns, `workspace_leases.release_requested`, and migrate old lease/output tables into the current shape.
- `WorkspaceDirectoryService` migrates legacy `agents/<id>/home` and `agents/<id>/outputs` directories into the current `agents/<id>/workspace/` layout when safe.

## Runtime Settings

Source package: [`ai/orchestration/settings`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/settings).

- `runtime_settings`: singleton-style persisted defaults for default agent/model, planning/summary/compaction models, context buffer, system chat model/prompt/tools/context limit/enabled flag, assignment history auto-purge days, and temp work retention.

`RuntimeSettingsRepository` adds settings columns as needed for warm databases. `RuntimeSettingsService` falls back to legacy file config when persisted values are absent.
