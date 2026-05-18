# Subplan 02: Path Segment ID Validation

## Context

bug-02 reports caller-supplied agent ids flowing into `agents/{agentId}` paths with only nonblank validation.

## Goal

Reject invalid ids before they can become filesystem path segments or route-owned ids.

## In Scope

- Central validation helper for ids used as path segments.
- Agent id creation/update validation.
- Workspace/service call sites that compose filesystem paths from ids.
- Tests for traversal, separators, absolute paths, and encoded variants.

## Out of Scope

- Changing persisted ids that are already valid.
- Broad id format migrations unless invalid persisted rows are discovered.

## Implementation Steps

1. Locate id-to-path composition call sites from bug-02 evidence.
2. Add a strict plain-segment validator in an existing core/service utility package.
3. Apply the validator at profile creation/update and before workspace lifecycle operations.
4. Fail fast with clear validation messages and non-2xx route responses.
5. Add unit and controller tests.

## Validation

- Valid id accepted.
- `..`, `a/b`, `a\\b`, `/abs`, encoded traversal, blank, and dot-only ids rejected.
- Existing workspace operations still pass with normal ids.

## Exit Criteria

No user-controlled id can move an operation outside its intended subtree.
