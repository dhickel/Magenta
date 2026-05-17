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

Node label/key/type and editable fields are interpolated into template strings assigned to `innerHTML`.

## Evidence

- `WorkflowController.java:45` and `WorkflowController.java:54` create/update workflow definitions.
- `workflows.js:132` loads persisted definitions.
- `workflows.js:338` writes node values into `card.innerHTML`.
- `workflows.js:387` writes node fields into side-panel HTML.
- `workflows.js:504` saves graph data.

## Impact

High/security: persistent XSS on an operator/admin surface.

## Status

Open.

## Next Action

Escape all persisted text or replace template strings with DOM text-node construction; add Playwright/security regression coverage.
