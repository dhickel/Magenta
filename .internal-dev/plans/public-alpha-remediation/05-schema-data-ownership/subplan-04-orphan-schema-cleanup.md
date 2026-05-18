# Subplan 04: Orphan Schema Cleanup

## Goal

Resolve likely orphan `job_work_items` schema baggage.

## Status

Implemented. `job_work_items` was found only in clean `schema.sql`, with no production repository, service, controller, or test reader/writer. Clean schema no longer creates the orphan table. No destructive warm-DB drop was added because no current owner or migration path requires deleting a pre-existing local table.

## Implementation Steps

1. Search all code/tests/docs for `job_work_items`.
2. If unused, remove from clean schema and add migration/no-op compatibility note for warm DBs.
3. If used indirectly, document owner and add coverage.

## Validation

Schema contains no unexplained orphan execution tables.

Implementation checks:

- `rg -n "job_work_items" . src docs pom.xml README.md` found only the clean schema definition before removal.
- `WorkspaceRepositorySchemaMigrationTest.schemaSqlDoesNotCreateOrphanJobWorkItemsTable` proves clean `schema.sql` omits `job_work_items` while keeping `orchestration_job_items`, `job_definitions`, and `job_runs`.
- `mvn -Dtest=WorkspaceRepositorySchemaMigrationTest,OrchestrationRuntimeTest,OperationalUiContractControllerTest test` passed with 47 tests.
- Clean and post-startup SQLite probes returned `job_definitions`, `job_runs`, and `orchestration_job_items`, with no `job_work_items`.
- `git diff --check` passed.
- Bounded Spring startup reached `Started Magenta2Application` with isolated SQLite `/tmp/domain05-subplan04-startup.sqlite`.
