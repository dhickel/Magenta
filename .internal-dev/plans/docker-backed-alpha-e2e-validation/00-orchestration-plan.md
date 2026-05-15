# Orchestration Plan: Docker-Backed Alpha E2E Validation

## Context

Prior alpha work deferred Docker-backed validation even though Docker is the production execution environment for agents. That means recent UI/UX and orchestration refactors were not dogfooded through the real runtime. This plan corrects that by making Playwright-driven, Docker-enabled end-user validation the acceptance gate for the whole operational contract.

Source inputs:
- User feature contract from the alpha orchestration discussion.
- `.internal-dev/plans/alpha-blocking-operational-completion/`
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`
- `.internal-dev/knowledge/docker-runtime-host-setup-and-prereqs.md`
- `.internal-dev/notes/operational-ui-contract-missing-features.md`
- `.internal-dev/notes/2026-05-13-phase-04-docker-deferred.md`
- `.internal-dev/notes/2026-05-13-phase-05-live-docker-validation-blocked.md`

## Goal

Run a full Playwright-first validation campaign that proves Magenta's alpha operational experience works with Docker-backed agent execution. The campaign must create and execute real plans/tasks, compose workflows with blocking approval gates, route approval messages through inbox, resume workflow progress, manage jobs/projects/schedules, validate outputs, prove model override behavior, and exercise the operational UI as an end user.

## In Scope

- Playwright setup, test harness, fixture data, and evidence capture.
- Docker/Podman runtime availability as a hard precondition.
- Agent create/enable/disable/delete/archive flows and Docker container management UI.
- Plan/task creation, structured inputs/outputs, deliverables, model overrides, execution, and output validation.
- Workflow creation with task nodes, output mappings, user approval gates, agent approval gates, inbox messages, resume, and terminal statuses.
- Jobs, projects, project agent assignment, schedules, reactions, and agent work assignment.
- Chat surfaces: `/chat`, agent side-panel chat, project/job/top-level agent chat if present.
- Output artifacts, workspace mounts, temp workspace cleanup, persistent agent home, and output directory materialization.
- Defect ledger and consolidated alpha-readiness report.

## Out of Scope

- Replacing Playwright with curl or endpoint-only validation.
- Accepting host execution as equivalent to Docker execution.
- New feature implementation during validation except tiny harness/documentation fixes.
- Kubernetes, compose, registry auth, or custom image build work unless validation proves the current `python:3.11` image cannot support alpha.

## Execution Model

The orchestrator owns setup, sequencing, and consolidation. Each subagent gets one subplan and a clean context. Subagents may inspect code and write test utilities under a shared Playwright validation location if needed, but they must not make production code changes unless the orchestrator explicitly converts a found defect into a remediation task.

Parallelization:
- `02` and `06` can run after `01` passes.
- `03` depends on `02`.
- `04` depends on `03`.
- `05` can run after `02`, but workflow/job execution evidence must be reconciled with `03` and `04`.
- `07` depends on output-producing flows from `03`, `04`, and `05`.
- `08` runs last.

## Shared Playwright Contract

All subplans must:
- Start from a live browser page, not raw HTTP from shell.
- Use Playwright to click/type/select/submit normal UI controls when the UI exists.
- Use browser-origin `fetch` only when the feature is intentionally API-first or when validating the same endpoint the UI calls.
- Capture console errors, failed network responses, screenshots on failure, and the final DOM state for each major flow.
- Verify persisted state by returning to the UI or using browser-origin API calls from the same app origin.
- Record whether HTMX handled CRUD/fragment flows or whether JavaScript was used and justified.

## Shared Environment

Use an isolated app run:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=18080 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-alpha-e2e.sqlite --magenta.docker.enabled=true --magenta.docker.agent-image=python:3.11 --magenta.executor.chat-threads=4'
```

The command starts the app, but validation must happen in Playwright. If startup fails because Docker is unavailable, that is a blocking environment failure.

## Evidence Output

Each subagent writes:
- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/<phase>-evidence.md`
- Screenshots/traces if available under `.internal-dev/reviews/docker-backed-alpha-e2e-validation/assets/`
- Bug reports in `.internal-dev/bugs/` for confirmed unresolved defects.

## Exit Criteria

- Every subplan has pass/fail evidence.
- Docker-backed execution is proven for at least one task, one workflow task node, and one job/agent assignment path.
- Workflow approval gates block progress, message the user inbox, accept approval, and resume to completion.
- Outputs are validated as files/artifacts, not just success text.
- The final report distinguishes product defects, missing features, test harness blockers, environment blockers, and accepted post-alpha deferrals.
