# Topic

Task and workflow schema-backed run snapshots.

# Source References

- `src/main/resources/schema.sql`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowRepository.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/task/TaskServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/workflow/WorkflowServiceTest.java`

# Key Takeaways

- Task and workflow repository constructors should not create task/workflow tables; schema ownership lives in `schema.sql`.
- Task runs snapshot the full `TaskDefinition` as JSON so later edits to the definition do not change the execution record.
- Task definitions and drafts use typed `outputs` as the single runtime/output contract. Legacy task `deliverables` fields and `deliverables_json` columns may exist in old data, but current repository reads/writes ignore them.
- Old task run snapshots may still contain a `deliverables` JSON property; task snapshot deserialization tolerates and ignores unknown legacy fields.
- Old task drafts with `planning_task = define_deliverables` are normalized to `define_outputs` when loaded or set through task tooling.
- Runtime input and output values are stored as JSON objects keyed by declared field name, not inferred from assistant text.
- Workflow runs snapshot the full `WorkflowDefinition` and store per-step `WorkflowStepRun` records as ordered JSON.
- Jackson needs Java time module registration for snapshot records that contain `Instant`; the repositories call `findAndRegisterModules()` on their mapper.

# Engine Relevance

This pattern keeps task/workflow execution reproducible and makes downstream workflow bindings deterministic. A workflow step should consume `TaskRun.outputValues().get("<declaredName>")` rather than parsing final messages or evidence text.

# Open Questions

- Should task definitions eventually get immutable version ids instead of overwrite-in-place plus run snapshots?
- Should task input/output schemas move from loose text hints to strict JSON Schema validation?
