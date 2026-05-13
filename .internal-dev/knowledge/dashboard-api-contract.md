# Topic

Dashboard API contract and HTMX partial endpoints for the operational orchestration dashboard.

# Source References

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java` -- dashboard shell and HTMX partial endpoints
- `src/main/java/io/mindspice/magenta2/api/web/FrontendController.java` -- legacy frontend routes
- `src/main/resources/static/js/orchestration/dashboard.js` -- JS ticker and settings save
- Phase 02 HTMX handoff notes in `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md`

# Key Takeaways

## Dashboard page shell (`GET /dashboard`)

Server-rendered HTML page with these structural sections:
- **Chat band** (`.dashboard-chat-band`): disabled text input placeholder for future system chat
- **Status strip** (`#dashboard-stats-container`): 5 KPI stat cards (Running, Pending, Waiting Approval, Failed, Active Agents) plus freshness indicator. Loaded via `hx-get="/dashboard/_stats"` with `hx-trigger="load, every 30s"`.
- **Main content** (`.dashboard-primary`):
  - Active Work table (`#active-work-section`): Loaded via `hx-get="/dashboard/_active-work"` with `hx-trigger="load, every 30s"`.
  - Open Projects cards (`#open-projects-section`): Loaded via `hx-get="/dashboard/_open-projects"` with `hx-trigger="load, every 30s"`.
  - Agents table (`#agents-section`): Loaded via `hx-get="/dashboard/_agents"` with `hx-trigger="load, every 30s"`.
- **Side rail** (`.dashboard-side`):
  - Inbox summary (`#side-inbox`): Loaded via `hx-get="/dashboard/_side-inbox"` with `hx-trigger="load, every 30s"`.
  - Recent Outputs (`#side-outputs`): Loaded via `hx-get="/dashboard/_side-outputs"` with `hx-trigger="load, every 30s"`.
  - Recent Events (static: "No recent events" placeholder).

## HTMX partial endpoints

All endpoints return HTML fragments (Content-Type: text/html):

| Endpoint | Purpose | Refresh |
|---|---|---|
| `GET /dashboard/_stats` | 5 stat cards with live numeric values | every 30s |
| `GET /dashboard/_active-work` | Active work table (job rows) | every 30s |
| `GET /dashboard/_open-projects` | Open project cards | every 30s |
| `GET /dashboard/_agents` | Agent table | every 30s |
| `GET /dashboard/_side-inbox` | Inbox count and link | every 30s |
| `GET /dashboard/_side-outputs` | Recent outputs list (top 5) | every 30s |

All use `hx-swap="innerHTML"` to replace the container content.

## REST API endpoint

`GET /api/dashboard/summary` returns a JSON payload with:
```json
{
  "openProjects": [...],
  "activeWork": [...],
  "agents": [...],
  "userInbox": [...],
  "recentOutputs": [...],
  "stats": {
    "runningJobs": 0,
    "pendingJobs": 0,
    "runningWorkflows": 0,
    "pendingAssignments": 0,
    "waitingApprovals": 0,
    "failedItems": 0,
    "agentsByStatus": {"ACTIVE": 1}
  },
  "generatedAt": "ISO-8601 timestamp"
}
```

This endpoint is used by the HTMX partial renderers server-side but is also available as a REST API for external consumers.

## JS boundaries

Only the freshness ticker (`initDashboardTicker()`) uses JS with `setInterval` to update `#stat-freshness` every 30s. No client-side rendering functions (`renderDashboardStats`, `renderActiveWork`, etc.) remain in dashboard.js after Phase 02 HTMX remediation. The `formatSince()` helper converts ISO timestamps to relative time strings.

## Inherited JS (settings)

`dashboard.js` also contains `initSettings()` for the settings page, which uses JS `fetch()` to populate model dropdowns and save settings. This is retained because model dropdowns require API calls to populate and the form spans multiple panels.

# Engine Relevance

Future work that touches the dashboard should:
- Add new sections as HTMX partials with consistent 30s refresh pattern
- Keep the chat band as a placeholder until dashboard-aware system chat is implemented
- Use `OrchestrationController` for new dashboard partial endpoints
- Verify the 6 existing partial endpoints still return valid HTML when adding new controller dependencies

# Open Questions

- Should the 30s refresh interval be configurable?
- Should the chat band be removed until system chat is implemented?
- Should the "Recent Events" section be wired to a real endpoint?
