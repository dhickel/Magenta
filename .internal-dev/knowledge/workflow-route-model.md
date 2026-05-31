# Topic

Workflow route types and graph traversal semantics for the Operate UI Contract Refactor.

# Source References

- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRoute.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRouteType.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowNode.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowDefinition.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- Phase 04 handoff notes in `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md`
- Phase 04 re-validation report

# Key Takeaways

## Route types

`WorkflowRouteType` enum defines four route kinds:

| Type | Purpose | Creates dependency? |
|---|---|---|
| `MAP_OUTPUT` | Maps a named output from source node to a named input on target node | Yes (target waits for source) |
| `PASS_THROUGH` | Forwards all source node outputs into target node inputs without naming ports | Yes (target waits for source) |
| `LOG` | Logs a named output from source node (materials output without creating downstream dependency) | No |
| `CONTROL` | Pure ordering edge (target waits for source, no data passed) | Yes |

## Route structure

```java
record WorkflowRoute(
    String id,              // unique route ID (e.g. "route_1")
    String fromNodeKey,     // source node key
    String fromOutputName,  // output name on source node (null for PASS_THROUGH/CONTROL)
    String toNodeKey,       // target node key (can be same as source for LOG)
    String toInputName,     // input name on target node (null for PASS_THROUGH/CONTROL/LOG)
    WorkflowRouteType routeType,
    String condition        // reserved for future conditional routing
)
```

## Node structure

```java
record WorkflowNode(
    String key,              // unique node key (e.g. "node_1")
    WorkflowNodeType type,   // TASK, GATE, APPROVAL
    String planId,           // referenced plan (for TASK nodes)
    String label,            // human-readable label
    String inputName,        // input name mapping
    Map<String,Object> config,
    boolean parallel,        // allow parallel execution with siblings
    List<WorkflowBinding> inputBindings,  // legacy input bindings
    String messageTemplate,  // message template text
    String resumePolicy
)
```

## Production canonicalization update

As of the workflow canonicalization remediation, production callers import `io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService`. The older `io.mindspice.magenta2.ai.chat.workflow` classes are deprecated and are no longer Spring beans, so runtime assignments, job workflow items, and workflow stream helpers should not depend on `ai_workflow_definitions` or `ai_workflow_runs`.

As of the 2026-05-18 Domain 08 legacy cleanup, the older `io.mindspice.magenta2.ai.chat.workflow` package has been removed from `src/main/java`. New workflow code should use only `io.mindspice.magenta2.ai.orchestration.workflow`; the old `ai_workflow_*` table names are historical references, not active persistence targets.

Task nodes execute through `WorkflowTaskExecutor`, which calls `ChatService.executeTaskBlocking(...)`. Workflow task outputs must come from persisted `TaskRun.outputValues()` keyed by declared output names; assistant text and default output maps are not valid workflow outputs.

The runner now has deterministic control-node handling for:

- `VALIDATION`: checks configured required values and emits `valid=true` when satisfied.
- `COPY`: copies/fans out selected input values through node config.
- `LOG`: materializes incoming values as output artifacts/evidence.

## Graph traversal

The runner computes ready nodes using topological rules:
1. A node is ready when all incoming dependency-creating route sources have completed.
2. `MAP_OUTPUT`, `PASS_THROUGH`, and `CONTROL` routes create dependencies.
3. `LOG` routes do NOT create dependencies.
4. Nodes with no incoming dependency routes are roots and execute first.
5. Fan-out is supported in the data model (one source to multiple targets) but parallel execution of ready nodes is deferred.

## PASS_THROUGH data semantics

Canonical `PASS_THROUGH` routes use `fromOutputName=null` and `toInputName=null`. Validation requires a source and destination node but does not require source or target ports. At runtime, the runner merges every source output key into the downstream node input map in sorted key order for deterministic materialization.

Compatibility behavior remains for older saved `PASS_THROUGH` routes that have both a source and target port. Those routes keep the previous single-port behavior and validate like a port-mapped data route. Partially ported `PASS_THROUGH` routes are invalid because they cannot unambiguously choose either compatibility single-port forwarding or canonical full-map forwarding.

When multiple incoming routes write the same input key, later routes in definition order overwrite earlier route values. Node `config` is applied after route resolution and therefore overrides any route-provided value for the same key. This matches existing `MAP_OUTPUT` input materialization behavior and makes operator-authored node config the final local override.

## Cycle detection

The graph validator uses DFS to detect cycles. A cycle is reported as an ERROR:
```
ERROR: Workflow contains a cycle: node_1 -> node_2 -> node_1
```

## Legacy compatibility

Old `inputBindings` with `STEP_OUTPUT` type are auto-converted to `MAP_OUTPUT` routes on save. Old `LITERAL` bindings remain as legacy data read by the runner's fallback path.

## Data persistence

Routes are stored in the `workflow_definitions.routes_json` column (TEXT, default `'[]'`). The `WorkflowRepository` provides online migration via `ALTER TABLE ADD COLUMN IF NOT EXISTS` pattern.

## Validation rules

The validator checks:
- Node key uniqueness (ERROR if duplicate)
- Route endpoint existence (ERROR if from/to node not found)
- Cycle detection (ERROR if cycle found)
- Required input satisfaction for TASK nodes with MAP_OUTPUT routes (ERROR if missing)
- Type compatibility for MAP_OUTPUT routes (WARNING if source output type != target input type)

# Engine Relevance

When adding new route types or node types:
- Update `WorkflowRouteType` and `WorkflowNodeType` enums
- Add validation rules in `WorkflowValidator`
- Add execution handling in `WorkflowRunner.resolveNodeInputs()`
- Update `WorkflowRepository` schema if new columns needed
- Update HTMX editor endpoints in `OrchestrationController` for UI affordances

The route model intentionally keeps the data shape small so workflow definitions serialize cleanly and the graph can be computed without loading full plan definitions at validation time.

# Open Questions

- Should conditional routing (expression evaluation) be added for GATE nodes?
- Should parallel fan-out execution be implemented for ready nodes?
- Should the compatibility importer be removed once all legacy workflows are migrated?
