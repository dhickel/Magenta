# Topic
Workflow v2 graph contract and runtime behavior (typed ports, control branches, parallel scheduling, final outputs)

# Source References
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowDefinition.java`

# Key Takeaways
- Save/validate now hard-gates on v2 (`schemaVersion=2`) with explicit graph validation.
- TASK nodes must bind to approved plan task templates and pass required-input satisfaction checks.
- Control routing is explicit: approval nodes branch via `CONTROL` routes using `APPROVED` and `REJECTED` outcomes.
- Runtime scheduling executes ready nodes in parallel batches up to `maxConcurrency`, with join-style dependency resolution.
- Non-selected control branches are marked `SKIPPED` so runs can terminate deterministically.
- Final run result now includes persisted `finalOutputs` and `artifactIds` alongside per-node run state.

# Engine Relevance
This establishes the durable workflow execution contract for orchestration-driven plan/task composition and enables deterministic branch-aware execution with materialized outputs.

# Open Questions
- Should graph-composer persistence/validation calls be routed through dedicated HTMX fragment endpoints rather than direct API fetch calls?
- Do we want stronger strict typing for non-task adapter node ports (currently typed enforcement is strongest on task-template ports)?
