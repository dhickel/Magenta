# Agent Detail Workspace Health Pattern

## Topic

Rendering real agent-detail operational facts without placeholder event data.

## Source References

- `.internal-dev/plans/public-alpha-remediation/06-operational-ui-htmx-mobile/subplan-05-agent-detail-quality.md`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AgentWorkspaceStatusService.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

## Key Takeaways

- Do not render static operational timelines in agent detail. If no scoped recent-event/audit source exists, omit the section instead of showing plausible-but-fake events.
- Optional dashboard read models should be injected through `ObjectProvider` when the controller can fall back to simpler existing behavior.
- `AgentWorkspaceStatusService` is the richer workspace read model for agent detail and list health. Useful operator fields include health, workspace path, writability, active runs, active leases, linked projects, output artifact totals, last activity, and message.
- Keep the agent dashboard HTMX-first: the tab remains a server-rendered fragment and does not need JavaScript transport for workspace health.

## Engine Relevance

This pattern keeps public-alpha operational UI honest: missing telemetry is absent, while existing service-backed read models are rendered directly and tested with focused controller coverage.

## Open Questions

- Should a future scoped assignment/audit event service provide a real recent-events side panel for agent detail?
