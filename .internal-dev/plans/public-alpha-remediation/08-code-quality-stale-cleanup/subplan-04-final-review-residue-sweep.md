# Subplan 04: Final Review Residue Sweep

## Goal

After functional domains land, sweep for residual review findings that were partially addressed but left stale artifacts.

## Implementation Steps

1. Re-read `finding-inventory.md` and all domain progress rows.
2. Run targeted `rg` scans for stale ids, direct-run references, Docker labels, legacy workflow imports, and orphan schema names.
3. File any newly discovered out-of-scope bug immediately.
4. Update `no-action-registry.md` only for explicitly ruled-out residue.

## Validation

Coverage sweep shows no unplanned addressable review residue remains.
