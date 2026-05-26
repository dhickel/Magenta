# Agent Skills (Phases 02-03 Backend)

This document captures the implemented backend foundation from Phase 02 plus Phase 03 runtime assignment/catalog/activation behavior.

## Scope Boundary

- **Implemented in Phase 02:** root `skills/` repository resolution, `SKILL.md` parsing/validation diagnostics, skill discovery/catalog metadata persistence, optional directory visibility flags, path confinement checks, and refresh-after-edit hashing.
- **Implemented in Phase 03:** DB-backed agent skill assignments, runtime catalog filtering by assigned+enabled+loadable skills, prompt-time catalog disclosure, dedicated `activate_skill` loading with resource listing, and activation deduplication per conversation.
- **Planned next MVP layers:** API/file management and browser UI.
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

Phase 03 adds `agent_skill_assignments` metadata:

- `skill_id`, `skill_name`
- `target_type` (`AGENT` in MVP), `target_id` (agent id)
- `enabled`
- `created_at`, `updated_at`
- uniqueness on `(skill_name, target_type, target_id)` to prevent duplicate rows

## Service Ownership (Implemented)

Current ownership boundary:

- `AgentSkillRepositoryService`: root path resolution and confined skill-path access.
- `AgentSkillParser`: frontmatter/body extraction, YAML parse fallback, validation, and stable diagnostics.
- `AgentSkillCatalogService`: discovery scan, malformed isolation, optional-directory flags, and metadata refresh persistence.
- `AgentSkillRepository`: SQLite persistence for discovered skill metadata and diagnostics.
- `AgentSkillAssignmentRepository` + `AgentSkillAssignmentService`: assignment persistence and lookup independent from tool approval metadata.
- `AgentSkillRuntimeCatalogService`: assigned/enabled/loadable catalog construction for the effective agent context.
- `AgentSkillPromptCatalogAssembler`: concise `available_skills` prompt section injection.
- `AgentSkillActivationService`: dedicated activation lookup, assignment checks, skill-body loading, resource listing, and dedupe tracking.

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

## Runtime Catalog And Activation Contract (Implemented)

- Runtime catalog is metadata-only and contains `name` + `description` for assigned+enabled+loadable skills.
- Unassigned, disabled, invalid, malformed, or missing skills are hidden from the runtime catalog.
- If no skills are available for the conversation/agent context, prompt catalog disclosure is omitted.
- `activate_skill` is hidden from mode-filtered tool exposure when no available skills exist for that conversation.
- Dedicated activation returns **body-only** instructions (frontmatter stripped) wrapped in `<skill_content ...>` with:
  - skill directory hint (`skills/<slug>`)
  - `<skill_resources>` listing for files under `scripts/`, `references/`, and `assets/`
  - no eager resource file reads
- Repeated activation of the same skill in the same conversation returns `ALREADY_ACTIVE` and does not re-inject duplicate content.

## Validation Expectations

Phase 02 evidence:

- Parser and discovery service tests.
- Metadata repository tests against SQLite.
- Path confinement tests for traversal/symlink rejection.
