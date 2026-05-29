# Topic

HTMX fragment navigation for Assistant dashboard surfaces.

## Source References

- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/03-htmx-endpoint-and-swap-patterns.md`
- `/home/hickelpickle/Code/Java/cannasite/java-html-framework/docs/patterns/02-dynamic-fragment-caching-patterns.md`
- `.internal-dev/specifications/web.md`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `docs/technical/avatar-dashboard-fragments.md`

## Key Takeaways

- For same-surface navigation, treat the changing area as a component/fragment, not a full page. The SimplyPages/HTMX pattern is: render a stable root element from a fragment endpoint, target that root, and use `hx-swap="outerHTML"`.
- Dashboard selector navigation should not reload the full Assistant shell. Use a fragment endpoint that renders the stable `#dashboard-home` root and swap it with `hx-swap="outerHTML"`.
- Full dashboard URLs remain valid at `/dashboards/{dashboardId}`. Fragment navigation uses `/dashboards/{dashboardId}/_page` and pushes the full dashboard URL through `hx-push-url`.
- Keep the normal `href` on links as the non-HTMX fallback. Add `hx-get` for the fragment endpoint, `hx-target` for the stable component root, `hx-swap="outerHTML"`, and `hx-push-url` for the canonical full URL.
- Do not target `body` or return the full shell for routine in-page dashboard switching. That reloads/replaces page chrome, risks duplicated scripts/nav, and loses browser-local surface state that should survive component navigation.
- The fragment response should omit page-level assets because the full page already loaded the dashboard scripts. `avatar-shell.js` re-binds after HTMX settle through its existing `htmx:afterSettle` listener.
- Edit-mode toggles follow the same pattern: fetch `/dashboards/{dashboardId}/_page?edit=true` or the non-edit fragment and push the matching full URL.
- Browser validation should listen for HTMX lifecycle events or inspect network requests to prove the request hit the fragment endpoint and the swap target was the intended component root. Also assert only one shell/nav and one fragment root remain after the swap.

## Engine Relevance

Use this before changing Assistant dashboard selector, edit-mode, or shell navigation behavior. Prefer a stable fragment target over full-page shell reloads for dashboard-to-dashboard navigation. This same HTMX pattern applies to future home/dashboard sub-surfaces where the URL should change but global chrome should stay in place.

## Open Questions

- Whether dashboard creation should eventually return the same fragment response instead of targeting `body` after create.
