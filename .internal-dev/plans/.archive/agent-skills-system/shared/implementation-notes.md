# Implementation Notes

## Suggested Package Layout

```text
src/main/java/io/mindspice/magenta2/ai/skills/
  AgentSkill.java
  AgentSkillAssignment.java
  AgentSkillAssignmentRepository.java
  AgentSkillAssignmentService.java
  AgentSkillCatalogService.java
  AgentSkillActivationService.java
  AgentSkillParser.java
  AgentSkillRepository.java
  AgentSkillRepositoryService.java
  AgentSkillPromptCatalogAssembler.java
  AgentSkillDiagnostic.java
  AgentSkillStatus.java
  AgentSkillTargetType.java
  AGENTS.md
```

Keep names plain. Adjust file names if local code conventions suggest better records, but do not hide skill behavior inside unrelated orchestration/runtime classes.

## Root Path Handling

- Inject `MagentaRootProperties`.
- Resolve repository root as `magentaRoot.path().resolve("skills").normalize()`.
- Create the root lazily or during service startup, but avoid failing startup solely because no skills exist.
- Store paths relative to the skill repository root in DB when practical.
- Never store or display unrelated application-root paths in browser UI.
- Use `Path.normalize()`, `toRealPath(LinkOption.NOFOLLOW_LINKS)` where needed, and explicit `startsWith(rootRealPath)` checks for reads/writes.
- Reject symlink escapes and traversal.

## Parsing Notes

- Use a real YAML parser already available through the project stack if possible. Do not parse YAML with ad hoc string splitting beyond frontmatter delimiter extraction.
- Extract frontmatter only when the file starts with `---` and has a closing delimiter.
- The body is the Markdown after the closing delimiter.
- Validate official constraints, but decide strict load/skip/warn behavior only after re-checking the official spec and client implementation guide.
- Represent diagnostics with stable codes so tests and UI do not assert freeform prose.

## Persistence Notes

- Use dedicated tables rather than extending `agent_profiles.approved_tool_names_json`.
- Include timestamps and a content hash/stale marker so edit/refresh behavior is observable.
- Assignment rows should be stable and auditable. Prefer `target_type='AGENT'` plus `target_id=<agentId>` for MVP.
- Future target types can be documented in specs/deferred docs without active code paths if that keeps the DB shape future-safe.

## Runtime Notes

- Runtime catalog lookup needs an agent identity. Follow existing chat/default agent resolution rather than adding a parallel default agent concept.
- When no skills are assigned or valid, omit the catalog and activation instructions.
- If using a dedicated activation tool, do not register it or expose valid names when no valid assigned skills exist.
- Activation deduplication can be stored in conversation/session-scoped service state or durable chat metadata if a suitable existing place exists. Choose the smallest reliable surface and test it.
- If compaction protection cannot be fully implemented in MVP, document it as deferred and ensure activation outputs are structured so future compaction can preserve them.

## UI Notes

- Start from `/plans` master/detail behavior for list/detail/editor flow.
- Start from project/Work Area file editor behavior for safe text file editing, but re-implement confinement around the skill repository root rather than reusing Work Area roots.
- Use existing `EntitySelectorComponents` for agent assignment when practical.
- Add skill navigation intentionally. If using the operational nav inside `OrchestrationController`, ensure `/skills` is visible and consistent.
- Keep UI copy concise and operational. Avoid tutorial text inside the app; put usage guidance in docs.

## Documentation Notes

- End-user docs should explain how to create/edit/assign skills, what `SKILL.md` contains, what optional directories are for, and that MVP scans Magenta root `skills/` only.
- Technical docs should explain parser/loader/catalog/activation/assignment contracts, schema, API routes, and deferred layers.
- Knowledge notes should capture reusable spec-validation lessons and UI reuse decisions discovered during implementation.
