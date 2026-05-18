# Subplan 01: Draft Editing Validation Split

## Goal

Allow incomplete workflow drafts to be saved while reserving strict validation for validate/submit/run.

## Implementation Steps

1. Split draft structural persistence from executable validation in service/controller paths.
2. Allow node add/update with missing future routes or inputs.
3. Add condition editing for approval/control routes.
4. Add tests for incrementally creating an approval workflow.

## Validation

Browser and service tests prove intermediate states save and final valid graph validates.
