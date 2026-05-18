# Workflow Graph XSS-Safe Rendering

## Topic

Stored-XSS-safe rendering for workflow graph composer node data.

## Source References

- `.internal-dev/plans/public-alpha-remediation/01-security-access-control/subplan-03-workflow-xss-security.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-11-high-workflow-stored-xss/report.md`
- `src/main/resources/static/js/orchestration/workflows.js`
- `src/test/java/io/mindspice/magenta2/api/web/WorkflowGraphComposerSecurityTest.java`

## Key Takeaways

- Persisted workflow node values must not be interpolated into `innerHTML`, including node `label`, `key`, `type`, `planId`, `messageTemplate`, `config`, `inputPorts`, or `outputPorts`.
- Graph card text should be assigned through `textContent`.
- Side-panel form values should be assigned through input/textarea `.value`, not HTML `value="..."` attributes or textarea body interpolation.
- Static trusted composer shell templates are acceptable only when they do not include persisted or user-editable values.
- Focused regression coverage can guard the JS source by asserting graph/side-panel render methods do not use `innerHTML` and that old raw node-field templates do not return.

## Engine Relevance

When future workflow UI work touches `workflows.js`, keep the graph card and selected-node side panel on DOM construction or an equivalent escaping helper. Domain 04 can redesign the workflow builder, but it should preserve this XSS boundary rather than reintroducing raw template interpolation for saved graph data.

## Open Questions

- Focused browser/Playwright validation still needs to prove the live `/workflows` page displays script-like persisted values as inert text/form values.
