# Summary
Agent Shell Exec now runs in the selected agent workspace when using `.`; however the UI default working directory value `workspace` is invalid and returns an error.

# Scope
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`

# Reproduction
1. Open `/agents`, select an agent, open Exec tab.
2. Leave default Working Directory as `workspace`.
3. Run `pwd`.

# Expected
`workspace` should resolve to the agent workspace root (per plan contract and Exec tab default).

# Actual
Exec returns `Error: Working directory is not a directory: workspace`.

# Evidence
- Playwright revalidation (2026-05-15): `workspace` fails, while `.` succeeds and resolves to `/home/hickelpickle/.magenta/root/agents/<agentId>/workspace`.

# Impact
- Operator-facing Shell Exec default flow fails.
- Refactor contract says `workspace` alias is supported, but runtime behavior does not match.

# Status
Resolved (2026-05-15).

# Next Action
Monitor for regressions in Playwright validation lanes; no immediate remediation pending.
