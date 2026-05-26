# Phase 01 Worker Directive: Contract, Docs, And Guidance Lock

## Objective

Update intended contracts and guidance so the repo no longer teaches the old job-owned workspace, runtime temp, scratch, or final-output-as-execution-output model. This phase is documentation/specification/guidance only.

## Editable Files

- `.internal-dev/specifications/architecture.md`
- `.internal-dev/specifications/service-graph.md`
- `.internal-dev/specifications/services.md`
- `.internal-dev/specifications/api.md`
- `.internal-dev/specifications/web.md`
- `.internal-dev/specifications/simplypages.md`
- `.internal-dev/specifications/decisions.md`
- `.internal-dev/specifications/deferred-features.md`
- `.internal-dev/specifications/workflow.md`
- `.internal-dev/knowledge/workspace-file-architecture-rules.md`
- `.internal-dev/knowledge/agent-shell-workspace-alias-resolution.md`
- `.internal-dev/knowledge/file-tool-workspace-scope-pattern.md`
- `.internal-dev/knowledge/project-workspace-materialized-links.md`
- `AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `docs/AGENTS.md`
- `docs/api/00-index.md`
- `docs/end-user/projects-and-workspaces.md`
- `docs/end-user/jobs.md`
- `docs/end-user/plans-and-tasks.md`
- `docs/end-user/workflows.md`
- `docs/end-user/inbox-outputs-settings.md`
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/orchestration-runtime.md`
- `docs/technical/services.md`
- `docs/technical/data-model.md`
- `docs/technical/workflow-engine.md`
- `docs/technical/api-reference.md`

## Forbidden Scope

- Do not edit production Java code, tests, schema, runtime config, CSS, JS, or templates.
- Do not delete/revert `.internal-dev/reviews/test-suite-quality-review.md` or `artifacts/playwright/`.
- Do not archive active plan artifacts.

## Supporting Docs To Read

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/specifications/AGENTS.md`
- This suite's `00-specification-lock.md`, `01-current-state-analysis.md`, `02-target-design.md`, and `shared/senior-engineer-guidance.md`
- Current package guides listed under editable files before changing them.

## Implementation Steps

1. Replace intended-future references to `scratch/`, `runtime/task-runs`, `runtime/workflow-runs`, `outputs/tasks`, `outputs/workflows`, `outputs/jobs`, and job-owned workspaces with the target model.
2. Add a durable decision that static structural paths are application-owned constants/helpers, not config.
3. Record deferred follow-ups for direct write-blocking, agent metadata/home alias semantics, advanced unrestricted filesystem browser, and project git behavior.
4. Update docs so MVP UX is Work Area/project browsing/editing, not internal root management.
5. Mark any unavoidable old-path references as `legacy` or `compatibility`, not future direction.
6. Update knowledge files only where reusable guidance changed.

## Acceptance Criteria

- Specs and docs agree with `02-target-design.md`.
- Package guides tell workers to use centralized layout helpers and the new run/output semantics.
- Deferred items are recorded in `deferred-features.md`.
- No current-intent doc says jobs own workspace directories or final outputs are execution-time `outputs/`.

## Negative Checks

Run:

```bash
rg -n "runtime/task-runs|runtime/workflow-runs|outputs/jobs|jobs/.*/workspace|scratch/" .internal-dev/specifications .internal-dev/knowledge docs AGENTS.md src/main/java/io/mindspice/magenta2/**/AGENTS.md
```

Every remaining hit must be explicitly compatibility, legacy, historical, deferred, or a search term in this plan.

## Validation Commands

```bash
rg -n "runtime/task-runs|runtime/workflow-runs|outputs/jobs|jobs/.*/workspace|scratch/" .internal-dev/specifications .internal-dev/knowledge docs AGENTS.md src/main/java/io/mindspice/magenta2/**/AGENTS.md
git diff -- .internal-dev/specifications .internal-dev/knowledge docs AGENTS.md src/main/java/io/mindspice/magenta2/**/AGENTS.md
```

## Stop Conditions

- Stop if a spec conflict implies job-owned workspaces must remain active product behavior.
- Stop if docs require describing UI behavior not covered by this handoff.

## Senior Guidance

Keep wording precise. Compatibility references are allowed, but future-facing contracts must describe the new model.

## Do Not Close Unless

- Specs, docs, and package guidance are updated together.
- Deferred follow-ups are recorded.
- Negative `rg` hits are explained.
- Worker report lists every file changed and why.

