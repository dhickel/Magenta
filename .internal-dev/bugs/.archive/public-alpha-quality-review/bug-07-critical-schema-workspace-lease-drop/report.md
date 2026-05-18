# Startup Can Drop Workspace Leases via Deprecated Workspace Roots

## Summary

`schema.sql` recreates deprecated `workspace_roots`; repository migration then treats that as legacy state and can drop `workspace_leases`.

## Scope

SQLite startup schema and `WorkspaceRepository` migration.

## Reproduction

1. Start from a DB where `schema.sql` creates `workspace_roots` and `workspace_leases`.
2. Initialize `WorkspaceRepository`.
3. Observe migration path dropping legacy `workspace_leases` and `workspace_roots`.

## Expected

Startup must not drop active lease tables or rows on every boot.

## Actual

`spring.sql.init.mode: always` runs stale schema each startup; workspace migration can remove `workspace_leases`.

## Evidence

- `application.yml:28` runs schema init always.
- `schema.sql:409` creates deprecated `workspace_roots`.
- `schema.sql:429` defines `workspace_leases` against `workspace_roots`.
- `WorkspaceRepository.java:609` treats roots as deprecated.
- `WorkspaceRepository.java:714` migration drops `workspace_leases` and `workspace_roots`.
- Persistence review reproduced `roots_after_schema|1`, `leases_before_migration|1`, `leases_table_after_migration|0` in read-only SQLite probing.

## Impact

Critical: warm DB startup can lose lease state or repeatedly rebuild/drop workspace lease tables.

## Status

Open.

## Next Action

Make `schema.sql` current with `workspaces` ownership and replace destructive legacy migration with guarded one-time migration that preserves current lease rows.
