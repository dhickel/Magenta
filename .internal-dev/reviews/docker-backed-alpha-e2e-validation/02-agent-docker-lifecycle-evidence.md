# 02 — Agent Docker Lifecycle Evidence

**Date**: 2026-05-13  
**Branch**: `operational-ui-refactor`  
**App**: http://localhost:18080 (running)  
**Container runtime**: Podman 5.8.2 at `unix:///run/user/1000/podman/podman.sock` (Docker API v1.44)  
**Image**: `python:3.11` (docker.io/library/python:3.11)  
**DB**: `/tmp/magenta2-alpha-e2e.sqlite`  
**Test agent UUID for phases 03-07**: `23579fcf-ca99-4862-a2fd-b8eb6073928c` ("magenta")

---

## 1. Agent Creation

**Status**: PASS (with note)

### Test
```bash
curl -s -X POST http://localhost:18080/agents/_create
```

### Evidence
- Returns the full agent list HTML fragment (`#agents-list-table`) with the new agent row prepended.
- Controller at `OrchestrationController.java:3906` auto-generates name as `"Agent " + System.currentTimeMillis() % 100000` — **no form body parameters are accepted**. The endpoint creates an agent with empty model, empty system prompt, and default settings.

### Response (truncated)
```html
<table class="table dashboard-table" id="agents-list-table">
  ...
  <tr>
    <td><a href="/agents/152243d8-...">Agent 71374</a></td>
    <td><span class="orch-status-chip active">ACTIVE</span></td>
    <td><span class="orch-chip">STOPPED</span></td>
    <td><span></span></td>
    ...
  </tr>
</table>
```

### Note
The create endpoint is a one-click "create with defaults" operation. There is no form to set name/model/tools at creation time. The user must create first, then use the editor form to configure the agent. This is functional but limits the initial creation experience. A pre-existing "magenta" agent was used for most Docker lifecycle testing since it already had model `local-qwen` configured.

---

## 2. Agent List (`GET /agents/_list`)

**Status**: PASS

### Test
```bash
curl -s http://localhost:18080/agents/_list
```

### Evidence
Returns an HTML table fragment with columns:
| Name | Status | Docker | Model | Queue | Inbox | Actions |

Each row includes HTMX action buttons: Wake (`hx-post .../start`), Sleep (`hx-post .../stop`), Restart (`hx-post .../restart`), Refresh (`hx-get .../status-row`), Disable/Enable (`hx-post .../disable` or `.../enable`), and Delete (`hx-get .../delete-confirm`).

Docker status values observed: `STOPPED`, `IDLE`, `DISABLED`.

---

## 3. Agent Detail (`GET /agents/_detail/{agentId}`)

**Status**: PASS

### Test
```bash
curl -s http://localhost:18080/agents/_detail/23579fcf-ca99-4862-a2fd-b8eb6073928c
```

### Evidence
Returns a full detail layout via `agentDetailLayout()` with:
- **Tab navigation**: Dashboard, Queue, Inbox, Jobs, Schedules, Reactions, Workspace, Outputs, History (all 9 tabs present)
- **Tab content**: Each tab lazy-loads via `hx-get` with `hx-trigger="load"`
- **Side panel**: Profile editor (`hx-get /agents/_editor/{id}`) and Submit Work form (`hx-get /agents/_submit-form/{id}`)
- **Docker status widget**: Loads via `hx-get .../docker-status` with Docker lifecycle buttons

### Tab responses (all return 200 HTML):

| Tab | Content |
|-----|---------|
| Dashboard | Name, Status, Model, ID, Direct Line, Created time; counters for Queue/Inbox/Jobs; Docker status widget; Docker action buttons; Chat link |
| Queue | "No assignments." |
| Inbox | "No inbox messages." |
| Jobs | "No jobs." |
| Schedules | "Schedules are disabled." (feature flag `magenta.features.schedules-enabled=false`) |
| Reactions | "Event reactions are disabled." (feature flag `magenta.features.reactions-enabled=false`) |
| Workspace | Workspace metadata (ID, owner, root path, output dir hint), Active Leases (empty), Workspace Links (empty) |
| Outputs | "No recent outputs." |
| History | "Run history appears as assignments and job events are persisted." with link to queue |

### Agent Editor (side panel)
- `GET /agents/_editor/{agentId}`: Identity form (name, status, default model, direct line), System Prompt textarea, Approved Tools input, Shell Allowlist input
- `PUT /agents/_editor/{agentId}/profile`: Update name, status, model, direct line — **PASS**
- `PUT /agents/_editor/{agentId}/prompt`: Update system prompt — **PASS**
- `PUT /agents/_editor/{agentId}/tools`: Update approved tools list — **PASS** (shows "*" for all tools)
- `PUT /agents/_editor/{agentId}/shell`: Update shell allowlist — **PASS** (shows "*" for all commands)

---

## 4. Docker Lifecycle Controls

### 4a. Start (`POST /agents/_docker/{agentId}/start`)

**Status**: PASS

```bash
curl -s -X POST "http://localhost:18080/agents/_docker/23579fcf-ca99-4862-a2fd-b8eb6073928c/start?view=list"
```

**Evidence**:
- Before: Docker column shows `<span class="orch-chip">STOPPED</span>`
- After: Docker column shows `<span class="orch-chip">IDLE</span>`
- Podman confirms container running:
  ```
  CONTAINER ID  IMAGE         COMMAND                      STATUS        NAMES
  a8bed6f492f3  python:3.11   sh -lc while true; do...     Up ...        magenta-agent-23579fcf-ca9
  ```

- Container details from `docker-status` fragment:
  - Container ID: `a8bed6f492f3...`
  - Name: `magenta-agent-23579fcf-ca9` (deterministic from agent UUID, truncated to 12 chars)
  - Image: `python:3.11`
  - Mounts: `/home/agent`, `/workspace`, `/output` (bound to agent home, workspace root, output root)
  - Workspace directories created at `/home/hickelpickle/.magenta/root/agents/23579fcf-ca99-4862-a2fd-b8eb6073928c/{home,outputs}`

### 4b. Stop (`POST /agents/_docker/{agentId}/stop`)

**Status**: PASS

```bash
curl -s -X POST "http://localhost:18080/agents/_docker/23579fcf-ca99-4862-a2fd-b8eb6073928c/stop?view=list"
```

**Evidence**:
- After: Docker column shows `<span class="orch-chip">STOPPED</span>`
- Podman confirms container exited:
  ```
  CONTAINER ID  ...  STATUS                                  NAMES
  a8bed6f492f3  ...  Exited (137) Less than a second ago     magenta-agent-23579fcf-ca9
  ```
- Docker status fragment shows `STOPPED` + metadata (container ID, name, mounts preserved)

### 4c. Restart (`POST /agents/_docker/{agentId}/restart`)

**Status**: PASS

```bash
curl -s -X POST "http://localhost:18080/agents/_docker/23579fcf-ca99-4862-a2fd-b8eb6073928c/restart?view=list"
```

**Evidence**:
- Container re-enters `IDLE` state
- Same container ID preserved (stop + start, not destroy + recreate)
- Podman confirms container running

### 4d. Status-Row Refresh (`GET /agents/_docker/{agentId}/status-row`)

**Status**: PASS

```bash
curl -s "http://localhost:18080/agents/_docker/23579fcf-ca99-4862-a2fd-b8eb6073928c/status-row?view=list"
```

**Evidence**:
- Returns the full agent list table with updated Docker status for all agents
- Status in list matches `podman ps` state

---

## 5. Agent Lifecycle

### 5a. Disable (`POST /agents/_lifecycle/{agentId}/disable`)

**Status**: PASS

```bash
curl -s -X POST "http://localhost:18080/agents/_lifecycle/23579fcf-ca99-4862-a2fd-b8eb6073928c/disable?view=list"
```

**Evidence**:
- Agent status changes from `ACTIVE` to `DISABLED` in the list
- Docker status changes to `DISABLED` (special status set by `AgentContainerRuntimeService.statusFor()`)
- DB confirms: `23579fcf-ca99-4862-a2fd-b8eb6073928c|magenta|DISABLED`
- Detail view Docker fragment shows `<span class="orch-status-chip disabled">DISABLED</span>` with message "agent disabled"

### 5b. Enable (`POST /agents/_lifecycle/{agentId}/enable`)

**Status**: PASS

```bash
curl -s -X POST "http://localhost:18080/agents/_lifecycle/23579fcf-ca99-4862-a2fd-b8eb6073928c/enable?view=list"
```

**Evidence**:
- Agent status returns to `ACTIVE`
- Docker status returns to `IDLE` (container still running from before)
- DB confirms: `23579fcf-ca99-4862-a2fd-b8eb6073928c|magenta|ACTIVE`
- Enable on a previously-disabled agent with no running container starts the container (tested on agent `152243d8`)

### 5c. Delete Confirm (`GET /agents/_lifecycle/{agentId}/delete-confirm`)

**Status**: PASS (two-step flow confirmed)

```bash
curl -s http://localhost:18080/agents/_lifecycle/23579fcf-ca99-4862-a2fd-b8eb6073928c/delete-confirm
```

**Evidence**: Returns a panel with three explicit lifecycle actions:

1. **Disable Only** — `hx-post .../disable`
2. **Archive + Disable** — `hx-post .../archive-and-disable`
3. **Hard Delete** — `hx-post .../hard-delete` with confirmation text input (`DELETE {agentId}`)

Each action targets `#agent-docker-status-{agentId}` for HTMX swap. This is a proper two-step flow: the user clicks "Delete" in the list, gets this confirmation panel, then chooses the appropriate action.

### 5d. Archive + Disable (`POST /agents/_lifecycle/{agentId}/archive-and-disable`)

**Status**: PASS

```bash
curl -s -X POST "http://localhost:18080/agents/_lifecycle/152243d8-71f4-44d1-a44d-e6e5bf41c62e/archive-and-disable"
```

**Evidence**:
- Returns Docker status fragment with `DISABLED` status and "agent disabled" message
- DB shows status `DISABLED` (archived semantics handled by service layer)

### 5e. Hard Delete (`POST /agents/_lifecycle/{agentId}/hard-delete`)

**Status**: PASS

```bash
curl -s -X POST "http://localhost:18080/agents/_lifecycle/5b30c98f-6f09-4943-9595-8698c0d24a63/hard-delete" \
  -d "confirmationText=DELETE 5b30c98f-6f09-4943-9595-8698c0d24a63"
```

**Evidence**:
- Returns auto-refresh fragment: `"Agent deleted. Refreshing agent list..."` with `hx-get` to reload list
- DB confirms agent row removed
- Two test agents cleaned up during validation (`5b30c98f` and `152243d8`)

---

## 6. No Clone Path

**Status**: PASS

**Evidence**:
- `grep -rn -i "clone"` across `OrchestrationController.java` returned zero matches
- Agent list rows contain only: Wake, Sleep, Restart, Refresh, Disable/Enable, Delete — no Clone button
- No `/agents/_clone/` or similar endpoint found in controller

---

## Summary

| # | Check | Status |
|---|-------|--------|
| 1 | Agent Creation (`POST /agents/_create`) | PASS (auto-generated name; no form params) |
| 2 | Agent List (`GET /agents/_list`) | PASS (all columns present, HTMX buttons wired) |
| 3 | Agent Detail (`GET /agents/_detail/{id}`) | PASS (9 tabs all respond, editor forms work) |
| 4a | Docker Wake/Start | PASS (container runs, status = IDLE) |
| 4b | Docker Sleep/Stop | PASS (container stops, status = STOPPED) |
| 4c | Docker Restart | PASS (container restart confirmed) |
| 4d | Docker Status-Row Refresh | PASS (reflects podman state) |
| 5a | Lifecycle Disable | PASS (status = DISABLED in UI + DB) |
| 5b | Lifecycle Enable | PASS (status = ACTIVE, container auto-starts) |
| 5c | Delete Confirm (two-step) | PASS (three explicit actions presented) |
| 5d | Archive + Disable | PASS |
| 5e | Hard Delete | PASS (confirmation text required) |
| 6 | No Clone Path | PASS |

## Defects Found

None. All endpoints behave as expected.

## Notes

1. **Create endpoint limitation**: `POST /agents/_create` accepts no body parameters. Agents are created with auto-generated names and empty configuration. Users must edit after creation. This is usable but limits the creation UX.
2. **Docker status lifecycle mapping**: The `AgentContainerRuntimeService` maps container state to UI status as: running-with-work = `RUNNING`, running-idle = `IDLE`, stopped = `STOPPED`, disabled = `DISABLED`. This is clear and matches the UI chips.
3. **Container naming convention**: `magenta-agent-{first12chars-of-uuid}` — deterministic and easy to trace.
4. **Container lifecycle**: Containers are created on first start, stopped (not removed) on sleep, and subject to idle TTL cleanup. Hard delete removes the container.
5. **Workspace mounts**: Three bind mounts per container: `/home/agent`, `/workspace`, and `/output`, all under `.magenta/root/agents/{id}/`.
6. **Schedules and Reactions**: Disabled at feature-flag level (`magenta.features.schedules-enabled=false`, `magenta.features.reactions-enabled=false`). Tabs show disabled state gracefully.

## Agent UUID for Phases 03-07

`23579fcf-ca99-4862-a2fd-b8eb6073928c` (name: "magenta", model: local-qwen)
