## Date

2026-05-16

## Change Summary

Added guarded hard delete for agent assignments and a read-only live audit transcript panel on the agent queue tab. Non-running assignments can be deleted through REST or HTMX; running and cancel-requested assignments are rejected by the service layer. The queue now defaults the transcript to the current running assignment or the newest active assignment and lets operators switch watched assignments with a Watch row action.

## Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AuditRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowExecutionObserver.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunner.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AssignmentAuditTranscriptRenderer.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/css/orchestration.css`
- `src/test/java/io/mindspice/magenta2/ai/chat/repository/AuditRepositoryTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

## Behavioral Impact

Operators can remove stale queued, waiting, paused, interrupted, cancelled, failed, needs-review, and completed assignments from an agent queue without deleting linked task, workflow, job, or audit rows. The live transcript panel renders audit events as chat-like bubbles for assistant output, tool calls, thinking metadata, context, compaction, user, and error events.

Model-backed task execution now records active conversation ids into assignment checkpoints before entering the blocking chat task call. Job and workflow execution preserve conversation ids in checkpoint/output data so completed assignments can still display their audit transcript.

## Risks

Workflow node conversation capture is intentionally lightweight and records task-node conversation ids through an observer callback. It does not make workflow node outputs carry audit metadata.

## Follow-up Items

No feature follow-up items were deferred for this change.
