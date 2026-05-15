# Docker-Backed Alpha Remediation: Orchestration Plan

## Context

The Docker-backed alpha E2E campaign proved that the operational UI, agent lifecycle, Podman integration, chat, plans, jobs, projects, inbox, and output metadata are present at the HTTP/HTMX layer. Alpha remains blocked because the verification artifacts found real execution and access failures:

- Workflow task nodes do not perform model/Docker work.
- Approval rejection does not stop workflow resume.
- Docker-backed task tools still write to host data-root paths instead of the mounted container `/output`.
- Outputs are listed as metadata but cannot be opened from the UI/API.
- Multiple editor, model-routing, job-status, history, artifact, and browser-validation gaps remain.

## Goal

Produce a subagent-ready remediation path that fixes every defect and failed validation surface from the Docker-backed alpha reports, then proves alpha readiness with automated tests, bounded Spring Boot startup, live Podman/Docker execution, and Playwright MCP browser-origin validation.

## In Scope

- Fix all blocker, should-fix, low-priority, and cosmetic findings listed in the seven evidence files and four `DEFECT-*` bug reports.
- Connect workflow task nodes and assignment-backed task execution to the same model-backed execution path.
- Propagate orchestration run context into plan/task execution, shell tools, output directory allocation, and artifact attribution.
- Add output content viewing/downloading with path confinement.
- Repair plan editor persistence, approval lifecycle, model dropdown validation, job status synchronization, history rendering, disabled feature messages, agent chat surfacing, and browser harness gaps.
- Update tests, validation evidence, bug statuses, changelog, and reusable knowledge after implementation.

## Out of Scope

Nothing from the named reports is deferred. Any proposed exclusion must be treated as a blocker and escalated to the user before implementation continues.

## Execution Model

Use one implementation subagent per phase file. Workers must:

- Read this file and their phase file.
- Read the nearest package `AGENTS.md` before changing Java code.
- Not revert unrelated dirty work.
- Return files changed, tests added, commands run, and blockers.
- Update `.internal-dev/bugs/DEFECT-*/report.md` statuses only when their fix is implemented and validated.

Phase ordering:

1. Phase 1 fixes workflow execution and approval gate correctness.
2. Phase 2 fixes Docker-backed task execution context, output paths, workspace cleanup, and artifact registration.
3. Phase 3 fixes web/operator data-contract issues.
4. Phase 4 wires agent chat and browser harness support.
5. Phase 5 performs final validation only.

## Cross-Phase Contracts

- Controllers stay thin; services own validation and use-case behavior.
- SimplyPages/HTMX remains the default for CRUD, filters, row actions, fragments, and tabs.
- JavaScript is allowed only for SSE/chat/toggle/browser behaviors where it is the path of least resistance, and every JS use must be justified in validation.
- Docker failures must fail visibly; do not hide them behind non-Docker fallback paths.
- Task execution must not fabricate outputs. If model-backed execution is unavailable, the run fails with a clear error.
- Filesystem paths must remain confined under the configured data root.
- Output artifact metadata must include agent/job/project/workspace/run type whenever that context exists.
- Browser validation must use Playwright MCP first. Curl can support diagnosis but cannot sign off user-facing browser flows.

## Validation

The orchestrator may start Phase 5 only after:

- Focused phase tests pass.
- `mvn test` passes.
- A bounded startup reaches healthy Spring Boot startup.
- Podman/Docker runtime smoke proves daemon ping, image availability, agent container start, and `/home/agent`, `/workspace`, `/output` write access.
- Each open `DEFECT-*` has either a validated fix or a user-approved blocker note. No blocker may be silently deferred.

## Exit Criteria

- All phase files are completed with evidence.
- The final validation gate passes or blocks with exact failing criteria.
- `.internal-dev/changelogs/` has a completion entry.
- `.internal-dev/knowledge/` records reusable Docker/browser validation lessons.
- Fixed bug reports are archived only after Phase 5 confirms the fixes.
