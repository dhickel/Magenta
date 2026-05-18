# Subplan 01: Lease-Preserving Schema

## Goal

Prevent startup from dropping `workspace_leases`.

## Implementation Steps

1. Remove or gate stale `workspace_roots` creation from clean schema.
2. Replace destructive legacy migration with guarded one-time migration.
3. Preserve existing `workspace_leases` rows on warm startup.
4. Add warm fixture test reproducing prior drop risk.

## Validation

Warm DB with leases retains lease rows after startup/bootstrap.
