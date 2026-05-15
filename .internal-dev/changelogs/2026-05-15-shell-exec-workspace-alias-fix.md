# Date
2026-05-15

# Change Summary
Fixed agent shell execution working-directory alias handling so `workspace` maps to the selected agent workspace root in agent-context execution.

# Files
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolServiceTest.java`

# Behavioral Impact
- Agent Exec UI default `workingDirectory=workspace` now resolves correctly.
- `workspace/<subpath>` now resolves relative to the agent workspace root.
- Existing `.` / `outputs` / `scratch` behavior remains intact.

# Risks
- Low. Change is confined to agent-context directory resolution.

# Follow-up Items
- Re-run Playwright operator-flow checks after broader refactor merge/commit boundaries are finalized.
