# Phase 02 Worker Directive: Skill Repository, Parser, Discovery, Metadata

## Objective

Implement the backend skill domain foundation: Magenta root skill repository resolution, safe filesystem access, official-spec-aware `SKILL.md` parsing/validation, discovery, metadata persistence, diagnostics, optional directory visibility, and reload-after-edit behavior.

## Agent Assignment

- Worker: `implementation_worker_agent`, `gpt-5.3`, high reasoning.
- Validator: `validation_redteam_agent`, `gpt-5.5`, high reasoning.

## Required Reading

- This suite:
  - `00-specification-lock.md`
  - `01-current-state-analysis.md`
  - `02-target-design.md`
  - `shared/senior-engineer-guidance.md`
  - `shared/implementation-notes.md`
  - `shared/validation-matrix.md`
- Official Agent Skills pages listed in `00-specification-lock.md`; re-open them before parser/discovery work.
- Affected package guides:
  - `src/main/java/io/mindspice/magenta2/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/core/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
  - new `src/main/java/io/mindspice/magenta2/ai/skills/AGENTS.md` if Phase 01 created it.
- Existing code:
  - `src/main/java/io/mindspice/magenta2/core/config/MagentaRootProperties.java`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileRepository.java`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkAreaExplorerService.java` for confinement patterns only.

## Editable Targets

- New package: `src/main/java/io/mindspice/magenta2/ai/skills/`
- New tests: `src/test/java/io/mindspice/magenta2/ai/skills/`
- Existing config/root classes only if needed for root skill path helper:
  - `src/main/java/io/mindspice/magenta2/core/config/MagentaRootProperties.java`
- Affected docs/specs/knowledge from Phase 01 if implementation changes contract details.

## Forbidden Scope

- Do not implement chat prompt catalog injection, activation tool registration, skill assignment UI, or web pages in this phase.
- Do not edit existing agent profile schema for assignments.
- Do not implement project-local `.agents/skills/` scanning.
- Do not run scripts or load resource files into model context.
- Do not add broad migration tooling.

## Implementation Steps

1. Verify official spec requirements for directory shape, frontmatter fields, `name`, `description`, optional directories, and validation.
2. Add a skill domain package with records/services/repositories equivalent to the target design.
3. Implement root skill repository resolution under `MagentaRootProperties.path()/skills`.
4. Implement safe skill path resolution:
   - route/slug validation;
   - normalized paths;
   - realpath confinement;
   - traversal and symlink escape rejection;
   - no access outside `skills/`.
5. Implement `SKILL.md` parser:
   - frontmatter delimiter extraction;
   - YAML parser use;
   - required/optional fields;
   - body extraction;
   - diagnostic codes.
6. Implement validation per official spec and client-guide decisions:
   - valid frontmatter;
   - missing/invalid required fields;
   - directory/name mismatch;
   - malformed YAML;
   - field length/character constraints;
   - warning vs skip status where deliberately chosen and documented.
7. Implement discovery/catalog metadata refresh:
   - scan immediate child skill directories under root;
   - record valid, warning, and malformed skills safely;
   - detect optional `scripts/`, `references/`, `assets/`;
   - persist diagnostics and content hash/stale marker.
8. Add repository/service tests with temp roots and SQLite test DB.
9. Update specs/docs/knowledge if exact load/skip behavior differs from the plan.

## Acceptance Criteria

- `skills/<skill-name>/SKILL.md` discovery works from a test Magenta root.
- Parser handles valid minimal and optional-field examples.
- Missing/invalid required fields produce deterministic diagnostics and expected load/skip status.
- Parent directory/frontmatter mismatch is handled deliberately and documented.
- Optional `scripts/`, `references/`, `assets/` visibility is represented without reading resources.
- Malformed skills do not crash discovery.
- Refresh after file edits updates metadata/body hash/diagnostics.
- DB metadata is separate from agent tool assignment fields.

## Negative Checks

- No `../` or symlink escape can read/write outside root skills directory.
- No resource directory is eagerly loaded into any prompt/model-facing content.
- No references to project-local `.agents/skills/` are active implementation paths.
- No parser behavior is based only on the local research report.

## Validation Commands

```bash
mvn -Dtest='*AgentSkill*Parser*,*AgentSkill*Repository*,*AgentSkill*Catalog*,*AgentSkill*Discovery*' test
rg -n "\\.agents/skills|allowed-tools|SKILL.md|skills/" src/main/java/io/mindspice/magenta2/ai/skills src/test/java/io/mindspice/magenta2/ai/skills .internal-dev/specifications docs
```

## Stop Conditions

- Stop if strict official spec validation and client-guide leniency conflict in a way that changes acceptance criteria.
- Stop if root path confinement cannot be tested reliably.
- Stop if schema shape requires migration tooling not currently present.

## Senior Guidance

Prefer small explicit services and records. Parser diagnostics are part of the product contract; do not bury them in logs only.

## Do Not Close Unless

- Official spec behavior checked and recorded.
- Parser/discovery tests cover every minimum case from the validation matrix relevant to Phase 02.
- Metadata refresh after edits is tested.
- Malformed skills are visible as diagnostics and cannot crash discovery.
