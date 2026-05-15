# Phase 04 Docker Deferred Follow-ups

## Deferred
1. Add dedicated live Docker integration tests (`-Dmagenta.docker.live=true`) that validate mounted writes and timeout-recovery against real daemon/image.
2. Expand hard-delete coverage to include explicit cleanup policy for inbox/job/history records tied to the agent.
3. Add richer per-agent Docker state rendering in list rows (idle countdown/last-used tooltip) after CSS/UX pass.
