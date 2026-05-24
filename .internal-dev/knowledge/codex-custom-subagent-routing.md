# Topic

Codex Custom Subagent Routing For Advanced Planning

# Source References

- `/home/hickelpickle/.codex/agents/advanced_planning_agent.toml`
- `/home/hickelpickle/.codex/agents/orchestration_planning_agent.toml`
- `/home/hickelpickle/.codex/agents/implementation_worker_agent.toml`
- `/home/hickelpickle/.codex/agents/validation_redteam_agent.toml`
- `/home/hickelpickle/.codex/skills/advanced-planner/SKILL.md`
- `/home/hickelpickle/.codex/skills/orchestrate-plan/SKILL.md`
- OpenAI Codex subagents documentation: `https://developers.openai.com/codex/subagents`

# Key Takeaways

- Global personal Codex custom agents are standalone TOML files under `/home/hickelpickle/.codex/agents/`.
- Each custom agent must define `name`, `description`, and `developer_instructions`; optional model, reasoning, sandbox, and nickname settings can tune the spawned session.
- The durable split is:
  - `advanced_planning_agent`: planning-only, criteria-first advanced plan suites.
  - `orchestration_planning_agent`: planning-only conversion of plan suites into serial worker/validator execution.
  - `implementation_worker_agent`: mutating heads-down worker constrained by an assigned plan unit.
  - `validation_redteam_agent`: read-only criteria-based validation and red-team review.
- The `advanced-planner` and `orchestrate-plan` skills should stay small. Their job is trigger routing and output-contract enforcement, not carrying the full role instructions.
- In repositories with `.internal-dev`, planning artifacts should default to `.internal-dev/plans/<task-slug>/`; review artifacts should default to `.internal-dev/reviews/`.
- Subagent prompts for Magenta work should tell workers and validators to read the relevant `.internal-dev` plan files first.

# Engine Relevance

- This keeps advanced planning, orchestration, implementation, and validation roles separate without relying on each main-thread agent to remember the full workflow from memory.
- Criteria-driven development is mandatory for this workflow: criteria first, plan mapped to criteria, worker executes against criteria, validator reports pass/fail per criterion, and remediation updates the plan when the original criteria are insufficient.
- Planning agents may write planning artifacts in `.internal-dev`, but they must not implement product code.
- Validation agents should treat missing `.internal-dev` closeout as a finding when repo guidance requires closeout artifacts.

# Open Questions

- Whether future Codex runtimes will expose these custom agent names directly through every subagent tool surface, or only through the CLI/app custom-agent selector.
