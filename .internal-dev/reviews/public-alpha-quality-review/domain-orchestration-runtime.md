# Domain Orchestration Runtime Review

## Agent

- Agent: domain-orchestration-runtime
- Agent id: `019e371c-62a2-7e00-bc55-a366c12e0647`
- Model / reasoning: GPT-5.5 Codex high
- Mode: read-only

## Scope

Reviewed runtime services/controllers/tests for assignments, queue/history diagnostics, schedules, reactions, project workspace leases, and shell-backed filesystem runtime.

## Files, Routes, and Tables Reviewed

- Files: `AssignmentService`, `OrchestrationRuntimeRepository`, `OrchestrationRunnerService`, `ScheduleService`, `OrchestrationEventService`, `EventReactionService`, `ProjectService`, `WorkspaceLeaseService`, `WorkspaceDirectoryService`, `WorkspaceService`, `AgentShellToolService`, `AgentOrchestrationController`, `OrchestrationController`, `AssignmentAuditTranscriptRenderer`.
- Routes: `/api/agents/{agentId}/assignments*`, `/api/agents/{agentId}/assignment-history`, `/agents/_detail/{agentId}/queue*`, `/agents/_detail/{agentId}/history`, diagnostics/transcript routes, project workspace release, schedule/reaction routes.
- Tables: `work_assignments`, `assignment_conversation_links`, `agent_schedules`, `schedule_firings`, `agent_event_reactions`, `orchestration_events`, `workspace_leases`.

## Commands and Probes

- `git status --short`
- `find .. -name AGENTS.md -print`
- Targeted `nl -ba` reads
- `rg --files` over runtime/API/tests
- Targeted `rg` for leases, history, diagnostics, schedule/reaction enqueue, project workspace links, filesystem aliases

## Findings

- High: assignment lifecycle mutation routes are not agent-scoped. Cancel/pause/resume/force-interrupt load only by assignment id and do not verify the route agent owns the assignment.
- High: project workspace leases are acquired but not exposed to the filesystem runtime promised to tasks. The runner acquires the lease but does not materialize the project workspace into the agent workspace view.
- Medium: schedule/reaction assignment templates are not validated at save time; bad persisted templates fail later in polling/event handling.
- Medium: filesystem allocation failure is logged and execution continues with null paths, while stale comments still say Docker will fail later.

## Explicitly Ruled Out

- Terminal queue delete preserving history appears fixed; purge is explicit.
- Stale lease interruption and cancel-request handling are present.
- Force interrupt has late-completion protection through lease-owner guarded save.
- Durable transcript links exist and are used with checkpoint/output/legacy fallback.
