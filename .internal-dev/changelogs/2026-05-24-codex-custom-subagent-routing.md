# Date

2026-05-24

# Change Summary

Added global Codex custom agents for advanced planning, orchestration planning, implementation work, and validation/red-team review. Refactored the `advanced-planner` and `orchestrate-plan` skills into compact routers that preserve existing trigger phrases while directing work to the new agents.

The agents and routers now explicitly require `.internal-dev` as the default durable planning store when a repository provides it. Advanced plans and orchestration plans belong under `.internal-dev/plans/<task-slug>/`, reviews under `.internal-dev/reviews/`, and subagent prompts must tell workers and validators to read the relevant `.internal-dev` plan files before acting.

# Files

- `/home/hickelpickle/.codex/agents/advanced_planning_agent.toml`
- `/home/hickelpickle/.codex/agents/orchestration_planning_agent.toml`
- `/home/hickelpickle/.codex/agents/implementation_worker_agent.toml`
- `/home/hickelpickle/.codex/agents/validation_redteam_agent.toml`
- `/home/hickelpickle/.codex/skills/advanced-planner/SKILL.md`
- `/home/hickelpickle/.codex/skills/advanced-planner/agents/openai.yaml`
- `/home/hickelpickle/.codex/skills/orchestrate-plan/SKILL.md`
- `/home/hickelpickle/.codex/skills/orchestrate-plan/agents/openai.yaml`
- `.internal-dev/knowledge/codex-custom-subagent-routing.md`
- `.internal-dev/focus/decisions.md`

# Behavioral Impact

- Requests such as "create an advanced plan", "advanced planning", "technical implementation plan", "domain work units", "planning-first", "validation criteria", and "less capable worker" remain routed through `advanced-planner`, but the skill now directs the work to `advanced_planning_agent`.
- Requests such as "orchestrated plan" and "subagent orchestration" remain routed through `orchestrate-plan`, but the skill now directs the work to `orchestration_planning_agent`.
- Planning and orchestration agents are allowed to write planning artifacts but are forbidden from product-code implementation.
- Implementation workers are constrained to assigned plan units and must stop on plan flaws instead of expanding scope.
- Validation/red-team agents are read-only and must validate against written criteria, including `.internal-dev` closeout requirements.

# Risks

- Custom-agent availability depends on the active Codex runtime loading files from `/home/hickelpickle/.codex/agents/`.
- The configured model names follow the requested plan (`gpt-5.5`) and were validated as TOML, but actual runtime access still depends on the user's Codex environment.
- Global skill and custom-agent files live outside this repository, so only the `.internal-dev` documentation updates are represented in this repo's git state.

# Follow-up Items

- None for this setup pass.
