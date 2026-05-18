# Alpha Blocking Operational Completion: Orchestration Plan

## Context

The operational UI and orchestration implementation has moved past the bare mockup stage, but several alpha-facing gaps remain deferred or only partially proven:

- Schedules and event reactions have JSON APIs but no operator UI.
- Output artifacts are queryable, but agent ownership is inferred through job/run traversal instead of stored on the artifact.
- Workspace UI exists, but the API surface is incomplete and the agent workspace tab can degrade when the workspace service is unavailable.
- Docker/Podman execution exists only as one-off containers, but the alpha product contract expects an active agent to have a persistent personal runtime while it is awake or working.
- Browser validation is still too shallow for the operational workflows expected in alpha.

This plan suite is written for lesser-skilled implementation agents. Each subplan is self-contained and should be launched in a clean context. The orchestrating agent must not ask a worker to infer architecture from the original broad feature outline; each worker gets one narrow contract, named files, expected behavior, and validation commands.

## Goal

Resolve all outstanding alpha-blocking operational gaps and prove the system is alpha-usable through code tests, startup smoke, and browser validation. The end state should make schedules, reactions, outputs, workspace inspection, and Docker-backed execution visible, editable, and verifiable from the operational UI and APIs.

## In Scope

- Add HTMX-first CRUD UI for agent schedules and event reactions.
- Add first-class artifact attribution fields so output filtering can directly use `agentId`, `jobId`, `projectId`, and `workspaceId` when known.
- Complete workspace list/read APIs and make the workspace tab a real operational surface rather than a placeholder.
- Replace one-off-only Docker execution with a persistent per-agent container lifecycle for active agents, then prove it with live container smoke and model-backed plan/task execution when local services are available.
- Define agent creation, deletion, enable, disable, and workspace archive behavior so Docker containers and durable agent data have clear ownership.
- Remove agent cloning for alpha to avoid ambiguous workspace, home directory, output, inbox, and Docker container copying semantics.
- Add targeted unit/controller/repository tests and a final Playwright MCP operational workflow validation pass.
- Update `.internal-dev` docs after implementation: changelog, reusable knowledge, and any newly deferred or out-of-scope concerns.

## Out of Scope

- Docker Compose, Swarm, Kubernetes, multi-node scheduling, image registry authentication, and private image pull flows are out of scope.
- Agent cloning is out of scope and should be removed or hidden for alpha.
- Collapsing `FrontendController` and `OrchestrationController` is out of scope. It is cleanup, not alpha functionality.
- Broad JavaScript unit testing for legacy orchestration modules is out of scope unless a subplan touches those modules directly. HTMX should remain the default.
- Security hardening beyond path confinement, runtime failure clarity, and container cleanup validation is out of scope.

## Execution Model

Use one implementation worker per phase with a clean context and the corresponding phase file only. Tell each worker that other agents may be editing the repo, that they must not revert unrelated work, and that their write scope is limited to the files named in their subplan unless tests force a small adjacent change.

Execution order:

1. `01-schedules-reactions-ui.md`
2. `02-output-artifact-attribution.md`
3. `03-workspace-api-and-agent-tab.md`
4. `04-docker-alpha-completion.md`
5. `05-final-validation-gate.md`

Phases 1, 2, and 3 can be implemented in parallel if their workers keep to the assigned write scopes. Phase 4 should not run in parallel with any other worker touching runtime execution, workspace leases, or agent lifecycle code because it changes Docker lifecycle ownership. Phase 5 must run after all implementation phases are merged.

## Cross-Phase Contracts

- Controllers stay thin. Business validation belongs in services.
- SimplyPages UI must stay HTMX-first for CRUD, filters, tabs, row actions, and form submissions.
- Do not add raw HTML strings unless SimplyPages cannot express the element and the worker documents why.
- Do not touch `/chat` routes or chat UI surfaces except where Docker model-backed execution tests need existing execution flows.
- Docker lifecycle ownership must be explicit: the app owns the Docker API client and runtime manager; each active agent owns one persistent container; task/job/workflow executions create bounded exec sessions inside that container; terminal app shutdown cleans up managed containers according to the configured policy.
- Agent data ownership must be explicit: each agent has a durable host-backed home mounted at `/home/agent`, an agent workspace mounted at `/workspace`, and persistent outputs mounted or copied under `/output`; delete flows must ask whether to archive workspace data before removing anything.
- All new database columns must be additive and migration-safe against existing SQLite databases.
- All operational pages must degrade with clear user-facing messages rather than empty containers.

## Implementation Supervision

The orchestrator should require each worker to return:

- Files changed.
- Tests added or updated.
- Commands run and their result.
- Any behavior intentionally deferred.
- Any package `AGENTS.md` guidance that became stale and was updated.

Do not start the final validation gate until:

- Focused tests from phases 1-4 pass.
- `mvn -q test` passes or a blocker is documented with evidence.
- A bounded app startup smoke reaches healthy Tomcat startup.

## Validation

Final acceptance requires:

- Schedules and reactions can be created, edited, enabled/disabled, listed, and visibly validated from the agent detail UI.
- Output filters for agent/job/project/run/type use first-class artifact metadata where available.
- Workspace list/read APIs return real workspace metadata, and agent workspace tabs show roots, links, and clear empty/error states.
- Docker runtime status is visible, persistent agent containers can start/stop/recover, exec sessions can write to mounted workspace/output paths, timeout cleanup is verified, and a model-backed execution path is either proven live or blocked only by a documented local dependency.
- Agent creation ensures durable workspace/home setup, agent enable/disable controls activate/deactivate runtime space, and delete/archive flows require explicit operator confirmation.
- Browser validation covers create/edit/run flows rather than static DOM presence only.

## Exit Criteria

- All implementation subplans have been executed or explicitly marked blocked with evidence.
- `05-final-validation-gate.md` has been executed by a separate validation agent.
- `.internal-dev/changelogs/` contains a dated completion entry.
- `.internal-dev/knowledge/` records reusable operational UI and Docker validation lessons.
- `.internal-dev/notes/` records only genuinely post-alpha items.
