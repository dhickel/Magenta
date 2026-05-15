# Implementation Playbook

## Purpose

This playbook gives implementation agents concrete code targets, sequencing advice, and gotchas. Follow phase files for scope and this file for execution quality.

## Code Targets

- Planning/task core:
  - `src/main/java/io/mindspice/magenta2/ai/chat/plan/`
  - `src/main/java/io/mindspice/magenta2/ai/chat/tool/plan/`
  - `src/main/java/io/mindspice/magenta2/ai/chat/tool/task/`
  - `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- MVP task/workflow replacement:
  - `src/main/java/io/mindspice/magenta2/ai/chat/task/`
  - `src/main/java/io/mindspice/magenta2/ai/chat/workflow/`
- Runtime/orchestration:
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
  - new `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/`
- Web/API:
  - `src/main/java/io/mindspice/magenta2/api/web/`
  - `src/main/resources/static/js/orchestration/`
  - `src/main/resources/static/css/orchestration.css`
- Schema:
  - `src/main/resources/schema.sql`
  - tests that manually run schema from `src/test/java/io/mindspice/magenta2/`

## Recommended Sequencing

1. Start with schema and records, not controller routes.
2. Get repository tests passing before touching chat/model execution.
3. Move task behavior into unified plan service with a compatibility facade only if compile pressure demands it.
4. Update tool registry/tool names after service behavior is stable.
5. Add Docker/workspace output logic before workflow/job execution.
6. Add workflows before jobs/projects.
7. Add UI only after backend contracts are stable enough to dogfood.

## Best Practices

- Keep repositories responsible for SQL and JSON mapping only.
- Keep services responsible for use-case validation.
- Keep controllers thin and explicit about HTTP status.
- Keep model-facing prompts compact but unambiguous.
- Persist snapshots for runs. Never let later edits mutate historical run meaning.
- Use structured outputs. Do not parse assistant final messages to infer outputs.
- Use `Files.createDirectories` and normalized path checks for all managed paths.
- Always release workspace leases in terminal states.
- Return SSE emitters immediately and run work asynchronously.

## Gotchas

- Current `TaskValueType` includes `long_text` and `boolean`; the new requested type list does not. Do not accidentally preserve unsupported types in the new public schema unless you explicitly map them.
- Current task definitions require at least one output before approval. New behavior must allow zero outputs.
- Current plan `hasSavedPlan()` accepts deliverables or outputs. New task completion must distinguish deliverables from structured outputs.
- Current workflow service assumes two or three linear steps. Remove that constraint.
- Current job items use `AssignmentType`; new workflow/job node types should not be forced into that enum unless it still reads cleanly.
- Current orchestration UI defaults have previously drifted from backend enum wire values. Always derive options from backend enums or constants.
- Existing `ChatService.java` and `TurnPhase.java` may have unrelated user edits in the worktree. Do not overwrite them without reading and integrating.
- Destructive DB reset is allowed, but be explicit and test it. Do not silently half-migrate old rows.

## Review Checklist

- Does the new model make a task literally a finalized plan?
- Can no-input plans run without asking for missing inputs?
- Can no-output plans complete without fabricating outputs?
- Are deliverables represented even when outputs exist?
- Are user-facing outputs copied or written into output directories?
- Can workflow gates wait and resume without losing run context?
- Are Docker failures surfaced as runtime errors, not hidden fallback behavior?
- Does `/chat` still use the chat client and not orchestration app scripts?
- Are all new public routes covered by controller tests?

