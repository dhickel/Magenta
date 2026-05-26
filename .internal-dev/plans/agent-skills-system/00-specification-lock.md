# Agent Skills System Specification Lock

## Acceptance Criteria

- Magenta has a first-class Agent Skills system backed by application services, SQLite metadata, APIs, and browser UI rather than ad hoc prompt text or loose files.
- The Magenta-owned skill repository for MVP is `skills/` under the configured Magenta application root (`MagentaRootProperties.path()`), not the workspace data root and not project-local `.agents/skills/`.
- MVP discovery scans only the Magenta root skill repository and discovers directories shaped as `skills/<skill-name>/SKILL.md`.
- `SKILL.md` parsing follows the official Agent Skills specification for YAML frontmatter, required `name` and `description`, optional `license`, `compatibility`, `metadata`, and experimental `allowed-tools`.
- Disk directory slug and frontmatter `name` are validated carefully. The plan requires spec-sensitive behavior to be verified against the official specification before implementation and approval.
- Valid skills become DB-backed records with filesystem location, parsed metadata, validation status, diagnostics, discovered optional directory/file summaries, timestamps, and a stable identity suitable for assignment.
- Malformed skills are surfaced as diagnostics and never crash catalog discovery, browser listing, startup, or chat prompt assembly.
- Backend metadata can assign skills to agents for MVP. Future target layers are documented but not implemented: project, job, task, workflow, chat/session, and global/user layers.
- Assigned skill availability filters the model-visible catalog. Unassigned or disabled skills are hidden from the runtime catalog rather than listed and blocked at activation time.
- Runtime loading uses progressive disclosure: catalog-only skill disclosure first, full `SKILL.md` body activation only when requested, and optional `scripts/`, `references/`, and `assets/` listed without eager resource loading.
- Activation deduplication prevents repeated injection/loading of the same skill into the same active conversation/session context.
- Loader behavior reflects file edits safely: edits to `SKILL.md` or skill files are discoverable through explicit refresh/reload paths and tested for stale metadata/body handling.
- UI supports MVP browse, directory overview, file viewer/editor, `SKILL.md` editing, file add/edit flows, agent assignment, and guided skill creation inspired by the saved plan builder Q&A.
- Specs, docs, knowledge, and package `AGENTS.md` guidance distinguish MVP root repository behavior from deferred project-local and layered assignment behavior.

## Validation Criteria

- Unit tests cover parsing, validation, diagnostics, optional-directory listing, catalog building, activation loading, reload-after-edit behavior, and activation deduplication.
- Repository/service tests cover skill metadata persistence, discovered record refresh, malformed record status, assignment CRUD/lookup, assigned-vs-unassigned catalog filtering, and purge/disable behavior.
- Controller/API tests cover skill list/detail, refresh, validation diagnostics, file list/view/save/create, guarded path handling, guided creation endpoints, and assignment endpoints.
- Chat/tool integration tests cover model-visible catalog insertion only when skills are assigned, omission when none are available, full activation body behavior, duplicate activation behavior, and safe activation failures.
- Focused browser validation covers the skill browser/editor and guided creation flow with desktop and mobile screenshots plus visual quality critique.
- Bounded Spring startup passes after schema/wiring changes.
- Final spec-adherence review is performed by a `gpt-5.5` xhigh validation agent using the official Agent Skills specification pages as source of truth.
- The final validator must mark every intentional Magenta divergence as documented. Any undocumented divergence from the official specification is a failure.

## Negative Criteria

- Do not implement project-local `.agents/skills/` or `.<client>/skills/` scanning in MVP.
- Do not treat `.internal-dev/research/agent-skills-specification-research.md` as source of truth for spec behavior. It is context only.
- Do not rely on intuition, memory, or stale local summaries for name validation, required field handling, progressive disclosure, or resource directory behavior.
- Do not let malformed YAML, invalid names, missing descriptions, oversized fields, symlink/path traversal, or unexpected files crash discovery or UI rendering.
- Do not expose skill directories as general unrestricted filesystem browser roots.
- Do not eagerly load `scripts/`, `references/`, or `assets/` into model context.
- Do not support or enforce `allowed-tools` as a permission model unless it is explicitly scoped and documented as experimental/non-authoritative for MVP.
- Do not add broad subagent execution, remote registries, marketplace behavior, packaging/install flows, or project trust workflows in this MVP.
- Do not overload existing tool assignment fields with skill assignment JSON. Skills require their own metadata/assignment model.

## Non-Goals

- Project-local `.agents/skills/` scanning.
- User-home `~/.agents/skills/`, organization, registry, marketplace, or upload-package support.
- Automatic model trigger matching beyond catalog disclosure and explicit activation.
- Running bundled scripts from the UI.
- Skill versioning, publishing, dependency installation, import/export packaging, or trust review workflows.
- Full layered assignment resolution beyond the MVP agent assignment layer.
- Broad refactor of existing chat, task, plan, or orchestration systems beyond the skill integration points named in this suite.

## Constraints And Assumptions

- Work classification is `large`: the feature spans root filesystem layout, schema, persistence, service/domain design, chat/tool integration, web/API, UI, docs/specs, and external spec validation.
- The root `skills/` repository is under `MagentaRootProperties.path()` and must not be confused with `AiConfig.dataRoot` or workspace roots.
- Application-owned skill file editing must use confined path resolution under the skill repository and must reject traversal/symlink escapes.
- Controllers remain thin; services own skill discovery, parsing, filesystem mutation, assignment lookup, and runtime catalog/activation behavior.
- SQLite repositories in this repo commonly self-create schema. If a worker finds active migration tooling has appeared, use it instead and update this suite before continuing.
- The UI should follow existing operational pages: dense master/detail browsing, HTMX-first fragments, compact controls, and existing reusable selector/browser/file-editor patterns where practical.
- Planning UI implementation is its own work unit and must be assigned to a `gpt-5.3` xhigh implementation agent.
- Final spec-adherence validation must be assigned to a `gpt-5.5` xhigh validation agent.

## External Source-Of-Truth Requirement

Implementation and validation agents must open and verify these official pages before making or approving spec-sensitive behavior:

- `https://agentskills.io/specification`
- `https://agentskills.io/client-implementation/adding-skills-support`
- `https://agentskills.io/skill-creation/best-practices`
- `https://agentskills.io/skill-creation/using-scripts`

The local research document is useful orientation only. If the official pages and local research disagree, the official pages win unless Magenta deliberately documents a product divergence.

## User-Decision Gates

- Stop and consult the main thread if implementing strict spec validation would reject existing real-world skills that the user expects to load leniently.
- Stop and consult the main thread before expanding MVP scope to project-local/user-home skill discovery, package import/export, script execution, or non-agent assignment layers.
- Stop and consult the main thread if existing schema/migration constraints make DB-backed skill records unsafe without a broader migration plan.
- Stop and consult the main thread if browser validation cannot run against a live Spring app.

## Stop Rules

- Stop and replan if the implementation cannot keep malformed skills isolated from discovery/runtime failures.
- Stop and replan if root skill repository confinement cannot be proven with tests.
- Stop and replan if the assignment model cannot distinguish MVP agent assignments from deferred future layers.
- Stop and replan if final spec validation finds undocumented divergence from required Agent Skills format behavior.
