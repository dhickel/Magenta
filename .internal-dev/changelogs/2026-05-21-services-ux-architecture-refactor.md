# Date

2026-05-21

# Change Summary

Closed out the services/frontend/UX architecture refactor after phases 01 through 04. The documented behavior now treats projects as shared workspace contexts, assignments as the durable execution boundary, jobs as assignment-routed work definitions with run summaries, and output artifacts as directly attributable records.

# Files

- `docs/api/00-index.md`
- `docs/technical/api-reference.md`
- `docs/technical/data-model.md`
- `docs/technical/frontend-htmx.md`
- `docs/technical/orchestration-runtime.md`
- `docs/technical/services.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/end-user/jobs.md`
- `docs/end-user/inbox-outputs-settings.md`
- `.internal-dev/changelogs/2026-05-21-services-ux-architecture-refactor.md`
- `.internal-dev/changelogs/2026-05-21-services-ux-architecture-technical.md`
- `.internal-dev/knowledge/services-ux-architecture-rules.md`
- `.internal-dev/plans/services-ux-architecture-refactor/agent-notes.md`
- `.internal-dev/plans/services-ux-architecture-refactor/orchestration-state.md`

# Behavioral Impact

Operator docs now cover:

- first-class assignment `projectId`, effective workspace id/kind, and compatibility `workspaceId` semantics.
- project-scoped task, workflow, and job submission through agents.
- job assignment/run identity and `JobExecutionSummary` context.
- assignment-routed job recurrence/start behavior.
- opt-in per-assignment persistent job workspace UI and paths.
- project membership controls and active-work guards.
- output filters for project, workspace, plan/workflow id, job assignment, job run, run id, run type, and artifact type.
- project/job/operator UI expectations for provenance, context labels, and focused Playwright validation.

# Risks

- This closeout is documentation-only and does not re-run the phase implementation test matrix.
- Output artifact filters remain alpha discovery/debugging controls, not authorization enforcement.
- Root `AGENTS.md` and `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md` were intentionally not edited because they already had unrelated dirty changes.

# Follow-up Items

- Commit the closeout artifacts with the implementation when the owning orchestration pass is ready.
- Keep any future permission enforcement, editor revision checks, and loose output discovery policy changes as separate scoped work.
