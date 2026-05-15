# Phase 04 - Projects And Agent Surfaces

## Context

Projects currently expose work type terminology that should become manager type, and agent selection should be a dropdown backed by durable agent profiles. The agent page is visually over-wide: profile editing lives in a side column, the dashboard tab claims too much horizontal space, Docker unavailable messaging is too easy to dismiss as normal, and "Chat with Agent" currently links to `/chat?agent=...` while the operational side-panel chat uses `/api/agents/{agentId}/chat/stream`.

## Goal

Make projects and agents usable for alpha: projects configure management style and agent ownership with structured controls, agent profile editing lives under the top tab module, Docker runtime status is accurate and actionable, and agent chat is an accordion/panel that actually talks to the selected agent.

## In Scope

- Rename project "work type" UI to "manager type".
- Replace free-text agent/project owner entry with dropdowns where the backing data is an agent id.
- Review and complete project setup constraints needed for alpha.
- Re-layout agent detail so Profile is a tab/panel at the top, not a squeezed side column.
- Fix horizontal overflow and dense table issues in agent list/detail.
- Make Docker unavailable/disabled reasons actionable and correct.
- Replace "Chat with Agent" link with expandable operational chat accordion.
- Fix active tab styling so Dashboard is not permanently selected.

## Out of Scope

- Multi-tenant authorization.
- Agent cloning.
- Drag-canvas project planning.

## Implementation Steps

1. Inspect `Project`, `ProjectService`, `ProjectRepository`, project controller/UI sections, and all project forms.
2. Rename user-facing "Worktype" to "Manager Type" or "Manager Profile". Keep database field names only if renaming persistence is larger than necessary for alpha; document any internal-name mismatch.
3. Convert project agent fields to `Select` controls populated from `agentProfileService.list()`:
   - owner agent;
   - project members;
   - default submit-to-agent;
   - any reviewer/manager agent slots.
4. Review project constraints and add missing alpha guards:
   - project name required;
   - owner agent exists if set;
   - duplicate memberships rejected;
   - disabled agents shown but not default-selected for new work;
   - model override uses configured model key.
5. Rework `agentDetailLayout()`:
   - top area: tab navigation and profile tab under the main module;
   - remove the persistent right-side profile editor;
   - "Submit Work" can be a tab or collapsible action panel, not a side card that forces horizontal squeeze.
6. Add/adjust tabs: dashboard, profile, queue, inbox, jobs, schedules, reactions, workspace, outputs, history, chat, docker.
7. Move `agentEditor()` sections into the profile tab. Keep identity, prompt, tools, and shell commands as sub-sections or nested accordions.
8. Replace profile free-text fields where backing data is structured:
   - default model is a dropdown of model keys;
   - approved tools should be checkboxes or multi-select chips from `ChatToolRegistry` where possible;
   - allowed shell commands may remain a structured list editor rather than comma text.
9. Docker runtime:
   - inspect `AgentContainerRuntimeService`, `DockerRuntimeClient`, `DockerRuntimeConfig`, `application.yml`, and docker package `AGENTS.md`;
   - distinguish "feature disabled by config", "Docker client bean unavailable", "daemon/socket unreachable", "image missing", and "container stopped";
   - surface the exact reason in `/agents/_detail/{agentId}/docker-status` and the list column;
   - do not mark Docker as unavailable if Podman-compatible configuration is present but the UI failed to request status correctly.
10. Chat with agent:
   - remove or demote `/chat?agent=...` link unless it is proven to route through a special agent context;
   - use an accordion at the top of the agent detail content;
   - initialize `agent-chat.js` only for that accordion;
   - acceptance: the request goes to `/api/agents/{agentId}/chat/stream` with the selected agent id.
11. Fix tab active styling. The `tabNav()` helper currently marks Dashboard active on initial render. Add active tab tracking by rendering active class only for the loaded tab or updating it after HTMX swaps.

## Validation

- Project service/controller tests reject missing owner agent, unknown agent ids, duplicate members, and unknown model keys.
- Controller tests prove project forms render agent dropdowns and Manager Type label.
- Controller tests prove agent detail profile is in a tab/panel, not the side column.
- Docker status tests cover disabled config, unavailable daemon, stopped container, running container, and restart/start/stop HTMX targets.
- Agent chat controller/browser tests prove the accordion sends to `/api/agents/{agentId}/chat/stream`.
- Playwright MCP validates `/projects` and `/agents` at desktop and mobile widths:
  - no horizontal overflow;
  - Profile tab renders under the top tab module;
  - Docker status explains the real reason;
  - Chat accordion opens and sends a request for the selected agent;
  - Dashboard tab active styling moves when other tabs are selected.

## Exit Criteria

- Projects use manager terminology and structured agent selection.
- Agents page is no longer horizontally cramped.
- Docker status is actionable and accurate.
- Agent chat is agent-specific, not a generic link.

