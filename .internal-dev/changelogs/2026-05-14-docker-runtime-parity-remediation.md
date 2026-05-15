# Date
2026-05-14

# Change Summary
Addressed the actionable Docker runtime parity review findings by fixing stale stopped-container reporting and wiring existing backend capabilities into HTMX operator surfaces.

# Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AgentContainerRuntimeService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/ModelController.java`
- `src/main/resources/static/js/orchestration/projects.js`
- `src/main/resources/static/js/orchestration/dashboard.js`

# Behavioral Impact
- Sleep/stop now re-inspects the container before returning status so stopped containers surface as `STOPPED`, not stale `IDLE`.
- Agents now expose queue pause/resume/cancel controls and a bounded container-exec tab.
- Plans, workflows, and jobs now expose run-history surfaces; workflow runs can resume from the UI, jobs can start/cancel runs, and jobs expose recurrence editing.
- Projects now expose a lightweight network summary; `/api/models` exposes configured model metadata.
- The projects JS syntax error was fixed and settings behavior is kept HTMX-first instead of dual-save transport.

# Risks
- Browser validation could not be completed in this run because the Playwright MCP browser profile was already locked by another session.
- Lease management remains a separate gap because there is still no public lease-mutation controller surface to bind into the UI cleanly.

# Follow-up Items
- Re-run the Docker parity review’s browser phase once Playwright MCP is available.
- Finish the remaining lease-management/product-parity gaps if they are still desired as part of this campaign.
