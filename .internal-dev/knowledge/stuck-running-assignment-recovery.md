Topic
Stuck RUNNING assignment recovery diagnostics

Source References
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRuntimeRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunnerService.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`

Key Takeaways
- A healthy lease heartbeat is not proof that execution is progressing. `last_heartbeat_at` and `last_progress_at` must be interpreted separately.
- Suspected stuck in the Agent Queue means RUNNING, heartbeat age under 5 minutes, and progress age at least 15 minutes.
- Force interrupt is an operator action, not an automatic watchdog. It sets status to INTERRUPTED, clears lease fields, and preserves resumability.
- Runner writes after acquisition should use lease-owner guarded saves for progress and terminal transitions. This prevents a stale thread from changing an interrupted assignment to COMPLETED or FAILED.
- Local future cancellation is best-effort. The durable database transition is the source of truth for cross-JVM or already-stale owners.

Engine Relevance
- Use diagnostics first when a queue item looks stuck: compare progress age, heartbeat age, lease owner/expiry, linked run ids, and recent audit events.
- Check assignment input/checkpoint/output for `taskRunId`, `planRunId`, `workflowRunId`, `jobRunId`, and `conversationId`; missing ids are an operational gap but should not block force interruption.

Open Questions
- Should task execution persist the task conversation id in assignment checkpoint before entering `executeTaskBlocking` so audit correlation is always available?

Live Evidence
- First snapshot for assignment `751ee1e4-9b0f-4a0e-888f-1188aa039c2a`: status RUNNING, lease owner `b1b025bd-d028-44cc-9f52-e929366003c0`, lease expiry `2026-05-16T00:35:48.037495078Z`, updated `2026-05-16T00:30:48.037564779Z`, started `2026-05-15T22:11:48.008878049Z`, no completed time, no error text, no checkpoint JSON, no output JSON.
- Second snapshot roughly five minutes later during implementation: status RUNNING, same lease owner, lease expiry `2026-05-16T00:41:48.037568101Z`, updated `2026-05-16T00:36:48.037652319Z`, started `2026-05-15T22:11:48.008878049Z`, no completed time, no error text, no checkpoint JSON, no output JSON.
- Assignment input was `{"inputValues":{},"taskId":"1e10405c-f1c5-490b-89ae-8997b20f0bfa"}`.
- Correlated plan/task run row: `ca9e9e93-dd24-479a-aa89-8336de0843e3`, plan id `1e10405c-f1c5-490b-89ae-8997b20f0bfa`, status RUNNING, started `2026-05-15T22:11:48.039247920Z`, no completed time, no error text.
- No workflow run or job run rows were present for the incident snapshot.
- The plan definition had no conversation id, so no audit timeline could be correlated from `audit_event`.
- Local deployed commit at inspection time was `8e26e36d7c34e487d65360607bc1f1a0936efe0a` (`8e26e36 Removed docker surface, this added complexity too early`). No environment build variable was present, so runtime diagnostics fall back to `unknown` unless `MAGENTA_BUILD_COMMIT`, `GIT_COMMIT`, `magenta.build.commit`, or `git.commit` is supplied.
