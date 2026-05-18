# Subplan 03: JS Island Narrowing

## Goal

Keep graph drag/layout as the JavaScript island while restoring CRUD and validation to HTMX/server-owned flows where practical.

## Implementation Steps

1. Identify fetch-based CRUD in `workflows.js`.
2. Replace standard create/update/delete/validate actions with HTMX endpoints or server fragments.
3. Keep JavaScript only for graph canvas/dragging and request orchestration that is clearly simpler.
4. Document any remaining JS justification in code or plan closeout.

## Validation

Review active workflow editor actions and prove CRUD/validation no longer depend on raw JS transport where HTMX fits.
