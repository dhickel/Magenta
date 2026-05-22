# Phase 03: Task/Plan Runtime Paths And Outputs

## Context

Task/plan runs currently write outputs under agent-oriented paths and use loose artifact discovery during completion. Runtime tools need stable aliases that distinguish durable workspace, run temp, and explicit outputs.

## Goal

Move task/plan execution onto effective workspace paths, update aliases, and introduce explicit output publishing while keeping loose discovery as gated compatibility behavior.

## In Scope

- `PlanService` and `TaskService` runtime path updates.
- `OrchestrationTaskContext` population for aliases.
- File and shell tool alias behavior.
- Explicit output publishing service path.
- Gated and realpath-confined loose artifact discovery.
- Related tests.

## Out of Scope

- Workflow output migration.
- Project owner-agent migration.
- Removing loose discovery entirely.
- Chat-file storage migration.

## Implementation Steps

1. Use the resolver for task/plan output path selection.
2. Ensure project-scoped task outputs land under project workspace outputs.
3. Populate context so tools can expose `workspace/`, `work/`, `outputs/`, `run/`, `scratch/`, and optional `job/`.
4. Add explicit output publishing through the output artifact service.
5. Gate `discoverLooseArtifacts` behind compatibility policy and add realpath/data-root confinement.
6. Add tests for alias resolution, project output placement, explicit publishing, discovery disabled behavior, and chat-file exclusion.
7. Append phase notes and validation results.

## Validation

- `PlanServiceTest`.
- Task service tests if present.
- `AgentFileToolServiceTest`.
- `AgentShellToolServiceTest`.
- `OutputArtifactServiceAttributionTest`.
- Workspace confinement tests.
- Spring context smoke.

## Exit Criteria

- Task/plan runs use effective durable workspace paths.
- Tools expose the target alias contract.
- Explicit output publishing works.
- Loose discovery remains available only as a gated/confined compatibility path.
- Phase validation passes and the phase is committed.
