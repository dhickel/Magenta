# Phase 03 - Workspace Monitoring And Output Routing

## Context

Docker monitoring currently stands in for three separate concerns: whether an agent runtime exists, whether it is usable, and where work/output lives. In the filesystem model those facts come from directories, leases, active runs, and artifact metadata instead of daemon/container state.

## Goal

Replace container status with workspace status and move durable agent outputs into the workspace output tree while keeping artifact metadata authoritative.

## In Scope

- Add a workspace status service/read model for agents.
- Track workspace readiness, write access, linked projects, active runs, latest activity, output counts, and output size.
- Reroute agent output materialization to `workspace/outputs/`.
- Replace stale output path hints and any `containerOutputPath`-style runtime metadata.
- Define what counts as agent activity and how it is updated.

## Out Of Scope

- UI rendering changes.
- Removing Docker controller routes before UI consumers are migrated.
- Adding broad filesystem watchers if a cheaper query/update model is sufficient.

## Implementation Steps

1. Introduce a narrow service such as `AgentWorkspaceStatusService` backed by existing workspace, lease, assignment, and output services.
2. Prefer deterministic query/update state over an always-on recursive file watcher unless a real operator use case requires watchers. Recommended activity sources:
   - latest task/workflow shell execution timestamp;
   - latest output materialization timestamp;
   - current active assignment/run count;
   - current linked project set.
3. Define a concise status enum if needed, for example `READY`, `MISSING`, `READ_ONLY`, `BUSY`, `ERROR`; keep detailed facts on the response instead of hiding them in one label.
4. Update `OutputArtifactService` and workspace directory helpers so agent outputs materialize under `workspace/outputs/`.
5. Keep metadata persistence as the source of truth for outputs; filesystem scans are for health summaries and diagnostics, not primary records.
6. Remove or rename runtime fields that still describe container output paths.
7. Add tests for:
   - missing workspace;
   - writable workspace;
   - linked project visibility;
   - active-run count;
   - output count/size after artifact materialization;
   - activity timestamp updates after shell execution and output creation.

## Validation

- Focused workspace/output service tests.
- Existing output artifact tests updated to the new root.
- Verify workspace status still behaves if an output file is deleted out of band while metadata remains.
- `rg -n 'containerOutputPath|/output|agent-docker-status'` is reduced to known UI cleanup references handed to Phase 04.

## Exit Criteria

- The app can answer “is this agent workspace usable?” without Docker.
- Agent outputs are durably placed under the workspace tree.
- The UI/API phase has a concrete read model to render instead of inventing heuristics.
