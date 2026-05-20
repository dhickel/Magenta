# Service Architecture Schema And Spec Review

## Scope

This artifact focuses on the database schema, repository ownership, persistence lifecycle, and docs/API/spec mismatches found during the service architecture review.

## Findings

### Current Schema Groups

```sql
-- chat and audit
ai_chat_memory
ai_chat_session_metadata
audit_event
agent_jobs

-- plans, tasks, and plan chats
plan_definitions
plan_runs
plan_chat_messages

-- workflow definitions and execution
workflow_definitions
workflow_runs
workflow_node_runs
inbox_messages

-- runtime assignments and agent operations
agent_profiles
work_assignments
assignment_conversation_links
agent_inbox_messages
agent_schedules
schedule_firings
agent_event_reactions
orchestration_events

-- jobs, projects, workspaces, outputs, settings
job_definitions
job_runs
job_recurrences
projects
project_agent_memberships
project_events
workspaces
workspace_links
workspace_leases
run_output_artifacts
runtime_settings
```

### High: workspace deletion can be blocked by lease rows

`workspace_leases.workspace_id` references `workspaces(id)`, but workspace owner deletion does not remove lease rows before deleting the workspace. Releasing leases is not enough because released rows still reference the workspace.

Affected paths:

- `WorkspaceRepository.deleteByOwner`
- `AgentProfileService.hardDelete`
- `workspace_leases`
- `workspaces`

### High: assignment transcript links can orphan

`assignment_conversation_links` has no FK and is deleted by normal assignment delete/purge, but not by agent-owned reference purge paths that delete assignments in bulk.

Affected paths:

- `OrchestrationRuntimeRepository.deleteAssignment`
- `OrchestrationRuntimeRepository.purgeAgentOwnedReferences`
- `assignment_conversation_links`
- `work_assignments`

### Medium: plan chat messages can orphan through task delete paths

`PlanController` deletes `plan_chat_messages` before deleting a saved plan/task, but `TaskController` and `OrchestrationController` call task deletion paths that bypass that cleanup. `PlanRepository.deleteDefinition` deletes runs and definitions only.

Affected paths:

- `PlanController`
- `TaskController`
- `OrchestrationController`
- `PlanRepository.deleteDefinition`
- `plan_chat_messages`

### Medium: workflow node cleanup relies on FK behavior

`WorkflowRepository.deleteDefinition` deletes workflow runs and definitions but does not explicitly delete `workflow_node_runs`. This relies on FK cascade, even though repository bootstrap is intended to support warm/test databases where FK behavior may differ.

Affected paths:

- `WorkflowRepository.deleteDefinition`
- `workflow_runs`
- `workflow_node_runs`

### Medium: repository bootstrap does not reproduce canonical indexes

`schema.sql` creates indexes that some repository bootstrap paths omit. Fresh app databases get those indexes through SQL init; repository-created warm/test databases may not.

Examples:

- `idx_ai_chat_memory_conversation`
- `idx_workflow_runs_workflow`
- `idx_workflow_node_runs_run`
- plan definition/run indexes noted by the plan review

### Medium: docs and public payloads drift from schema/service behavior

Examples:

- Output schema and repository support `workspace_id`, but `OutputController` cannot filter by `workspaceId`.
- Job docs mention retry count, continue-on-failure, and config JSON, but public `JobWorkItem` and `JobController.JobItemRequest` omit them.
- Runtime status docs describe model/default-agent details, but `RuntimeController` returns only workspace runtime status.
- Security docs describe open-alpha access while `api/web/AGENTS.md` still requires auth/CSRF for unsafe routes.

## Risk Assessment

The schema is broad but mostly understandable. The weak point is not table naming; it is lifecycle ownership. Many references are application-enforced rather than FK-enforced, which is acceptable only if delete, purge, validation, and attribution paths are exhaustive and tested. Current gaps prove they are not yet exhaustive.

Repository bootstrap drift is also a long-term maintenance risk. It can make unit tests, warm local databases, and fresh application databases behave differently.

## Recommendations

1. Add FK-enabled repository tests for every owner delete path.
2. Move child cleanup into owning repositories where possible, not controllers.
3. Decide which references remain application-owned and document their cleanup owner.
4. Add schema bootstrap drift tests that compare required indexes and columns between repository-created DBs and `schema.sql`.
5. Add public API tests for documented query parameters and payload fields.
6. Decide whether to retain or purge output artifacts during agent/job/project hard delete; implement one rule consistently.

## Follow-ups

- Consider explicit migration tooling once schema evolution slows enough to justify it.
- Update `docs/technical/data-model.md` after cleanup ownership is decided.
- Add an API contract table for job item fields, output query filters, runtime status shape, and workflow advanced fields.
