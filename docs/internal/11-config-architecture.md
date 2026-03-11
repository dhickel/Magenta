# Config Architecture

## Design intent

`RuntimeConfig` provides deterministic runtime bootstrap from `configs/magenta.yaml` and include sets.

## Responsibilities

- Parse root config document.
- Resolve include globs for models/agents/prompts/tasks/workflows.
- Load model/agent/task/workflow YAML docs.
- Load prompt markdown content and derive prompt IDs from relative prompt paths.
- Derive model/agent/task/workflow IDs from relative file paths (without extension).
- Expand `*` references to full domain sets and resolve basename/path references.
- Resolve `baseAgentId`, `compactionAgentId`, and `maxTurns` defaults.
- Load terminal UI defaults from `terminal.rendering`, `terminal.security`, and `terminal.tools`.
- Load security policy defaults from `security` (mode, tools, command rules, approved roots, web access).
- Validate runtime graph before runtime startup.

## Explicit non-goals

- config hot reload
- dynamic runtime graph mutation
- env var / CLI precedence merging (declared target, not yet implemented in this runtime slice)

## Invariants

- Returned maps (`modelsById`, `agentsById`, `promptsById`, `tasksById`, `workflowsById`) are immutable.
- Base agent and compaction agent must exist and be enabled.
- Enabled agents must reference enabled models.
- All enabled references (prompt/task/workflow) must resolve.
- Workflow dependency graph for enabled workflows must be acyclic.
- Unknown YAML keys fail deserialization.
- Unsupported terminal config tokens (color names, security visibility, tool output format) fail startup.
- Security mode/action tokens map to closed enums and fail startup on unknown values.

## State transitions

```text
read magenta.yaml
-> parse root document
-> resolve include file list
-> parse model/agent docs + read prompts
-> derive defaults (base/compaction/maxTurns)
-> validate graph
-> create RuntimeConfig record
```

## Failure behavior

- Missing `configs/magenta.yaml`: startup exception.
- Parse errors: include file path and line/column details.
- Graph errors: explicit illegal-state message (missing/disabled refs).
- Invalid security mode/rule tokens: startup parse failure with source location.

## Extension points

- Add fields in `ModelConfig`/`AgentConfig` nested records.
- Add additional validation rules in `validate(...)`.
- Keep record-centric structure unless a second independent config graph emerges.

## Known constraints

- Duplicate file-derived IDs fail fast with explicit source file diagnostics.
- Include resolution walks full config tree per pattern.
- `allowedPaths` is configured as path roots; target path resolution and authorization semantics are enforced at runtime by `SecurityManager`.
- Runtime compaction quality depends on `compactionAgentId` prompt selection; a dedicated summarizer prompt is recommended over reusing the main agent persona prompt.
