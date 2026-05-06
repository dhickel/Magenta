# Task Workflow Deferred Items

The archived implementation plan explicitly left these outside v1 scope:

- Workflow DAGs, branching, and conditional routing.
- Scheduling or recurring task execution.
- Background job orchestration beyond request/stream execution.
- Strict JSON Schema validation for task input/output values.
- Task versioning UI.
- Subagent coordination.
- Marketplace or shared task libraries.

Additional follow-up from this implementation:

- Replace the compact workflow binding JSON editor with dedicated literal and previous-output binding controls.
- Add a model-backed task execution endpoint once timeout, review, and fallback behavior are specified.
