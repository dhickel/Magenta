# Topic

HTMX static module cleanup

# Source References

- `.internal-dev/plans/public-alpha-remediation/08-code-quality-stale-cleanup/subplan-02-static-module-cleanup.md`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/`

# Key Takeaways

- Before deleting stale static assets, scan active controller/page references for script paths and module constants.
- If an active page is already HTMX-rendered, preserve the server fragments and delete the old JavaScript transport instead of keeping inert duplicate behavior.
- Tests for removed static modules should assert the active page contract and absence of stale module loads rather than reading deleted files.

# Engine Relevance

This keeps the public-alpha UI aligned with the SimplyPages/HTMX default policy and prevents obsolete direct-run routes from being exposed through unused static assets.

# Open Questions

- Browser validation should continue checking public pages for deleted asset requests because static 404 regressions are easiest to catch from the page origin.
