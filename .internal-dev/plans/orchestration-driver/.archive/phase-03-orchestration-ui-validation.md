# Orchestration UI and Validation

## Context

Current browser UI is mostly embedded in `FrontendController`, with `/chat` backed by `chat-client.js`. The user explicitly required that `/chat` not be touched. Task and workflow pages exist but are barebones and embedded in Java strings.

The orchestration UI needs a professional operational surface: agent overview, agent detail, side-panel agent chat, settings, job editor, task/workflow improvements, queues, schedules, inbox, event reactions, and run history. This UI is stateful enough that plain static JS modules are preferable to making HTMX the core mechanism. Any stale HTMX reference should be fixed or removed only if still required by existing pages.

## Goal

Build the new orchestration UI on separate pages and static assets, validate it with automated tests and Playwright MCP, and preserve the existing chat UI untouched.

## In Scope

- Add settings page for DB runtime settings.
- Add agent overview and agent detail pages.
- Add collapsible/reopenable agent chat side panel for agent pages and configuration pages.
- Add job overview/detail/editor pages.
- Improve task/workflow pages to support real editing and orchestration run context.
- Add live queue/run/inbox/schedule/event reaction status views.
- Add frontend tests and Playwright MCP validation fixtures.
- Update live-chat MCP workflow knowledge only if new validated gotchas are discovered.

## Out of Scope

- Modifying `/chat` or `chat-client.js`.
- Adding a SPA build stack.
- Making HTMX the main orchestration UI mechanism.
- External webhook, repo clone, or watcher UI.
- Branching job graph editor.

## Implementation Steps

1. Refactor `FrontendController` for new pages:
   - Keep existing `/chat` route and embedded chat content unchanged.
   - Add lightweight shell routes for `/settings`, `/agents`, `/agents/{id}`, `/jobs`, `/jobs/{id}`, `/tasks`, and `/workflows`.
   - Move new orchestration page behavior into static files under `src/main/resources/static/js/orchestration/` and `src/main/resources/static/css/orchestration.css`.
2. Add shared frontend utilities:
   - JSON fetch wrapper with clear error rendering.
   - SSE parser for agent chat and run progress.
   - Small DOM helpers for forms, tabs, modals, and status chips.
   - Keep these independent from `chat-client.js`.
3. Settings page:
   - Edit default agent, default model, planning model, summary model, compaction model, and context buffer.
   - Models are selected from file-config model keys/remote names.
   - Save through `/api/settings/runtime`.
4. Agents overview:
   - Agent cards with name, status, default model, current queue count, active assignment, unread inbox count, and recent job/run summary.
   - Actions: create, clone, disable/delete, open detail.
5. Agent detail:
   - Tabs or segmented views for dashboard, inbox, queue, schedules, event reactions, jobs, workspace, history.
   - Editable profile controls: name, model, system prompt, approved tools, shell allowlist, direct-line setting.
   - Submit task/workflow/job work with priority and optional model override.
   - Show current state and run history with checkpoint/evidence summaries.
6. Side-panel agent chat:
   - A collapsible panel that can be opened from agent, task, workflow, and job pages.
   - Stream via `/api/agents/{id}/chat/stream`.
   - The panel sends page context when opened from partial task/workflow/job configuration so the agent can inspect and suggest corrections.
   - Agent responses can recommend changes; automatic mutation should only happen through explicit tools/services already allowed by backend.
7. Jobs UI:
   - Job list and detail page.
   - Create/edit title, summary, owner agent, default model, workspace links.
   - Ordered item editor for task run, workflow run, wait-for-message, and report/checkpoint items.
   - Per-item model override and priority controls.
   - Run/cancel/pause/resume actions.
   - Checkpoint/evidence/run history display.
8. Task/workflow UI improvements:
   - Keep definitions reusable.
   - Add agent/job/model/priority context when submitting runs.
   - Workflow editor should render bindings with structured controls instead of raw JSON as the primary path.
   - Preserve compatibility warnings inline.
   - Link task/workflow runs back to assignments/jobs where applicable.
9. Visual design:
   - Operational dashboard style: dense but readable, restrained colors, no landing-page hero.
   - Use stable dimensions for cards, tables, toolbar buttons, queues, and status chips.
   - Avoid nested cards and decorative backgrounds.
   - Use recognizable icons where available in the existing stack; otherwise use compact text buttons.
10. HTMX cleanup:
   - Search for actual HTMX dependency.
   - If unused, remove the broken page reference.
   - If used by Simply Pages shell, add the correct webjar or static resource and cover it with a small page render/smoke test.
11. Add test fixtures:
   - `.internal-dev/test-fixtures/orchestration-driver/`
   - Agent profile fixture.
   - Task/workflow/job pipeline fixture.
   - Schedule/event reaction fixture.
   - Expected status/checkpoint assertions.

## Validation

- Automated tests:
  - Page render tests for `/settings`, `/agents`, `/agents/{id}`, `/jobs`, `/jobs/{id}`, `/tasks`, `/workflows`.
  - Tests assert `/chat` still includes `/js/chat-client.js?v=23` and new pages do not depend on it.
  - Controller tests for agent chat stream start/error behavior.
  - JS static-file smoke tests through string checks for endpoint names, SSE parsing, and root selectors.
- Playwright MCP validation:
  - Start app with isolated SQLite.
  - Open `/agents`; create/clone/disable an agent.
  - Open an agent detail page; verify inbox, queue, schedules, event reactions, workspace, and side-panel chat controls.
  - Submit a task assignment with explicit model override and priority.
  - Create a job with ordered task/workflow/report items.
  - Run job, cancel/pause/resume if supported by phase 02 APIs, and verify persisted checkpoints after reload.
  - Open `/tasks` and `/workflows`; verify editors still work and can submit runs with orchestration context.
  - Ensure `/chat` still loads and existing live-chat workflow still passes.
- Startup smoke:
  - `mvn test`
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-orchestration-phase03.sqlite'`

## Exit Criteria

- New orchestration pages provide a usable dashboard for agents, jobs, queue, inbox, schedules, event reactions, workspaces, and run history.
- Agent side-panel chat works on orchestration/configuration pages without touching the existing chat UI.
- Task/workflow pages are no longer barebones and support orchestration execution context.
- Playwright MCP validates the main orchestration workflow against a real Spring Boot app and isolated SQLite DB.
- Any stale HTMX reference is either fixed or removed with test coverage.
