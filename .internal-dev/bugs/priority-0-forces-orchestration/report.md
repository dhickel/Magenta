# Summary
The task and workflow run UIs always send `priority=0`, which makes `OrchestrationRunContext.hasContext()` return true, forcing all runs through the orchestration codepath. Chat-backed task execution is unreachable from the UI because `priority` can never be null once the form is submitted.

# Scope
- `src/main/java/io/mindspice/magenta2/api/web/TaskController.java` — `TaskRunRequest` and `streamRun`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java` — `WorkflowRunRequest` and `streamRun`
- `src/main/resources/static/js/orchestration/app.js` (tasks page inline script in `FrontendController.java`) — `run-task` handler
- `src/main/resources/static/js/orchestration/app.js` (workflows page inline script in `FrontendController.java`) — `run-workflow` handler
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationRunContext.java` — `hasContext()`

# Reproduction
1. Start the app, navigate to `/tasks`.
2. Create or select a task with typed inputs.
3. Fill the runtime input value(s).
4. Leave `priority` at its default value (`0`).
5. Click Run.
6. Observe SSE log: `event:failed data:{"error":"agentId is required for orchestration-context runs"}`.

The same flow on `/workflows` with no agent ID produces the same outcome, or in the synchronous path fails with missing inputs because initial inputs are never supplied to the first step.

# Expected
- When no orchestration fields are filled (agentId, jobId, workspaceId, modelOverride are empty, priority reflects "no value"), the request should route through the chat-backed task execution path.
- The chat path should work end-to-end from the UI without requiring an agentId.

# Actual
- The JS `run-task` handler computes `priority: Number(document.getElementById('task-run-priority').value || 0)`, which coerces an empty string to `0`.
- `OrchestrationRunContext.hasContext()` treats `priority != null` as true, routing to orchestration. Orchestration then rejects the request because no `agentId` is present.
- There is no UI affordance to omit priority from the request body to reach the chat execution path.

# Evidence
- Live Playwright MCP test on 2026-05-08 against `http://localhost:18080`:
  - Task "Web Page Analyzer" (id `79080345-d748-4170-a210-1f661582a5ee`) run via UI button with priority=`0` and no agentId produced `event:failed data:{"error":"agentId is required for orchestration-context runs"}`.
  - Direct `page.evaluate` fetch with `body: JSON.stringify({ inputValues: { url: '...' } })` (no orchestration fields) successfully ran through chat execution, fetched example.com via `web_fetch`, and completed.
- In `OrchestrationRunContext` (line 20): `priority != null` — the only non-String field and the hardest for the client to send as null.

# Impact
- Chat-backed task execution is unreachable from the task and workflow run UIs.
- Users must construct API calls outside the UI (or use browser dev tools) to run tasks without an agent configured.
- The workflow synchronous path is blocked from accepting initial inputs for the first step, causing downstream binding resolution to fail.

# Status
Open.

# Next Action
1. Change the client JS to send `priority: null` when the input is empty/unset (`Number(val || undefined)` or omit the key entirely).
2. Decide whether `priority=0` should still mean "no orchestration context." If so, update `OrchestrationRunContext.hasContext()` to treat `priority == null || priority == 0` as no priority. Alternatively, change `TaskRunRequest.priority` to `Integer` and exclude `0` from `hasContext`.
3. Consider adding initial input support to `WorkflowService.runSynchronously` so the first step can receive runtime values provided by the caller.
