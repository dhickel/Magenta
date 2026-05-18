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

# Risks

Browser-origin HTMX validation is still required by the domain validation agent to prove the live swap behavior in a running app.

# Follow-up Items

- Validation agent should run focused browser-origin proof for the lifecycle confirmation/result swap.
