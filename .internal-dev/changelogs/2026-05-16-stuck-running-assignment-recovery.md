Date
2026-05-16

Change Summary
Implemented operator-controlled recovery for assignments that remain RUNNING while their lease heartbeat is still healthy but execution progress has stopped.

Files
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/WorkAssignment.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/schema.sql`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

Behavioral Impact
- `work_assignments` now tracks `last_progress_at` separately from `last_heartbeat_at`.
- Heartbeat extension updates heartbeat time only; status/checkpoint/output/error changes update progress time.
- RUNNING assignments with recent heartbeat but 15+ minutes without progress surface as suspected stuck in the Agent Queue UI.
- Operators can open HTMX diagnostics for a queue row and force-interrupt RUNNING assignments.
- Force interrupt transitions RUNNING or CANCEL_REQUESTED assignments to INTERRUPTED, clears lease fields, records the operator reason, and leaves the assignment resumable.
- Runner terminal/progress saves are lease-owner guarded so a stale local runner cannot overwrite an operator interrupt with COMPLETED or FAILED.
- The runner tracks local background futures and cancels the local future when force-interrupt is invoked in the owning JVM.

Risks
- Force interrupt cannot guarantee token-level or model-provider cancellation; it clears Magenta ownership and interrupts the local Java future when local ownership is present.
- Linked run diagnostics are best-effort and depend on ids already present in assignment input/checkpoint/output fields.

Follow-up Items
- None.
