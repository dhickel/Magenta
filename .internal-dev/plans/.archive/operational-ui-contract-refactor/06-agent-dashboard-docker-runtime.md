# Phase 06 - Agent Dashboard And Docker Runtime Visibility

## Context

Agents handle the most operational responsibility, but current pages render shallow counts, JSON blocks, and raw text fields. Docker runtime is required and verified at startup, but not visible in agent dashboards.

## Goal

Redesign agents as detailed operational dashboards with structured editing, queue/inbox/job/workspace visibility, and Docker/runtime monitoring.

## In Scope

- Agents list and detail redesign.
- Agent-only dashboard similar to main dashboard but deeper.
- Docker status API and UI.
- Expandable profile/tool/prompt editors.
- Structured list editors for tools, shell allowlist, deliverables/validation/notes-like fields where applicable.
- Queue/inbox/jobs/workspace/history views with useful rows instead of raw JSON.

## Out of Scope

- Long-running container lifecycle management beyond current per-execution model.
- New subagent lifecycle features.
- Replacing the agent side-panel chat backend.

## Implementation Steps

1. Add agent summary contract.
   - Extend `GET /api/agents` response or add `GET /api/agents/summary`.
   - Include:
     - profile id/name/status/model;
     - queue counts by status;
     - inbox counts;
     - running assignment;
     - active jobs;
     - last output/event;
     - Docker runtime status summary if available.

2. Add Docker status endpoint.
   - Add controller under web layer, backed by a small service wrapper around `DockerRuntimeClient`.
   - Suggested endpoint:
     - `GET /api/runtime/docker/status`
   - Response:

```java
public record DockerStatusResponse(
    boolean enabled,
    boolean available,
    String dockerHost,
    String agentImage,
    String message,
    Instant checkedAt
) {}
```

   - `DockerRuntimeClient` currently verifies daemon/image at startup. Add a safe status method if the bean exists. If Docker is disabled by property, return `enabled=false`.
   - Do not hide startup failures behind success states.

3. Agents list redesign.
   - Replace pure cards with dense list/table plus detail preview.
   - Columns:
     - name/status;
     - model;
     - queue;
     - inbox;
     - running work;
     - Docker;
     - recent output/event.
   - Keep create/reload/search controls compact.
   - Implement list filtering, row refresh, and detail preview updates with HTMX by default.

4. Agent detail dashboard.
   - First section:
     - agent identity/status/model;
     - Docker status;
     - queue/inbox/running counters;
     - current assignment if any;
     - quick chat button.
   - Main tabs or panels:
     - Dashboard;
     - Queue;
     - Inbox;
     - Jobs;
     - Workflows/Tasks submitted to this agent;
     - Workspace;
     - Outputs;
     - History.
   - Rows should show status, owner, created/updated age, next action, and detail link.
   - Avoid raw JSON except in an advanced debug details panel.
   - Use HTMX for tab/panel content loads and row actions unless a specific interaction is clearly easier in JS.

5. Structured editors.
   - Profile editor as expandable sections:
     - Identity/status/model.
     - System prompt.
     - Tools.
     - Shell allowlist.
     - Direct line/settings.
   - Tools editor:
     - fetch known tool registry if available;
     - checkbox or multi-select list;
     - validate unknown tools server-side through existing `ChatToolRegistry`.
   - Shell allowlist editor:
     - list rows, one command/pattern per row;
     - remove comma-separated editor.
   - Prompt editor:
     - textarea with save/cancel;
     - show dirty state.
   - Build save/cancel and section refresh paths with HTMX posts/patches and fragment swaps first.
   - Use JavaScript only for least-resistance enhancements such as dirty-state affordances or advanced multi-select ergonomics.

6. Submit work panel.
   - Replace raw assignment JSON with structured fields:
     - type select;
     - plan/workflow/job selector based on type;
     - priority;
     - model override;
     - workspace;
     - generated input fields when selected item has schema.
   - Validate before submit.
   - Submit and response rendering should be HTMX-driven by default.

7. Preserve agent side-panel chat.
   - Keep existing `/api/agents/{agentId}/chat/stream`.
   - Improve page context sent from dashboard/detail pages so chat knows what agent/page/entity the user is discussing.
   - Do not implement new agent creation loops here; track in future features.

## Validation

- Unit/controller tests:
  - Docker status endpoint enabled/disabled cases;
  - agent summary returns queue/inbox counts;
  - unknown tool save still fails clearly;
  - shell allowlist list maps to persisted list.
- Browser validation:
  - agents list loads summaries;
  - agent detail dashboard shows Docker status;
  - profile sections expand/edit/save;
  - tools and shell allowlist save as arrays;
  - tab/panel loads and form submissions are HTMX-driven for normal flows;
  - submit work creates assignment without raw JSON.
- `mvn test`
- Startup smoke.

## Exit Criteria

- Agent pages are detailed operational dashboards.
- Docker status is visible on dashboard and agent pages.
- Agent editors match backing data structures.
- Raw JSON/CSV editing is removed from normal agent workflows.
