# Phase 02 - Bash Execution Runtime

## Context

`AgentShellToolService` already supports host execution, but orchestration contexts are force-routed into `AgentContainerRuntimeService`. That means the runtime decision is inverted for the new architecture: agent work must become host Bash execution inside the managed workspace, while retaining command allowlists, timeouts, bounded output capture, and path confinement.

## Goal

Replace Docker-backed agent command execution with Bash-backed workspace execution and define the new provenance contract consumed by tasks, workflows, operators, and tests.

## In Scope

- Refactor orchestration-context shell execution away from `AgentContainerRuntimeService`.
- Default agent commands to `agents/<agentId>/workspace/`.
- Support managed working-directory aliases for `workspace`, `projects/<projectId>`, `outputs`, and `scratch` without exposing raw host paths to callers.
- Preserve allowed-command validation, timeout handling, bounded output capture, and cancellation behavior.
- Replace Docker-flavored provenance (`executionSource=docker`, `containerId`) with filesystem/Bash provenance.
- Keep project lease enforcement and project membership checks intact.

## Out Of Scope

- Workspace monitoring read models.
- UI route changes.
- Deleting the Docker package while any callers still compile against it.

## Implementation Steps

1. Introduce a small execution abstraction only if it reduces churn, for example `AgentWorkspaceExecutionService`; do not create a generic plugin runtime framework.
2. Replace `execInContainer(...)` with workspace execution that resolves from the agent workspace root and runs through `ProcessBuilder`/Bash on the host.
3. Normalize working-directory semantics before command launch:

```text
. or blank                -> <agent workspace>
workspace                 -> <agent workspace>
outputs                   -> <agent workspace>/outputs
scratch                   -> <agent workspace>/scratch
projects/<projectId>/...  -> <agent workspace>/projects/<projectId>/...
```

4. Reject any absolute or relative path that resolves outside the current agent workspace unless it is a managed project link established by the lease lifecycle.
5. Update `ShellExecResult` or its successor so callers can distinguish `executionSource="bash"` or `"workspace"`, workspace-relative path, and optional agent/project context. Remove `containerId` from the new active contract.
6. Decide whether command parsing should keep the current direct executable model or use `bash -lc`; if Bash shell features are now required, keep the allowlist on the first executable token and document the tradeoff explicitly.
7. Update project-link acquisition/release so a valid lease guarantees the corresponding workspace link exists before command execution and is removed or made unavailable after graceful release.
8. Add tests for:
   - blank working directory default;
   - outputs and scratch aliases;
   - project-link access under lease;
   - path escape rejection;
   - timeout/capture behavior;
   - provenance no longer mentioning Docker.

## Validation

- Focused shell tests with real temp directories.
- Orchestration runtime tests proving an assignment executes from the agent workspace.
- Existing task/workflow tests updated so they no longer expect `/output` or `containerOutputPath()`.
- `rg -n 'containerId|executionSource\\(\\).*docker|execInContainer'` shows only intentional temporary references left for later cleanup.

## Exit Criteria

- Agent work executes successfully without Docker enabled or present.
- Working-directory behavior is deterministic and based on the workspace tree from Phase 01.
- The next phase receives a stable execution/provenance contract to monitor.
