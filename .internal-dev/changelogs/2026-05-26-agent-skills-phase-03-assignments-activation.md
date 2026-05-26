# Date
2026-05-26

# Change Summary
- Implemented Phase 03 Agent Skills runtime assignment and activation backend.
- Added SQLite-backed agent skill assignment metadata (`agent_skill_assignments`) separate from `agent_profiles.approved_tool_names_json`.
- Added assigned/enabled/loadable runtime catalog filtering and concise model-visible `available_skills` disclosure via prompt assembly.
- Added dedicated `activate_skill` tool + service with assignment checks, body-only `SKILL.md` activation payload, resource listing without eager reads, and per-conversation deduplication.
- Added no-skill omission behavior for `activate_skill` tool exposure in chat tool filtering.
- Added focused tests for assignment filtering, activation success/failure, dedupe, prompt disclosure omission/append, and tool exposure gating.

# Files
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillTargetType.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillAssignment.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillAssignmentRepository.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillAssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillCatalogEntry.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillAgentContextResolver.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillRuntimeCatalogService.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillPromptCatalogAssembler.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillActivationOutcome.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillActivationResult.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AgentSkillActivationService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/skills/AgentSkillActivationTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/skills/AgentSkillToolConfiguration.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/skills/AGENTS.md`
- `src/test/java/io/mindspice/magenta2/ai/skills/AgentSkillAssignmentCatalogActivationTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssemblerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicyTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistryTest.java`
- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/services.md`
- `docs/technical/agent-skills.md`
- `docs/end-user/agent-skills.md`

# Behavioral Impact
- Agent skill assignments are now persisted independently from approved tool lists and can be enabled/disabled per agent target.
- Prompt-time skill disclosure appears only when assigned, enabled, loadable skills exist for the effective conversation agent context.
- `activate_skill` now loads full skill instructions as parsed `SKILL.md` body text, includes resource file listings, and deduplicates repeated activations in the same conversation.
- When no skills are available for a conversation, `activate_skill` is removed from mode-filtered tool callbacks.

# Specification Impact
- Updated architecture/services specifications to mark Phase 03 assignment/catalog/activation backend contracts as implemented.
- Updated end-user and technical Agent Skills docs to describe runtime catalog disclosure, dedicated activation behavior, and body-only activation choice.

# Risks
- Activation deduplication is currently in-process memory keyed by conversation id; it is not persisted across restarts.
- Effective agent resolution falls back to runtime default agent when no explicit conversation agent origin is present, so catalog visibility follows current runtime default behavior for normal chat conversations.

# Follow-up Items
- Phase 04 should expose assignment and skill file-management APIs while preserving path-confinement and malformed-skill safety.
- Phase 05 should deliver browser management UI and validate assignment/editing flows with focused Playwright checks.
