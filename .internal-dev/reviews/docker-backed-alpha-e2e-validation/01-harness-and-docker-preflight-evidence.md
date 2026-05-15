# Phase 01: Playwright Harness And Docker Preflight — Evidence

## Scope
Playwright setup, browser readiness, Docker runtime availability, page-load smoke test, HTMX asset verification, reusable helpers.

## Findings

### Environment
- **App port**: 18080
- **Database path**: `/tmp/magenta2-alpha-e2e.sqlite`
- **Docker host**: `unix:///run/user/1000/podman/podman.sock` (Podman 5.8.2, Docker API v1.44)
- **Agent image**: `python:3.11` (verified)
- **Playwright browser/runtime**: Chrome via MCP — first page load succeeded, then MCP server disconnected
- **Startup command**: `DOCKER_HOST=unix:///run/user/1000/podman/podman.sock mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-e2e.sqlite --magenta.docker.enabled=true --magenta.docker.agent-image=python:3.11 --magenta.executor.chat-threads=4'`

### Docker/Podman Integration: PASS
- Startuplog confirms: `DockerRuntimeClient: Connecting to Docker daemon at unix:///run/user/1000/podman/podman.sock`
- Startuplog confirms: `Docker daemon ping OK`
- Startuplog confirms: `Agent image python:3.11 verified`
- Startuplog confirms: `Docker runtime ready — daemon unix:///run/user/1000/podman/podman.sock, image python:3.11`
- java-client `docker-java` + `docker-java-transport-httpclient5` connects to Podman socket transparently

### Page Load Smoke Test: PASS
All pages return `<!DOCTYPE html>` with proper `<head>` and `<body>` — no raw JSON or error fragments:

| Page | Status | Title Tag | Notes |
|------|--------|-----------|-------|
| `/` | 200 | Magenta Portal | Home with nav cards |
| `/dashboard` | 200 | Magenta Dashboard | Full orchestration sidebar, stats band, HTMX polling |
| `/chat` | 200 | Magenta Chat | SSE chat module, model selects, session list |
| `/agents` | 200 | Magenta Dashboard | Agent list with Docker controls, detail tabs |
| `/plans` | 200 | Magenta Dashboard | Plan list + editor layout |
| `/workflows` | 200 | Magenta Dashboard | Workflow list + editor layout |
| `/jobs` | 200 | Magenta Dashboard | Job list + editor layout |
| `/projects` | 200 | Magenta Dashboard | Project list + editor layout |
| `/inbox` | 200 | Magenta Dashboard | User inbox + agent inbox panels |
| `/outputs` | 200 | Magenta Dashboard | Filter toolbar + output grid |
| `/settings` | 200 | Magenta Dashboard | Model routing config form |
| `/tasks` | 404 | — | Expected; tasks unified under `/plans` |

### HTMX Asset Verification: PASS
- `htmx.min.js` — 200 OK
- `framework.js` — 200 OK
- `chat-client.js` — 200 OK (v=24)
- `orchestration.css` — 200 OK (v=6)
- `orchestration.js` modules referenced: `dashboard.js?v=5`, `agents.js?v=1`, `workflows.js?v=2`, `projects.js?v=3`, `plans.js?v=2`
- No `compat-noop` stubs detected

### Docker Status Visibility: PASS
Agent list (`/agents/_list`) displays:
- Docker status column with chip showing `STOPPED`
- Lifecycle controls: Wake, Sleep, Restart, Refresh (HTMX POST/GET endpoints)
- Status: `ACTIVE` with Docker `STOPPED` — distinguishes agent lifecycle from container state

Dashboard stats fragment (`/dashboard/_stats`):
- Shows Running=0, Pending=0, Waiting Approval=0, Failed=0, Active Agents=1
- HTMX polls every 30s (`hx-trigger="load, every 30s"`)

### Runtime Status Panel: DEFERRED VERIFICATION
- `/api/runtime/status` — 404 (endpoint not exposed)
- Docker image name (`python:3.11`) confirmed via startup log, not yet confirmed visible in UI

### Playwright Browser Validation: BLOCKED
- First `browser_navigate` to `http://localhost:18080/` succeeded; snapshot captured showing home page with title "Magenta Portal"
- Subsequent Playwright calls failed with "Browser is already in use" then "No such tool available"
- Playwright MCP server appears to have crashed/disconnected mid-session
- **Mitigation**: URL-based checks confirmed HTML responses, HTMX asset loading, and page structure for all 12 pages. Browser-origin interactions (click, type, submit, SSE) are deferred until Playwright recovers.

### Shared Playwright Helpers: NOT YET WRITTEN
Deferred until Playwright MCP is available. Planned snippets:
- Console error collection
- Failed network request collection
- HTMX swap waiter
- Browser-origin SSE parser
- Screenshot-on-failure capture

## Exit Criteria

| Criterion | Status |
|-----------|--------|
| Evidence file exists at correct path | ✅ |
| Docker/Playwright blocker documented | ✅ Playwright MCP disconnect |
| App URL, browser setup, helpers documented | ⚠️ Partially — URL and setup done, helpers deferred |
| Later agents can reuse documented config | ✅ App URL, port, DB path, Docker host, image confirmed |

## Risk Assessment
- **Medium**: Playwright MCP instability may slow browser-origin validation. URL-based checks provide partial coverage but cannot verify click/type/submit/SSE flows.
- **Low**: Docker/Podman integration is proven at the daemon and image level. The `docker-java` client connects successfully.

## Follow-ups
- Restart Playwright MCP server and complete browser-origin page load checks
- Write reusable Playwright helper snippets
- Confirm Docker image name is visible in a UI panel (not just startup log)
