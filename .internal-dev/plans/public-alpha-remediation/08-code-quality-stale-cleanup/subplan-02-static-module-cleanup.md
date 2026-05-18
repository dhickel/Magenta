# Subplan 02: Static Module Cleanup

## Goal

Remove or quarantine stale static JS modules with obsolete direct-run/workflow/inbox/output behavior.

## Implementation Steps

1. Confirm current page imports for `magenta-tools.js`, inbox JS, and outputs JS.
2. Delete unused modules or move behavior into active HTMX fragments if still needed.
3. Remove references to stale direct-run routes.
4. Browser-check public pages for missing asset errors.

## Validation

No active page loads deleted assets and no stale direct-run JS remains in active modules.
