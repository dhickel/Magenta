# Agent Skills

This page documents the intended Agent Skills behavior for Magenta MVP while implementation work is in progress.

## Current Contract Status

- **Implemented now:** not yet.
- **Intended MVP contract:** manage skills from a Magenta-owned root repository and assign skills to agents.
- **Deferred:** project-local `.agents/skills`, user-home scopes, layered assignment beyond agent scope, and script/registry trust workflows.

Until implementation lands, treat this page and `.internal-dev/specifications/*` as the intended behavior contract.

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

## Intended MVP User Workflow

1. Open the skills surface.
2. Create or import a skill directory in root `skills/`.
3. Edit metadata and instructions in `SKILL.md`.
4. Optionally add `scripts/`, `references/`, and `assets/`.
5. Assign selected skills to one or more agents.
6. Use assigned agents in chat/task execution flows that can activate relevant skills.

## Activation Expectations

- Skills are disclosed as a compact catalog first.
- Full skill instructions load only when activated.
- Supporting files are loaded on demand when referenced.
- Activation behavior is agent-assignment driven in MVP, not project/job/task/session layered assignment.

## Important MVP Limits

- No browser-driven script execution contract is promised in MVP.
- `allowed-tools` is experimental metadata and not an enforced permissions guarantee in MVP.
- Cross-client `.agents/skills` interoperability support is deferred.
