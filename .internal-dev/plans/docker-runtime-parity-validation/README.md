# Docker Runtime Parity Validation Plan Suite

## Purpose

This suite defines the post-integration validation campaign for Magenta now that Docker/Podman is the required execution environment for agents. It is stricter than the earlier alpha Docker validation suite: success means proving that agent execution actually happens inside managed containers, that no user-facing execution path silently falls back to host behavior, that mounted directories and workspace links behave correctly, and that the operational UI exposes the backend capabilities an operator needs.

## Binding Rules

- Docker-backed execution is the only acceptable success path for agent work. Host fallback is a defect, not a substitute.
- Playwright is the primary validation harness for all user-facing behavior. Shell, repository, API, and database checks may support findings but do not replace browser-origin proof.
- UI parity must be validated against backend capability. A backend feature that is not reachable, understandable, or truthful from the UI is a defect or explicitly tracked gap.
- Docker control validation must distinguish desired state from actual daemon/container state. A UI status that lies about a running container is a blocker.

## Execution Order

1. `00-orchestration-plan.md`
2. `01-docker-mandate-and-playwright-harness.md`
3. `02-docker-control-and-status-surface.md`
4. `03-agent-execution-provenance.md`
5. `04-workspaces-mounts-and-linked-directories.md`
6. `05-backend-ui-parity-audit.md`
7. `06-end-user-operational-flows.md`
8. `07-failure-modes-and-no-fallback-contract.md`
9. `08-final-report-and-remediation-gate.md`

## Recommended Agent Model

Use one validation agent per phase after phase `01` passes. Phases `02`, `03`, `04`, and `05` can run in parallel because they inspect disjoint evidence categories. Phase `06` depends on the earlier findings because it exercises full workflows. Phase `07` should run after the happy-path evidence exists. Phase `08` consolidates all results and decides whether the plan can be archived.

## Required Outputs

Each phase writes evidence under:

- `.internal-dev/reviews/docker-runtime-parity-validation/`

Confirmed defects go under:

- `.internal-dev/bugs/`

Reusable findings go under:

- `.internal-dev/knowledge/`

## Stop Conditions

- If Docker/Podman is unavailable, stop and report the exact blocker. Do not continue with host-only validation.
- If Playwright cannot control a browser, stop and report the exact MCP/browser blocker. Do not replace browser validation with curl-only checks.
- If any execution path completes without provable container provenance, classify it as a blocker until proven otherwise.
- If the UI exposes stale or false Docker status, treat that as an operator-control blocker even when backend execution still works.
