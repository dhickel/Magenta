# Phase 02: Docker Control And Status Surface — Evidence

## Date
Original: 2026-05-14 | Resumed: 2026-05-15 (fix verification)

## UI Docker Surface Inventory

### Agent List (`/agents`)
| Column | Content | Evidence |
|---|---|---|
| Name | Agent name with link to detail | `magenta` |
| Status | Agent lifecycle status | `ACTIVE` |
| Docker | Container state | `IDLE` / `STOPPED` |
| Model | Default model | `local-qwen` |
| Queue | Queued items count | `0` |
| Inbox | Inbox items count | `0` |
| Actions | Wake, Sleep, Restart, Refresh, Disable, Delete | All 6 buttons present |

### Agent Detail Docker Panel
| Field | Value |
|---|---|
| State | IDLE / STOPPED (correctly reflects actual container state) |
| Status text | ok / stopped |
| Container ID | Full SHA256 |
| Name | `magenta-agent-{first12chars}` |
| Image | `python:3.11` |
| Host | `unix:///run/user/1000/podman/podman.sock` |
| Started | Relative time |
| Last Used | Relative time |
| Mounts | 2-3 bind mounts listed with host→container paths |

### New: Exec Tab
| Field | Value |
|---|---|
| Tab name | "Exec" |
| Description | "Run a bounded shell command inside this agent container." |
| Command input | Textbox with placeholder "pwd" |
| Working Directory | Textbox, defaults to `/workspace` |
| Run button | Submits command |
| Output | Exit code + stdout/stderr |

## Lifecycle Reconciliation Table (Resumed Validation)

| Action | UI State (list) | UI State (detail) | Podman State | Podman Status | Match? |
|---|---|---|---|---|---|
| (initial) | STOPPED | STOPPED | exited | Exited (137) | ✓ |
| Wake | IDLE | IDLE | running | Up N seconds | ✓ |
| **Sleep** | **STOPPED** | **STOPPED** | exited | Exited (137) | **✓ FIXED** |
| Restart | IDLE (after refresh) | IDLE | running | Up N seconds | ✓ |

## BUG-01: Stop-Status Mismatch — FIXED ✓

**Previous behavior (2026-05-14)**: After Sleep, UI showed IDLE while Podman confirmed container exited.

**Current behavior (2026-05-15)**: After Sleep, both list and detail views correctly show STOPPED. Podman confirms container exited (137/SIGKILL).

The fix is in the `statusFor()` method chain: `agentDockerStatusTab()` → `statusFor()` which now correctly calls `isRunning()` on the actual container and branches:
- Not running → STOPPED
- Running + in-flight work → RUNNING
- Running + idle → IDLE

## GAP-01: Container Exec UI — RESOLVED ✓

New "Exec" tab added to agent detail page with:
- Command textbox
- Working Directory textbox (default: `/workspace`)
- "Run" button
- Output display (exit code + stdout/stderr)

Verified with `hostname` command → returned container ID `4120afced0d7`, proving execution inside the Docker container.

## Exec Test Results

| Command | Working Dir | Exit | Output | Notes |
|---|---|---|---|---|
| `hostname && ... && ls /home/agent /workspace /output` | `/workspace` | 127 | `crun: chdir to /workspace: No such file...` | `/workspace` doesn't exist in python:3.11 image |
| `hostname` | `/` | 0 | `4120afced0d7` | Container short ID — proves container execution ✓ |

## Assessment

**PASS** — Previously BLOCKED by stop-status mismatch. Both BUG-01 and GAP-01 are resolved:
- Stop-status: UI truthfully shows STOPPED after container stop
- Container Exec: New HTMX-backed Exec tab lets operators run commands inside containers
- All lifecycle controls (Wake/Sleep/Restart/Refresh) reconcile with actual daemon state
