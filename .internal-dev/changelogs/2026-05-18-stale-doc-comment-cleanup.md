# Date

2026-05-18

# Change Summary

Updated active validation guidance so it no longer names Docker/Podman daemon-backed execution as the alpha-blocking runtime example. The current wording points at filesystem/workspace-backed execution validation, matching the public-alpha remediation runtime contract.

# Files

- `AGENTS.md`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/knowledge/runtime-wording-cleanup.md`

# Behavioral Impact

Documentation-only change. Runtime behavior, tests, routes, and static assets are unchanged.

# Risks

Low. The retained active `container` wording is limited to DOM/HTMX/CSS containers and local variable names, not execution isolation or Docker/Podman claims.

# Follow-up Items

- External validator should rerun the stale wording scan and bounded startup before the orchestrator commits subplan 03.
