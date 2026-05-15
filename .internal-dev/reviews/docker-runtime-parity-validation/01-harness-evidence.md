# Phase 01: Docker Mandate And Playwright Harness — Evidence

## Environment Facts

| Fact | Value |
|---|---|
| App start command | `mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-docker-parity-validation.sqlite'` |
| Database path | `/tmp/magenta2-docker-parity-validation.sqlite` |
| Docker host | `unix:///run/user/1000/podman/podman.sock` |
| Agent image | `python:3.11` |
| Git revision | `5476f36` |
| Java version | 25.0.3 |
| Spring Boot version | 3.4.4 |
| Browser origin | `http://localhost:18080` |
| Playwright MCP | Headless Chromium, 1280x720 viewport |

## Docker Readiness — Backend

`GET /api/runtime/docker/status` returned:

```json
{
    "enabled": true,
    "available": true,
    "dockerHost": "unix:///run/user/1000/podman/podman.sock",
    "agentImage": "python:3.11",
    "message": "Docker runtime ready: daemon reachable and agent image verified.",
    "checkedAt": "2026-05-15T02:48:42.293564386Z"
}
```

## Docker Readiness — UI

- Agent list (`/agents`) shows Docker column with status `STOPPED` for agent `magenta`.
- Agent detail (`/agents/30f51f72-f521-4a5b-84fb-dd024cf43291`) Docker panel shows:
  - State: STOPPED / not started
  - Container name: `magenta-agent-30f51f72-f52`
  - Image: `python:3.11`
  - Host: `unix:///run/user/1000/podman/podman.sock`
  - Lifecycle controls: Wake, Sleep, Restart, Refresh, Disable Agent, Delete / Archive

## Page Load Results

| Page | Status | Notes |
|---|---|---|
| `/dashboard` | OK | Sidebar, stats strip, agent table, inbox, outputs, events |
| `/agents` | OK | Agent list with Docker column, filter, Create/Reload buttons |
| `/agents/{id}` | OK | All 10 tabs, Docker panel, profile editor, submit work form, chat panel |
| `/plans` | OK | Plan list loaded |
| `/workflows` | OK | Workflow list loaded |
| `/jobs` | OK | Job list loaded |
| `/projects` | OK | Console: "Unexpected token '}'" (4 occurrences across session) |
| `/outputs` | OK | Output listing loaded |
| `/inbox` | OK | Inbox loaded |
| `/settings` | OK | Settings loaded |
| `/chat` | OK | Title "Magenta Chat", chat page rendered |

## HTMX Verification

- Clicked "Workspace" tab on agent detail page — tab panel loaded workspace content via HTMX (no full page reload).
- Network trace confirmed `GET /agents/_detail/{agentId}/workspace` returning 200 HTML fragment.

## Console Errors

4 instances of `Unexpected token '}'` across the session. No 500 errors or failed requests. The JS parsing error is present but does not block page functionality.

## Network

All dynamic requests returned HTTP 200. No failed requests or unexpected error responses.

## Gate Assessment

**PASS** — Docker is enabled and reachable. Agent image `python:3.11` is verified. All top-level operational pages load in the browser. HTMX partial swaps work. Playwright MCP can control the browser. No Docker or Playwright blocker exists.

Later phases may proceed.
