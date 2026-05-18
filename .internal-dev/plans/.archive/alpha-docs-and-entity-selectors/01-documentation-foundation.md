# Documentation Foundation

## Context

The current README still describes a minimal chat scaffold and does not cover the alpha operational app. The repo already has `docs/maestro/`, but that is older design/planning material, not the alpha documentation system requested here.

`.internal-dev/AGENTS.md` mentions docs indexes such as `docs/api/00-index.md` and `docs/internal/00-index.md`, but those do not currently exist. The alpha docs need a clear folder contract and update policy before content subagents start writing.

## Goal

Create the durable documentation home, navigation structure, and update rules for technical and end-user documentation.

## In Scope

- Add `docs/AGENTS.md`.
- Add `docs/README.md` or `docs/00-index.md` as the top-level documentation index.
- Add end-user and technical documentation folders with index placeholders.
- Update root `README.md` to point at current docs and avoid stale scaffold-only claims.
- Update root `AGENTS.md` with a documentation maintenance policy.

## Out of Scope

- Completing all docs content in this phase.
- Moving or deleting `docs/maestro/`.
- Building a documentation web server.

## Target Design

Use this tree unless code inspection proves a better local convention:

```text
docs/
  AGENTS.md
  README.md
  end-user/
    00-index.md
    quickstart.md
    chat.md
    dashboard.md
    plans-and-tasks.md
    workflows.md
    jobs.md
    agents.md
    projects-and-workspaces.md
    inbox-outputs-settings.md
  technical/
    00-index.md
    architecture.md
    api-reference.md
    data-model.md
    services.md
    chat-planning-tasks.md
    orchestration-runtime.md
    workflow-engine.md
    workspaces-tools-outputs.md
    frontend-htmx.md
    security.md
    configuration-operations.md
  api/
    00-index.md
```

`docs/AGENTS.md` must say:

- Docs are user-visible intended truth; code remains logical source of truth.
- Any technical change updates relevant technical docs.
- Any user-facing behavior change updates end-user docs.
- API/controller changes update API docs.
- New docs must link from an index.
- Avoid speculative future docs unless explicitly marked future.
- Record mismatches found during doc work in `.internal-dev/bugs/` or current task notes.

Root `AGENTS.md` should add a short rule under required workflow:

- For any feature or non-trivial fix, update relevant docs in `docs/`: end-user docs for behavior changes, technical docs for architecture/API/service/schema/config changes.

## Implementation Steps

1. Read live `AGENTS.md`, `.internal-dev/AGENTS.md`, and `docs/maestro/` inventory.
2. Create `docs/AGENTS.md` with the policy above.
3. Create top-level docs index explaining audience split:
   - End users: how to operate Magenta.
   - Technical contributors: how the services, APIs, persistence, and UI fit together.
   - API references: route and payload contracts.
4. Create placeholder index pages with required sections and owner notes.
5. Update `README.md`:
   - Replace scaffold-only wording with Magenta alpha overview.
   - Keep run commands.
   - Link to `docs/end-user/00-index.md`, `docs/technical/00-index.md`, and `docs/api/00-index.md`.
6. Update root `AGENTS.md` documentation policy without clobbering unrelated edits.

## Validation

- `find docs -maxdepth 3 -type f | sort` shows the expected docs tree.
- All new docs are linked from `docs/README.md`.
- Root README no longer claims the app is only a minimal chat scaffold.
- Root `AGENTS.md` contains the docs update rule.
- Markdown links are manually checked or validated with an available link-check command if one already exists in the repo.

## Exit Criteria

- Docs subagents can use this structure without asking where content belongs.
- Documentation maintenance is encoded in both `docs/AGENTS.md` and root `AGENTS.md`.

