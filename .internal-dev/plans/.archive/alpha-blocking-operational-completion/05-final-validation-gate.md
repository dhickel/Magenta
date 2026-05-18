# Phase 05: Final Validation Gate

## Context

This phase must be executed by a separate validation agent after phases 01-04 complete. The validator must inspect code, diffs, tests, and browser behavior directly. Do not trust worker summaries alone.

## Goal

Prove that the alpha-blocking operational gaps are resolved end to end and that no phase introduced regressions in chat, orchestration, Docker startup behavior, or HTMX-first UI policy.

## In Scope

- Review all diffs from phases 01-04.
- Run focused tests and full test suite.
- Run bounded application startup smoke.
- Run browser validation for operational routes.
- Validate HTMX-first behavior for CRUD surfaces.
- Validate Docker live behavior when host services are available.
- Write final `.internal-dev` closeout artifacts.

## Out of Scope

- Implementing new fixes beyond small test or documentation corrections.
- Redesigning Docker lifecycle beyond the persistent per-agent container contract in phase 04.
- Broad controller cleanup or route consolidation.

## Implementation Steps

1. Inspect changed files.
   - Run `git status --short`.
   - Run targeted `git diff -- <changed files>`.
   - Confirm no unrelated user changes were reverted.

2. Verify phase-specific acceptance.
   - Schedules/reactions UI has HTMX CRUD and disabled states.
   - Output artifacts have direct attribution and old rows remain readable.
   - Workspace API has list/read/leases and agent tab renders real data.
   - Docker persistent agent-container lifecycle, tests, status, agents-page controls, and docs are aligned.
   - Agent creation, enable, disable, delete/archive, and clone removal behavior matches the phase 04 contract.

3. Run tests.
   - Execute focused tests named by each phase.
   - Execute full `mvn -q test`.
   - Run `git diff --check`.

4. Run startup smoke.
   - Use an isolated or safe local data root if test configuration requires it.
   - Command:

```bash
timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

   - Exit code `124` is acceptable only if logs show healthy startup before timeout.

5. Run browser validation.
   - Read `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md` before using Playwright MCP.
   - Start the app on a concrete port.
   - Visit:
     - dashboard
     - agents list
     - one agent detail page
     - agents management Docker controls
     - schedules tab
     - reactions tab
     - workspace tab
     - outputs page
     - jobs page
     - workflows page
   - Create/edit schedule and reaction records.
   - Filter outputs by agent/job/project/type.
   - Create an agent and verify durable home/workspace setup.
   - Enable/start an agent container, then stop/disable it and verify data remains.
   - Exercise delete/archive confirmation and verify no first-click filesystem deletion occurs.
   - Confirm no normal CRUD path depends on broad JavaScript transport when HTMX can do it.

6. Run Docker live validation if possible.
   - If Podman/Docker socket is available, run the live Docker test command from phase 04.
   - If not available, record the exact daemon/socket error.
   - If model service is available, run the smallest model-backed Docker execution proof.
   - If not available, record the exact model/service blocker.

7. Write closeout artifacts.
   - Changelog: `.internal-dev/changelogs/<date>-alpha-operational-completion.md`
   - Knowledge: `.internal-dev/knowledge/alpha-operational-validation.md`
   - Notes: only for post-alpha items confirmed out of scope.
   - Bugs: only for newly discovered bugs that remain unresolved.

## Validation

Minimum command set:

```bash
mvn -q -Dtest=OrchestrationControllerTest,AgentOrchestrationControllerTest,OperationalUiContractControllerTest test
mvn -q -Dtest=WorkspaceLeaseServiceTest,OrchestrationRuntimeTest,DockerRuntimeClientTest test
mvn -q test
git diff --check
timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0
```

Live optional but strongly preferred:

```bash
mvn -q -Dmagenta.docker.live=true -Dtest=DockerRuntimeClientLiveTest test
```

Browser acceptance:

- No raw JSON appears in HTMX-swapped panels.
- Schedule/reaction invalid submissions show inline server-rendered errors.
- Output filtering changes the results without a full page reload.
- Workspace tab shows real metadata or a specific actionable error.
- Docker status panel and agents management page show disabled/unavailable/ready/running/stopped accurately.
- Agents management page can start, stop, restart, and refresh an agent container through HTMX controls.
- Agent creation shows/creates durable workspace and home paths; disable preserves them; delete/archive requires explicit confirmation.
- No visible or callable alpha clone path remains unless a documented production dependency forced it to stay hidden.

## Exit Criteria

- All alpha-blocking items from the source deferred files are either fixed or have a new bug report with reproduction and owner.
- Full test suite passes.
- Startup smoke is healthy.
- Browser validation evidence exists.
- Docker live validation proves persistent agent containers, exec sessions, and agents-page container controls, or is blocked by documented local daemon/model dependency.
- Closeout `.internal-dev` artifacts are written.
