# Public Alpha Review Campaign Lessons

## Topic

Review-first public alpha campaign structure for Magenta's filesystem-backed runtime.

## Source References

- `.internal-dev/plans/public-alpha-quality-review/index.md`
- `.internal-dev/reviews/public-alpha-quality-review/automated-validation-evidence.md`
- `.internal-dev/reviews/public-alpha-quality-review/playwright-public-pages-evidence.md`
- `.internal-dev/reviews/public-alpha-quality-review/bug-ledger.md`

## Key Takeaways

- Passing `mvn test` is not enough for public alpha readiness when most public routes are direct-controller tested and not Spring web/context tested.
- Browser-origin validation can prove route reachability and persistence while still finding layout blockers that static review misses.
- Warm DB validation should use text evidence and avoid committing copied local DBs.
- Filesystem-backed runtime review must treat tool confinement, path ids, workspace links, and output attribution as one contract.
- Stale Docker/Podman references are quality findings under the current filesystem-runtime contract, not Docker validation gates.

## Engine Relevance

Future public-alpha or release-readiness reviews should reuse the mixed strategy: domain agents, horizontal agents, focused Maven/startup validation, DB probes, and Playwright page matrix.

## Open Questions

- Whether public alpha will require full authentication or an operator-only network boundary plus CSRF/session protection.
- Whether project workspaces should be materialized as symlinks, copied views, or a tool-level virtual path mapping.
