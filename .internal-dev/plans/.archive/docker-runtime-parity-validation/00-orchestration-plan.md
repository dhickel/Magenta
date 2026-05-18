# Orchestration Plan: Docker Runtime Parity Validation

## Objective

Validate Magenta as a Docker-first operational system now that container integration is expected to be the default runtime for agents. The campaign must prove that agent work executes through managed Docker/Podman containers, that `/home/agent`, `/workspace`, and `/output` behavior is correct, that the UI fully exposes the useful backend contract, and that Docker lifecycle/status controls are accurate enough for real operators to trust.

## Inputs And Assumptions

Confirmed inputs:
- `AGENTS.md`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- `.internal-dev/knowledge/docker-backed-playwright-validation-policy.md`
- `.internal-dev/knowledge/docker-backed-alpha-playwright-validation.md`
- `.internal-dev/plans/docker-backed-alpha-e2e-validation/`
- `.internal-dev/reviews/docker-backed-alpha-remediation/2026-05-14-final-alpha-validation.md`
- `.internal-dev/bugs/2026-05-14-docker-stop-status-mismatch/report.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/docker/`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/`
- `src/main/java/io/mindspice/magenta2/api/web/RuntimeController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`

Assumptions to verify during phase `01`:
- Docker/Podman is available through the configured `DOCKER_HOST`.
- `magenta.docker.enabled=true` is the intended default for this campaign.
- The configured agent image is available locally.
- Playwright can reach the chosen app origin from an allowed origin.
- Docker execution is now intended to be mandatory for all agent shell/tool work, not only selected flows.

## Scope

In scope:
- Docker readiness, daemon/image visibility, and failure reporting.
- Managed agent container lifecycle: start, stop, restart, refresh, disable/enable, archive/delete implications.
- Runtime provenance for agent task execution, workflow task nodes, jobs, assignments, and chat-triggered plan execution where relevant.
- Mount and directory contract for `/home/agent`, `/workspace`, `/output`, temp workspaces, agent homes, job/project workspaces, linked workspaces, output directories, and cleanup behavior.
- UI/backend parity across Docker runtime status, agent controls, workspace links, outputs, jobs, projects, workflows, schedules, inbox, model overrides, and chat surfaces.
- Negative validation that proves there is no silent host fallback when Docker is unavailable or a managed container is missing.
- Consolidated blocker list, remediation ordering, and archive/no-archive decision.

Out of scope:
- Building new product features during validation except tiny harness/evidence helpers.
- Kubernetes, Compose, registry auth, multi-host scheduling, or external artifact storage.
- Treating direct API access as acceptable UX when the backend has a user-facing operational feature that should be exposed in the UI.
- Accepting legacy host-execution behavior for backward compatibility.

## Current-State Analysis

Relevant existing architecture:
- `DockerRuntimeClient` validates daemon reachability and agent image availability.
- `AgentContainerRuntimeService` owns one managed container per agent and mounts `/home/agent`, `/workspace`, and `/output`.
- `AgentShellToolService` routes agent shell execution into the managed Docker container when agent context exists and is expected to fail if the container runtime is unavailable.
- `PlanService` injects Docker runtime instructions into execution prompts.
- Workspace services own persistent agent/job/project directories, temp task/workflow directories, output directories, links, and write leases.
- `RuntimeController` exposes runtime health, while `OrchestrationController` exposes HTMX-driven agent Docker controls and status fragments.
- The prior validation loop proved one Docker-backed task run and output artifact path, but left the Docker stop-status mismatch open and did not fully prove UI/backend parity across the whole operational surface.

Known risk already present:
- `2026-05-14-docker-stop-status-mismatch` shows the UI can report `IDLE` while the real container is still running. This suite must verify truthful post-action status, not only endpoint success.

## Target Design

The validation campaign should produce three independent proofs:

1. Runtime truth proof
- Every meaningful agent execution path records evidence that work occurred inside the managed container.
- No path is accepted merely because a task completed; provenance must show container id/name, agent binding, container-visible paths, and output materialization from Docker-mounted directories.

2. Operator control proof
- The UI exposes enough Docker state for an operator to understand readiness and lifecycle: enabled/disabled, daemon reachability, image availability, host, current container identity, state, last refresh/update, and actionable errors.
- Lifecycle controls show actual post-action state, not optimistic intent.

3. Product parity proof
- For every backend feature that matters to operators, the UI either exposes it fully, intentionally omits it with documented rationale, or records a missing-feature defect.
- Standard CRUD and fragment flows should remain HTMX-first; JavaScript usage must be reviewed for justification.

Design decisions:
- Use Playwright first because the question is about what an operator can actually see and do.
- Use Docker CLI, app logs, and browser-origin fetch as supporting evidence only when they are needed to prove provenance or reconcile UI truthfulness with real runtime state.
- Keep phases separated so a passing happy path cannot mask a UI gap or false status problem.

## Implementation Plan

1. Establish the runtime gate and reusable Playwright harness in phase `01`.
2. Audit Docker control/status truthfulness in phase `02`.
3. Prove execution provenance across supported agent work entry points in phase `03`.
4. Validate mounts, linked directories, leases, outputs, and cleanup in phase `04`.
5. Build a backend capability inventory and compare it to reachable UI flows in phase `05`.
6. Run full browser journeys across the operational product in phase `06`.
7. Force negative scenarios in phase `07` to prove fail-fast behavior and absence of host fallback.
8. Consolidate findings, log blockers, and make an archive/no-archive decision in phase `08`.

## Validation Plan

Minimum campaign-wide evidence:
- `mvn test`
- bounded Spring Boot startup smoke with Docker enabled
- Playwright browser validation against an isolated SQLite database
- at least one agent task run, one workflow task-node run, and one job/assignment-backed run with Docker provenance
- at least one successful output artifact readback from `/output`
- at least one proof of mounted persistence across container restart
- at least one linked workspace or job/project workspace validation with lease behavior
- at least one negative proof that Docker-disabled/unavailable execution does not silently succeed on the host
- console/network capture for all major browser flows

Acceptance criteria:
- All agent execution evidence resolves to Docker-backed containers.
- UI Docker controls match actual daemon/container state after each action.
- All important backend capabilities are either reachable from the UI or explicitly logged as gaps.
- Directory mounts, workspace links, leases, outputs, and cleanup rules behave as documented.
- No unresolved blocker remains untracked.

## Handoff Checklist

- Run the phases in order and keep phase evidence separate.
- Record every mismatch immediately in `.internal-dev/bugs/`.
- Keep an issue ledger from the first blocker onward.
- Do not archive this suite until phase `08` explicitly says `pass` or the user accepts named non-blocking deficiencies.
