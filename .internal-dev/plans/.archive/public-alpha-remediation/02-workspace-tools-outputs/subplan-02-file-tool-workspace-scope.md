# Subplan 02: File Tool Workspace Scope

## Goal

Scope file tools through the active task context instead of the whole `dataRoot`.

## Implementation Steps

1. Locate `AgentFileToolService` root resolution and `AgentFileTools` descriptions.
2. Resolve allowed roots from assignment workspace plus materialized project links.
3. Preserve existing realpath confinement for reads/writes inside allowed roots.
4. Update tool descriptions so they no longer advertise data-root access.
5. Test unrelated workspace denial and allowed current workspace access.

## Validation

File tool unit/integration tests with active context, no context, unrelated workspace, and project link cases.
