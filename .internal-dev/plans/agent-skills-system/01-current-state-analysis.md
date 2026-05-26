# Current State Analysis

## Verified Local Context

- Root process guidance requires controlled `.internal-dev` reads, relevant specification checks before non-trivial planning, docs updates for feature/API/UI changes, Playwright validation for UI changes, and closeout artifacts.
- Existing specifications place service behavior in `services.md`, route/payload behavior in `api.md`, web/page behavior in `web.md`, SimplyPages reuse and HTMX policy in `simplypages.md`, durable tradeoffs in `decisions.md`, future accepted scope in `deferred-features.md`, and exploratory future ideas in `horizon-ideas.md`.
- Current Magenta root configuration is represented by `MagentaRootProperties`, with `path()` defaulting to `~/.magenta` and `defaultDataRoot()` returning `<root>/root`.
- Current runtime agent profiles are DB-backed through `AgentProfileRepository` and include `approved_tool_names_json` and `allowed_shell_commands_json`. Skill assignments should not be jammed into those tool fields.
- Current chat prompt assembly happens in `PromptContextAssembler`, which composes default, plan, task, execute-task, and worktype prompts. Skill catalog injection belongs near this boundary or in a dedicated assembler used by this boundary.
- Current chat tools are owned under `ai.chat.tool`; a dedicated activation tool belongs there if Magenta chooses tool-based skill activation.
- Current operational web UI has master/detail patterns in `OrchestrationController` for plans, jobs, projects, outputs, and agents. Work Area/project file editing patterns exist in `WorkAreaExplorerService`, `WorkAreaController`, and `WorkAreaExplorerFragments`.
- Existing HTMX selector patterns live under `api/web/selector/` and should be reused for agent assignment controls where practical.
- Existing navigation is sparse (`Home`, `Dashboard`, `Chat` in `AppNavigation`; orchestration nav inside `OrchestrationController`). The skill UI needs deliberate placement rather than hidden routes.

## Relevant Local Knowledge

- `agent-skills-specification-reference.md` summarizes the external spec but is not authoritative. It points at the same official pages and records expected progressive-disclosure concepts.
- `agent-scoped-assignment-lifecycle.md` warns that agent-scoped mutation routes must validate route agent ownership and treat mismatches like not found.
- `entity-selector-htmx-pattern.md` describes reusable HTMX selector behavior that should guide agent assignment UI.
- `plans-list-status-chip-and-delete-pattern.md` records a reusable master/detail list pattern and OOB reset behavior relevant to the skill browser/editor.
- `live-chat-mcp-workflow-testing.md` must be read before browser validation if the implementation changes chat/runtime activation behavior visible through the browser.

## External Specification Observations

- Official Agent Skills format requires a skill directory with `SKILL.md`, YAML frontmatter, Markdown body, and optional `scripts/`, `references/`, and `assets/` directories.
- Official `name` constraints include 1-64 characters, lowercase alphanumeric plus hyphen, no leading/trailing hyphen, no consecutive hyphens, and matching the parent directory.
- Official `description` is required, non-empty, up to 1024 characters, and should describe what the skill does and when to use it.
- Client implementation guidance recommends progressive disclosure: catalog metadata first, full instructions on activation, and resources loaded only when needed.
- Client implementation guidance recommends skipping or warning safely for malformed files rather than breaking the entire skill system.
- Creator guidance emphasizes concise, coherent skills and moving detailed material into references; scripts guidance emphasizes relative paths from the skill root and clear prerequisites.

## Architectural Gaps

- There is no first-class skill package, service, repository, parser, or activation/catalog domain.
- There is no root `skills/` repository contract in specs, docs, package guidance, or configuration.
- There is no DB-backed skill metadata table or assignment table.
- There is no skill assignment read model equivalent to agent tool assignment behavior.
- There is no runtime catalog filtering or activation deduplication surface.
- There is no skill API or UI surface for browse/create/edit/assign flows.
- Existing file browser services are Work Area/project scoped and cannot be reused blindly for application-root skill editing without a new confinement boundary.
- Existing docs and specs do not distinguish MVP root skills from deferred project-local/layered assignment behavior.

## Risks

- Spec drift risk: the local research file may be stale, so spec-sensitive implementation must verify official pages live.
- Filesystem risk: application-root skill editing could become a general filesystem editor if path confinement is weak.
- Prompt bloat risk: listing too much skill data in system prompts would violate progressive disclosure.
- Assignment ambiguity risk: future layers could leak into MVP unless the DB model and specs name active vs deferred targets clearly.
- Stale metadata risk: persisted records can drift from disk after file edits unless refresh and diagnostics are explicit.
- UI sprawl risk: adding a one-off file editor could diverge from SimplyPages/HTMX conventions and project browser patterns.
- Context retention risk: if activated skill content is represented as ordinary messages, compaction may remove it; MVP needs at least explicit deduplication and documented follow-up if full protected-context support is deferred.

## Candidate Targets To Inspect During Implementation

- `src/main/java/io/mindspice/magenta2/core/config/MagentaRootProperties.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/selector/`
- `src/main/java/io/mindspice/magenta2/api/web/WorkAreaExplorerFragments.java`
- `src/main/resources/static/css/orchestration.css`
- `docs/end-user/`
- `docs/technical/`
- `.internal-dev/specifications/`
- Package `AGENTS.md` files under affected packages.
