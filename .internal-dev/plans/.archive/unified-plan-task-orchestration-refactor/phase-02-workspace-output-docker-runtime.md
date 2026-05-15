# Phase 02 - Workspace, Outputs, and Docker Runtime

## Context

The target architecture requires mandatory Docker execution and explicit output handoff. Current workspaces are path-confined but do not model temp task/workflow spaces, output directories, Docker mounts, or writable workspace leases.

## Goal

Add managed workspace/output services and Docker-backed execution primitives that later phases can use for tasks, workflows, jobs, and projects.

## In Scope

- Add Docker Java dependency: `com.github.docker-java:docker-java:3.7.1`.
- Add `ai.orchestration.docker` package.
- Add persistent agent home directories, task/workflow temp directories, job/project persistent directories, output directories, and lease records.
- Add output materialization service for file path, message, string, number, and JSON outputs.
- Make Docker availability mandatory for orchestration execution.
- Add a test-agent Docker path for integration validation.

## Out of Scope

- Full workflow/job/project orchestration.
- UI output browser.
- Building complex custom agent images beyond a minimum configured image contract.

## Implementation Steps

1. Extend workspace schema:
   - `workspace_roots`
   - `workspace_leases`
   - `run_output_artifacts`
   - Keep records under `dataRoot`.
2. Implement workspace directory service.
   - `agentHome(agentId)`
   - `taskTemp(runId)`
   - `workflowTemp(runId)`
   - `jobWorkspace(jobId)`
   - `projectWorkspace(projectId)`
   - `agentOutput(agentId, planSlug, runId)`
   - `jobOutput(jobId, planSlug, runId)`
3. Implement lease service.
   - Writable lease is exclusive per job/project workspace.
   - Lease has `holderType`, `holderId`, `workspaceId`, `mode`, `expiresAt`, and `releasedAt`.
   - Extension must verify holder ownership.
4. Implement output service.
   - For `file_path`, copy file into output directory and store artifact metadata.
   - For `user_message`, write `message.md` or `{outputName}.md`.
   - For JSON, write `{outputName}.json` and validate JSON-serializable value.
   - For string/number, write `{outputName}.txt`.
   - Persist artifact rows and return canonical output metadata in `PlanRun.outputValues`.
5. Implement Docker runtime client.
   - Configure Docker host from environment/default local socket.
   - Verify daemon with a ping/info call at startup.
   - Verify configured image exists or fail with actionable error.
   - Create exec requests with mounted agent home, run temp, output dir, and optional leased job/project dirs.
6. Add runtime prompt context text.
   - Explain mounted paths to the agent.
   - Explain output directory requirements.
   - Explain Python/venv practice.
7. Wire unified plan runs to allocate temp/output directories before execution and clean temp after terminal state.
8. Add cleanup behavior.
   - On `COMPLETED`, `FAILED`, `CANCELLED`, or `NEEDS_REVIEW`, release leases and delete temp directory.
   - Never delete output directories.

## Validation

- Unit tests for path confinement, output materialization, lease ownership, lease extension, and temp cleanup.
- Docker integration test with the test image:
  - container starts;
  - agent home is mounted;
  - temp workspace is writable;
  - output directory is writable;
  - Python is available;
  - run exits and temp cleanup occurs.
- Startup smoke must fail clearly when Docker is unavailable.

## Exit Criteria

- All plan/task execution has an allocated temp workspace and output directory.
- Output artifacts are copied or written before completion is accepted.
- Docker is the only orchestration execution path.
- Workspace leases prevent concurrent writable job/project mounts.

