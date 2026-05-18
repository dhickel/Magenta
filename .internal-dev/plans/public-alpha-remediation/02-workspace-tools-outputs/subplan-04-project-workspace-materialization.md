# Subplan 04: Project Workspace Materialization

## Goal

Make acquired project workspace leases visible through the filesystem path promised to task/tool execution.

## Implementation Steps

1. Trace lease acquisition in `OrchestrationRunnerService` and project link expectations in shell/file tools.
2. Materialize project links under the documented assignment workspace path, preserving membership and lease checks.
3. Define release behavior so links disappear when leases are released or assignment cleanup runs.
4. Update UI/docs only if the promised path changes.
5. Add runtime/browser or API proof that project-linked work can access the path.

## Validation

Runtime test with a project lease, linked path, and tool access through the documented alias.
