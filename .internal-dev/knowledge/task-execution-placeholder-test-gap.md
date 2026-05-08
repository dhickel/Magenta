# Topic

Task execution placeholder behavior and test coverage gap

# Source References

- `TaskService.runSynchronously()`
- `TaskController.streamRun()`
- `WorkflowService.runSynchronously()`
- `OrchestrationRunnerService.runTask()` and `runJobItem()`
- `TaskServiceTest`
- `WorkflowServiceTest`
- `OrchestrationDurableRuntimeTest`

# Key Takeaways

`EXECUTE_TASK` mode depends on `TaskService.registerExecutionContext()`. Current production run paths do not call it, while tests call it directly or assert only completed status/output-map shape. This lets generated placeholder outputs satisfy the task, workflow, and orchestration test suite.

The fixed production path starts a task run through `TaskService.startChatExecution()`, stores the active run id on chat session metadata, and drives execution through `ChatService` with `EXECUTE_TASK` mode. A run is `COMPLETED` only when `task_complete` accepts declared output values. If the model returns final text after repair retries without `task_complete`, the run is marked `NEEDS_REVIEW`; thrown execution errors mark it `FAILED`.

For future task execution tests, a passing test should prove at least one of these:

- a user-facing task run enters `PlanMode.EXECUTE_TASK`;
- the model/tool loop receives task execution instructions;
- `task_complete` is invoked through the tool path;
- the completed output value comes from the simulated tool/model path, not from `defaultOutputs()`;
- the execution context is cleared only after successful completion.

# Engine Relevance

Task runs are a central reusable-work primitive. Tests that only verify persistence and map propagation are insufficient for this domain because fake outputs look structurally valid.

# Open Questions

Direct synchronous task execution is now unsupported as a production shortcut. Blocking callers must use the chat-backed task execution path.
