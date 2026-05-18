# Phase 03: Agent Execution Provenance — Evidence

## Task Run Summary

| Field | Value |
|---|---|
| Plan ID | `b2c42b4f-22e3-4df4-8b59-2d2783989808` |
| Run ID | `c3b9791d-03b3-4bd2-b02a-11e4daf6d136` |
| Assignment ID | `c537d1b5-8f90-4234-8e30-dbeec3ea7ab2` |
| Agent | `30f51f72-f521-4a5b-84fb-dd024cf43291` (magenta) |
| Container | `0d003d975cf0` (magenta-agent-30f51f72-f52) |
| Image | `python:3.11` |
| Model | local-qwen (qwen3.6:35b) |
| Entry point | Plan → Submit to Agent (TASK_RUN) |

## Container Provenance Evidence

### Path Mapping (from app log)
```
temp=/home/hickelpickle/.magenta/root/runtime/task-runs/c3b9791d-... 
output=/home/hickelpickle/.magenta/root/agents/30f51f72-.../outputs/docker-provenance-validation-c3b9791d-...
containerOutput=/output/docker-provenance-validation-c3b9791d-...
```

### Materialized Output Artifacts
All 3 files written inside the container via `/output` mount were materialized on the host:

| Artifact | Container Path | Host Path |
|---|---|---|
| `home_agent_provenance.txt` | `/home/agent/provenance.txt` | outputs/.../home_agent_provenance.txt |
| `workspace_provenance.txt` | `/workspace/provenance.txt` | outputs/.../workspace_provenance.txt |
| `output_provenance.txt` | `/output/.../provenance.txt` | outputs/.../output_provenance.txt |

### Log Evidence
```
[ta-background-1] Allocated temp=... output=... containerOutput=/output/... agent=30f51f72... for run=c3b9791d...
[magenta-chat-1] Materialized 3 output artifacts for run=c3b9791d...
```

### UI Evidence
- Docker state transitioned IDLE → RUNNING during execution ✓
- Current Assignment displayed: TASK_RUN, RUNNING, Priority 9 ✓
- Queue count incremented to 1 during execution ✓
- Outputs page shows all 3 artifacts with run ID, plan ID, type, and timestamp ✓

### Temp Workspace Cleanup
The temp workspace at `runtime/task-runs/c3b9791d-...` was cleaned up after completion — confirms temp cleanup behavior.

## Execution Provenance — Docker State During Run

| Check | Pre-execution | During | Post-execution |
|---|---|---|---|
| UI Docker state | IDLE | RUNNING | IDLE |
| Podman state | running | running | running |
| Queue count | 0 | 1 | 0 |

The Docker state correctly reflects actual execution status.

## Assessment

**PASS** — Task execution provenance is confirmed:
1. Work was allocated with explicit container output paths
2. Files written inside the container materialized on the host via bind mount
3. Output artifacts are registered and visible in the UI with full attribution
4. Docker state transitions correctly reflect execution lifecycle
5. Temp workspace is cleaned after completion

Gap: The model (local-qwen) wrote file paths rather than executing `hostname`/`pwd` shell commands. This is a model behavior issue, not a Docker provenance issue — the files exist on the host only because the container wrote them to bind-mounted directories.

Task execution entry point ✓ (1 of 3 required). Workflow and job entry points remain for phases 06/07.
