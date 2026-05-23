## Avatar Package

This package owns Avatar user-centric data and services.

### Responsibilities
- Store Avatar profile, preferences, dashboard layout, organizer data, facts, and domain events in `avatar.sqlite`.
- Keep Avatar persistence separate from the primary `magenta.sqlite` runtime store.
- Expose small repository and service APIs that later UI, tools, and assistant behavior lanes can reuse.
- Reserve the backing `Avatar` agent profile without changing runtime defaults or creating a parallel execution runtime.
- Keep Avatar dashboard layout state compatible with SimplyPages-style row/module composition and 12-column sizing.

### Boundaries
- Agent profiles, runtime settings, assignments, workspaces, jobs, output artifacts, schedules, reactions, and chat execution stay in their existing packages.
- Do not create cross-database foreign keys between `avatar.sqlite` and `magenta.sqlite`.
- Do not add model clients, queues, runners, tool loops, or assignment machinery here.
- Do not make the Avatar layout editor a separate source of truth from the displayed dashboard. Layout movement, resize, row insertion, widget add/remove, and placement controls should decorate the real dashboard surface whenever practical.
- Module-specific detail editing may open a modal or drawer, but row placement and 12-column sizing remain in-place layout concerns.

### UI Knowledge
- Before changing Avatar dashboard UI, read `.internal-dev/notes/2026-05-22-avatar-dashboard-ui-style-guidelines.md` and `.internal-dev/knowledge/simplypages-avatar-layout-and-editing.md`.
- Compare Avatar UI code to the SimplyPages docs and demo editing patterns before adding custom structures.
- For layout editing, use the SimplyPages HTMX editing demo as the concrete visual baseline: small top-corner module controls, in-place add-widget/add-row affordances, and no separate layout-list modal as the main editing interface.
- Scratch layout pages are allowed only for dev/planning experiments and Playwright visual checks. Do not reference scratch pages as source truth in package docs or knowledge files; extract stable lessons into reusable components or `.internal-dev/knowledge/`.

### Validation
- Add focused datasource, schema, repository, service, and bootstrap tests for persistence changes.
- Run Avatar package tests after changes.
- For Avatar UI changes, run delegated Playwright visual validation with desktop and mobile screenshots. The validation must critique alignment, density, empty space, text wrapping, control placement, and consistency with `/dashboard`, `/agents`, and the SimplyPages editing demo when layout editing changes, not just confirm that the route loads.
