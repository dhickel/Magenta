# Scope

Validation review for the remediation of final orchestration blockers recorded in `.internal-dev/bugs/orchestration-final-validation-context-and-schedule-gaps/report.md`.

Reviewed the implemented changes for task/workflow context propagation, schedule due idempotency, focused tests, startup behavior, and browser validation.

Updated after the full wide Playwright MCP pass on 2026-05-07, which found and verified fixes for approved-tool validation status handling and `WAIT_FOR_MESSAGE` job checkpoint behavior.

# Findings

No unresolved blocking findings remain for the two remediated issues.

No unresolved blocking findings remain after the wider dynamic validation pass.

Task and workflow context-bearing run requests now create durable assignments and execute through the orchestration runner. The supplied or inferred agent, job, workspace, model override, and priority are persisted on `WorkAssignment` records and surfaced in SSE payloads through assignment/run identifiers.

Schedule polling now creates a durable `schedule_firings` row keyed by `schedule_id + due_at` before assignment creation. Repeated polling of the same due instant no longer creates duplicate scheduled assignments.

Unknown approved tool names now return a client validation error path instead of a server error path.

`WAIT_FOR_MESSAGE` job items now persist `WAITING` at the step boundary with checkpoint/output/evidence retained. The wide browser pass verified the checkpoint state after reloading the job run.

# Risk Assessment

The implementation is scoped to the existing orchestration runtime and preserves legacy task/workflow behavior when no orchestration context is present.

The schedule idempotency guard covers duplicate due processing. It relies on repository-owned DDL, which is already the established convention for the orchestration runtime package.

The documented task/workflow default model precedence layer remains limited by the current task/workflow definitions, which do not expose a default model field. Existing explicit request, job, agent, and runtime defaults remain covered by the durable assignment model.

# Recommendations

Keep the remediation plan active until the user accepts final validation, then archive it with the related fixed bug artifact.

If task/workflow default model settings are added later, wire them into `AssignmentService.resolveModel` or the orchestration run creation path with focused precedence tests.

# Follow-ups

Validation completed:

- `mvn test`: passed, 167 tests, 0 failures.
- Startup smoke: passed; app started on random port `42303` against `/tmp/magenta2-orchestration-remediation.sqlite`.
- Playwright MCP remediation probe: passed against `http://localhost:18080/chat`.
- Existing live browser validation script: passed against `http://localhost:18080`.
- Post-wide-validation startup smoke: passed; app started on random port `43077` against `/tmp/magenta2-orchestration-final-smoke.sqlite`.
- Playwright MCP wide dynamic pass: passed against `http://localhost:18080`; covered chat load, htmx compatibility, agent CRUD/clone/disable, agent detail tabs, side-panel hosts, task/workflow context runs, job pause/resume to `WAITING`, checkpoint reload, cancellation, schedule persistence, and console/network failure capture.
