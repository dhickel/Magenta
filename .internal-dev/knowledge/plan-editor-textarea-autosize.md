# Topic
Plan editor textarea autosizing

# Source References
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/resources/static/js/orchestration/plans.js`

# Key Takeaways
Plan editor list fields are HTMX-rendered fragments, so textarea autosizing needs to run both on initial page load and after HTMX swaps. Server-side row hints improve first paint, but client-side `scrollHeight` is the reliable source of truth once wrapping and CSS are applied.

# Engine Relevance
Keep plan CRUD and field updates HTMX-first. A narrow JavaScript enhancement is justified here because measuring rendered textarea content height cannot be done accurately in raw HTML or on the server.

# Open Questions
None.
