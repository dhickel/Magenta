# Scope

Reviewed `.internal-dev/plans/robustness-fixes/TECHNICAL_REPORT.md` against current task, workflow, orchestration, controller, and test code.

# Findings

1. Critical: task execution paths complete with generated placeholders instead of model-backed work.
   - `TaskService.runSynchronously()` starts a run, calls `defaultOutputs()`, and immediately completes it with `"Generated declared outputs."`
   - `TaskController.streamRun()` does the same for direct HTTP task runs.
   - `OrchestrationRunnerService` uses `TaskService.runSynchronously()` for direct task assignments and job task items.
   - `WorkflowService.runSynchronously()` uses `TaskService.runSynchronously()` for every step, so workflows inherit placeholder outputs.

2. Critical: `EXECUTE_TASK` mode is effectively test-only unless some external caller manually invokes `registerExecutionContext()`.
   - `TaskService.mode()` enters `EXECUTE_TASK` only when `executionRunsByConversationId` contains the conversation id.
   - Production call-site search found no caller of `registerExecutionContext()`.
   - Tests call it directly, proving the mechanism works once injected but not proving any production entry point reaches it.

3. Major: task and orchestration tests pass because they assert plumbing, not semantic execution.
   - `TaskServiceTest` manually calls `registerExecutionContext()` and directly calls `completeRun()` with test output values.
   - `WorkflowServiceTest` asserts completed status and output key propagation, which placeholder outputs satisfy.
   - `OrchestrationDurableRuntimeTest` asserts completed assignments, run ids, and output map presence, which placeholder task runs satisfy.

4. Major: the browser task/workflow run buttons currently hit the placeholder run endpoints.
   - `/api/tasks/{taskId}/runs/stream` returns SSE text from generated outputs.
   - `/api/workflows/{workflowId}/runs/stream` runs workflows whose task steps use generated outputs.

5. Major: event reaction handling is non-transactional.
   - `OrchestrationEventService.publish()` saves an event, calls `handle()`, and `handle()` creates assignments before saving `handledAt`.
   - A failure partway through reaction handling can leave committed assignments and an unhandled event.

6. Major: job item execution has no item retry or continue-on-failure policy.
   - `OrchestrationRunnerService.runJob()` invokes each item once and lets any item exception fail the assignment.
   - `OrchestrationJobItem` has no retry or continue-on-failure fields.

# Risk Assessment

The application can report task, workflow, job, and orchestration task runs as completed even though no AI model executed the task instructions. This is user-visible because persisted run records and SSE responses contain plausible-looking outputs. Downstream workflows can compound the problem by feeding generated outputs into later steps.

The current tests are useful for persistence and state-transition plumbing, but they do not protect against fake execution.

# Recommendations

1. Replace direct task run completion paths with a real task execution entry point that creates a run and registers the chat execution context.
2. Persist conversation-to-task-run execution context instead of relying only on an in-memory map.
3. Add tests that fail unless a task run reaches completion through `EXECUTE_TASK` chat mode and a `task_complete` tool call.
4. Make placeholder synchronous task execution explicit as unsupported or internal-only until a headless model-backed runner exists.
5. Add transactional boundaries around event publish/reaction handling.
6. Add explicit retry and continue-on-failure semantics for orchestration job items.

# Follow-ups

- Bug record: `.internal-dev/bugs/task-run-placeholder-execution/report.md`
- Bug record: `.internal-dev/bugs/orchestration-event-partial-side-effects/report.md`
- Bug record: `.internal-dev/bugs/orchestration-job-items-no-retry-policy/report.md`
- Knowledge note: `.internal-dev/knowledge/task-execution-placeholder-test-gap.md`
