# Phase 01 - Filesystem Layout And Config Contract

## Context

Docker currently supplies the perceived runtime root through container mounts such as `/home/agent`, `/projects`, `/workspace`, and `/output`. The replacement runtime needs one host-side directory contract that later phases can depend on before they change shell execution, monitoring, or UI.

## Goal

Make the filesystem layout canonical, delete Docker-specific configuration defaults from the active runtime contract, and provide typed workspace helpers for the new agent workspace structure.

## In Scope

- Promote `agents/<agentId>/workspace/` to the agent execution root.
- Place agent outputs at `agents/<agentId>/workspace/outputs/<run-slug>-<runId>/`.
- Add typed helpers for agent workspace, workspace outputs, project-link root, and scratch root.
- Decide and document migration behavior for existing `agents/<agentId>/home` and `agents/<agentId>/outputs` directories.
- Remove `magenta.docker.*` from active config defaults and add any needed filesystem runtime properties only if they are truly required.
- Update workspace package guidance and tests around the new layout.

## Out Of Scope

- Changing execution behavior.
- Changing public UI/API wording.
- Deleting Docker classes before downstream phases stop depending on them.

## Implementation Steps

1. Inspect `WorkspaceDirectoryService`, `WorkspaceService`, output materialization, and schema expectations before editing.
2. Add or rename workspace helpers around this contract:

```java
Path agentWorkspace(String agentId);      // agents/<id>/workspace
Path agentWorkspaceOutputs(String agentId, String runId, String slug);
Path agentProjectLinks(String agentId);   // workspace/projects
Path agentScratch(String agentId);        // workspace/scratch
```

3. Keep all helper methods confined under `dataRoot` with normalized path checks and `Files.createDirectories`.
4. Choose one migration path and document it in the handoff:
   - preferred: one-time in-place migration from legacy `home/` and sibling `outputs/` into `workspace/` on first access;
   - acceptable only with explicit evidence: clean-break mode if existing data can be safely discarded in this deployment.
5. Update `WorkspaceService.agentWorkspace(...)` so persisted metadata points at the canonical workspace path, not the broader `agents/<id>` directory.
6. Replace path-hint methods and tests that still describe `agents/<id>/outputs`.
7. Remove `magenta.docker.*` defaults from `application.yml` and test resources only after confirming later phases will not read them.
8. Update `ai.orchestration.workspaces/AGENTS.md` to describe workspace roots instead of agent homes.

## Validation

- Path confinement tests for every new helper.
- Migration tests for one legacy agent with `home/` and sibling `outputs/`.
- Workspace-service tests prove the persisted root is `agents/<id>/workspace`.
- `rg -n "agents/.*/outputs|/home/agent|magenta\\.docker"` leaves only intentional downstream references noted in the handoff.

## Exit Criteria

- Later phases can resolve an agent execution root, output root, project-link root, and scratch root without inventing paths.
- The target tree in `README.md` is executable code, not only documentation.
- The phase handoff states exactly what old on-disk data does during migration.
