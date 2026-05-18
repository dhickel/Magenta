# Subplan 04: Orphan Schema Cleanup

## Goal

Resolve likely orphan `job_work_items` schema baggage.

## Implementation Steps

1. Search all code/tests/docs for `job_work_items`.
2. If unused, remove from clean schema and add migration/no-op compatibility note for warm DBs.
3. If used indirectly, document owner and add coverage.

## Validation

Schema contains no unexplained orphan execution tables.
