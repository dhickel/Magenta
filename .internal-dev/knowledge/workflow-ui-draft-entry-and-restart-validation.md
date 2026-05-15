## Topic

Workflow UI draft creation and restart validation

## Source References

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`

## Key Takeaways

- Workflow node and route editing requires a durable workflow id, so the visible `New Workflow` action should create a draft before showing the node/route editor.
- Server-render the initial list content inside the HTMX target instead of showing only `Loading...`; HTMX can still refresh the list on load, but the page remains useful if asset loading or swaps are delayed.
- A Spring Boot process started from `target/classes` keeps already-loaded controller classes until the process restarts. Recompiling with Maven is not enough to update a running IntelliJ-launched app.
- Existing local SQLite databases may predate route-aware workflow columns. Repository startup must migrate both `nodes_json` and `routes_json`, or a fresh deploy can look like a UI failure while `/workflows` is actually returning HTTP 500.

## Engine Relevance

This pattern keeps the workflow editor HTMX-first while avoiding a misleading first screen that looks like a form-only MVP. When validating UI fixes, compare the running process against a fresh startup and the default local database, not just the source tree or an isolated temp SQLite database.

## Open Questions

- Should empty abandoned workflow drafts be auto-pruned or explicitly surfaced as drafts in the list?
