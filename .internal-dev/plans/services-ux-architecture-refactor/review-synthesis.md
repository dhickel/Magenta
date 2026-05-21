# Services/UX Architecture Refactor Review Synthesis

Date: 2026-05-21

## Inputs

- Backend services review: `.internal-dev/plans/services-ux-architecture-refactor/review-backend-services.md`
- Frontend/UX review: `.internal-dev/plans/services-ux-architecture-refactor/review-frontend-ux.md`
- Integration/API review: `.internal-dev/plans/services-ux-architecture-refactor/review-integration-api.md`
- Risk/testing review: `.internal-dev/plans/services-ux-architecture-refactor/review-risk-testing.md`
- Architecture baseline: `docs/technical/workspaces-tools-outputs.md`, `docs/technical/orchestration-runtime.md`, `docs/technical/services.md`, `docs/end-user/projects-and-workspaces.md`, `docs/end-user/jobs.md`, `.internal-dev/notes/current-architecture-focus.md`

## Consolidated Findings

The reviews agree on one primary architecture gap: Magenta already has the pieces for project workspaces, assignment queues, job runs, persistent job workspaces, and output artifacts, but the service/API/UI contracts do not expose the effective execution context as durable, queryable state.

Most current failures come from four places:

- `projectId` is accepted by assignment requests but is not first-class assignment state.
- jobs have definitions, work assignments, job runs, child task/workflow runs, and output artifacts, but no stable read model ties them together.
- some job execution paths can still allocate runs directly rather than entering through assignment leasing.
- UI surfaces show forms and tables that hide effective project/workspace/job assignment/output context, so users cannot confirm where work will run or where outputs belong.

## Architecture Decisions For Implementation

- Treat projects as shared workspace and visibility contexts only. A project must never become a direct executor.
- Treat agents as the execution entry point. All user-facing task, workflow, and job execution must create or resume a `WorkAssignment`.
- Treat tasks/plans and workflows as bounded executable work units. They use the effective durable workspace but do not get persistent workspaces of their own.
- Treat jobs as hybrid work-unit/orchestrator records. A job definition is repeatable work; a job assignment is a concrete execution request; a job run is runtime history; an optional persistent job workspace is per assignment.
- Preserve `workspaceId` compatibility while making `projectId` explicit. `projectId` selects the effective durable workspace. `workspaceId` remains legacy/compatibility metadata and must not be interpreted as a project id.
- Keep the coarse project writable lease as the first implementation boundary. Add read models and retry hooks that are compatible with future read leases or subtree locks.
- Use assignment/run/output read models for UI rather than parsing assignment input JSON or relying on route-local fallback queries.

## Required Implementation Order

1. Service contracts and assignment context.
2. Job execution read model and assignment-routed execution.
3. Output provenance, query, and display contracts.
4. Project/job/operator UI updates using the new service contracts.
5. Validation, documentation, changelog, knowledge capture, and final review.

The order is intentionally conservative. UI work depends on durable context fields and read models; adding controls first would make new screens depend on the same implicit state the refactor is meant to remove.

## Cross-Cutting Risks

- Direct execution bypass: any new route that calls plan, workflow, or job execution directly can bypass assignment leases and project workspace leases.
- Workspace ambiguity: accepting both `projectId` and `workspaceId` can mislead callers unless project is the only effective-workspace selector.
- Active mutation: project membership, project deletion, job deletion, and job item edits can invalidate running assignments unless blocked or versioned.
- Waiting assignment recovery: project lease conflicts currently produce `WAITING` state, but implementation must provide a deterministic requeue/resume path.
- Output fallback masking: output route fallback can make job pages look correct even when artifacts lack direct project/job/workspace attribution.
- Selector context: reusable selectors can filter by context, but current wrappers often drop context.
- Playwright scope: this refactor changes operational UI; browser validation must run in a subagent with screenshots for changed surfaces.

## Validation Themes

- Contract tests must prove assignment responses and query read models expose `projectId` and effective workspace identity.
- Runtime tests must prove project-scoped work enters through assignment leasing and direct job recurrence does not bypass that path.
- Job tests must prove each persistent job workspace is per assignment and that summaries bridge definition, assignment, job run, output directory, and outputs.
- Output tests must prove direct attribution supports project/job/agent/workspace/run filters without relying on compatibility fallback.
- UI controller and Playwright tests must prove users can select and inspect project/job/workspace/output context on `/projects`, `/jobs`, `/outputs`, plan/workflow submit flows, and agent submit/history tabs.
