# Phase 02 - Dashboard Information Architecture

## Context

The current dashboard is a grid of navigation cards. The target dashboard should be an operational overview of the whole system, not a menu.

## Goal

Replace `/dashboard` with a dense, modern, functional system overview that surfaces state, exceptions, recent work, and fast drill-downs. Keep dashboard chat visible at the top, but wire it only as a placeholder until dashboard-aware prompts/tools are implemented later.

## In Scope

- Dashboard layout and reusable dashboard components.
- Dashboard summary API consumption.
- Open projects, active tasks/workflows/jobs, basic agent status, user inbox, recent outputs, and statistics.
- First viewport chat composer shell for system-level chat.
- Navigation paths from summary cards/rows to module detail pages.
- Desktop and mobile responsive layout.

## Out of Scope

- Implementing dashboard chat tools/prompts.
- Autonomous dashboard actions.
- Replacing module detail pages.

## Implementation Steps

1. Build a reusable dashboard shell with SimplyPages.
   - Use `Page`, `Row`, `Column`, `Grid`, `DataTable`, `Badge`, `Alert`, and `Card` only for repeated entities.
   - Do not nest cards inside cards.
   - Keep `OrchestrationController` thin. Extract dashboard component construction into package-local helper classes if the controller grows past route composition.
   - Use `Template`/`RenderContext`/`SlotKey` for repeated stable structures with dynamic values.

2. Add top-level dashboard sections.
   - Top chat band:
     - Compact system chat composer.
     - Label it as operational system chat in UI copy only if needed; do not add explanatory tutorial text.
     - Disabled or "coming soon" state is acceptable only if visually restrained and tracked in `future_features.md`.
   - System state strip:
     - running jobs;
     - pending assignments;
     - waiting approvals;
     - failed or needs-review items;
     - active agents;
     - Docker runtime health summary.
   - Main overview:
     - "Active Work" table with type, title, owner agent, status, age, next action.
     - "Open Projects" list with owner, workspace, active jobs, recent activity.
     - "Agents" compact health table with status, queue, inbox, running assignment, Docker status.
   - Side rail:
     - user inbox approvals;
     - recent outputs;
     - recent events.

3. Make the dashboard action-first.
   - Every row should answer:
     - what is it;
     - what state is it in;
     - who owns it;
     - what needs attention;
     - where to open details.
   - Prefer row actions and badges over paragraph descriptions.
   - Use progressive disclosure: the dashboard shows status and links, while edits happen on module pages.

4. Define route targets.
   - Project row -> `/projects/{projectId}`.
   - Job row -> `/jobs/{jobId}`.
   - Workflow run row -> `/workflows/{workflowId}` or future `/workflow-runs/{runId}` if added.
   - Assignment row -> `/agents/{agentId}` with queue tab selected if tab deep linking exists.
   - Output row -> `/outputs?runId=...` or detail route if added later.

5. Add frontend module.
   - Build dashboard refresh with HTMX by default:
     - page shell renders server-side;
     - summary sections load from HTMX-targeted partial endpoints backed by `/api/dashboard/summary`;
     - use `hx-get`, `hx-trigger`, `hx-target`, and `hx-swap` for load/refresh behavior.
   - Render empty, loading, error, and stale states in server-rendered partials.
   - Add `generatedAt`/freshness display in the status strip, because operational dashboards need data recency visible.
   - Only add page-level JS when it is the path of least resistance (for example lightweight client-side recency ticker or SSE hook), and keep it narrowly scoped.

6. Visual design guidance.
   - Avoid one-note blue/slate dashboard styling. Use restrained neutral surfaces with purposeful status colors.
   - Use dense tables for operational lists, not large marketing cards.
   - Keep first viewport useful on laptop width: chat band plus status strip plus the top of active work should be visible.
   - On mobile, order: chat, status strip, inbox/action-needed, active work, agents, projects, outputs.

## Validation

- Controller test proves `/dashboard` includes the dashboard page marker and expected HTMX container/targets.
- API test proves `/api/dashboard/summary` includes all required sections.
- Browser validation at desktop and mobile:
  - no console errors;
  - summary loads;
  - summary sections refresh through HTMX requests;
  - empty states are professional and compact;
  - active work rows link to detail pages;
  - text does not overflow or overlap;
  - `/chat` still loads its original chat client.

## Exit Criteria

- `/dashboard` is no longer a set of card links.
- A user can understand open projects, active work, agent status, inbox needs, recent outputs, and system counts without leaving the dashboard.
- Dashboard chat is visually present but explicitly deferred in behavior.
