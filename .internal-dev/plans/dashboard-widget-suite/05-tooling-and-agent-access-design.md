---
schema_version: 1
document_type: tool-design
status: planning
owner: advanced-planner
created: 2026-05-29
---

# Tooling And Agent Access Design

## Registration Strategy

Use static Spring AI `@Tool` classes and existing `MethodToolCallbackProvider` registration, then map widget definitions to exact tool names. Do not promise dynamic widget-specific tool registration in this suite.

Rationale from local code:

- `AvatarAssistantTools` and `AgentOperationalTools` already use annotated static methods.
- `ChatToolRegistry` and tests validate exact tool names from `ToolCallbackProvider`.
- Existing access policy distinguishes `avatar_*` supervisor tools from normal `agent_*` tools.

If a worker later proposes dynamic tools, it must inspect official Spring AI docs/current local dependency docs and return to planning before implementation.

## Widget Tool Descriptor

Each widget definition declares:

- `readTools`: exact names, max result sizes, compact response record type.
- `mutationTools`: exact names, service method owner, confirmation requirement.
- `authorization`: `AVATAR_SUPERVISOR`, `CURRENT_AGENT_CONTEXT`, or `READ_ONLY_DASHBOARD_CONTEXT`.
- `contextScope`: personal, current orchestration context, selected binding display only, project membership, Work Area service.
- `visibleInSettings`: whether the settings modal explains tool availability.

The descriptor is metadata for validation, documentation, and dashboard-aware context. It does not itself bypass `ToolAccessPolicy`.

## Personal Organizer Tools

Expected static tool coverage after phases:

- Today Planner: `avatar_today_plan_get`, `avatar_today_plan_update`, `avatar_quick_capture`, `avatar_day_restart`.
- Tasks/Routines: replace or extend current `avatar_todo_*` and `avatar_daily_task_*` with task/routine tools that support recurrence, subtasks, skip/snooze/restart, due ranges, project links.
- Calendar/Schedule: extend current `avatar_calendar_*` with range queries, time blocks, task projections, and reminder links.
- Notes: retain `avatar_note_append/search`; add file-backed note search/read only through controlled Work Area/project note services where context allows.
- Projects/Contacts/Materials: `avatar_project_context_get`, `avatar_project_artifact_update` or narrower typed tools with service-owned file adapters and confirmation for destructive changes.
- Habits/Trackers: list/upsert/log/archive tools with bounded responses.
- Reminders/Alerts: list/snooze/complete/reschedule tools, in-dashboard only.

## Agent Operational Tools

- Agent-bound widgets display selected agent/project/Work Area settings, but normal `agent_*` tools keep using current `OrchestrationTaskContext`.
- Do not add normal-agent tools that accept arbitrary `agentId`.
- Agent Status/Queue and Agent Outputs widgets may display selected-agent data for the user through dashboard services, but tool access remains governed by current context for agents and supervisor gating for Avatar.
- Output read/list tools must preserve bounded content reads and existing service visibility.

## Dashboard-Aware Assistant Context

Phase 06 may add a read-only Dashboard Context Panel if accepted:

- It summarizes selected dashboard/widget state for display and eventual prompt injection.
- It does not import the full `/chat` client.
- It does not mutate state without approved tools.
- It must distinguish read-only context injection from action tools.
- Any future tool action from dashboard chat requires confirmation/authorization through existing tool policy.

## Validation Requirements

- `ChatToolRegistryTest` or new focused tests assert every declared widget tool name is registered or explicitly marked planned/deferred.
- Tool access policy tests assert PLAN/TASK drafting modes do not expose operational tools.
- Avatar supervisor gating tests assert non-supervisor calls fail.
- Normal agent tool tests assert no arbitrary agent id can be supplied to bypass context.
- Mutation tool tests assert services are called, limits are bounded, destructive confirmations are enforced, and JSON responses are compact.
