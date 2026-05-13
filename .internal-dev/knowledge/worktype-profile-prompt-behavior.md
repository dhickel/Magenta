# Topic

Worktype profiles and their prompt assembly behavior in the plan/task system.

# Source References

- `src/main/java/io/mindspice/magenta2/ai/chat/plan/WorkTypeProfile.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/WorkTypeProfileService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java`
- `src/main/java/io/mindspice/magenta2/api/web/PlanController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- Phase 03 handoff notes in `.internal-dev/plans/operational-ui-contract-refactor/phase_handoff_notes.md`

# Key Takeaways

## WorkTypeProfile enum

Three profiles replace the old `PromptProfile`:

| Profile | Legacy mapping | System text focus |
|---|---|---|
| `CODING_CENTRIC` | `CODING` | Code generation, refactoring, debugging, architecture |
| `DATA_CENTRIC` | Everything else (default) | Data analysis, transformation, reporting |
| `RESEARCH_CENTRIC` | `RESEARCH` | Research, exploration, analysis, literature |

## Legacy mapping

```java
// Legacy PromptProfile values map to WorkTypeProfile:
"CODING"    -> CODING_CENTRIC
"RESEARCH"  -> RESEARCH_CENTRIC
default     -> DATA_CENTRIC
```

The `promptProfile` field in `PlanDefinition` now stores `WorkTypeProfile.name()` rather than `PromptProfile.name()`.

## Append-only system text

`WorkTypeProfileService.getAppendSystemText(WorkTypeProfile)` returns a fixed system prompt fragment appended to the agent context when a plan is submitted for execution:

- **CODING_CENTRIC**: "You are working in a coding-centric task. Prefer concrete code output, implementation details, and technical correctness. When generating code, include necessary imports, handle edge cases, and write idiomatic solutions."
- **DATA_CENTRIC**: "You are working in a data-centric task. Prefer structured output, data transformations, analytical reasoning, and clear reporting. When working with data, validate inputs, handle missing values, and explain your methodology."
- **RESEARCH_CENTRIC**: "You are working in a research-centric task. Prefer thorough exploration, well-reasoned analysis, and evidence-based conclusions. Consider alternative viewpoints, cite sources when possible, and identify areas of uncertainty."

## Prompt assembly integration

`PromptContextAssembler` injects `WorkTypeProfileService` as an optional dependency. When present:
1. During plan submission, the plan's `workTypeProfile` is resolved to a `WorkTypeProfile` enum.
2. `getAppendSystemText(profile)` returns the profile-specific system text.
3. The text is appended to the system prompt context for the agent turn.

`ChatService` passes `WorkTypeProfileService` through its constructor to `PromptContextAssembler`. All three constructors accept the optional parameter.

## Plan create/update contract

Both `PlanCreateRequest` and `PlanUpdateRequest` accept:
- `workTypeProfile` (String): "CODING_CENTRIC", "DATA_CENTRIC", or "RESEARCH_CENTRIC"
- `promptProfile` (String, legacy): Mapped to `WorkTypeProfile` for backward compatibility

If both are provided, `workTypeProfile` takes precedence.

## UI representation

The plan editor renders a `<select name="workTypeProfile">` with three options:
- Coding-centric (CODING_CENTRIC)
- Data-centric (DATA_CENTRIC)
- Research-centric (RESEARCH_CENTRIC)

The old `promptProfile` text field is removed. The label reads "Worktype" (not "Prompt Profile").

# Engine Relevance

When adding new worktype profiles:
1. Add enum value to `WorkTypeProfile`
2. Add system text in `WorkTypeProfileService.getAppendSystemText()`
3. Add `<option>` to the HTMX editor form
4. Add legacy mapping if needed
5. Update tests in `WorkTypeProfileTest` and `WorkTypeProfileServiceTest`

The append-only design means the system text is additive to whatever base system prompt the agent already has. Future profiles can be added without changing the prompt assembly pipeline.

# Open Questions

- Should worktype profiles influence tool selection (e.g., coding-centric gets shell/code tools, data-centric gets data tools)?
- Should profiles be user-extensible with custom system text?
- Should the plan submit form show the profile text preview before submission?
- Should profile-specific model recommendations be added?
