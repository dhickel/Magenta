# Mobile Orchestration Shell Is Unusable at Phone Width

## Summary

At phone width, the SimplyPages sidebar grid leaves the content area around 70-100px wide.

## Scope

Public orchestration shell pages, especially `/agents/{agentId}`.

## Reproduction

1. Start the app.
2. Open `/agents/{agentId}` at `390x780`.
3. Inspect content width.

## Expected

Mobile layout should show usable content with sidebar collapsed/overlayed or one-column layout.

## Actual

Playwright measured `#content-area` width `70px`, `.content-wrapper` width `100px`, and grid template `250px 100px`.

## Evidence

- Playwright evidence route: `/agents/826bd773-38e0-4de1-8d34-0c9c3565ef25`.
- `OrchestrationController.java:184` enables collapsible sidebar.
- SimplyPages `.main-container.has-sidebar` specificity overrides mobile one-column grid in the framework CSS.

## Impact

High: public-alpha operational UI is effectively unusable on phones.

## Status

Open.

## Next Action

Patch Magenta/SimplyPages mobile sidebar specificity and add Playwright mobile viewport checks.
