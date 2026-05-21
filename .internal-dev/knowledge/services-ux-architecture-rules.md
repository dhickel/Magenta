# Topic

Services/frontend/UX architecture rules after the 2026-05-21 refactor.

# Source References

- `.internal-dev/plans/services-ux-architecture-refactor/implementation-plan.md`
- `.internal-dev/plans/services-ux-architecture-refactor/agent-notes.md`
- `.internal-dev/plans/services-ux-architecture-refactor/orchestration-state.md`
- `docs/technical/orchestration-runtime.md`
- `docs/technical/services.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/api-reference.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/end-user/jobs.md`

# Key Takeaways

- Agents execute work. Projects provide shared workspace and visibility context, not execution.
- Every user-facing task, workflow, and job run should enter through `WorkAssignment`.
- `projectId` is first-class assignment state and selects the effective project workspace.
- `workspaceId` is compatibility metadata and must not be treated as project context.
- Assignment summaries are the UI read model for queue/history/project/workspace diagnostics.
- Jobs are hybrid records: definition, assignment, run, optional per-assignment persistent workspace, child work-unit runs, and outputs.
- Job recurrence/start behavior should enqueue assignments, not allocate user-facing runs directly.
- `JobExecutionSummary` is the operator read model for job execution context.
- Persistent job workspaces are opt-in and scoped by assignment id.
- Direct output artifact attribution is the primary output query/display contract.
- Job output fallback is compatibility behavior and must not mask missing direct attribution in new work.
- Chat files are conversation files, not orchestration output artifacts.
- Project membership removal/deletion and job execution-affecting edits are blocked while active work references that state.
- Output filters are alpha discovery/debugging controls, not authorization enforcement.
- Operational CRUD and partial refresh interactions should remain HTMX-first unless a narrow JavaScript island is clearly simpler.

# Engine Relevance

Future changes should pass project, assignment, job, workspace, and output context through service records instead of parsing opaque input JSON or inferring from current job definitions. Controllers should adapt requests and render read models; services should own validation, mutation policy, assignment creation, run summaries, and output queries.

When adding UI controls, make the effective context visible near the action that creates or mutates work. The minimum context to show for execution surfaces is assignment id, project, effective workspace id/kind/path when available, compatibility workspace metadata when supplied, job assignment/run ids for jobs, and output provenance after artifacts exist.

When validating project/job/output UI changes, run against a live app, use focused Playwright checks on the changed surfaces, capture screenshots, and verify that added provenance does not hide primary actions or imply unimplemented security boundaries.

# Open Questions

- Should project membership become an assignment authorization rule or remain advisory visibility/context during alpha?
- Should editor revision checks be added for project/job/workflow/plan forms?
- When should loose output artifact discovery become opt-in or disabled by default?
- What explicit permission model should govern output artifact content and downloads after public alpha?
