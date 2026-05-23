# Orchestration Runtime

The orchestration runtime is the durable operating layer for agents, assignments, jobs, inboxes, schedules, reactions, projects, runtime settings, workspace ownership, and retained execution history. Source anchors are [`ai/orchestration/runtime`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/runtime), [`ai/orchestration/agents`](../../src/main/java/io/mindspice/magenta2/ai/orchestration/agents), and [`AgentOrchestrationController`](../../src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java).

## Agents

Agent profiles are durable records in `agent_profiles`. `AgentProfileService` owns CRUD and validation. A profile has:

- Name and status.
- Default model.
- System prompt text.
- Approved tool names.
- Allowed shell commands.
- Direct-line enabled flag.

`AgentProfileSeeder` imports a legacy file-configured default agent only when the durable profile store needs a seed. File config remains the model endpoint source, not the durable agent state source.

## Assignments

Assignments are stored in `work_assignments` and represented by `WorkAssignment`.

Core fields:

- `agent_id`, optional `job_id` and `job_item_id`.
- `assignment_type`: task, workflow, job, or other runtime work type.
- `priority`, `status`, `model_override`, first-class `project_id`, compatibility `workspace_id`, `effective_workspace_id`, and `effective_workspace_kind`.
- `current_item_index`, `checkpoint_json`, input/output/evidence JSON.
- `error_text`, lease owner/expires, progress/heartbeat timestamps.
- Created, updated, started, completed timestamps.

`AssignmentService` owns creation, queue reads, retained history reads, lifecycle transitions, deletion, purge, diagnostics, transcript lookup, and assignment-to-conversation links.

`projectId` selects project-scoped execution and the project workspace. `workspaceId` remains compatibility metadata for older callers and UI references; it is not interpreted as project context. New assignments persist first-class project/effective workspace fields and still keep `input.projectId` for compatibility with older records and consumers.

`AssignmentService.AssignmentSummary` is the UI-facing read model for queue/history/project context. It exposes assignment id, agent/job ids, status, `projectId`, compatibility `workspaceId`, effective workspace id/kind/display path, workspace blocker details, linked run ids, and lifecycle timestamps.

## Queue Lifecycle

Assignments enter the queue through API or runtime triggers:

- Plan/task/workflow/job submit routes create assignments.
- Schedules create assignments when due.
- Event reactions can create assignments when matching events are handled.
- Agent direct submission fragments create assignments from operator UI.

Status values are represented by `OrchestrationStatus`. Lifecycle controls include cancel, pause, resume, guarded delete, retained history purge, diagnostics, transcript fragments, and force-interrupt from the operational UI.

The runtime stores retained terminal history separately from active queue reads. History can be purged by age through assignment history APIs and settings.

Assignments waiting on project workspace leases can be requeued after the blocking lease clears through assignment service/operator actions. The UI should show the blocker rather than implying lease release is immediate.

## Leases and Progress

Assignment queue leasing is DB-backed and independent from filesystem workspace leases. Assignment lease fields (`lease_owner`, `lease_expires_at`, progress and heartbeat timestamps) allow runner ownership and stale-run diagnostics.

Workspace writable leases are a separate concept in `workspace_leases`; see [Workspaces, Tools, and Outputs](workspaces-tools-outputs.md).

## Execution Boundaries

`OrchestrationRunnerService` executes assignment work at durable boundaries:

- Task assignments execute through task/chat execution services.
- Workflow assignments execute through `WorkflowRunner`, preserving `WAITING` assignment state for resumable approval/wait runs.
- Job assignments execute ordered job items and can continue/fail based on item policy.

The runtime does not attempt token-level or partial model response resume. Durable recovery is at task, workflow, and job item boundaries using assignment checkpoints and run records.

Every assignment resolves one effective durable workspace before execution: project workspace when `projectId` is present, otherwise the executing agent workspace. Runtime temp directories remain separate from durable outputs.

## Jobs

There are two job-related schemas:

- User-facing `job_definitions`, `job_runs`, and `job_recurrences`, owned by `JobService`/`JobRepository`.
- Runtime legacy/internal `orchestration_jobs` and `orchestration_job_items`, owned by `OrchestrationRuntimeRepository`.

Current public job APIs use `JobDefinition` and `JobWorkItem`. Public job execution submits `AssignmentType.JOB_RUN`. Job items can reference tasks or workflows, include model override and priority, and persist retry count plus continue-on-failure policy. Empty job submissions are valid no-op executions: the assignment-owned `job_runs` row moves to `COMPLETED` with an empty item-run list.

User-facing jobs can opt into persistent per-assignment workspace with `persistentWorkspaceEnabled`. When enabled, job workspace state lives under the effective workspace at `jobs/<assignmentId>`. Job outputs live under `outputs/jobs/<assignmentId>/<jobRunId>` and output artifacts carry job assignment/run attribution. The compatibility `workspaceId` field remains available on definitions and submissions.

`JobExecutionSummary` is the stable read model for operator views. It bridges job definition, assignment id/status, agent/project labels, compatibility workspace id, effective workspace id/kind/path, persistent job workspace state/path, job run id/status, child run ids, output directory/count, and lifecycle timestamps. It covers both the pending gap after assignment creation and the later state after a `job_runs` row exists.

Job recurrence/start behavior is assignment-routed. Recurrence firing enqueues `JOB_RUN` assignments and advances recurrence timestamps; direct job run allocation is reserved for assignment-owned runner execution.

The alpha mutation policy is conservative. Project deletion and membership removal are blocked while active work references the project or member agent. Job deletion and execution-affecting edits, including item changes, project/default agent, recurrence, model, and persistent workspace flag changes, are blocked while non-terminal job assignments or runs exist. Label-only edits remain allowed where services permit them.

## Inboxes

There are two inbox models:

- Runtime direct-line agent inbox: `agent_inbox_messages`, owned by `ai.orchestration.runtime.InboxService`. Used by `/api/agents/{agentId}/inbox` and operational agent inbox surfaces.
- Workflow approval inbox: `inbox_messages`, owned by `ai.orchestration.workflow.InboxService`. Used by workflow user/agent approval nodes and `/api/users/inbox`.

Keep these separate. They have different recipients, lifecycle flags, and use cases.

## Schedules

`ScheduleService` owns `agent_schedules` and `schedule_firings`.

Schedules include agent id, optional job id, assignment template JSON, cron expression, timezone, enabled flag, next run time, and timestamps. `schedule_firings` de-duplicates assignment creation for the same schedule/due time.

API routes under `/api/agents/{agentId}/schedules` are gated by `magenta.features.schedules-enabled`; disabled routes return `404`.

## Event Reactions

`EventReactionService` owns `agent_event_reactions`; `OrchestrationEventService` owns `orchestration_events`.

Reactions match event type and optional filter JSON, then apply an action type such as assignment creation from a stored assignment template. API routes under `/api/agents/{agentId}/event-reactions` are gated by `magenta.features.reactions-enabled`; disabled routes return `404`.

## Projects

`ProjectService` owns projects, memberships, events, and project workspace summaries.

Projects are durable shared workspace, membership, and visibility abstractions. They are not executable work units and no longer require a permanent owner agent. `ownerAgentId` remains a nullable legacy compatibility field on project records and create payloads. Work executes through an agent with optional `projectId`; project-scoped work uses the project workspace as the effective durable workspace.

## Runtime Settings

`RuntimeSettingsService` owns persisted runtime defaults:

- Default agent/model.
- Planning, summary, and compaction model choices.
- Context buffer percent.
- System chat model, prompt, approved tools, context limit, and enabled flag.
- Assignment history auto-purge days.

The settings service falls back to legacy file config where persisted values are unset.

## Operator Visibility

Operational surfaces expose:

- Queue and retained assignment history.
- Assignment diagnostics and audit transcript fragments.
- Inbox message counts and actions.
- Jobs, job runs, outputs, project events, workspace summaries.
- Runtime status and settings.

Controllers should keep those surfaces as transport/read-model adapters and delegate state changes to services.
