# Summary
Agent detail Shell Exec runs outside the selected agent workspace. Commands execute relative to `/home/hickelpickle/.magenta/root/workspace` instead of `agents/<agentId>/workspace`, so the UI contract introduced by the filesystem-runtime refactor is not met.

# Scope
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `/agents` agent detail Exec tab workflow

# Reproduction
1. Start app on `http://localhost:18080` with isolated SQLite DB.
2. Open `/agents`, select an agent, open **Exec** tab.
3. Run `pwd` with working directory `workspace`.
4. Observe returned path.
5. Run command with working directory `outputs`.

# Expected
- `workspace` resolves to `.../agents/<agentId>/workspace`.
- `outputs` resolves to `.../agents/<agentId>/workspace/outputs`.
- Commands stay scoped to the selected agent workspace.

# Actual
- `workspace` resolves to `/home/hickelpickle/.magenta/root/workspace`.
- `outputs` resolves to `/home/hickelpickle/.magenta/root/outputs` and fails when missing.
- Agent context is not provided to shell execution path from agent detail UI.

# Evidence
- Playwright result (2026-05-15): `Exit: 0` and output path `/home/hickelpickle/.magenta/root/workspace` after `pwd`.
- Playwright result (2026-05-15): `Error: /home/hickelpickle/.magenta/root/outputs` for working directory `outputs`.
- Workspace DB row exists with correct root: `agents/930676a8-b552-4920-a4df-19ffd9da6cb1/workspace`.

# Impact
- Breaks core runtime isolation guarantee for operator-triggered shell execution.
- Prevents validation of artifact placement under `agents/<agentId>/workspace/outputs/...` from the primary UI workflow.
- High risk for production operations because commands are not scoped per-agent as intended.

# Status
Resolved (2026-05-15) by scoping exec calls with agent orchestration context in OrchestrationController.

# Next Action
Keep covered by Playwright `/agents` Exec-tab validation in final gate.
