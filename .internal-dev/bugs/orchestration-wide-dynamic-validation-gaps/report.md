# Summary

Wide Playwright MCP validation found two additional orchestration validation defects after the initial context and scheduler remediation.

# Scope

Applies to final orchestration acceptance validation for agent profile API error handling and job step-boundary waiting behavior.

# Reproduction

1. Start the app on a fresh SQLite database and open `/chat` through Playwright MCP.
2. From the browser origin, create an agent with `approvedTools: ["missing_tool"]`.
3. Observe the API returns 500 because the unknown tool name is raised as `IllegalStateException`.
4. Create a job containing `TASK_RUN`, `REPORT`, and `WAIT_FOR_MESSAGE` items.
5. Enqueue, pause, resume, and let the runner process the job.
6. Observe the job assignment fails with `WAIT_FOR_MESSAGE pauses job execution` even though final validation expects a persisted waiting checkpoint.

# Expected

Invalid approved tool names are client validation failures. Job execution pauses at `WAIT_FOR_MESSAGE` with a durable `WAITING` assignment and checkpoint state that survives reload.

# Actual

Invalid approved tool names produced HTTP 500. `WAIT_FOR_MESSAGE` inside a job threw through the runner and marked the job assignment `FAILED`.

# Evidence

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- Playwright MCP wide dynamic validation on 2026-05-07 against `http://localhost:18080` with `/tmp/magenta2-orchestration-wide.sqlite`.

# Impact

Final validation could not pass the wide dynamic browser pass until both issues were fixed.

# Status

Fixed on 2026-05-07.

# Resolution

- Changed unknown approved tool names to throw `IllegalArgumentException`, allowing agent profile create/update requests to return 400.
- Added `ChatToolRegistryTest` coverage for unknown approved tool names.
- Changed the job runner to persist `WAITING` at `WAIT_FOR_MESSAGE` boundaries with `waitingItemId`, `nextItemIndex`, output, and evidence.
- Added `OrchestrationDurableRuntimeTest` coverage for `WAIT_FOR_MESSAGE` checkpoint behavior.

# Validation Evidence

- `mvn test -Dtest=ChatToolRegistryTest`: passed, 6 tests.
- `mvn test -Dtest=OrchestrationDurableRuntimeTest,ChatToolRegistryTest`: passed, 12 tests.
- Playwright MCP wide dynamic validation: passed; verified chat load, htmx route, agent CRUD/clone/disable, agent detail tabs, side-panel hosts, task/workflow context runs, job pause/resume to `WAITING`, checkpoint reload, cancel, schedule persistence, and no unexpected console or failed network events.
- `mvn test`: passed, 167 tests, 0 failures, 0 errors.
- Startup smoke: passed; Spring Boot started on random port `43077` with `/tmp/magenta2-orchestration-final-smoke.sqlite` and was stopped by the bounded timeout.
