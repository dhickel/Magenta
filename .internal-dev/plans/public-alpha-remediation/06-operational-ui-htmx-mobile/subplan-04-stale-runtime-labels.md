# Subplan 04: Stale Runtime Labels

## Goal

Remove stale Docker/Podman naming from active filesystem-runtime UI/resources/docs.

## Implementation Steps

1. Scan active UI, CSS, docs, comments, and resource names for Docker/Podman runtime references.
2. Replace active labels with filesystem-backed runtime wording.
3. Do not rewrite historical review evidence.
4. Leave intentional setup docs only if clearly marked historical or not public-alpha active.

## Validation

`rg` scan shows no stale active UI/resource references.
