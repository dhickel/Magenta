# Agent Skills

This page documents current Agent Skills status in Magenta MVP.

## Current Contract Status

- **Implemented now:** root skill repository discovery, `SKILL.md` parsing/validation diagnostics, metadata persistence, agent-level assignment persistence, runtime catalog disclosure, dedicated skill activation with per-conversation deduplication, `/api/skills`, and the `/skills` browser/editor UI.
- **Deferred:** project-local `.agents/skills`, user-home scopes, layered assignment beyond agent scope, and script/registry trust workflows.

## MVP Repository And File Shape

MVP skills live under the Magenta root `skills/` repository:

```text
<magenta-root>/skills/
  <skill-name>/
    SKILL.md
    scripts/      (optional)
    references/   (optional)
    assets/       (optional)
```

`SKILL.md` is required. It contains YAML frontmatter plus Markdown instructions.

## `SKILL.md` Minimum Metadata

- `name` (required): lowercase letters/numbers/hyphens, max 64 chars, matches directory name.
- `description` (required): non-empty, max 1024 chars, describes what the skill does and when to use it.
- Optional fields may include `license`, `compatibility`, `metadata`, and experimental `allowed-tools`.

## Current Workflow

1. Open `/skills`.
2. Use **Guided Create** to scaffold a valid skill, or create/import a skill directory under root `skills/`.
3. Edit `SKILL.md` in the browser or on disk.
4. Use **Refresh** to rescan metadata and diagnostics.
5. Add text files under confined skill directories when needed.
6. Assign enabled skills to the runtime agent profile (MVP target layer is agent-only).
7. During chat, the model sees a concise `available_skills` catalog and can call `activate_skill` to load full instructions.
8. Repeat calls to activate the same skill in one conversation are deduplicated.

## Browser UI

`/skills` provides a compact operational browser:

- filterable skill list with status, assignment count, and diagnostics count;
- detail view with metadata, diagnostics, optional directory indicators, and root-confined file table;
- `SKILL.md` and UTF-8 text-file editor with save and refresh;
- add-file form for the currently selected skill directory;
- top-level `scripts/`, `references/`, and `assets/` directory creation controls;
- guided scaffold creation with `SKILL.md`, optional directories, and optional starter text files;
- agent assignment/unassignment controls.

The UI manages only the Magenta root `skills/` repository. `scripts/` are visible as skill resources, but the browser does not execute scripts.

## Current API Surface

For operational scripting and integration, the MVP now exposes:

- `GET /api/skills`, `POST /api/skills/refresh`, `POST /api/skills`
- `GET /api/skills/{skillName}`, `GET /api/skills/{skillName}/diagnostics`
- `GET /api/skills/{skillName}/files`, `GET /api/skills/{skillName}/files/view`
- `PUT /api/skills/{skillName}/files/text`, `POST /api/skills/{skillName}/files`
- `GET /api/skills/{skillName}/assignments`
- `POST|DELETE /api/skills/{skillName}/assignments/agents/{agentId}`

The `/skills` page uses server-rendered HTMX fragments for browser CRUD/list/detail interactions.

## Validation Behavior

- Name mismatch and some name-shape issues are warning-level and still load.
- Missing/empty `description` or unparseable YAML marks the skill invalid.
- Malformed skills stay visible as diagnostics and do not crash discovery.
- Optional directories (`scripts/`, `references/`, `assets/`) are detected but not eagerly loaded.

## Important MVP Limits

- No browser-driven script execution contract is promised in MVP.
- `allowed-tools` is experimental metadata and not an enforced permissions guarantee in MVP.
- Cross-client `.agents/skills` interoperability support is deferred.
- Skill activation currently returns the `SKILL.md` **body** (frontmatter stripped) plus a resource file listing.
