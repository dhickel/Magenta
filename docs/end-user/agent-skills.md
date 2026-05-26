# Agent Skills

This page documents current Agent Skills status in Magenta MVP.

## Current Contract Status

- **Implemented now (Phases 02-03 backend):** root skill repository discovery, `SKILL.md` parsing/validation diagnostics, metadata persistence, agent-level assignment persistence, runtime catalog disclosure, and dedicated skill activation with per-conversation deduplication.
- **Planned next MVP layers:** skill management UI/API.
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

1. Create or import a skill directory under root `skills/`.
2. Add `SKILL.md` with valid metadata.
3. Trigger a catalog refresh path (service/API layer when exposed) to rescan metadata.
4. Assign enabled skills to the runtime agent profile (MVP target layer is agent-only).
5. During chat, the model sees a concise `available_skills` catalog and can call `activate_skill` to load full instructions.
6. Repeat calls to activate the same skill in one conversation are deduplicated.

## Current API Surface

For operational scripting and integration, the MVP now exposes:

- `GET /api/skills`, `POST /api/skills/refresh`, `POST /api/skills`
- `GET /api/skills/{skillName}`, `GET /api/skills/{skillName}/diagnostics`
- `GET /api/skills/{skillName}/files`, `GET /api/skills/{skillName}/files/view`
- `PUT /api/skills/{skillName}/files/text`, `POST /api/skills/{skillName}/files`
- `GET /api/skills/{skillName}/assignments`
- `POST|DELETE /api/skills/{skillName}/assignments/agents/{agentId}`

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
