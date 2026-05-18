# Public Alpha Final Validation Pattern

## Topic

Final validation pattern for the public alpha remediation suite.

## Source References

- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `/tmp/public-alpha-final-mvn-test.log`
- `/tmp/public-alpha-final-live/playwright-harness.log`
- `/tmp/public-alpha-final-live/redteam-api-probes.log`

## Key Takeaways

- Run final validation on the rolling integration branch after all domain branches are merged.
- Pair full `mvn test` with clean and warm isolated SQLite startup so migration-only regressions are visible.
- Run the checked-in Playwright harness against the live app, then add a broader public/mobile page sweep for deleted assets, console errors, and stale HTMX behavior.
- Keep red-team probes explicit and map each failure back to the owning domain before making any serial fix branch.

## Engine Relevance

This pattern is the durable validation contract for future public-alpha-style remediation campaigns: domain gates prove local fixes, while the final gate proves the merged behavior and catches cross-domain regressions.

## Open Questions

- Should the broader public/mobile page sweep be promoted from validation evidence into a checked-in Playwright spec after the public alpha branch settles?
