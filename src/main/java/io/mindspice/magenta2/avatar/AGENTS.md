## Avatar Package

This package owns Avatar user-centric data and services.

### Responsibilities
- Store Avatar profile, preferences, dashboard layout, organizer data, facts, and domain events in `avatar.sqlite`.
- Keep Avatar persistence separate from the primary `magenta.sqlite` runtime store.
- Expose small repository and service APIs that later UI, tools, and assistant behavior lanes can reuse.
- Reserve the backing `Avatar` agent profile without changing runtime defaults or creating a parallel execution runtime.
- Keep Home dashboard layout state compatible with SimplyPages-style row/module composition and 12-column sizing. `Avatar*` package, class, and database names are legacy implementation names, not the product-facing dashboard abstraction.

### Boundaries
- Agent profiles, runtime settings, assignments, workspaces, jobs, output artifacts, schedules, reactions, and chat execution stay in their existing packages.
- Do not create cross-database foreign keys between `avatar.sqlite` and `magenta.sqlite`.
- Do not add model clients, queues, runners, tool loops, or assignment machinery here.
- Do not make the Home dashboard layout editor a separate source of truth from the displayed dashboard. Layout movement, resize, row insertion, widget add/remove, and placement controls should decorate the real dashboard surface whenever practical.
- Module-specific detail editing may open a modal or drawer, but row placement and 12-column sizing remain in-place layout concerns.

### UI Knowledge
- Before changing Home dashboard UI backed by this package, read `.internal-dev/specifications/web.md`, `.internal-dev/specifications/simplypages.md`, `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`, and `.internal-dev/knowledge/avatar-work-area-ui-refactor.md` when Work Areas are in scope.
- Compare Home dashboard UI code to the SimplyPages docs and demo editing patterns before adding custom structures.
- Consider `Template`, `SlotKey`, and per-request `RenderContext` for stable dashboard layout, widget chrome, status strips, selector/detail, and repeated fragment structures. Prefer slot-keyed reuse when only labels, counts, hrefs, chips, statuses, or bounded child fragments change.
- New SlotKey/template key helper or key-bundle value types must expose concise `.of(...)` factories, and mutable component instances must not be shared across requests.
- For layout editing, use the SimplyPages HTMX editing demo as the concrete visual baseline: small top-corner module controls, in-place add-widget/add-row affordances, and no separate layout-list modal as the main editing interface.
- Scratch layout pages are allowed only for dev/planning experiments and Playwright visual checks. Do not reference scratch pages as source truth in package docs or knowledge files; extract stable lessons into reusable components or `.internal-dev/knowledge/`.

### Validation
- Add focused datasource, schema, repository, service, and bootstrap tests for persistence changes.
- Run Avatar package tests after changes.
- For Home dashboard UI changes, run delegated Playwright visual validation with desktop and mobile screenshots. The validation must critique alignment, density, empty space, text wrapping, control placement, and consistency with `/manage`, `/agents`, and the SimplyPages editing demo when layout editing changes, not just confirm that the route loads.
