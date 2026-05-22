## Avatar Package

This package owns Avatar user-centric data and services.

### Responsibilities
- Store Avatar profile, preferences, dashboard layout, organizer data, facts, and domain events in `avatar.sqlite`.
- Keep Avatar persistence separate from the primary `magenta.sqlite` runtime store.
- Expose small repository and service APIs that later UI, tools, and assistant behavior lanes can reuse.
- Reserve the backing `Avatar` agent profile without changing runtime defaults or creating a parallel execution runtime.

### Boundaries
- Agent profiles, runtime settings, assignments, workspaces, jobs, output artifacts, schedules, reactions, and chat execution stay in their existing packages.
- Do not create cross-database foreign keys between `avatar.sqlite` and `magenta.sqlite`.
- Do not add model clients, queues, runners, tool loops, or assignment machinery here.

### Validation
- Add focused datasource, schema, repository, service, and bootstrap tests for persistence changes.
- Run Avatar package tests after changes.
