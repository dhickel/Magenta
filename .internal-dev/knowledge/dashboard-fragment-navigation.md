# Topic

Assistant dashboard HTMX fragment navigation.

## Source References

- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/02-dynamic-fragment-caching-patterns.md`
- `.internal-dev/specifications/web.md`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `docs/technical/avatar-dashboard-fragments.md`

## Key Takeaways

- Dashboard selector navigation should not reload the full Assistant shell. Use a fragment endpoint that renders the stable `#dashboard-home` root and swap it with `hx-swap="outerHTML"`.
- Full dashboard URLs remain valid at `/dashboards/{dashboardId}`. Fragment navigation uses `/dashboards/{dashboardId}/_page` and pushes the full dashboard URL through `hx-push-url`.
- The fragment response should omit page-level assets because the full page already loaded the dashboard scripts. `avatar-shell.js` re-binds after HTMX settle through its existing `htmx:afterSettle` listener.
- Edit-mode toggles follow the same pattern: fetch `/dashboards/{dashboardId}/_page?edit=true` or the non-edit fragment and push the matching full URL.

## Engine Relevance

Use this before changing Assistant dashboard selector, edit-mode, or shell navigation behavior. Prefer a stable fragment target over full-page shell reloads for dashboard-to-dashboard navigation.

## Open Questions

- Whether dashboard creation should eventually return the same fragment response instead of targeting `body` after create.
