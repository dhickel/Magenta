# Summary

Task, workflow, and orchestration task runs can complete with generated placeholder outputs instead of model-backed task execution.

# Scope

Confirmed in task service, task HTTP run endpoint, workflow run service, and orchestration runner task/job paths.

# Reproduction

Create a task with required outputs and run it through `TaskService.runSynchronously()`, `/api/tasks/{taskId}/runs/stream`, `WorkflowService.runSynchronously()`, or an orchestration `TASK_RUN` assignment.

# Expected

The run should execute the task instructions through `EXECUTE_TASK` chat mode and complete only after `task_complete` records declared outputs and evidence.

# Actual

The run is completed immediately with generated values such as `0`, `true`, JSON metadata, examples, or `"Generated <output> for <task>"`.

# Evidence

- `TaskService.runSynchronously()` uses `defaultOutputs()` and `completeRun()`.
- `TaskController.streamRun()` has its own `defaultOutputs()` and completes direct task runs without chat execution.
- `WorkflowService.runSynchronously()` runs every task step through `TaskService.runSynchronously()`.
- `OrchestrationRunnerService` runs direct task assignments and task job items through `TaskService.runSynchronously()`.
- Production code does not call `TaskService.registerExecutionContext()`, which is required for `TaskService.mode()` to return `EXECUTE_TASK`.

# Impact

Users can receive completed task, workflow, job, and orchestration results that were never actually produced by an AI model or validated against task steps. Downstream workflow steps can consume these fake outputs as if they were real.

# Status

Fixed in this pass.

# Next Action

Archived after replacing placeholder task completion with model-backed task execution through chat `EXECUTE_TASK` mode.
