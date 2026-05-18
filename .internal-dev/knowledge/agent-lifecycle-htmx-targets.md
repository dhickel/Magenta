# Topic

Agent lifecycle HTMX target pattern

# Source References

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/bugs/public-alpha-quality-review/bug-18-medium-agent-lifecycle-stale-target/report.md`
- `.internal-dev/plans/public-alpha-remediation/06-operational-ui-htmx-mobile/subplan-02-agent-lifecycle-htmx-targets.md`

# Key Takeaways

- Agent detail lifecycle mutations need a rendered target that exists on `/agents/{agentId}` before the user clicks.
- Use `#agent-lifecycle-panel-{agentId}` for dashboard lifecycle confirmations and result fragments.
- Use `hx-swap="outerHTML"` when the response root is the lifecycle panel itself, so repeated confirmation/result swaps do not nest duplicate ids.
- Keep list-row lifecycle actions targeting the agent list or detail container as appropriate, but confirmation buttons should target the lifecycle panel they render.
- Avoid stale Docker-specific ids/classes for filesystem-runtime lifecycle UI.

# Engine Relevance

This keeps agent lifecycle controls HTMX-first and makes operator feedback visible without a JavaScript transport layer.

# Open Questions

- Browser validation still needs to prove the live `/agents/{agentId}` Delete/Archive confirmation and result swaps after implementation.
