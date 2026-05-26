# Target Design

## MVP Product Contract

Magenta exposes Agent Skills as reusable instruction packages stored under the Magenta application root at `skills/<skill-name>/`. Users can browse skills, inspect directory contents, edit skill files, create new skills through a guided flow, and assign skills to runtime agents. Assigned skills become available to those agents through catalog disclosure and explicit/full activation.

The MVP does not load project-local `.agents/skills/`, user-home skills, registries, marketplace packages, or non-agent assignment layers. Those are documented deferred capabilities.

## Root Skill Repository

- Repository root: `MagentaRootProperties.path().resolve("skills")`.
- Required skill shape: `skills/<skill-name>/SKILL.md`.
- Optional recognized child directories: `scripts/`, `references/`, `assets/`.
- Other files/directories may exist but must not be eagerly loaded into runtime context.
- Disk operations must normalize and realpath-check paths under the root repository.
- Skill slugs should use the same allowed shape as the official `name` field for guided creation.

## Backend Domain Model

Add a dedicated package, recommended:

- `io.mindspice.magenta2.ai.skills`

Recommended records/classes:

- `AgentSkill`: DB-backed discovered/known skill metadata.
- `AgentSkillFrontmatter`: parsed frontmatter fields.
- `AgentSkillParseResult`: body, metadata, diagnostics, status.
- `AgentSkillDiagnostic`: severity, code, message, source path.
- `AgentSkillCatalogEntry`: model-visible `name` and `description`, optionally activation hint/tool name.
- `AgentSkillActivation`: activated body/resource listing/deduplication outcome.
- `AgentSkillAssignment`: skill-to-target DB assignment with target type.
- `AgentSkillTargetType`: MVP `AGENT`; deferred enum values may exist only if clearly inert/documented.

Recommended services:

- `AgentSkillRepositoryService`: root path creation, safe path resolution, file reads/writes, directory listing.
- `AgentSkillParser`: frontmatter/body parsing and official-spec validation.
- `AgentSkillCatalogService`: discovery, metadata persistence refresh, diagnostics aggregation, catalog construction.
- `AgentSkillAssignmentService`: agent assignment CRUD and lookup.
- `AgentSkillActivationService`: full body loading, resource listing, activation deduplication, activation diagnostics.
- `AgentSkillPromptCatalogAssembler`: concise runtime catalog rendering for prompt injection or tool descriptions.

Recommended repositories:

- `AgentSkillRepository`: `agent_skills` metadata and diagnostics persistence.
- `AgentSkillAssignmentRepository`: assignment metadata table.

## Persistence Contract

Minimum metadata table fields:

- `id`
- `name`
- `directory_slug`
- `description`
- `license`
- `compatibility`
- `metadata_json`
- `allowed_tools`
- `skill_root_relative_path`
- `skill_md_root_relative_path`
- `status`
- `diagnostics_json`
- `has_scripts`
- `has_references`
- `has_assets`
- `content_hash` or equivalent stale-change marker
- `discovered_at`
- `last_scanned_at`
- `created_at`
- `updated_at`

Minimum assignment table fields:

- `id`
- `skill_id`
- `skill_name`
- `target_type`
- `target_id`
- `enabled`
- `created_at`
- `updated_at`

Use uniqueness to prevent duplicate active assignment rows for the same `skill_name`, `target_type`, and `target_id`.

## Runtime Loading Contract

- Discovery/catalog load reads and validates metadata only.
- Runtime catalog includes only assigned and enabled skills with usable `name` and `description`.
- If no skills are available for an agent/conversation, omit skill catalog instructions entirely.
- Activation loads the full `SKILL.md` body and lists bundled resources. It does not read every resource.
- Activation results should be structured so the model can distinguish skill instructions from normal conversation content.
- Deduplication is per conversation/session context. Re-activation of an already active skill should return an explicit "already active" result or no-op equivalent without duplicate context injection.
- If `allowed-tools` is parsed, it is metadata only unless a worker gets explicit approval to wire experimental enforcement.

## Chat Integration Choice

Use a dedicated `activate_skill`-style tool for MVP activation unless implementation evidence shows system-prompt file-read activation is simpler and equally safe in Magenta.

Rationale:

- Magenta can control root skill repository access without exposing the application root to generic file tools.
- Activation can list resources without eager reads.
- Activation can track deduplication and diagnostics.
- Tool schema can constrain valid names or validate them server-side.

The model-visible catalog can be appended to the effective system prompt by `PromptContextAssembler` or a narrowly injected collaborator. Keep the catalog concise.

## API And UI Contract

Recommended backend routes:

- `GET /api/skills`
- `POST /api/skills/refresh`
- `GET /api/skills/{skillName}`
- `GET /api/skills/{skillName}/files`
- `GET /api/skills/{skillName}/files/view?path=...`
- `PUT /api/skills/{skillName}/files/text?path=...`
- `POST /api/skills/{skillName}/files`
- `POST /api/skills`
- `GET /api/skills/{skillName}/assignments`
- `POST /api/skills/{skillName}/assignments/agents/{agentId}`
- `DELETE /api/skills/{skillName}/assignments/agents/{agentId}`

Recommended browser routes/fragments:

- `/skills`
- `/skills/_list`
- `/skills/_detail/{skillName}`
- `/skills/_files/{skillName}`
- `/skills/_viewer/{skillName}?path=...`
- `/skills/_create`
- `/skills/_assignments/{skillName}`

Actual route placement may use existing orchestration web conventions, but public API routes and browser fragments must be documented.

## UI Experience Contract

- First viewport: dense master/detail skill browser with filter/search, status chips, selected skill summary, directory/file overview, diagnostics, and assignment controls.
- Editor: `SKILL.md` has a prominent editable pane with validation preview/diagnostics after save.
- File browser: shows root files and recognized optional directories; allows adding text files in safe directories; avoids presenting app root as a general file manager.
- Guided creation: question-driven flow asks for skill name, purpose/when-to-use, core workflow, optional references/scripts/assets, and produces a valid `SKILL.md` scaffold.
- Agent assignment: uses existing selector patterns where possible; assignment state updates with HTMX fragments and clear status feedback.
- Mobile: list/detail stacks cleanly, editor controls remain reachable, text does not overflow buttons/panels, diagnostics remain readable.
- Visual style: operational console, compact panels, thin borders, small radii, semantic chips, no hero/marketing layout.

## Specification And Docs Updates Required During Implementation

- `.internal-dev/specifications/architecture.md`: skill repository and runtime boundary.
- `.internal-dev/specifications/services.md`: skill loader/parser/catalog/assignment/activation service contracts.
- `.internal-dev/specifications/api.md`: skill API routes and payload/status contracts.
- `.internal-dev/specifications/web.md`: skill browser/editor and guided creation UX.
- `.internal-dev/specifications/simplypages.md`: reusable HTMX/browser/editor patterns if new reusable components appear.
- `.internal-dev/specifications/decisions.md`: root repository MVP, dedicated activation tool choice, deferred project-local/layered assignments.
- `.internal-dev/specifications/deferred-features.md`: project/user/global skill discovery and deferred assignment layers.
- `docs/end-user/`: skill creation/editing/assignment guide.
- `docs/technical/`: skill architecture/API/service docs.
- `.internal-dev/knowledge/`: spec-validation lessons and UI reuse decisions.
- Affected package `AGENTS.md`: skill package, web/API, chat service/tool, orchestration/agent assignment, core config if root semantics change.
