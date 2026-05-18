# Schema and Data Ownership Domain

## Objective

Make SQLite schema initialization and repository bootstrap canonical, lease-preserving, and clear about table ownership.

## Branch

Implementation branch: `public-alpha-remediation/schema-data-ownership`.

## Owned Findings

- bug-07, bug-19, bug-25.
- ro-12 orphan `job_work_items`.
- ro-13 schema/repository drift beyond workspaces.

## Subplans

| Order | Subplan | Findings |
| --- | --- | --- |
| 1 | `subplan-01-lease-preserving-schema.md` | bug-07 |
| 2 | `subplan-02-canonical-schema-drift.md` | bug-19, ro-13 |
| 3 | `subplan-03-inbox-table-ownership.md` | bug-25 |
| 4 | `subplan-04-orphan-schema-cleanup.md` | ro-12 |

## Context

Validators must read `domain-persistence-schema.md`, `horizontal-di-rest-schema-stale.md`, `automated-validation-evidence.md`, `remediation-handoff.md`, and owned bug reports.
