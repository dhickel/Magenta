# Subplan 04: Regression Gap Tests

## Goal

Add targeted regression tests for blockers that passed the old suite.

## Implementation Steps

1. Add tests for direct-run route contract, workflow XSS, empty workflow submit, schema workspace-root migration, and agent queue ownership.
2. Prefer focused tests close to the changed code.
3. Avoid brittle string-only assertions when DOM/HTMX target behavior matters.

## Validation

Each previously missed blocker has at least one failing-before/fixed-after regression.
