# Subplan 05: Schedule and Reaction Template Validation

## Goal

Reject invalid assignment templates when schedules or reactions are saved.

## Implementation Steps

1. Reuse the same assignment type/parser used at runtime.
2. Validate assignment type and required saved-definition references at save time.
3. Return operator-visible validation errors.
4. Add service/controller tests for invalid and valid templates.

## Validation

Bad persisted templates cannot be saved and polling/event handling no longer encounters those invalid records.
