# Filesystem Agent Runtime Refactor: Architecture Map

## Before

```text
Agent task/workflow
  -> AgentShellToolService
    -> AgentContainerRuntimeService
      -> DockerRuntimeClient
        -> Docker/Podman daemon
          mounts:
            /home/agent
            /projects/<projectId>
            /workspace
            /output
```

Operator-facing state:

```text
container lifecycle + daemon health + image + mounts
```

## After

```text
Agent task/workflow
  -> AgentShellToolService
    -> workspace-aware Bash execution
      -> agents/<agentId>/workspace/
         projects/<projectId> -> project workspace link
         outputs/<run>/
         scratch/
```

Operator-facing state:

```text
workspace existence + writability + linked projects + active runs + outputs + latest activity
```

## Responsibility Map

| Concern | Primary Owner After Refactor |
| --- | --- |
| Path creation/confinement | `WorkspaceDirectoryService` |
| Workspace metadata/links/leases | `WorkspaceService`, `WorkspaceLeaseService` |
| Shell execution | `AgentShellToolService` plus a narrow workspace execution helper only if needed |
| Artifact placement | `OutputArtifactService` |
| Workspace health/activity read model | new workspace status service |
| Operator HTML fragments | `OrchestrationController` |
| Public runtime API | either removed Docker endpoint or a new workspace summary endpoint if still needed |

## Keep Versus Remove

Keep:

- `dataRoot` confinement.
- project lease semantics.
- output artifact metadata.
- HTMX-first operator UI.
- bounded command timeouts/output capture.

Remove:

- Docker daemon/image config.
- managed container lifecycle.
- container mounts and container IDs.
- Docker runtime status API.
- Docker tabs/actions/labels.
- Docker-specific test and validation lanes.
