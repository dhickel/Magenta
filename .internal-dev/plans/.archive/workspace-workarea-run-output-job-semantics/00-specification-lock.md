# Workspace, Work Area, Run Output, And Job Semantics Specification Lock

## Acceptance Criteria

- Magenta intended contracts describe the new filesystem model: data root contains `workspace/`, `chats/`, `agents/`, and `projects/`; agent execution roots live under `workspace/<agentWorkspaceId>/`; Work Areas live under `workspace/<agentWorkspaceId>/workareas/<workAreaId>/`; execution staging lives under `runs/<runId>/outputs/`.
- Jobs are task-like executable work units bound to an agent, project, and Work Area. Jobs never own workspace directories, never allocate `jobs/<jobId>/workspace`, and are no longer described as multi-task directory containers.
- Task, workflow, and job behavior converges around executable work-unit semantics without forcing a full public rename in this plan.
- During execution, model-facing `outputs/` resolves to the current run-local `runs/<runId>/outputs/`. Final destinations are written only by backend completion, validation, or promotion logic.
- Jobless task/workflow final outputs promote to the agent workspace final `outputs/`; job-bound task/workflow/job final outputs promote to the bound Work Area or project output destination.
- Run staging is retained for at least one day. There is no immediate terminal cleanup path for run staging.
- Non-job task/workflow submissions require a user-visible run name persisted in DB while disk paths use immutable `runId`.
- Work Areas are DB-backed, use stable IDs on disk, and keep display names DB-owned. The implementation must use `work_areas.id` as the disk segment unless a blocker is found; `display_name` remains the user-facing name.
- Project directories are fully browsable/editable in the MVP browser UX; agent workspace root, run staging, outputs, and internals stay out of normal user management except read-only/diagnostic future scope.
- Static structural path segments are centralized in one application-owned source of truth and used by services, tools, controllers, prompts, tests, and docs where practical.

## Validation Criteria

- Unit/service tests cover path layout helpers, Work Area creation/resolution, project binding, job-bound routing, output promotion, run-local `outputs/` aliases, retention/deletion behavior, and schema-backed migration/reset.
- API/controller tests cover changed payloads, required non-job run names, Work Area/project routing, output browsing assumptions, and rejection of legacy job-owned workspace assumptions.
- Dedicated tests prove structural constants/helpers produce the intended physical paths and that legacy strings such as `runtime/task-runs`, `runtime/workflow-runs`, `scratch`, `outputs/jobs`, and `jobs/<jobId>/workspace` are not new-contract paths.
- Full `mvn test` passes after directory restructuring/dev reset completes.
- Bounded Spring startup passes after schema/runtime changes.
- Focused Playwright validation covers affected browser surfaces and includes screenshots plus visual quality critique for browser/file explorer behavior.

## Negative Criteria

- Do not preserve job-owned directories as a new contract.
- Do not keep final output directories as the agent's execution-time `outputs/` alias.
- Do not require MVP users to manage internal agent workspace roots.
- Do not turn structural paths into broad user/operator configuration.
- Do not leave docs/specs saying `scratch/`, `runtime/task-runs`, `runtime/workflow-runs`, or `outputs/jobs` are intended future paths unless explicitly marked legacy compatibility.
- Do not delete or revert unrelated untracked files: `.internal-dev/reviews/test-suite-quality-review.md` and `artifacts/playwright/`.

## Non-Goals

- Direct write-blocking to final outputs and system structures is deferred, but it must be recorded in `.internal-dev/specifications/deferred-features.md`.
- Agent metadata/home semantics need specification expansion but not implementation beyond compatibility-safe alias planning. Candidate future direction: `/agents/<agentId>/home` aliases into assigned workspace `home/`.
- Project git behavior is out of scope.
- Advanced unrestricted filesystem browser is future scope.
- No production code is implemented by this planning suite.

## Constraints And Assumptions

- Work classification is `large`: this spans specifications, docs, package guidance, schema, persistence, runtime contexts, tools, prompts, UI/API assumptions, tests, and development data reset/migration.
- Root/process guides read for planning: `AGENTS.md`, `.internal-dev/AGENTS.md`, `.internal-dev/specifications/AGENTS.md`, and relevant specification files.
- Relevant current specs are active but stale against the new model; implementation must update `architecture.md`, `service-graph.md`, `services.md`, `api.md`, `web.md`, `simplypages.md`, `decisions.md`, `deferred-features.md`, and `workflow.md` as applicable.
- Existing code confirms current drift in `WorkspaceDirectoryService`, `WorkspaceService`, `JobService`, `PlanService`, `WorkflowRunner`, file/shell tools, docs, and package `AGENTS.md`.
- Development migration/reset can delete ambiguous loose filesystem files, but schema-backed records must drive known migration or reset decisions.

## User-Decision Gates

- None before execution. The handoff explicitly says execution should begin after this suite returns.
- If workers discover that `work_areas.id` cannot safely be the disk segment, they must stop and return to planning with evidence before inventing another backend reference model.
- If local services/secrets block startup or browser validation, workers/validators must report the blocker and cannot mark the work fully validated.

## Stop Rules

- Stop and replan if a worker needs to preserve job-owned workspace directories as active product behavior.
- Stop and replan if run-staging retention cannot be made at least one day without risking deletion of active/resumable runs.
- Stop and replan if centralizing structural constants would require config-backed/operator-customizable path fields.
- Stop and consult the main thread if filesystem restructuring threatens unrelated untracked files or non-schema-backed user data outside the development reset scope.

