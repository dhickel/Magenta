# Durable Queue, Scheduler, Events, and Resume

## Context

Magenta already has an in-memory priority executor and per-conversation chat turn coordination, but orchestration work needs durable user-facing assignments that survive restart, support priority, record checkpoints, and resume at step boundaries. Existing task/workflow services can persist definitions and runs, but task/workflow execution is still synchronous and not bound to agents, jobs, schedules, events, or workspaces.

Jobs are not the existing internal `AgentJobService` conversation-title jobs. User-facing jobs are bounded workspaces/domains containing ordered work items. Agents may execute tasks/workflows only through an assignment created by user submission, schedule trigger, or internal event reaction.

## Goal

Add durable orchestration assignments, ordered jobs, scheduling, internal event reactions, and step-boundary resume.

## In Scope

- Add user-facing job definitions and ordered job work items.
- Add durable assignment queue with priority, owner agent, optional job/workspace context, model override, status, evidence, and checkpoint state.
- Add step-boundary resume for task/workflow/job execution.
- Add cron-like schedules that enqueue assignments.
- Add internal event reactions for agents.
- Add inbox/direct-line message persistence needed for event-driven work.
- Extend task/workflow submission APIs and services with agent/job/model/priority context.

## Out of Scope

- Token-level or tool-call-level model resume.
- Branching job graphs.
- External webhooks.
- File/repo watchers.
- Git clone and credential lifecycle.
- UI beyond API contracts and minimal status views needed for testing.

## Implementation Steps

1. Add orchestration status enums:
   - `QUEUED`
   - `RUNNING`
   - `WAITING`
   - `PAUSED`
   - `INTERRUPTED`
   - `CANCEL_REQUESTED`
   - `CANCELLED`
   - `FAILED`
   - `COMPLETED`
   - `NEEDS_REVIEW`
2. Add schema/repositories for:
   - `orchestration_jobs`: id, owner agent id, title, summary, default model, workspace id, status, created/updated timestamps.
   - `orchestration_job_items`: id, job id, item order, item type, task id, workflow id, model override, priority, config JSON, created/updated timestamps.
   - `work_assignments`: id, agent id, optional job id, optional job item id, assignment type, priority, status, model override, workspace id, current item index, checkpoint JSON, input JSON, output JSON, evidence JSON, error text, lease owner, lease expires, timestamps.
   - `agent_inbox_messages`: id, to agent, from agent/user/system, message type, body, metadata JSON, read/handled flags, timestamps.
   - `agent_schedules`: id, agent id, optional job id, assignment template JSON, cron expression, timezone, enabled flag, next run, timestamps.
   - `agent_event_reactions`: id, agent id, event type, filter JSON, action type, assignment template JSON, enabled flag, timestamps.
   - `orchestration_events`: id, event type, source type/id, payload JSON, created timestamp, handled timestamp.
3. Define assignment creation rules:
   - User assignment creates `work_assignments` directly.
   - Schedule due creates a `SCHEDULE_DUE` event, then enqueues its configured assignment.
   - Internal event reaction creates an assignment only through the reaction service.
   - Direct-line messages write inbox rows and may create interrupting assignments only if direct-line permission allows it.
4. Define executable assignment types:
   - `TASK_RUN`
   - `WORKFLOW_RUN`
   - `JOB_RUN`
   - `AGENT_MESSAGE`
   - `WAIT_FOR_MESSAGE`
   - `REPORT`
5. Implement model override precedence:
   - Explicit execution request override.
   - Job/workflow step override.
   - Job default model.
   - Task/workflow default model if added.
   - Agent default model.
   - Runtime global default model.
6. Implement durable runner service:
   - Poll queued work by priority and created time.
   - Acquire lease before running.
   - Mark stale `RUNNING` leases as `INTERRUPTED` or eligible for resume.
   - Check `CANCEL_REQUESTED` between steps.
   - Persist evidence and checkpoint after every completed step.
   - Use `MagentaWorkExecutor` for bounded execution lanes, but DB remains source of truth.
7. Implement step-boundary resume:
   - Task assignment resumes from task run status and persisted checkpoint.
   - Workflow assignment resumes from persisted completed step runs and output map.
   - Job assignment resumes from completed job item index and item outputs.
   - Incomplete model turns may be retried from the beginning of the current step with prior evidence included.
   - Do not attempt to continue a partial model response.
8. Extend task/workflow services:
   - Add run request context record with `agentId`, `jobId`, `workspaceId`, `modelOverride`, and `priority`.
   - Bind run workspace and model choice into execution instructions.
   - Persist task/workflow run references on assignments.
9. Add scheduler service:
   - Poll enabled schedules.
   - Compute next run from cron expression and timezone.
   - Enqueue assignment templates idempotently per due time.
10. Add internal event reaction service:
   - Supported v1 events: inbox message received, schedule due, task status changed, workflow status changed, job status changed, manual user event.
   - Match simple JSON filters.
   - Enqueue configured assignment action.
11. Add agent state tools:
   - Read own inbox.
   - Read own active assignment and queue.
   - Read own schedules and event reactions.
   - Read current job/workspace summary.
   - Send message to another agent only through inbox/direct-line service.
12. Add thin APIs:
   - `/api/agents/{id}/inbox`
   - `/api/agents/{id}/assignments`
   - `/api/agents/{id}/schedules`
   - `/api/agents/{id}/event-reactions`
   - `/api/jobs`
   - `/api/jobs/{id}/items`
   - `/api/jobs/{id}/runs`
   - `/api/jobs/{id}/events`
   - Extend task/workflow run request bodies with orchestration context.

## Validation

- Repository tests:
  - Job and ordered item persistence.
  - Assignment status transitions and checkpoint JSON persistence.
  - Inbox message persistence and read/handled flags.
  - Schedule and reaction persistence.
- Service tests:
  - Queue priority ordering.
  - Lease acquisition prevents duplicate execution.
  - Cancel request stops before next step.
  - Stale running lease resumes from last completed step.
  - Job run executes ordered task/workflow/wait/report items.
  - Workflow resume skips completed steps and uses persisted output map.
  - Model override precedence.
  - Internal event reaction enqueues expected assignment.
  - Agents cannot execute task/workflow except through assignment creation paths.
- Controller tests:
  - Job CRUD and item ordering.
  - Assignment creation, cancel, pause/resume.
  - Schedule creation rejects invalid cron/timezone.
  - Event reaction creation rejects unsupported event/action types.
- Regression:
  - Existing task/workflow APIs still work without orchestration fields.
  - Existing internal title jobs still work and remain separate.
- Startup smoke:
  - Run Spring context with isolated SQLite and verify queued stale work recovery does not fail startup.

## Exit Criteria

- User-facing jobs exist as ordered work item pipelines with managed workspace binding.
- Agents can receive assignments only through user submission, schedules, or internal event reactions.
- Task/workflow/job execution records durable checkpoints and can resume at step boundaries.
- Priority, cancellation, interruption, and status transitions are observable through APIs.
- Existing chat and internal conversation-title jobs are not conflated with user orchestration jobs.
