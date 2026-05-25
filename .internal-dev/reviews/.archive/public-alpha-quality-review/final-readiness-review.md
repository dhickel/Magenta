# Final Public Alpha Readiness Review

## Scope

Review-first public alpha quality campaign for Magenta's current filesystem-backed runtime and public web/API surfaces.

## Findings

Public alpha is not ready.

The strongest blockers are:

- Security/control: unauthenticated mutation/control routes, traversal-like agent ids in filesystem paths, host-level shell execution, broad data-root file tool access, web-fetch redirect SSRF, and workflow stored XSS.
- Execution contract: direct-run routes and chat/job UI controls still bypass submit-to-agent semantics.
- Data integrity/history: saved-plan execution deletes chat transcripts; stale schema can drop workspace leases; `schema.sql` drift persists.
- Workflow product surface: the builder cannot author common valid workflows incrementally and empty workflows can complete as successful no-ops.
- Runtime/workspace: project leases are acquired but not materialized into the promised agent filesystem view.
- UI/browser: public pages load, but mobile orchestration shell is unusable at phone width.
- Validation confidence: Maven is green, but public REST/SSE route and Spring web context coverage is too thin for this blast radius.

## Risk Assessment

This campaign found multiple critical issues that can cause unauthorized control, host/runtime data exposure, audit-history loss, invalid green workflow completions, and migration-time lease loss. These are release-blocking for a remote-host public alpha.

Passing validation:

- Focused controller/UI tests passed.
- Focused runtime/persistence tests passed.
- Full `mvn test` passed with 444 tests, 0 failures, 0 errors.
- Clean and warm isolated SQLite startup reached `Started Magenta2Application`.
- Playwright reached all requested public pages and proved a plan editor mutation persisted through UI, API, and SQLite.

Residual validation risk:

- Existing automated tests do not cover many public controller groups at Spring route level.
- Playwright found mobile layout failure but did not execute destructive or unsafe flows.
- Browser validation proved reachability/persistence, not readiness.

## Recommendations

Do not open public alpha until at least the alpha blocker set in `bug-ledger.md` is remediated and revalidated.

Prioritize:

1. Security/control confinement.
2. Single submit-to-agent execution contract.
3. Schema/lease migration correctness.
4. Workflow builder/run correctness.
5. Transcript preservation.
6. Public route/Spring web tests and browser regression harness.

## Follow-ups

Use `remediation-handoff.md` as the implementation plan seed. Preserve the review-first findings; do not resolve bugs by hiding surfaces without replacing the user workflow.
