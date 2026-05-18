# Subplan 03: HTMX Error Statuses

## Goal

Return meaningful non-2xx status for failed HTMX mutations while rendering helpful fragments.

## Implementation Steps

1. Inventory broad catch-and-return-200 handlers from bug-20.
2. Preserve HTMX fragment body but set appropriate status.
3. Route unexpected exceptions through central logging where possible.
4. Add tests for failed shell exec, hard delete, or settings save paths.

## Validation

Automation no longer sees failed mutations as successful 200 responses.
