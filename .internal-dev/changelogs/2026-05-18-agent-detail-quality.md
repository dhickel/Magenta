# 2026-05-18 - Agent Detail Quality

## Date

2026-05-18

## Change Summary

Removed the static placeholder agent event log from agent detail and surfaced richer workspace health fields from `AgentWorkspaceStatusService` when that read model is available. The agent detail layout now uses a full-width variant after removing the side panel, avoiding an empty side column.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/css/orchestration.css`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/knowledge/agent-detail-workspace-health-pattern.md`

## Behavioral Impact

Agent detail no longer displays fake timeline data such as dashboard-loaded, assignment-waiting, or workspace-ready events. The dashboard workspace section now shows real status-service fields for health, path, writability, active run and lease counts, linked projects, output artifact count and bytes, last activity, and the status message. If the optional status service is unavailable, the existing readiness fallback remains.

## Validation

- `mvn -Dtest=OrchestrationControllerTest test` passed with 88 tests.
- `rg -n "Agent dashboard loaded|1 assignment waiting|agent-event-log" src/main` returned no production matches.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` with isolated SQLite DB `/tmp/domain06-subplan05-parent.sqlite`; log: `/tmp/domain06-subplan05-parent-startup.log`.
- Validation agent passed commit `670200c`: focused controller tests passed with 88 tests, placeholder scan returned no matches, `git diff --check` and `git show --check 670200c` passed, and bounded startup reached `Started Magenta2Application`.
- Browser-origin Playwright on `/agents/1f9ce016-8cf9-44ee-8c53-4a9c7639622e` confirmed `/css/orchestration.css?v=10` and the dashboard partial loaded `200`; `.agent-event-log` count was `0`; `Event Log`, `Agent dashboard loaded`, and `1 assignment waiting` were absent; `entity-detail-layout-full` produced one full-width column; no side panel remained; workspace health showed real status-service fields; console/network were clean.

## Risks

No remaining subplan-specific risk after validation. No new event persistence or audit subsystem was added, so the event-log area is absent until a real scoped recent-event source exists.

## Follow-up Items

Run the Domain 06 validation gate.
