# Topic

Workflow HTMX authoring validation and error handling

# Source References

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workflow/WorkflowValidator.java`
- `src/main/resources/static/js/orchestration/workflows.js`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/04-workflow-authoring-runtime-js/validation-gate.md`

# Key Takeaways

- Workflow draft persistence and executable validation are intentionally split: draft saves use permissive structural validation, while validate/submit/run paths use strict executable graph validation.
- The active `/workflows` page is server-rendered HTMX. It should not load `workflows.js`, and workflow CRUD/validation should remain in controller fragments unless a future interaction truly needs a narrow JS island.
- `workflows.js` is currently a dormant explicit `window.MagentaWorkflowGraphComposer` utility for local graph canvas/layout/drag behavior only. It must not auto-bootstrap on `/workflows` or call `/api/workflows`.
- Failed HTMX mutations should return the relevant persisted target fragment with a visible `orch-status-error` banner. Returning a standalone tiny error fragment can make the section look like it saved successfully while hiding persisted state.
- For browser validation behind alpha Basic auth, local Playwright with `httpCredentials` may be more reliable than MCP browser navigation. Keep requests browser-origin and include `HX-Request` headers when probing HTMX server paths directly.

# Engine Relevance

This keeps workflow authoring usable for incremental graph construction while preserving the public-alpha security and HTMX ownership contract. It also gives future agents a concrete pattern for avoiding optimistic UI drift on fragment save failures.

# Open Questions

- Should workflow editor failure fragments eventually return non-2xx HTMX statuses once the operational UI error-status domain standardizes fragment error handling?
