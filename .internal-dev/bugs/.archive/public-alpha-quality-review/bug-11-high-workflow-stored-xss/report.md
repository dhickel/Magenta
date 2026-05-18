# Workflow Graph Composer Stored XSS Risk

## Summary

Workflow graph composer interpolates persisted node values into raw `innerHTML`.

## Scope

`src/main/resources/static/js/orchestration/workflows.js` and `/workflows`.

## Reproduction

1. Save a workflow/node label containing HTML/script-like markup.
2. Reopen `/workflows`.
3. The graph composer renders persisted values through `innerHTML`.

## Expected

User-editable persisted values are escaped or inserted as text nodes.

## Actual

Original review finding: node label/key/type and editable fields were interpolated into template strings assigned to `innerHTML`. The current workspace implementation has replaced those render sites with DOM text/value assignment and is pending validation subagent proof.

## Evidence

- `WorkflowController.java:45` and `WorkflowController.java:54` create/update workflow definitions.
- `workflows.js:132` loads persisted definitions.
- `workflows.js:504` saves graph data.
- 2026-05-18 implementation update: `workflows.js` graph cards now build header/type/key elements with `textContent`, and selected-node side-panel fields assign values through DOM input/textarea `.value` rather than raw HTML attributes/body text.
- 2026-05-18 regression coverage: `WorkflowGraphComposerSecurityTest` checks script-like workflow payload coverage by preventing graph/side-panel `innerHTML` use and raw node-field template reintroduction; focused Maven and `node --check` passed.

## Impact

High/security: persistent XSS on an operator/admin surface.

## Status

Fixed in workspace; pending validation subagent browser proof.

## Next Action

Run focused validation subagent checks for `/workflows` graph rendering with script-like persisted node label/key/type/editable fields, then mark bug-11 passed and commit subplan 03.
