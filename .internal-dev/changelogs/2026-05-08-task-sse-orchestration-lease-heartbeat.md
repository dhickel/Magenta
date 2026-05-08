# Task SSE And Orchestration Lease Heartbeat

## Date
2026-05-08

## Change Summary
- Made task run SSE streaming return immediately by subscribing to task execution on a bounded worker scheduler.
- Preserved existing task stream event names and payload shapes for started, progress/tool, completed, and failed events.
- Added configurable orchestration lease and heartbeat intervals with defaults of 300 seconds and 60 seconds.
- Added guarded lease extension that only updates assignments still `RUNNING` under the current runner owner.
- Wrapped assignment execution in a heartbeat lifecycle that stops when execution completes, fails, cancels, waits, or exits.

## Files
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/test/java/io/mindspice/magenta2/api/web/TaskControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationDurableRuntimeTest.java`

## Behavioral Impact
Task run stream requests no longer hold the servlet request thread for the full model-backed execution. Long-running orchestration assignments refresh their lease while the owning runner is still active, reducing false stale recovery for valid work.

## Risks
Heartbeat timing is intentionally coarse and single-node oriented. Distributed runner fencing remains deferred.

## Follow-up Items
- Revisit formal migration tooling before adding more orchestration schema compatibility checks.
- Consider distributed fencing tokens if multiple hosts run orchestration workers against shared storage.
