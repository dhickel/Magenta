# Phase 03 Worker Directive: Assignments, Runtime Catalog, And Activation

## Objective

Implement DB-backed agent skill assignments, runtime catalog filtering, model-visible skill catalog disclosure, full skill body activation/loading, and activation deduplication.

## Agent Assignment

- Worker: `implementation_worker_agent`, `gpt-5.3`, high reasoning.
- Validator: `validation_redteam_agent`, `gpt-5.5`, high reasoning.

## Required Reading

- Phase 02 implementation report and validator result.
- Official Agent Skills specification and client implementation guide.
- `shared/senior-engineer-guidance.md`
- `shared/implementation-notes.md`
- `shared/validation-matrix.md`
- Package guides:
  - `src/main/java/io/mindspice/magenta2/ai/skills/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- Existing code:
  - `PromptContextAssembler`
  - `ChatToolRegistry`
  - `ToolAccessPolicy`
  - `AgentProfileService`
  - `AgentProfileRepository`
  - runtime/default agent resolution paths used by chat.

## Editable Targets

- `src/main/java/io/mindspice/magenta2/ai/skills/`
- `src/test/java/io/mindspice/magenta2/ai/skills/`
- Narrow chat integration files as needed:
  - `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`
  - `src/main/java/io/mindspice/magenta2/ai/chat/tool/`
  - `src/test/java/io/mindspice/magenta2/ai/chat/`
- Agent assignment lookup integration if needed:
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/`
- Specs/docs/knowledge if implementation choices change contracts.

## Forbidden Scope

- Do not build browser UI or file-editing APIs in this phase.
- Do not store skill assignments in `approved_tool_names_json`.
- Do not enforce `allowed-tools` as a runtime permission boundary unless separately approved.
- Do not load project-local skills.
- Do not refactor the entire chat prompt pipeline beyond the smallest skill catalog hook.

## Implementation Steps

1. Re-open official client implementation guidance for catalog, activation, filtering, no-skill behavior, resource listing, and deduplication.
2. Add skill assignment repository/service:
   - MVP target type `AGENT`;
   - enabled/disabled assignment state;
   - duplicate prevention;
   - lookup by agent id;
   - safe behavior when skill metadata is missing/invalid.
3. Add runtime catalog construction:
   - only valid assigned enabled skills;
   - omit invalid/malformed/unassigned skills;
   - omit entire catalog and instructions when none available.
4. Add model-visible disclosure:
   - preferred: append concise catalog instructions through `PromptContextAssembler` using a collaborator;
   - keep PLAN/TASK/EXECUTE_TASK behavior coherent and covered by tests.
5. Add dedicated activation service/tool:
   - lookup by assigned skill name;
   - return full `SKILL.md` body or documented full-file/body-only choice;
   - include skill directory/resource listing;
   - do not read resources eagerly;
   - validate unavailable/invalid/unassigned skill failures safely.
6. Add activation deduplication:
   - per conversation/session context;
   - duplicate activation no-ops or returns explicit already-active response;
   - tests prove no duplicate instruction injection.
7. Add tests for assigned/unassigned availability, catalog omission, activation, malformed activation failure, and dedupe.
8. Update docs/specs/knowledge if catalog format or activation mechanism differs from target design.

## Acceptance Criteria

- Agent skill assignments are DB-backed and independent from tool assignments.
- Assigned valid skills appear in runtime catalog.
- Unassigned/invalid/malformed skills are hidden from runtime catalog.
- Catalog disclosure is metadata-only and concise.
- Full activation loads skill instructions and lists resources without eager resource reads.
- Activation deduplication is tested.
- No-skill case omits catalog/instructions/tool exposure where practical.

## Negative Checks

- No skill body appears in startup/catalog prompt unless activation happened.
- No duplicate activation content appears after repeated activation.
- No unassigned skill can be activated for an agent context.
- `allowed-tools` is not used as an enforcement promise unless documented and tested.

## Validation Commands

```bash
mvn -Dtest='*AgentSkill*Assignment*,*AgentSkill*Catalog*,*AgentSkill*Activation*,*PromptContext*,*ChatTool*' test
rg -n "activate_skill|available_skills|AgentSkill|approved_tool_names_json|allowed-tools" src/main/java src/test/java .internal-dev/specifications docs
```

## Stop Conditions

- Stop if chat cannot determine effective agent identity for catalog filtering without broad architecture changes.
- Stop if activation deduplication needs persistent context changes larger than this phase.
- Stop if no-skill tool registration behavior conflicts with Spring AI tool registry constraints.

## Senior Guidance

Keep the activation boundary explicit. A dedicated tool is preferable because it avoids exposing the application root through generic file tools and gives Magenta a clean diagnostic/deduplication point.

## Do Not Close Unless

- Assignment, catalog filtering, activation, dedupe, and malformed failure tests pass.
- The worker report states whether activation returns body-only or full file and why.
- Specs/docs reflect the chosen activation behavior.
