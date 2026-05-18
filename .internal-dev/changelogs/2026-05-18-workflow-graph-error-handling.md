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

# Risks

Focused controller coverage exercises title, node, route, and validation failure fragments. Browser validation for the live workflow editor remains pending with the parent validation agent.

# Follow-up Items

Run the domain validation gate, including focused Playwright proof against the live `/workflows` editor.
