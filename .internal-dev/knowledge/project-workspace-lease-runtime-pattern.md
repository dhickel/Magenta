# Topic
Project workspace lease lifecycle for orchestration runtime execution.

# Source References
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceLeaseService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/AgentContainerRuntimeService.java`

# Key Takeaways
Project workspaces should be represented by persisted `PROJECT` workspace rows, not only filesystem folders. Runtime must verify membership before lease acquisition, let the database partial unique index remain the exclusivity guard, reconcile expired leases before acquisition, and convert contention into durable `WAITING` work rather than fail-fast execution.

A project lease and assignment runtime lease are separate clocks. Long-running assignments need the project lease heartbeat too, otherwise a healthy assignment can outlive its workspace claim. Managed containers cannot mutate binds in place; preserve `/home/agent` and `/output`, then recreate between turns when `/projects/{projectId}` mounts change.

# Engine Relevance
This keeps shared project mutation serialized without turning the whole agent into a single-workspace machine. One agent may hold several different project mounts, but one project may only be writable in one agent container at a time.

# Open Questions
Should future acceptance coverage introduce a deterministic long-running project assignment fixture so graceful release and waiting retry can be browser-tested without relying on a live model turn?
