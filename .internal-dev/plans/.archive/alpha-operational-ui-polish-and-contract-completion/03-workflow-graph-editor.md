# Phase 03 - Workflow Graph Editor

## Context

The current workflow implementation has moved to `WorkflowDefinition` with explicit `WorkflowNode` and `WorkflowRoute` records. Routes have `fromNodeKey`, `fromOutputName`, `toNodeKey`, `toInputName`, `routeType`, and optional `condition`. The editor is HTMX-first and tree/form based, but the route rows lack column names, the add UI is too compressed, and adapter chains are not represented as first-class user concepts.

The existing alpha deferred note keeps drag-canvas editing out of scope. This phase must still deliver a robust graph editor for alpha by using structured node, route, and adapter panels. If the user later undeferes drag-canvas editing, build it on top of the same saved schema rather than replacing the schema.

## Goal

Make `/workflows` a real graph-builder surface: users can select a node, open a modal/panel editor, configure typed inputs/outputs and adapters, add multiple receivers, and understand each route without reverse-engineering unlabeled columns.

## In Scope

- Route rows with explicit column names and readable descriptions.
- Interactive add/edit/delete UI for nodes and routes.
- Selected-node modal or side panel.
- Adapter-chain representation for logging pass-through, output mapping, and fan-out/multi-receiver routing.
- Plan/task input/output schema support for array and optional fields.
- Save-time validation that prevents impossible graphs.

## Out of Scope

- Drag-canvas editing, unless the user explicitly removes that deferral.
- Runtime conditional expression evaluation.
- Cyclic workflows and retry loops.
- Parallel ready-node execution.

## Target Design

Use the existing route graph as the canonical runtime model. Represent adapters as explicit workflow nodes or a small typed adapter record stored in node `config`, but do not hide them as UI-only decorations.

Preferred alpha model:

- `TASK` nodes execute a selected `PlanDefinition`.
- `LOGGING` or `LOG` adapter nodes consume one selected output and produce a log artifact or audit event.
- `OUTPUT_MAPPER` adapter nodes map selected source outputs into named destination inputs.
- `FAN_OUT` or `MULTI_ADAPTER` nodes publish one source value to multiple route targets.
- Each adapter node has typed config rendered as structured fields, not raw JSON.
- Multiple receivers are configured by multiple outgoing routes from one source output or by a fan-out adapter when the fan-out itself has behavior.

If adding new `WorkflowNodeType` values is too large, use existing node `config` with a required `adapterType` enum for adapter nodes, but the UI must still show adapter chains as first-class rows/panels.

## Implementation Steps

1. Review `WorkflowNodeType`, `WorkflowRouteType`, `WorkflowValidator`, `WorkflowRunner`, `BindingResolver`, and `WorkflowTaskExecutor` before editing.
2. Add column names to route rows:
   - Route ID
   - Adapter/Route Type
   - Source Node
   - Source Output
   - Destination Node
   - Destination Input
   - Condition
   - Actions
3. Replace the compressed add-route row with an "Add Route" button that opens a modal or side panel using HTMX:
   - `GET /workflows/_editor/{workflowId}/routes/new`
   - `POST /workflows/_editor/{workflowId}/routes`
   - target `#workflow-modal-container` or a stable side panel target.
4. Add node selection:
   - clicking a node row loads `/workflows/_editor/{workflowId}/nodes/{nodeKey}/panel`;
   - the panel shows node identity, selected task, inputs, outputs, adapters, outgoing routes, and incoming routes.
5. Add adapter management endpoints:
   - `GET /workflows/_editor/{workflowId}/nodes/{nodeKey}/adapters/new`
   - `POST /workflows/_editor/{workflowId}/nodes/{nodeKey}/adapters`
   - `PUT /workflows/_editor/{workflowId}/nodes/{nodeKey}/adapters/{adapterId}`
   - `DELETE /workflows/_editor/{workflowId}/nodes/{nodeKey}/adapters/{adapterId}`
6. Make adapter configs schema-shaped:
   - output mapper: source output, target input, optional transform type;
   - logging pass-through: consumed source output, log label, artifact/output policy;
   - fan-out: source output, receiver list, per-receiver target input.
7. Add task/plan field improvements needed by workflows:
   - `PlanFieldDefinition.array` must be selectable and persisted;
   - optional vs required must be visible;
   - route validation must check array-to-scalar, scalar-to-array, required missing inputs, and unknown field names.
8. Add validation warnings/errors directly into the editor. Save should reject hard errors; submit should never bypass validation.
9. Keep JavaScript minimal. If client-side selection state is easier with JS, keep it to panel open/close and selected-row highlighting. HTMX still owns CRUD and fragment replacement.

## Validation

- `WorkflowRepositoryTest` proves adapter config or new node types round-trip.
- `WorkflowValidatorTest` or existing workflow service tests cover:
  - duplicate routes;
  - multiple receivers from a source output;
  - required input missing;
  - unknown source output;
  - array/scalar mismatch;
  - adapter chain with logging plus mapper;
  - cycles rejected.
- Controller tests cover modal/panel routes and labeled route-row output.
- Playwright MCP:
  - create a workflow;
  - add two task nodes;
  - configure task 1 with three outputs;
  - add logging adapter for one output;
  - map two outputs to task 2 inputs;
  - fan out task 2 output to two receivers;
  - validate, save, reload, and verify graph structure persists.

## Exit Criteria

- A user can understand and build workflow routes without guessing unlabeled columns.
- Adapter chains are persisted and validated, not just drawn.
- Multiple receivers are supported through explicit routes or a fan-out adapter.

