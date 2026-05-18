# Filesystem Runtime Label Cleanup

## Topic

Public-alpha stale runtime label cleanup after removal of container-backed execution.

## Source References

- `.internal-dev/plans/public-alpha-remediation/06-operational-ui-htmx-mobile/subplan-04-stale-runtime-labels.md`
- `.internal-dev/reviews/public-alpha-quality-review/domain-api-web.md`
- `.internal-dev/reviews/public-alpha-quality-review/domain-frontend-static.md`
- `.internal-dev/reviews/public-alpha-quality-review/horizontal-security-error-htmx.md`

## Key Takeaways

- Active public-alpha runtime surfaces should say filesystem-backed runtime, workspace runtime, or host shell tools.
- Historical `.internal-dev` evidence that describes earlier container-backed work should remain intact unless it is being presented as current public-alpha contract.
- Static stale-label validation should scan active source, tests, config, dependency declarations, and operator docs while excluding historical review/changelog/knowledge records.

## Engine Relevance

This keeps operator-facing runtime expectations aligned with the current filesystem workspace implementation and avoids reviving removed container-runtime assumptions through tests, comments, dependencies, or setup docs.

## Open Questions

- Should the ignored `docs/` setup tree be brought under version control for public-alpha operator documentation?
