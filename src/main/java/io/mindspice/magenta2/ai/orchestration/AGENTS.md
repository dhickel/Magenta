## Orchestration Package

This package owns durable runtime agent, setting, workspace, and orchestration state.

### Responsibilities
- Store runtime settings, agent profiles, and managed workspace metadata in SQLite.
- Store user-facing jobs, ordered job items, durable work assignments, inbox messages, schedules, event reactions, and orchestration events in SQLite.
- Treat file AI configuration as the source for model endpoint definitions and legacy agent import only.
- Keep orchestration services small, explicit, and usable by existing chat flows.
- Keep filesystem workspace roots confined under the configured data root.
- Keep durable assignment execution resumable at task/workflow/job item boundaries; do not attempt token-level or partial model response resume.
- Execute task and workflow assignments through the chat task execution service so durable orchestration never fabricates task outputs.
- Persist job item retry policy with explicit retry count and continue-on-failure behavior.

### Change guidance
- Do not add subagent lifecycle behavior without a concrete workflow.
- Keep new queue, scheduler, inbox, and event reaction behavior bound to explicit assignment creation paths.
- Keep controllers thin and delegate validation and persistence to services.
- Validate runtime model keys against configured file models.
- Validate chat tool allowlists through `ChatToolRegistry`.

### Validation
- Add focused repository and service tests for persistence, model resolution, seeding, and path confinement.
- Add focused repository and service tests for queue ordering, lease transitions, step-boundary checkpointing, schedule due handling, and event reaction matching.
- Run chat regression tests when changing runtime defaults consumed by chat.
