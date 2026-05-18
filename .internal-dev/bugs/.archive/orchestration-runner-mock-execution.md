# Bug Report: Orchestration Runner "Mock" Task Execution

**Date**: 2026-05-07
**Reporter**: Comprehensive Review Agent
**Status**: Open
**Severity**: Critical

## Description
The `OrchestrationRunnerService` currently "fakes" task completion for `TASK_RUN` assignments by calling a synchronous mock method instead of performing actual model-backed execution.

## Affected Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/task/TaskService.java`

## Evidence
In `OrchestrationRunnerService.java`, the `runTask` method calls `taskService.runSynchronously(taskId, ...)`:
```java
// ...
var result = taskService.runSynchronously(taskId, assignment.inputValues());
// ...
```
In `TaskService.java`, `runSynchronously` generates default/mock outputs based on types:
```java
// ...
// Generates default placeholder values instead of invoking AI
// ...
```

## Impact
Background jobs and orchestrated tasks do not actually perform any real work. They return placeholder data, which makes the orchestration system non-functional for real tasks.

## Steps to Reproduce
1. Create a `WorkAssignment` of type `TASK_RUN`.
2. Allow the `OrchestrationRunnerService` to process the assignment.
3. Observe that the assignment completes immediately with "mock" outputs.

## Recommended Fix
Implement a proper asynchronous path in `TaskService` that leverages the AI model (via `MagentaWorkExecutor`) and update `OrchestrationRunnerService` to use this path.
