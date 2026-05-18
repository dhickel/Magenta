# Date

2026-05-18

# Change Summary

Fixed public alpha bug-18 by replacing stale agent Delete/Archive HTMX targets that referenced missing `#agent-docker-status-{agentId}` elements. Agent dashboards now render a visible lifecycle panel and lifecycle confirmation/results swap into that panel with HTMX.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/css/orchestration.css`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/knowledge/agent-lifecycle-htmx-targets.md`

# Behavioral Impact

- `/agents/{agentId}` dashboard renders `#agent-lifecycle-panel-{agentId}` as the visible lifecycle swap target.
- Delete/Archive confirmation controls target the visible lifecycle panel with `outerHTML` swaps.
- Disable/archive lifecycle result fragments can render directly into the lifecycle panel instead of replacing a missing stale Docker target.
- No JavaScript transport was added; lifecycle controls remain HTMX-first.

# Validation

- `mvn -Dtest=OrchestrationControllerTest test` passed with 81 tests.
- `git diff --check` passed.
- Static scan found no remaining `agent-docker-status` target in touched controller/CSS/test surfaces.
- Bounded Spring startup reached `Started Magenta2Application` with isolated SQLite DB `/tmp/domain06-subplan02-parent.sqlite`; log: `/tmp/domain06-subplan02-parent-startup.log`.
- Validation agent passed commit `23b1795` with `mvn -Dtest=OrchestrationControllerTest test` (81 tests), `git diff --check`, bounded startup, and live Playwright MCP on port `18080` using isolated SQLite. Evidence: `/tmp/domain06-subplan02-23b1795-controller-test.log`, `/tmp/domain06-subplan02-23b1795-git-diff-check.log`, `/tmp/domain06-subplan02-23b1795-startup.log`, `/tmp/domain06-subplan02-23b1795-live.log`.
- Browser-origin proof on `/agents/9d93d2a6-dde5-4838-9b24-4fdbcb9145e3` confirmed the initial visible lifecycle panel, `Delete / Archive` targeting `#agent-lifecycle-panel-{agentId}` with `outerHTML`, confirmation swapping into the same panel, `Disable Only` returning `200` and rendering `Agent disabled.`, stale target count `0` before/after swaps, and Queue tab remaining usable afterward.
- Static transport check found no changed `.js` files and no JavaScript transport for `/agents/_lifecycle`; network assets and lifecycle requests returned `200`, console had `0` messages, and Spring logs had no unexpected `ERROR`, `Exception`, `500`, stale asset `404`, HTMX target error, or SSE regression indicator before intentional shutdown.

# Risks

No remaining subplan-specific risk after validation. The broader Domain 06 mobile/HTMX gate still needs to run after later subplans.

# Follow-up Items

- Continue serially to Domain 06 subplan 03.
