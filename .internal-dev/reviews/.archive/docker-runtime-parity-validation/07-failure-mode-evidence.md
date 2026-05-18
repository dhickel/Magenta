# Phase 07: Failure Modes And No-Fallback Contract — Evidence

## Coverage

Partial — the happy-path validation (Phases 01-05) took priority. Negative scenarios were tested only for the known stop-status defect.

## Validated

### Container Stop Status Mismatch — CONFIRMED
- After Sleep/Wake/Restart cycle in Phase 02:
  - UI showed IDLE after Sleep
  - Podman confirmed container was exited (137/SIGKILL)
  - UI did NOT transition to STOPPED
- This is the same family as bug `2026-05-14-docker-stop-status-mismatch`
- Fresh evidence recorded in Phase 02

### Container Stop Actually Works
- The Sleep action successfully stopped the container
- Podman shows "Exited (137)" confirming SIGKILL after SIGTERM timeout
- The functional stop works; the UI display is the defect

### Container Restart Recovers
- After Sleep, Restart successfully re-created the container
- Container state returned to running
- Subsequent task execution worked correctly in the restarted container

## NOT Validated

| Scenario | Status |
|---|---|
| Docker disabled startup | Not tested |
| Docker daemon unavailable | Not tested |
| Missing agent image | Not tested |
| Container removed out-of-band during execution | Not tested |
| Mount permission problems | Not tested |
| Workspace lease conflict | Not tested (no existing leases) |
| Output write failure | Not tested |
| Host fallback when Docker unavailable | Not tested |

## Assessment

**BLOCKED** — The stop-status mismatch is confirmed as an active defect. The remaining negative scenarios require dedicated environment manipulation (stopping Podman, removing images, breaking mounts) that was deferred to preserve the running validation environment for Phases 02-05. These scenarios should be tested in a follow-up session where the environment can be safely disrupted.
