# Agent Container Runtime Pattern (Phase 04)

## Summary
For alpha, Docker lifecycle should be managed per agent as a long-lived container abstraction, not one-off-only execution.

## Practical pattern
- Keep one app-owned Docker API client.
- Keep at most one managed container per agent.
- Use labels for reconciliation:
  - `magenta.managed=true`
  - `magenta.agent.id=<agentId>`
  - `magenta.runtime.generation=<instanceId>`
- Mount durable host paths:
  - `WorkspaceDirectoryService.agentHome(agentId)` -> `/home/agent`
  - `WorkspaceDirectoryService.agentWorkspaceRoot(agentId)` -> `/workspace`
  - `WorkspaceDirectoryService.agentOutputRoot(agentId)` -> `/output`
- Treat `AgentProfileStatus.ACTIVE` as availability permission, not guaranteed running container.
- Use explicit wake/sleep/restart operations from HTMX actions.
- On disable, stop/sleep container and reject new assignments.

## Safety constraints
- Do not silently hard-delete agent data.
- Require explicit confirmation text for hard-delete.
- No clone semantics for alpha; avoid cross-agent filesystem/data copying.
