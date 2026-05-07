# Date

2026-05-07

# Change Summary

Implemented phase 02 durable orchestration support: user-facing jobs and ordered job items, durable work assignments with priority/status/checkpoint/evidence state, inbox messages, schedules, event reactions, orchestration events, and a background runner that resumes job assignments from persisted item-boundary checkpoints.

# Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/*`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationJobController.java`
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/Magenta2Application.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationDurableRuntimeTest.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`

# Behavioral Impact

Agents can now receive durable assignments through explicit user/API creation, schedule due handling, and event reactions. Assignments expose observable priority, status, cancellation, pause/resume, checkpoint, output, evidence, and lease state. Job runs execute ordered task/workflow/message/report items and resume from the next unfinished job item after interruption. Existing direct task/workflow run APIs still accept their prior request shapes and now tolerate optional orchestration context fields.

# Risks

Execution resume is intentionally step-boundary only. Incomplete model turns or in-progress synchronous task/workflow calls are retried from the current assignment or job item boundary rather than continued mid-response. Scheduler polling is in-process and idempotence is limited to advancing `next_run_at` before enqueueing the due assignment.

# Follow-up Items

- Add browser/UI status views once a concrete operator workflow is selected.
- Extend task/workflow execution to use model/workspace context in live model-backed runs when those runs move beyond the current synchronous test implementation.
