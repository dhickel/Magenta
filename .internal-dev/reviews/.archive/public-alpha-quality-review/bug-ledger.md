# Public Alpha Bug Ledger

## Scope

Consolidated bug ledger for the `public-alpha-quality-review` campaign. Every entry links to a full report under `.internal-dev/bugs/public-alpha-quality-review/`.

## Alpha Blockers

| Bug | Severity | Surface | Summary |
| --- | --- | --- | --- |
| bug-01 | Critical | security | Unauthenticated public mutation/control surface |
| bug-02 | Critical | security | Agent ids can escape agent subtree inside data root |
| bug-03 | Critical | workflow | Builder rejects necessary intermediate states |
| bug-04 | Critical | workflow | Empty workflows validate and complete as no-ops |
| bug-05 | Critical | execution | Public direct-run surfaces bypass submit-to-agent |
| bug-06 | Critical | chat/plans | Saved plan execution deletes transcript |
| bug-07 | Critical | schema | Startup can drop workspace leases |
| bug-08 | Critical | tools | Shell tool runs host commands with wildcard config |
| bug-09 | High | tools | File tools scoped to whole data root |
| bug-10 | High | tools | Web fetch redirect SSRF risk |
| bug-11 | High | workflow/security | Workflow graph stored XSS risk |
| bug-12 | High | runtime | Assignment lifecycle routes not agent-scoped |
| bug-13 | High | workspaces | Project workspace leases not materialized |
| bug-14 | High | SSE | Plan run stream emits wrong event names |
| bug-15 | High | jobs | Job Start Run bypasses assignment submission |
| bug-16 | High | UI | Mobile orchestration shell unusable |
| bug-17 | High | tests | Public REST/SSE and Spring web coverage gaps |
| bug-19 | High | schema | `schema.sql` drift from repository shape |

## Remediation Items

| Bug | Severity | Surface | Summary |
| --- | --- | --- | --- |
| bug-18 | Medium | UI | Agent Delete/Archive target missing stale Docker element |
| bug-20 | Medium | errors | HTMX fragment errors often return 200 OK |
| bug-21 | Medium | schedules/reactions | Assignment templates not validated at save |
| bug-22 | Medium | filesystem | Filesystem allocation failure continues execution |
| bug-23 | Medium | outputs | `file_path` materialization can follow symlinks |
| bug-24 | Medium | outputs | Output attribution uses stale pre-workspace path logic |
| bug-25 | Medium | inbox/schema | Inbox persistence split across two tables |

## Readiness Decision

Public alpha is not ready. The campaign found critical security, execution-semantics, data-loss, workflow-authoring, and schema-migration blockers. Browser validation confirmed pages are reachable and core plan persistence works, but it also found a high-severity mobile layout blocker.
