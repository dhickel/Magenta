# Date

2026-05-18

# Change Summary

Narrowed the stale workflow graph JavaScript island so active workflow authoring remains HTMX/server-owned. `workflows.js` now exports an inactive graph canvas utility only; it no longer auto-mounts on `/workflows` and no longer performs workflow CRUD or validation transport.

# Files

- `src/main/resources/static/js/orchestration/workflows.js`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

The live `/workflows` page continues to use server-rendered HTMX fragments for workflow list, draft creation, node/route CRUD, validation, submit-to-agent, delete, and run history. The remaining workflow JavaScript is not loaded by the page and is limited to local graph canvas/layout/drag behavior for explicit future use.

# Validation

- `mvn -Dtest=OrchestrationControllerTest,WorkflowGraphComposerSecurityTest test` passed with 76 tests.
- `node --check src/main/resources/static/js/orchestration/workflows.js` passed.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` on port `46011`.
- Browser-origin `/workflows` validation confirmed the page does not request `workflows.js`, does not show Graph Composer text, and can create a draft through HTMX without console errors, page errors, stale asset 404s, 4xx/5xx responses, or server `ERROR` output.

# Risks

Browser proof covered page load and draft creation only, per the focused target. It did not re-run the full approval workflow authoring path from earlier subplans.

# Follow-up Items

None.
