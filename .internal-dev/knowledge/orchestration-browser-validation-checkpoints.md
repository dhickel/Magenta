# Topic
Browser validation checkpoints for the Magenta orchestration dashboard.

## Source References
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/resources/static/css/orchestration.css`
- `src/main/resources/static/js/orchestration/`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- Live MCP validation run on 2026-05-11 against `http://localhost:18080` with `jdbc:sqlite:/tmp/magenta2-validation.sqlite`

## Key Takeaways

The orchestration dashboard shell is built once per `OrchestrationController` constructor and reused across all pages. The sidebar navigation is rendered via SimplyPages `SideNav` component with collapsible behavior on mobile. Desktop layout at 1280x720 assigns 250px to sidebar and ~920px to main content. Mobile layout at 375x812 collapses the sidebar and prevents horizontal overflow.

All static orchestration JS modules (`dashboard.js`, `plans.js`, `workflows.js`, `projects.js`, `inbox.js`, `outputs.js`) are loaded as ES modules via `<script type="module">` tags, one per page. There is no bundler, so each page fetches its own module.

The HTMX webjar compatibility route at `/webjars/htmx.org/dist/htmx.min.js` returns a 200 with a no-op stub. This prevents the known 404 error that previously appeared in browser console logs.

Key DOM IDs to assert per page:
- `/chat`: data-chat-root, chat-form, chat-input, chat-model-select, chat-planning-model-select, chat-history, chat-planning-panel
- `/dashboard`: dashboard-landing, dashboard-summary-grid (8 dashboard-card children)
- `/plans`: plans-page, plan-kind, plan-title, plan-goal, plan-deliverables, plan-inputs, plan-outputs
- `/workflows`: workflows-page, workflow-title, workflow-nodes
- `/inbox`: inbox-page, user-inbox-messages, agent-inbox-messages, inbox-agent-select
- `/outputs`: outputs-page, outputs-agent-select, outputs-job-select, outputs-project-select, outputs-type-select
- `/agents`: agents-page, agent-cards, agent-detail-content, agent-filter
- `/settings`: settings-page, settings-default-model, settings-planning-model, settings-summary-model, settings-compaction-model
- `/jobs`: jobs-page, job-detail-panel, job-editor-form, job-items, job-runs
- `/projects`: projects-page, project-detail-panel, project-editor-form, project-agents, project-network

Console messages: expect zero errors or warnings from the orchestration pages (unlike the chat page which previously had the htmx webjar 404).

## Engine Relevance

Browser validation verifies the SimplyPages server-side rendering produces valid, navigable HTML with no client-side errors. The Playwright MCP approach provides fast DOM assertion coverage for all orchestration pages without needing headful SSH/X forwarding.

## Open Questions
- Should the orchestration JS modules have unit tests (e.g., with Vitest or Playwright component testing)?
- Should the sidebar be auto-collapsed on mobile viewports rather than requiring user interaction?
- Should the model selects on orchestration pages be populated from the `/api/agents` endpoint rather than hardcoded?
