# Phase 03 - Workflows, Gates, and Inbox

## Context

Current workflows are a small synchronous task chain. The target workflow system should chain finalized plans/tasks with first-class gates, approvals, delegation, and one-off user or agent messaging.

## Goal

Replace MVP workflows with durable workflow runs that execute ordered nodes, persist step-level state, wait at gates, and resume after approvals or inbox responses.

## In Scope

- New workflow schema and services.
- Workflow node types: `TASK`, `USER_APPROVAL`, `AGENT_APPROVAL`, `USER_MESSAGE`, `AGENT_MESSAGE`, `DELEGATION`, `REPORT`.
- Input/output binding between workflow nodes.
- User inbox and agent inbox response handling.
- `WAITING` checkpoint/resume semantics.
- Shared workflow temp workspace across nodes.

## Out of Scope

- Jobs/projects beyond accepting optional `jobId`/`projectId` context.
- UI workflow editor redesign beyond API compatibility if needed.
- Multi-agent network policy beyond basic recipient validation.

## Implementation Steps

1. Replace workflow domain records:
   - `WorkflowDefinition`
   - `WorkflowNode`
   - `WorkflowNodeType`
   - `WorkflowRun`
   - `WorkflowNodeRun`
   - `WorkflowBinding`
2. Replace workflow schema:
   - `workflow_definitions`
   - `workflow_runs`
   - `workflow_node_runs`
3. Implement binding resolver.
   - Bind literal values or prior node outputs into downstream task inputs.
   - Validate field type compatibility using unified plan field definitions.
4. Implement workflow runner.
   - Allocate one workflow temp workspace at run start.
   - Execute from checkpointed node index.
   - For `TASK`, run a finalized `PlanDefinition`.
   - For `REPORT`, materialize declared report/message outputs.
   - For message-only nodes, create inbox messages and continue unless configured to wait.
5. Implement gates.
   - `USER_APPROVAL` creates user inbox message and stores `WAITING`.
   - `AGENT_APPROVAL` creates agent inbox message and stores `WAITING`.
   - Approval response endpoint validates approver, records response payload, and resumes the workflow.
6. Implement delegation node.
   - Start configured child plan/workflow runs.
   - If `parallel = true`, enqueue children and wait for all terminal states.
   - Gather child outputs into a single node output object.
7. Add user inbox.
   - `toType`: `USER` or `AGENT`.
   - `messageType`: `INFO`, `QUESTION`, `APPROVAL`, `RUN_OUTPUT`.
   - `responseJson`, `respondedAt`, `handledAt`.
8. Update SSE stream events.
   - Emit `started`, `progress`, `waiting`, `completed`, `failed`.
   - Include workflow run id, node index, node type, and waiting message id.

## Validation

- Workflow repository tests for snapshots, node runs, and bindings.
- Service tests:
  - two-task workflow passes output to next input;
  - missing binding fails before model execution;
  - user approval pauses as `WAITING`;
  - approval response resumes next node;
  - rejection marks run `NEEDS_REVIEW` or `FAILED` according to node config;
  - workflow temp survives across nodes and is deleted at completion.
- Controller/SSE tests for waiting and resume events.

## Exit Criteria

- Workflows are no longer limited to MVP linear task chains.
- Gates are first-class workflow nodes.
- User and agent inbox responses can resume waiting workflows.
- Workflow outputs are structured and deterministic.

