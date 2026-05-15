# Date

2026-05-13

# Change Summary

Added a Playwright-first orchestration plan suite for validating Magenta's full alpha operational contract with Docker-backed agent execution enabled. The suite breaks validation into clean-context subplans for Docker readiness, agent lifecycle, plans/tasks, workflows/gates/inbox, jobs/projects/schedules, chat/model overrides, outputs/workspaces, and consolidated reporting.

# Files

- `.internal-dev/plans/docker-backed-alpha-e2e-validation/README.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/00-orchestration-plan.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/01-playwright-harness-and-docker-preflight.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/02-agent-docker-lifecycle-and-management-ui.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/03-plans-tasks-and-docker-execution.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/04-workflows-gates-inbox-and-resume.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/05-jobs-projects-schedules-and-agent-assignment.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/06-chat-model-overrides-and-agent-surfaces.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/07-outputs-workspaces-and-artifact-contract.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/08-consolidated-report-and-remediation-gate.md`
- `.internal-dev/knowledge/docker-backed-playwright-validation-policy.md`

# Behavioral Impact

No production behavior changed. This is a durable validation and orchestration plan for proving the current application through Playwright and Docker before alpha.

# Risks

The plan assumes Playwright can drive the local app and Docker/Podman is available. If either is blocked, the plan requires stopping and reporting the blocker instead of substituting narrower validation.

# Follow-up Items

Execute the subplans with fresh-context testing agents and consolidate all findings into the planned issue ledger and final readiness review.
