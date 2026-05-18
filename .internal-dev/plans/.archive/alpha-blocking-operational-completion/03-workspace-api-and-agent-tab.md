# Phase 03: Workspace API And Agent Workspace Tab Completion

## Context

Workspace services and filesystem helpers exist, but the API surface is incomplete. `/api/workspaces` has link endpoints but no list/read endpoint, and the agent workspace tab currently depends on optional service availability. For alpha, operators need to inspect workspace roots, links, leases, and output locations from the dashboard.

Relevant files:

- `src/main/java/io/mindspice/magenta2/api/web/WorkspaceController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceDirectoryService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseServiceTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

## Goal

Make workspaces a complete alpha operational surface. Operators should be able to list workspaces, inspect one workspace, inspect links and active leases, and see clear workspace information from agent/project/job pages.

## In Scope

- Add `GET /api/workspaces` with filters for `ownerType` and `ownerId`.
- Add `GET /api/workspaces/{workspaceId}`.
- Add service/repository list methods.
- Add active lease read support if not already exposed.
- Improve the agent workspace tab to render workspace metadata, links, active leases, and relevant paths.
- Keep path confinement and lease ownership rules intact.

## Out of Scope

- File browser/editor inside workspace directories.
- Upload/download UI.
- Workspace sharing beyond existing link and lease model.
- Deleting workspaces.

## Implementation Steps

1. Add repository list/read methods.
   - `WorkspaceRepository.findAll(WorkspaceOwnerType ownerType, String ownerId, int limit)`
   - `WorkspaceRepository.findById(String id)` already exists; preserve it.
   - Add `WorkspaceRepository.findActiveLeases(String workspaceId)` if absent.

2. Add service methods.
   - `WorkspaceService.list(WorkspaceOwnerType ownerType, String ownerId, int limit)`
   - `WorkspaceService.activeLeases(String workspaceId)`
   - Validate `limit` to a bounded range, for example 1-200.

3. Complete `WorkspaceController`.
   - Add `GET /api/workspaces`.
   - Add `GET /api/workspaces/{workspaceId}`.
   - Add `GET /api/workspaces/{workspaceId}/leases`.
   - Return `404` for missing workspace and `400` for invalid owner type.

4. Improve agent workspace tab.
   - Remove optional-service placeholder as the normal path. The service should be constructor-required unless application wiring proves it must stay optional.
   - Render:
     - workspace id
     - owner type/id
     - display name
     - root-relative path
     - active lease count/table
     - links table
     - output directory hint if derivable from `WorkspaceDirectoryService`
   - Empty links and empty leases should be clear zero states.

5. Add workspace list surface if lightweight.
   - If `OrchestrationController` already has a workspace dashboard area, wire it to the new list endpoint or server-rendered fragment.
   - Do not build a large standalone workspace manager unless existing navigation already supports it.

6. Tests.
   - Add controller tests for list/read/leases/link endpoints.
   - Add repository/service tests for owner filtering and active lease filtering.
   - Add path-confinement regression tests if touching directory resolution.

## Validation

Required commands:

```bash
mvn -q -Dtest=WorkspaceLeaseServiceTest,OrchestrationRuntimeTest test
mvn -q -Dtest=OrchestrationControllerTest test
mvn -q test
timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Manual validation:

- Call `GET /api/workspaces` and verify a JSON list.
- Call `GET /api/workspaces?ownerType=AGENT&ownerId=<agentId>`.
- Open an agent detail workspace tab and verify metadata, links, and active lease state render.
- Create a workspace link through the existing link API and confirm the tab updates.

Negative validation:

- Invalid `ownerType` returns `400`.
- Missing workspace returns `404`.
- Path escape attempts in links still fail.

## Exit Criteria

- `/api/workspaces` no longer returns 404.
- Agent workspace tab is a useful operational view with metadata, links, and leases.
- Workspace service availability is treated as normal app wiring, not as an expected missing dependency.
- Tests and startup smoke pass or blockers are documented with exact failure output.
