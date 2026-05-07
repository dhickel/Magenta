# Topic

Orchestration final-validation remediation for context-bearing task/workflow runs and idempotent schedule due handling.

# Source References

- `.internal-dev/plans/orchestration-validation-remediation/phase-01-context-and-schedule-idempotency.md`
- `.internal-dev/bugs/orchestration-final-validation-context-and-schedule-gaps/report.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`

# Key Takeaways

Context-bearing task/workflow HTTP runs should not extend the legacy task/workflow records ad hoc. The durable orchestration boundary is `WorkAssignment`, so requests with agent, job, workspace, model, or priority context should create `TASK_RUN` or `WORKFLOW_RUN` assignments and execute through `OrchestrationRunnerService`.

Legacy task/workflow runs still matter for chat and simple editor behavior. Preserve that path when no orchestration context is supplied.

Schedule idempotency belongs in durable storage. The runtime records `schedule_firings` with a unique `schedule_id + due_at` key before creating the scheduled assignment, which gives repeated polls and concurrent pollers a concrete duplicate guard.

# Engine Relevance

The orchestration engine now has a clear split:

- No context: run through the existing task/workflow services directly.
- Orchestration context present: run through durable assignments so job, workspace, model, and priority are persisted and observable.

For future trigger paths, prefer creating assignments at explicit boundaries and make each external or timed trigger carry a durable source key before enqueueing work.

Wide Playwright MCP validation should assert collapsed side-panel hosts by DOM attachment, not visibility. The orchestration agent chat panel starts collapsed/hidden on agent, task, workflow, and job pages.

Agent detail tabs are rendered through a single dynamic `#agent-tab-panel`; the page does not keep separate static panel nodes for inbox, queue, schedules, reactions, workspace, or history.

Task field type payloads use `TaskValueType` wire values such as `string`, `long_text`, `number`, `boolean`, and `json`. Browser/API validation fixtures should not use UI-ish labels such as `TEXT`.

`WAIT_FOR_MESSAGE` job items are step-boundary waits. The runner should persist `WAITING` and keep `currentItemIndex` on the waiting item rather than treating the item as a failed unsupported operation.

Unknown approved tool names are user/API validation errors, not runtime invariants. Throw `IllegalArgumentException` so controller paths can return 400.

# Open Questions

- Should task and workflow definitions eventually gain their own default model field so the documented task/workflow default precedence layer can be fully implemented?
- Should schedule firing rows record event IDs as well as assignment IDs if event replay or audit views become user-facing?
- What message-correlation API should advance a `WAIT_FOR_MESSAGE` assignment past the waiting item after the awaited condition is satisfied?
