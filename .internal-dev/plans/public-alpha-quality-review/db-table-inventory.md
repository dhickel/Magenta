# Database Table Inventory

## Agent

- Agent: main Codex campaign coordinator
- Model / reasoning: current parent Codex session
- Scope: SQLite schema and repository-created tables/upgrade paths
- Command: `rg -n "CREATE TABLE|create table|ALTER TABLE|alter table" src/main/java src/main/resources -g '*.java' -g '*.sql' -g '*.yml'`

## Tables in `src/main/resources/schema.sql`

- `ai_chat_memory`
- `ai_chat_session_metadata`
- `agent_jobs`
- `plan_definitions`
- `plan_runs`
- `audit_event`
- `workflow_definitions`
- `workflow_runs`
- `workflow_node_runs`
- `agent_profiles`
- `orchestration_jobs`
- `orchestration_job_items`
- `work_assignments`
- `assignment_conversation_links`
- `inbox_messages`
- `agent_schedules`
- `schedule_firings`
- `agent_event_reactions`
- `orchestration_events`
- `runtime_settings`
- `workspaces`
- `workspace_links`
- `workspace_roots`
- `workspace_leases`
- `run_output_artifacts`
- `job_definitions`
- `job_work_items`
- `job_runs`
- `job_recurrences`
- `projects`
- `project_agent_memberships`
- `project_events`

## Repository Schema Owners

- Chat memory/session/audit: `ChatMemoryRepository`, `ChatSessionMetadataRepository`, `AuditRepository`.
- Plan persistence: `PlanRepository`.
- Workflow persistence: `ai.orchestration.workflow.WorkflowRepository`; legacy-looking chat workflow classes also exist under `ai.chat.workflow`.
- Agent profiles/settings: `AgentProfileRepository`, `RuntimeSettingsRepository`.
- Runtime assignments/jobs/projects: `OrchestrationRuntimeRepository`, `JobRepository`, `ProjectRepository`.
- Workspaces/outputs: `WorkspaceRepository`.
- Legacy agent jobs: `AgentJobRepository`.

## Drift Targets

- `workflow_definitions` has repository-side upgrade columns (`schema_version`, `max_concurrency`, `ui_layout_json`, `nodes_json`, `routes_json`) that must remain compatible with warm DBs.
- `run_output_artifacts` and `workspace_leases` have repository-side upgrade columns (`agent_id`, `job_id`, `project_id`, `workspace_id`, `run_type`, `release_requested`) that must be checked against `schema.sql`.
- `work_assignments` has repository-side heartbeat/progress upgrade columns (`last_progress_at`, `last_heartbeat_at`) that should be validated on warm DB startup.
- `OrchestrationRuntimeRepository` creates `agent_inbox_messages`, while `schema.sql` lists `inbox_messages`; review should confirm whether both are intentional or stale.
