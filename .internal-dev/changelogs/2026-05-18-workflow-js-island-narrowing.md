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

# Risks

No active workflow CRUD path should depend on `workflows.js`; parent validation should still run the focused browser-origin `/workflows` pass to confirm the rendered page remains HTMX-first.

# Follow-up Items

Parent validation for ro-04 should confirm `/workflows` does not load `workflows.js` and that standard workflow CRUD/validation still works through HTMX.
