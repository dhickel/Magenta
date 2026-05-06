# Date

2026-05-06

# Change Summary

Refreshed the Playwright MCP live chat testing knowledge after validating the scalar-to-list tool argument coercion fix. Expanded the workflow with smaller bounded MCP probes, mutation/negative endpoint checks, console/network capture, and guidance for inspecting persisted state after MCP timeouts.

# Files

- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- `.internal-dev/bugs/plan-execution-list-argument-deserialization/report.md`
- `.internal-dev/bugs/plan-execution-timeout-leaves-executing/report.md`
- `.internal-dev/bugs/chat-page-missing-htmx-webjar/report.md`

# Behavioral Impact

No production code behavior changed in this documentation update. Future agents have clearer validation guidance for live chat, streaming, saved-plan execution, endpoint mutation checks, and MCP timeout follow-up.

# Risks

The knowledge file captures observed behavior from local live-model runs. Model-specific output can vary, so future agents should validate state transitions and persisted plan state rather than relying only on exact wording.

# Follow-up Items

- Fix or intentionally remove the missing htmx asset reference.
- Ensure saved-plan execution cannot remain `EXECUTING` after stream timeout or failure evidence.
- Keep the MCP workflow updated as better reusable snippets or test harnesses emerge.
