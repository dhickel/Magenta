# Config Architecture

## Design intent

`RuntimeConfig` provides deterministic runtime bootstrap from `configs/magenta.yaml` and include sets.

## Responsibilities

- Parse root config document.
- Resolve include globs for models/agents/prompts.
- Load model and agent YAML docs.
- Load prompt markdown content and derive prompt IDs.
- Resolve `baseAgentId`, `compactionAgentId`, and `maxTurns` defaults.
- Load terminal UI defaults from `terminal.rendering`, `terminal.security`, and `terminal.tools`.
- Validate runtime graph before runtime startup.

## Explicit non-goals

- config hot reload
- dynamic runtime graph mutation
- env var / CLI precedence merging (declared target, not yet implemented in this runtime slice)

## Invariants

- Returned maps (`modelsById`, `agentsById`, `promptsById`) are immutable.
- Base agent and compaction agent must exist and be enabled.
- Enabled agents must reference enabled models.
- All agent prompt IDs must resolve.
- Unknown YAML keys fail deserialization.
- Unsupported terminal config tokens (color names, security visibility, tool output format) fail startup.

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

## Extension points

- Add fields in `ModelConfig`/`AgentConfig` nested records.
- Add additional validation rules in `validate(...)`.
- Keep record-centric structure unless a second independent config graph emerges.

## Known constraints

- Duplicate IDs fail fast with explicit source file diagnostics.
- Include resolution walks full config tree per pattern.
