# Subplan 03: Stale Doc and Comment Cleanup

## Goal

Remove misleading Docker/Podman comments and docs where filesystem-backed runtime is authoritative.

## Implementation Steps

1. Scan active docs/config/comments for stale runtime wording.
2. Update text to filesystem-backed runtime.
3. Preserve historical review artifacts unchanged.
4. Link to current runtime contract where useful.

## Validation

Search confirms active docs/comments no longer imply Docker is the current execution environment for this campaign.
