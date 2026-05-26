# Target Design

## Contract Summary

Magenta runtime should provide two related behaviors:

1. Generate starter `AGENTS.md` guidance once when Magenta first creates an agent workspace.
2. Resolve and inject applicable `AGENTS.md` instructions for model-backed agent work in project, Work Area, and effective workspace contexts.

## Starter Workspace Guidance

Starter file:

- Path: root of the newly-created agent execution workspace.
- Name: `AGENTS.md`.
- Creation rule: write only when the workspace root did not previously exist or did not contain `AGENTS.md` at first creation time.
- No-overwrite rule: if `AGENTS.md` exists, do nothing; do not compare content, hash, normalize, or regenerate.
- Storage rule: hard-code starter content in application code for this phase.

Minimum starter topics:

- Workspace root expectations.
- `/home` as persistent agent-owned files/scripts.
- `/runs` as run staging.
- `<runId>/outputs` as agent-written output staging.
- `/workareas` as user-controlled Work Areas.
- Project/job-bound work expectations, including that jobs bind to agent/project/Work Area context and do not own new workspace roots.
- User prompt and task instructions override local guidance.

Implementation note: current docs describe physical paths under `workspace/<agentWorkspaceId>/...`. Starter wording may use model-facing aliases (`/home`, `/runs`, `/workareas`) only if it clearly maps them to the workspace root and existing runtime alias behavior.

## Resolver Semantics

Inputs:

- Bound root: project root, selected Work Area root, or effective durable workspace root.
- Active working path: file or directory path relative to or under the bound root.

Output:

- Ordered applicable layers from bound root toward active working path.
- Each layer includes path, content, relative directory, and precedence rank.

Rules:

- Plain Markdown only; do not impose a schema.
- Missing file returns an empty layer set.
- Root-only returns the root layer.
- Nested-only returns the nested layer if the nested file is under the bound root.
- Root-plus-nested returns both root and nested layers.
- All ancestor layers remain active as context.
- Closest layer wins only for conflicts.
- Moving to a sibling subtree removes or de-emphasizes the prior subtree's nested layer.
- Attempts to resolve outside the bound root fail closed.
- Symlink escape must fail closed.

## Prompt And Context Injection

The implementation may choose exact prompt formatting, but it must make the runtime ordering and precedence clear to the model:

1. Base/system/mode/worktype context remains in the existing order unless a narrower implementation need is proven.
2. User task prompt remains highest precedence.
3. `AGENTS.md` layers are included with:
   - their source path relative to the bound root;
   - ordering from broadest to closest;
   - explicit text that closest layers override ancestors only on conflict;
   - explicit text that ancestor guidance remains active unless contradicted.

The injection should be available to model-backed agent work that has an `OrchestrationTaskContext`. If ordinary browser chat without project/workspace binding cannot resolve a bound root, it must omit `AGENTS.md` context rather than read arbitrary local files.

## Project And Work Area Binding

Resolution root must be explicit:

- Project-bound job/task: use the project/effective workspace root unless a selected Work Area narrows `workspace/`; if narrowed, clarify whether instructions are resolved from the Work Area root or broader project root before coding. Preferred behavior is broader owner root baseline plus selected Work Area nested context when the selected Work Area is inside that root.
- Agent-only job/task: use the agent effective workspace root, narrowed to selected Work Area where applicable.
- Direct active path from file/shell tools: re-resolve against the actual confined target path.
- No bound root: no runtime `AGENTS.md` injection.

If implementation cannot represent broader-root baseline plus selected-Work-Area narrowing cleanly, stop and return to planning.

## Documentation Target

Specs and docs must clearly say:

- Magenta intentionally preserves ancestor `AGENTS.md` context.
- The closest applicable file has conflict precedence.
- Explicit user prompts override all `AGENTS.md` instructions.
- Starter `AGENTS.md` is generated only for new agent workspaces and never overwritten.
- Runtime resolver is confined to the bound root.

## Browser Applicability

No browser validation is expected unless a visible page, fragment, or form changes. If UI changes occur, add a focused Playwright checklist before implementation proceeds past that unit.
