# Public Alpha Remediation Sprint Plan Suite

## Objective

Turn every addressable finding from `.internal-dev/reviews/public-alpha-quality-review/` into a domain-owned remediation plan that the development team can execute over time. This suite covers filed bugs, quality issues, refactor targets, stale-code cleanup, test-harness gaps, and review-only concerns.

## Source Review

- Primary review root: `.internal-dev/reviews/public-alpha-quality-review/`
- Bug reports: `.internal-dev/bugs/public-alpha-quality-review/`
- Original review campaign plan: `.internal-dev/plans/public-alpha-quality-review/index.md`
- Remediation seed: `.internal-dev/reviews/public-alpha-quality-review/remediation-handoff.md`

Future agents should start from this suite. The original review files are indexed in `review-context-index.md`; implementation agents should read those files only when they need more detail, while validation agents must read the referenced original review and bug documents before validating.

## Execution Contract

- Root branch for this plan-generation work: `public-alpha-remediation-plans`.
- Each domain implementation starts by creating its own branch named in that domain `index.md`.
- Domain implementation should be serial in the shared checkout. Do not run parallel mutating workers inside one domain unless the domain orchestration plan explicitly names disjoint write scopes.
- Validation agents gate each subplan. Validation agents should read the original review and bug documents referenced by the domain before running checks.
- Domain orchestrators must create a git commit after each completed subplan before starting the next subplan. This applies going forward from 2026-05-18; already-run agents do not need retroactive prompt changes.
- Implementation/scoping workers should use GPT-5.5 Codex high unless the operator overrides it.
- Validation/testing agents should follow repository policy: `gpt-5.3-codex` with reasoning effort `medium`, unless the execution prompt explicitly overrides it.
- Agents must update `progress.md` and append useful cross-domain facts to `implementation_notes.md`.

## Domain Order

| Order | Domain | Branch | Owns |
| --- | --- | --- | --- |
| 1 | `01-security-access-control` | `public-alpha-remediation/security-access-control` | Auth/CSRF gate, id/path segment validation, workflow XSS security policy touchpoints, agent-scoped lifecycle controls. |
| 2 | `02-workspace-tools-outputs` | `public-alpha-remediation/workspace-tools-outputs` | Shell/file/web tool confinement, project workspace materialization, allocation failure handling, output symlink and attribution fixes. |
| 3 | `03-execution-history-streams` | `public-alpha-remediation/execution-history-streams` | Submit-to-agent semantics, chat transcript preservation, plan SSE event names, job run submission, priority consistency. |
| 4 | `04-workflow-authoring-runtime-js` | `public-alpha-remediation/workflow-authoring-runtime-js` | Incremental workflow editing, nonempty executable validation, workflow JS island narrowing, graph network failure handling. |
| 5 | `05-schema-data-ownership` | `public-alpha-remediation/schema-data-ownership` | Canonical schema, lease-preserving migration, inbox ownership, orphan schema cleanup, schema drift tests. |
| 6 | `06-operational-ui-htmx-mobile` | `public-alpha-remediation/operational-ui-htmx-mobile` | Mobile shell layout, HTMX error statuses, stale target IDs, stale Docker UI naming, placeholder events, workspace health display. |
| 7 | `07-validation-harness-regression` | `public-alpha-remediation/validation-harness-regression` | Spring web/context coverage, REST/SSE tests, Playwright harness, fixture foreign keys, schedule/reaction test parity. |
| 8 | `08-code-quality-stale-cleanup` | `public-alpha-remediation/code-quality-stale-cleanup` | Legacy workflow package, stale/dead static modules, stale docs/comments, final cleanup of review-only quality items. |

## Required Root Trackers

- `finding-inventory.md` is the coverage source of truth for this suite.
- `progress.md` tracks every finding and subplan status.
- `implementation_notes.md` is the shared coordination note used across domain branches.
- `review-context-index.md` maps domains and subplans to the original evidence.
- `no-action-registry.md` records only explicitly ruled-out non-findings and must not be used to skip addressable concerns without user approval.

## Completion Gate

The suite is ready for execution when every item in `finding-inventory.md` has one primary domain, a planned subplan, and a validation gate. During implementation, no domain is complete until its validation agent records passing evidence and the implementing agent completes the `.internal-dev` closeout workflow and commit for that domain phase.
