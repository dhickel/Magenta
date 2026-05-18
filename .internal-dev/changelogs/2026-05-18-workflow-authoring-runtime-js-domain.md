# Date

2026-05-18

# Change Summary

Completed domain 04 workflow authoring/runtime/JS remediation. Workflow drafts now save incomplete intermediate graph states, executable validation rejects empty or disconnected workflows, `/workflows` remains HTMX/server-owned, the old workflow JavaScript transport is dormant graph-canvas-only utility code, and workflow editor failures render visible persisted-state error fragments.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkflowController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/main/resources/static/js/orchestration/workflows.js`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/WorkflowGraphComposerSecurityTest.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowRunnerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

Users can build approval/task workflows incrementally through server-rendered HTMX fragments. Draft saves accept partial graphs, while validate/submit/run paths require an executable nonempty graph with a valid start path. Failed workflow title/node/route edits now leave the persisted editor state visible with explicit error messaging, avoiding silent optimistic drift.

# Validation

- Focused domain tests passed with 101 tests: `/tmp/domain04-focused-tests.log`.
- Full `mvn test` passed with 524 tests: `/tmp/domain04-full-mvn-test.log`.
- `node --check src/main/resources/static/js/orchestration/workflows.js` passed: `/tmp/domain04-node-check.log`.
- Static scans confirmed `/workflows` does not load `workflows.js` and `workflows.js` has no workflow CRUD/validation transport or auto-bootstrap markers: `/tmp/domain04-static-workflows-js-load-scan.log`, `/tmp/domain04-static-js-transport-scan.log`.
- `git diff --check` passed: `/tmp/domain04-git-diff-check.log`.
- Bounded Spring startup passed with isolated SQLite: `/tmp/domain04-bounded-startup.log`.
- Browser-origin Playwright validation passed against a live app on port `18080`: `/tmp/domain04-browser-evidence.json`, `/tmp/domain04-network.json`, `/tmp/domain04-console.json`, `/tmp/domain04-live-app-validation.log`.

# Risks

The domain validator used local Playwright with `httpCredentials` after MCP browser Basic-auth navigation was blocked. Some browser CRUD probes used browser-origin HTMX-style `fetch` with `HX-Request` headers for deterministic server path coverage rather than only pointer interactions.

# Follow-up Items

None for this domain. Legacy workflow cleanup remains owned by domain 08.
