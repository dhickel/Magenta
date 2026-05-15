# Docker-Backed Alpha E2E Validation Plan Suite

## Purpose

This suite defines a Playwright-first validation campaign for Magenta's alpha contract after the Docker execution environment was previously deferred. The goal is to prove the real end-user surface: agents, Docker lifecycle, plans/tasks, workflows, gates, inbox approvals, jobs, projects, schedules, outputs, workspaces, model overrides, and chat surfaces.

## Binding Rule

Every subplan must validate through Playwright against a running Spring Boot app. Direct repository tests, Maven commands, Docker CLI checks, or database inspection may be used as setup/supporting evidence, but they do not replace browser-origin proof.

## Execution Order

1. `00-orchestration-plan.md`
2. `01-playwright-harness-and-docker-preflight.md`
3. `02-agent-docker-lifecycle-and-management-ui.md`
4. `03-plans-tasks-and-docker-execution.md`
5. `04-workflows-gates-inbox-and-resume.md`
6. `05-jobs-projects-schedules-and-agent-assignment.md`
7. `06-chat-model-overrides-and-agent-surfaces.md`
8. `07-outputs-workspaces-and-artifact-contract.md`
9. `08-consolidated-report-and-remediation-gate.md`

## Subagent Model

Use a fresh-context testing agent per subplan. Each agent must write its own evidence notes into `.internal-dev/reviews/docker-backed-alpha-e2e-validation/` and append defects to the shared issue ledger defined in `08-consolidated-report-and-remediation-gate.md`.

## Stop Conditions

- If Docker/Podman is unavailable, stop and report the exact blocker. Do not replace Docker validation with host execution.
- If Playwright cannot control the browser, stop and report the exact MCP/browser blocker. Do not replace browser validation with curl.
- If a feature is missing or broken, record it as a bug/contract deficiency and continue only when the next validation target is independent.
