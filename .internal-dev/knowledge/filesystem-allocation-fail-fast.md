# Topic

Filesystem allocation fail-fast for plan/task run startup

# Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `.internal-dev/plans/public-alpha-remediation/02-workspace-tools-outputs/subplan-05-filesystem-allocation-fail-fast.md`

# Key Takeaways

- When `WorkspaceDirectoryService` exists, temp workspace, output directory, and assignment project link materialization are required startup resources for filesystem-backed execution.
- Allocation failure should create a persisted terminal `FAILED` run with a clear operator message in `errorText` and execution evidence.
- Chat execution callers must check the startup run status before invoking model execution, because `startRun` can now return a non-running terminal startup failure.
- Legacy tests and deployments without `WorkspaceDirectoryService` still use the older no-allocation path.

# Engine Relevance

This pattern keeps allocation faults close to the startup boundary and prevents confusing downstream model/tool behavior with null workspace or output paths.

# Open Questions

- Parent validation should decide whether additional UI copy is needed beyond the existing run status, linked-run error text, and execution evidence surfaces.
