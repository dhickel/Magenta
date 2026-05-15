# Phase 04 - UI And Public Contract Removal

## Context

The current operator surface exposes Docker as if it were part of the product: agent list columns, dashboard fragments, a Docker tab, `Wake/Sleep/Restart` controls, `Container Exec`, `/agents/_docker/*` routes, `/api/runtime/docker/status`, and Docker-specific CSS/test assertions. None of those concepts survive the new runtime contract.

## Goal

Remove Docker from the active UI/API and replace it with truthful workspace-first operator affordances built on the Phase 03 read model.

## In Scope

- Remove Docker wording, tabs, columns, routes, actions, fragments, CSS classes, and tests from the active UI.
- Replace agent status cards with workspace health/activity cards.
- Rename container execution UI to shell execution and default the working directory to the workspace contract.
- Replace `/api/runtime/docker/status` with the new workspace/runtime summary shape only if an equivalent public endpoint is still needed.
- Keep HTMX for standard CRUD/refresh interactions.

## Out Of Scope

- Cosmetic redesign unrelated to the contract change.
- Adding new operator controls that do not exist in the filesystem model.

## Implementation Steps

1. Update `OrchestrationController` so:
   - agent list uses `Workspace` instead of `Docker`;
   - the dashboard loads workspace status, not Docker status;
   - the Docker tab is removed from navigation;
   - `/agents/_detail/{id}/workspace` becomes the operator detail surface;
   - direct lifecycle actions tied only to containers disappear.
2. Rename shell-exec copy and default values:

```text
"Container Exec" -> "Shell Exec"
"Run a bounded shell command inside this agent container." -> "Run a bounded shell command in this agent workspace."
workingDirectory default -> "workspace" or blank, not "/workspace"
```

3. Replace `RuntimeController` Docker endpoint with a filesystem/runtime endpoint only if there is still a UI or external client need; otherwise remove it outright.
4. Remove `agent-docker-status` CSS/test hooks and add workspace-status hooks that render exists/writable/activity/outputs/project-links facts.
5. Preserve HTMX for tab loads, refreshes, and form submissions. JavaScript should remain limited to existing tab active-state affordance.
6. Update controller/API tests to assert absence of stale Docker controls and presence of workspace status controls.
7. Update any public docs or screenshots that expose Docker as the normal operator workflow.

## Validation

- Controller tests for agent list, dashboard, workspace tab, shell exec, and removed Docker routes.
- Browser validation:
  - `/agents` shows workspace status and no Docker column;
  - agent detail has no Docker tab or lifecycle controls;
  - shell exec runs a bounded command in the workspace;
  - outputs and linked projects remain visible.
- `rg -n 'Docker Runtime|Wake|Sleep|Restart|Container Exec|/_docker/|docker-status' src/main` has no active UI hits.

## Exit Criteria

- A user can operate agents without seeing container language or dead Docker controls.
- The public contract no longer advertises Docker as a supported runtime.
- Phase 05 can delete remaining Docker implementation without breaking active consumers.
