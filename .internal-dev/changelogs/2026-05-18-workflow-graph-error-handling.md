# Date

2026-05-18

# Change Summary

Workflow editor HTMX failure paths now keep the relevant persisted editor state visible with an explicit error banner when title, node, or route persistence fails. Workflow validation exceptions now render in the validation result fragment instead of replacing the target with an unrelated standalone status.

# Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

Failed workflow graph/editor saves no longer look like successful section swaps. The affected HTMX target shows persisted/current workflow data plus a visible failure message, preventing optimistic state drift after server-side persistence or validation failures.

# Validation

- `mvn -Dtest=OrchestrationControllerTest,WorkflowGraphComposerSecurityTest test` passed with 80 tests.
- `node --check src/main/resources/static/js/orchestration/workflows.js` passed.
- `git diff --check` passed.
- Static scans confirmed `/workflows` does not load `workflows.js`, and `workflows.js` has no workflow CRUD/validation transport or workflows-page bootstrap selector.
- Browser-origin validation on live port `18080` confirmed HTMX draft creation/editor rendering, no `workflows.js` request, empty draft validate/submit errors, visible node/route failure banners, validation exception rendering inside a warnings fragment, escaped XSS probe values, and clean console/server logs.

# Risks

Focused controller coverage simulates actual persistence outages. Live browser negative probes covered invalid node/route server failures, but did not force a live SQLite write outage.

# Follow-up Items

Run the domain validation gate, including approval workflow build proof.
