# Workspace, Tools, and Outputs Domain

## Objective

Constrain filesystem-backed runtime tools to the active assignment/workspace contract and make outputs/project workspace behavior match what the UI and runner promise.

## Branch

Implementation branch: `public-alpha-remediation/workspace-tools-outputs`.

## Owned Findings

- bug-08, bug-09, bug-10, bug-13, bug-22, bug-23, bug-24.
- Cross-domain coordination with security id validation and schema workspace ownership.

## Subplans

| Order | Subplan | Findings |
| --- | --- | --- |
| 1 | `subplan-01-shell-tool-confinement.md` | bug-08 |
| 2 | `subplan-02-file-tool-workspace-scope.md` | bug-09 |
| 3 | `subplan-03-web-fetch-redirect-ssrf.md` | bug-10 |
| 4 | `subplan-04-project-workspace-materialization.md` | bug-13 |
| 5 | `subplan-05-filesystem-allocation-fail-fast.md` | bug-22 |
| 6 | `subplan-06-output-symlink-materialization.md` | bug-23 |
| 7 | `subplan-07-output-attribution.md` | bug-24 |

## Context

Validators must read `domain-workspaces-tools-outputs.md`, `domain-orchestration-runtime.md`, `domain-persistence-schema.md`, `horizontal-security-error-htmx.md`, `remediation-handoff.md`, and owned bug reports.
