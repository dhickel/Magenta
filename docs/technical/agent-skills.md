# Agent Skills (Phase 02 Foundation)

This document captures the implemented backend foundation from Phase 02 and the remaining planned MVP layers.

## Scope Boundary

- **Implemented in Phase 02:** root `skills/` repository resolution, `SKILL.md` parsing/validation diagnostics, skill discovery/catalog metadata persistence, optional directory visibility flags, path confinement checks, and refresh-after-edit hashing.
- **Planned next MVP layers:** agent-level assignment, runtime catalog filtering/disclosure injection, activation lifecycle, API/file management, and browser UI.
- **Out of scope for MVP:** project-local `.agents/skills`, user-home/client-native scopes, layered assignment (project/job/task/workflow/chat/session), script trust/execution policy, and registry/package ingestion.

## Repository Root

MVP uses a Magenta-owned repository rooted at:

```text
MagentaRootProperties.path()/skills
```

This is an intentional client policy choice. Agent Skills format is still aligned with official `SKILL.md` structure and optional directories.

## Skill Record Contract

Persisted metadata currently includes:

- `name`, `directory_slug`, `description`
- optional `license`, `compatibility`, `metadata`, experimental `allowed-tools`
- `skill_root_relative_path`, `skill_md_root_relative_path`
- `status`, `diagnostics_json`
- `has_scripts`, `has_references`, `has_assets`
- `content_hash`, `discovered_at`, `last_scanned_at`, `created_at`, `updated_at`

## Service Ownership (Implemented)

Current ownership boundary:

- `AgentSkillRepositoryService`: root path resolution and confined skill-path access.
- `AgentSkillParser`: frontmatter/body extraction, YAML parse fallback, validation, and stable diagnostics.
- `AgentSkillCatalogService`: discovery scan, malformed isolation, optional-directory flags, and metadata refresh persistence.
- `AgentSkillRepository`: SQLite persistence for discovered skill metadata and diagnostics.

Chat/web/API layers consume this domain surface; they do not own direct parser/discovery logic.

## Parser/Discovery Validation Behavior

Current behavior:

1. Discovery scans immediate child directories under root `skills/`.
2. A valid or warning skill is persisted with metadata and diagnostics.
3. Malformed skills are persisted as `INVALID` with diagnostics and never crash refresh.
4. Optional `scripts/`, `references/`, and `assets/` are flagged by existence only; resources are not loaded.
5. Refresh recomputes hash/metadata and preserves first-discovered timestamp.

Lenient validation currently follows the client implementation guide:

- directory/name mismatch and name-shape issues warn but load,
- missing description or unparseable YAML mark the skill invalid/skip for runtime use.

## API/Web/Runtime Contract (Pending)

Pending phases will add:

- Skill list/read/create/update/delete in root repository.
- Guided skill creation/editing for `SKILL.md`.
- Agent-skill assignment and removal.

## Validation Expectations

Phase 02 evidence:

- Parser and discovery service tests.
- Metadata repository tests against SQLite.
- Path confinement tests for traversal/symlink rejection.
