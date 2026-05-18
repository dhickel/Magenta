# Workflow Validation Gate

## Validator Instructions

Read workflow/frontend/security review files and bug reports 03, 04, and 11 before validating.

## Required Checks

- Approval workflow can be built incrementally.
- Empty workflow cannot submit/run.
- CRUD/validation use HTMX/server paths where practical.
- Graph JS failures are visible.
- XSS security validation from domain 01 is not regressed.
- Focused tests, full `mvn test`, bounded startup, and focused Playwright pass.
