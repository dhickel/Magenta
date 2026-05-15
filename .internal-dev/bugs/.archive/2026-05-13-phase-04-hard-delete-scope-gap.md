# Phase 04 Bug: Hard-delete data-surface scope gap

## Summary
Hard-delete currently removes profile, agent workspace records, active leases, and filesystem data, but does not yet provide a full purge contract for all historical orchestration records (for example inbox/history/job ownership references).

## Impact
Potential residual historical references can remain queryable after hard-delete depending on downstream views.

## Recommended fix
Define a complete hard-delete policy for orchestration tables and implement coordinated cleanup with explicit user-facing warnings.

## Classification
Out-of-scope for Phase 04 implementation pass; tracked for follow-up.
