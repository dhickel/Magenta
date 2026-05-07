# Topic

Durable orchestration queue, scheduler, events, and resume behavior

# Source References

- `.internal-dev/plans/orchestration-driver/phase-02-durable-queue-scheduler-events-resume.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationDurableRuntimeTest.java`

# Key Takeaways

The orchestration database is the source of truth for user-facing assignments. `MagentaWorkExecutor` only bounds execution; it is not the queue of record. Assignments should move through explicit durable statuses and retain checkpoint, output, evidence, lease owner, and lease expiry details.

Resume is implemented at assignment and job item boundaries. A stale `RUNNING` lease becomes `INTERRUPTED`; callers resume it by moving it back to `QUEUED`. Job execution reads `current_item_index` and `checkpoint.nextItemIndex` to skip completed items.

Schedules use Spring `CronExpression` and timezone-aware next-run calculation. The scheduler advances `next_run_at` before publishing `SCHEDULE_DUE` and enqueueing from the assignment template.

Event reactions match simple top-level payload key/value filters. The only v1 action is `ENQUEUE_ASSIGNMENT`, which creates assignments through the same assignment service path used by user/API submissions.

# Engine Relevance

Future agent tools should read and mutate orchestration state through the runtime services rather than bypassing the repository. Tool-visible queue state should expose durable assignment rows, not executor internals.

# Open Questions

- Whether schedules need stricter per-due-time idempotency keys before multi-instance deployment.
- How live model-backed task/workflow execution should consume assignment model/workspace context once synchronous placeholder execution is replaced.
