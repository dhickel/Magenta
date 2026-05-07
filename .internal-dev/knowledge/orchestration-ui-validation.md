# Topic

Phase 03 orchestration UI validation.

# Source References

- `.internal-dev/plans/orchestration-driver/phase-03-orchestration-ui-validation.md`
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/resources/static/js/orchestration/app.js`
- Playwright MCP run on 2026-05-07 against `http://localhost:18080` with `jdbc:sqlite:/tmp/magenta2-orchestration-phase03-playwright.sqlite`

# Key Takeaways

The orchestration UI can stay independent from `/chat` by loading `/js/orchestration/app.js` only on operational pages and leaving `/chat` as the sole consumer of `/js/chat-client.js?v=23`.

The SimplyPages shell emits `/webjars/htmx.org/dist/htmx.min.js` on all pages. A static file under `static/webjars` is not enough because Spring's webjar handler owns that path; an explicit controller route was needed to stop browser 404s without adding HTMX to orchestration behavior.

Job creation must use `OrchestrationStatus.QUEUED`; `PENDING` is not a valid backend enum value.

Playwright MCP validation covered page loads for `/settings`, `/agents`, `/agents/{id}`, `/jobs`, `/jobs/{id}`, `/tasks`, `/workflows`, and `/chat`; verified orchestration pages do not load `chat-client.js`; verified `/chat` still does; created/cloned/disabled an agent; created a job item and queued a job run.

# Engine Relevance

Use static JS modules for orchestration pages where page state spans agents, assignments, jobs, and run history. Keep shared utilities small: JSON fetch, SSE parsing, DOM helpers, tab binding, and side-panel chat.

When adding browser-facing enum values, verify them against backend enums or exercise the API through Playwright; invalid enum values surface as generic 400s if Jackson fails before controller code runs.

# Open Questions

- Should the HTMX shell reference be removed upstream from SimplyPages instead of served by Magenta?
- Should side-panel agent chat get a dedicated async streaming service instead of synchronous `ChatService.chat` bridging?
