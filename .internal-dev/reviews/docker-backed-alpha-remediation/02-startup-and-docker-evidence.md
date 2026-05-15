# 02: Startup and Docker Evidence

## Scope
Verify bounded Spring Boot startup with Podman Docker socket and isolated SQLite, then test Docker/Podman live execution: agent container lifecycle, writes inside container, plan execution, and artifact registration.

## Bounded Startup

### Command
```bash
DOCKER_HOST=unix:///run/user/1000/podman/podman.sock timeout 30s mvn spring-boot:run \
  -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-final-smoke.sqlite --magenta.docker.enabled=true --magenta.docker.agent-image=python:3.11 --magenta.executor.chat-threads=4'
```

### Results
```
Docker daemon ping OK at unix:///run/user/1000/podman/podman.sock
Agent image python:3.11 verified
Docker runtime ready — daemon unix:///run/user/1000/podman/podman.sock, image python:3.11
Tomcat started on port 41031 (http)
Started Magenta2Application in 3.054 seconds
```

Exit code 124 (timeout command terminated healthy process). Startup is clean.

## Live App Startup (Port 18080)
Fixed port 18080 used for Playwright MCP and Docker execution validation:
```
Tomcat started on port 18081 (http) — initial attempt (port 18080 was occupied)
Tomcat started on port 18080 (http) — final run for MCP validation
Docker daemon ping OK, python:3.11 image verified
```

## Agent Container Lifecycle

### Container Start
`POST /agents/_docker/{agentId}/start`:
- Container started: `784b7717b3f3...`
- Image: `python:3.11`
- Host: `unix:///run/user/1000/podman/podman.sock`
- Mounts confirmed:
  - `/home/hickelpickle/.magenta/root/agents/{agentId}/home` -> `/home/agent`
  - `/home/hickelpickle/.magenta/root/agents/{agentId}` -> `/workspace`
  - `/home/hickelpickle/.magenta/root/agents/{agentId}/outputs` -> `/output`

### Container Writes (podman exec)
All writes inside the container succeeded:
- `/home/agent/alpha-home.txt` — "Alpha final validation home write" — exit 0
- `/workspace/alpha-workspace.txt` — "Alpha final validation workspace write" — exit 0
- `/output/alpha-output.txt` — "Alpha final validation output write" — exit 0

All files verified via `podman exec cat`.

## Plan Execution (Docker Context)

### Plan: "Alpha Final Validation Plan"
- ID: `522e31b4-aced-4d60-b21d-a0ecd92ab44e`
- Goal: create `/output/hello.txt` and `/output/result.json`
- Submitted to agent `9d948907-7ce1-4621-ade8-662dcb1db129`

### Execution Result
- Assignment ID: `78b4a463-9f47-4da3-9139-5a5d355c0989`
- Status: COMPLETED (after ~55s)
- Output values:
  - `field_1`: "hello.txt created with content: Hello from alpha final validation!"
  - `field_2`: "result.json created with content: {\"status\": \"validated\", \"phase\": \"alpha-final\"}"
- Evidence recorded: hello.txt verified, result.json verified

### BLOCKER: Execution ran through agent=system, not Docker container
```
PlanService: Allocated temp=... output=/home/hickelpickle/.magenta/root/agents/system/outputs/... agent=system for run=...
```
- The assignment was submitted to the "magenta" Docker-backed agent
- Actual execution ran through the "system" agent (non-Docker)
- Files landed at `.magenta/root/hello.txt` and `.magenta/root/result.json` (host root path, the old DEFECT-03-03 bug)
- Files were NOT written to `/output` inside the container
- Output artifacts registered under `agents/system/outputs/` instead of the agent's outputs directory

**DEFECT-03-03 is partially reopened.** The task execution does not route through the Docker container even when submitted to a Docker-backed agent. The execution falls through to the system agent path.

### Artifact Registration
- Two output artifacts registered in `/api/outputs`:
  - `field_1`: text file, proper agent/plan/run attribution
  - `field_2`: text file, proper agent/plan/run attribution
- Artifacts are attributed to `agentId: 9d948907-7ce1-4621-ade8-662dcb1db129` (correct)

### Output Content Viewing (DEFECT-07-01)
- `GET /outputs/_content/{artifactId}`: Returns content fragment with metadata and inline text — PASS
- `GET /api/outputs/{artifactId}/download`: Returns raw content with Content-Type: text/plain — PASS
- DEFECT-07-01 is FIXED — output content is viewable and downloadable from the app.

## Verdict
MIXED:
- Startup, Docker daemon, image, container lifecycle: PASS
- Manual container writes: PASS
- Plan execution through Docker container: FAIL (runs through system agent)
- Output file path: INCORRECT (writes to host .magenta/root/ instead of /output)
- Artifact registration with attribution: PASS
- Output content viewing/downloading: PASS
