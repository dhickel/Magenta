# Validation Harness Gate

## Validator Instructions

Read test harness review files and bug-17 before validating.

## Required Checks

- New Spring web/context tests run and cover listed route groups.
- Playwright harness runs against live app for focused specs.
- SQLite foreign keys enabled in relevant fixtures.
- Schedule/reaction parity covered.
- Regression tests exist for prior missed blocker classes.
- Full `mvn test`, focused Playwright, clean/warm startup pass.
