# Phase 04 - Workflow Builder Redesign

## Context

The current workflow editor is a row list with a raw JSON input-binding box. The backend executes workflows sequentially by node index. The requested product needs a tree/link structure with clear node relationships, one input per node, multiple output routes, fan-out, pass-through/log routes, and strong validation.

## Goal

Introduce route-aware workflow definitions and a workflow builder UI that lets users construct and validate task-node trees without writing JSON.

## In Scope

- Workflow definition model changes.
- Graph/tree validation.
- Route-aware execution semantics.
- Workflow builder UI with node list/tree, route editor, and properties panel.
- Submit-to-agent flow replacing direct run.
- Tests for fan-out, pass-through, and validation errors.

## Out of Scope

- Full freeform canvas editor if a structured tree builder can satisfy the workflow first.
- Agent-created workflow drafting by chat.
- Complex conditional expression language beyond explicit route types and simple conditions.

## Implementation Steps

1. Add explicit workflow routes.
   - Target records:

```java
public record WorkflowDefinition(
    String id,
    String title,
    String summary,
    List<WorkflowNode> nodes,
    List<WorkflowRoute> routes,
    Instant createdAt,
    Instant updatedAt
) {}

public record WorkflowNode(
    String key,
    WorkflowNodeType type,
    String planId,
    String label,
    String inputName,
    Map<String, Object> config,
    boolean parallel
) {}

public record WorkflowRoute(
    String id,
    String fromNodeKey,
    String fromOutputName,
    String toNodeKey,
    String toInputName,
    WorkflowRouteType routeType,
    String condition
) {}
```

   - `WorkflowRouteType` should start with:
     - `MAP_OUTPUT` - source output populates downstream input.
     - `PASS_THROUGH` - source output is forwarded unchanged to downstream node.
     - `LOG` - source output is materialized/logged and does not block or feed a downstream task.
     - `CONTROL` - gate/approval route if needed for status flow.

2. Keep compatibility importer for old workflows.
   - Convert old `WorkflowNode.inputBindings` into `WorkflowRoute` entries on load or migration.
   - Do not keep editing old JSON bindings in the new UI.
   - Document old fields as deprecated.

3. Build graph validator.
   - Validate node key uniqueness.
   - Validate exactly one primary input target per node if that remains the product rule.
   - Validate required task inputs are satisfied by route or literal config.
   - Validate route source node and output exist.
   - Validate route destination node and input exist.
   - Validate type compatibility using declared `PlanFieldDefinition`.
   - Validate no cycles unless an explicit future loop feature is introduced.
   - Validate every non-root executable node has at least one incoming route or literal input.
   - Validate every route has a route type.

4. Rewrite runner around graph traversal.
   - Compute ready nodes from incoming dependencies.
   - For sequential MVP, execute one ready node at a time in topological order.
   - For nodes marked `parallel`, enqueue ready siblings concurrently only after concurrency policy is explicitly tested.
   - Maintain `outputsByNode` as now, but use routes to assemble each node's single input object.
   - Fan-out is allowed by multiple routes from the same source output.
   - `LOG` route materializes output to run evidence/artifacts and should not create a downstream dependency.
   - Gate nodes set WAITING and resume through existing inbox response flow.

5. Redesign workflow UI.
   - Split into:
     - left workflow list;
     - center tree/link structure;
     - right properties/validation panel.
   - Node row/card collapsed state:
     - node label/key;
     - type;
     - task template;
     - required input completion status;
     - outgoing route count.
   - Expanded node editor:
     - node type select;
     - task template picker;
     - one input target configuration;
     - message template for gate/message nodes;
     - pass-through/log options.
   - Route editor:
     - source output select based on source task outputs;
     - route type select;
     - destination node select;
     - destination input select;
     - validation message inline.
   - Avoid raw JSON textareas except an advanced debug panel.
   - Use HTMX for CRUD and persistence flows (workflow load/save, node add/remove, route add/remove, validation refresh, submit-to-agent result rendering).
   - Use JavaScript only for the stateful editor behaviors that are awkward in pure HTMX (for example in-memory node/link interaction, drag/reorder, and transient graph selection state). Keep JS as a local enhancement layer, not the primary transport layer.

6. Remove direct workflow run UI.
   - Remove workflow run panel and `Run` button.
   - Add "Submit to agent" using `WORKFLOW_RUN` assignment with workflow id and context.
   - Show queue/assignment result, not raw SSE logs.

7. Decide library stance.
   - Because this app uses SimplyPages/HTMX-first UI, start with a structured tree/link editor rather than adding React solely for graph editing.
   - Keep HTMX as the primary request/update mechanism even if JS is used for graph interaction mechanics.
   - If structured tree editing proves insufficient, document a future feature to evaluate a dedicated graph editor. Do not add a React workflow library in this phase without user approval.

## Validation

- Unit tests:
  - graph validator rejects missing route endpoints;
  - fan-out source output can feed two nodes;
  - pass-through route forwards expected value;
  - log route materializes output and does not block downstream execution;
  - cycle is rejected;
  - required input missing is rejected.
- Controller tests:
  - create/update workflow with `routes`;
  - validate endpoint returns structured warnings/errors.
- Browser validation:
  - create workflow with two task nodes and one route;
  - add fan-out route;
  - add log route;
  - validation errors appear inline;
  - route/node persistence and validation refreshes occur through HTMX-driven requests;
  - submit-to-agent creates assignment;
  - no direct run controls are visible.
- `mvn test`
- Bounded startup smoke.

## Exit Criteria

- Workflow definitions can represent one node with multiple output routes.
- Users can link task outputs to multiple downstream inputs without writing JSON.
- UI validation matches backend validation.
- Workflow page no longer directly runs workflows.
