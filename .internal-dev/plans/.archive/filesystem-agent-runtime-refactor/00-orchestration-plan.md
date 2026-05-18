# Filesystem Agent Runtime Refactor: Orchestration Plan

## Context

The current application treats Docker/Podman as the agent runtime boundary. That assumption now leaks through configuration, shell execution, agent lifecycle management, runtime status APIs, CSS/UI labels, controller routes, tests, documentation, and validation gates. The user has decided to remove Docker entirely and return the same functionality through host filesystem workspaces plus Bash execution.

The codebase is not starting from zero. It already has a strong workspace spine:

- `WorkspaceDirectoryService` confines paths under `dataRoot` and already creates agent, job, project, temp, and output directories.
- `WorkspaceService` persists workspace metadata, links, and leases.
- `OutputArtifactService` materializes deliverables.
- `AgentShellToolService` already has a host execution path, but orchestration work is diverted into Docker today.

## Goal

Deliver a subagent-ready, blocking migration path that removes Docker as a runtime concept and replaces it with:

- filesystem-backed agent workspaces;
- Bash execution against those workspaces;
- workspace health/activity monitoring;
- durable output routing under each agent workspace;
- a UI and public API that talk about workspace state, not containers.

## In Scope

- Delete Docker runtime configuration, services, records, API routes, UI controls, CSS, tests, scripts, and knowledge that become invalid after migration.
- Promote the filesystem layout in `README.md` to the canonical runtime contract.
- Refactor shell execution so orchestration tasks run through Bash on the host inside managed workspace roots.
- Replace Docker monitoring with workspace monitoring and activity reporting.
- Move agent-owned outputs beneath the agent workspace output tree.
- Keep project lease semantics intact while exposing leased project work under each agent workspace.
- Update operator UI, public APIs, tests, and `.internal-dev` docs to the new vocabulary and behavior.

## Out Of Scope

- Remote execution, sandboxing beyond the existing data-root/path confinement model, or OS-level isolation replacement for Docker.
- A general process supervisor for arbitrary long-running daemons.
- New workflow, job, project, or chat features unrelated to removing Docker.
- Changing `/chat`.

## Terminology

- `dataRoot`: configured root directory from `AiConfig.dataRoot()`.
- `agent workspace`: `agents/<agentId>/workspace/`, the host-side execution root for an agent.
- `workspace health`: readiness and activity derived from filesystem state, leases, outputs, and bounded command activity.
- `project link`: managed link from `agents/<agentId>/workspace/projects/<projectId>` to the canonical project workspace while access is leased.
- `output root`: `agents/<agentId>/workspace/outputs/` for agent-owned deliverables.

## Target Architecture

### Runtime boundary

The trust boundary becomes `dataRoot`, not a container. Host shell execution must:

- resolve all working directories through typed workspace services;
- default an agent run to that agent's workspace root;
- refuse paths that escape `dataRoot` after normalization and real-path checks;
- use `bash -lc` only where shell features are explicitly required, otherwise preserve the current allowed-command model.

### Workspace monitoring

Replace container status with a workspace read model such as:

```java
public record AgentWorkspaceStatus(
    String agentId,
    Path workspacePath,
    boolean exists,
    boolean writable,
    Instant lastActivityAt,
    int activeRunCount,
    long outputArtifactCount,
    long outputBytes,
    List<String> linkedProjectIds,
    String message
) {}
```

Monitor facts that operators can act on:

- workspace exists and is writable;
- latest activity timestamp;
- active task/workflow count;
- linked project workspaces;
- output count and disk usage;
- most recent failure if workspace preparation or shell execution failed.

### Output routing

Agent-owned artifacts materialize under `workspace/outputs/<run-slug>-<runId>/`. Keep output metadata persisted through `OutputArtifactService`; only the filesystem placement changes.

### UI contract

Remove lifecycle verbs that imply containers (`Wake`, `Sleep`, `Restart`, `Docker Runtime`, `Container Exec`). Replace them with workspace vocabulary (`Workspace`, `Refresh Status`, `Shell Exec`, `Outputs`) and truthful read-only health/activity facts. There is no direct start/stop action for a directory.

## Subagent Dispatch Model

This migration is intentionally serialized. Do not parallelize the implementation phases even when write sets appear disjoint, because each phase changes the contract consumed by the next one.

| Order | Agent Package | Must Produce Before Next Phase |
| --- | --- | --- |
| 1 | Layout/config agent | canonical paths, config deletion map, migration notes, green focused tests |
| 2 | Execution agent | Bash execution path, provenance contract, project-link working-directory semantics |
| 3 | Monitoring/output agent | workspace status read model, output rerouting, compatibility findings |
| 4 | UI/API agent | Docker-free operator/API surface over the new backend contract |
| 5 | Cleanup agent | deleted Docker code/docs/tests, migration cleanup, no stale refs |
| 6 | Validator | full test/browser/startup proof, archive/closeout recommendation |

Each phase must:

1. read this file and its phase file;
2. read the nearest package `AGENTS.md` before editing;
3. implement directly, not write a review in place of code;
4. run focused tests;
5. append the required handoff section to `phase_handoff_notes.md`;
6. stop if the previous handoff is missing or reports an unresolved blocker.

## Handoff Requirement

Every implementation phase must append:

- exact files changed;
- public contracts added, removed, or renamed;
- migrations or data-shape changes performed;
- tests run and current failures;
- assumptions the next phase must rely on;
- known blockers and follow-up work intentionally left for the next phase.

The next phase must begin by confirming that handoff and explicitly state whether it accepts or rejects the prior contract.

## Validation Strategy

The final validator must prove:

- focused tests per phase;
- `mvn test`;
- bounded Spring Boot startup without Docker/Podman dependencies;
- browser validation for `/agents`, workspace tab, outputs tab, shell exec flow, and absence of Docker UI controls;
- real shell execution and output materialization under the configured data root;
- no stale Docker routes, settings, or labels remain in active code paths.

If the removal exposes an unapproved need for stronger isolation than filesystem confinement provides, stop and escalate. Do not silently recreate Docker through another abstraction.

## Exit Criteria

- Docker is no longer required to configure, start, run, monitor, or operate agents.
- Every agent runs from a host-side workspace rooted under `dataRoot`.
- Agent outputs are durable under the workspace `outputs/` tree.
- Workspace monitoring fully replaces container monitoring in UI and APIs.
- Docker runtime package, config, routes, docs, UI controls, and validation gates are removed or archived.
- The final validator confirms the blocking chain and the new runtime behavior end to end.
