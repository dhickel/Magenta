# Workspace File Architecture Refactor Agent Notes

This is the running cross-agent notes file for the workspace/file architecture refactor.

All review, planning, implementation, validation, remediation, documentation, and closeout agents must read this file before starting and append concise notes before finishing.

## Global Assumptions

- Source architecture note: `.internal-dev/notes/current-architecture-focus.md`.
- Work executes through agents, optionally with attached project context.
- Projects are durable shared workspace/visibility abstractions, not executable work units.
- Effective durable workspace is project workspace when project-scoped, otherwise agent workspace.
- Tasks/plans and workflows use per-run temp/execution space and do not get stable persistent per-work-unit workspaces.
- Jobs are work units and may also own persistent per-assignment/per-instance job workspaces when configured.
- Only explicit outputs should be tracked as output artifacts.
- Chat files remain a separate conversation-scoped system.
- Code-editing phases must be serial and validation-gated.
- Each completed phase should end with a commit after validation.

## Active Agents

- Planning Synthesis: implementation phase plan and orchestration suite.

## Completed Work

- Created initial architecture focus note.
- Created dedicated branch: `workspace-file-architecture-refactor`.
- Created this cross-agent notes file.
- Created setup commit: `9c05c24 docs: add workspace file architecture focus`.
- Review C completed read-only job/project/orchestration divergence review.
- Review E completed read-only testing/risk planning review.
- Review A completed read-only workspace/files divergence review.
- Review D completed read-only loose artifact discovery risk assessment.
- Review B completed read-only task/plan and workflow execution divergence review.

## Validation Results

- No implementation validation has run yet.

## Remediation Notes

- None yet.

## Blockers

- None currently.

## Closeout Work

- Required before final completion: docs updates, `.internal-dev` changelog, deeper technical changelog/review artifact, relevant package guide updates if responsibilities change, validation record, and commits.

## Final Validation Status

- Not started.

## Handoff Notes

- Initial work should be read-only review and planning. Do not implement until divergence, risk, and testing review passes are complete.
- Review C findings: projects still require owner agents across API/schema/service/UI; assignment/run APIs lack first-class `projectId`; project execution only works indirectly through job definitions; job workspaces are unconditional per job definition instead of opt-in per assignment/instance; project-scoped outputs are still written under agent output directories; job run identity is separate from assignment identity and resume can create inconsistent new job runs; legacy `OrchestrationJobService`/`orchestration_jobs` remains alongside current `JobService`/`job_definitions`. Recommended remediation should introduce `projectId` explicitly, preserve old payload compatibility, remove project owner-agent semantics through migration, resolve effective durable workspace centrally, and make persistent job workspaces opt-in and assignment-keyed.
- Review E findings: key validation risks are project owner-model drift, project-scoped outputs still landing under agent outputs, workflow outputs materializing into workflow temp, job persistent workspace isolation by job definition instead of assignment/instance, loose artifact discovery conflicting with explicit-output-only policy, active temp retention/data-loss risk, and lease/context races. Existing coverage is strongest for path confinement, lease basics, output artifact attribution/security, file/shell active-context scoping, task temp cleanup, project link materialization, route binding, and Spring context smoke. Missing coverage should be added before/with implementation for effective durable workspace resolution, project-scoped output placement, workflow durable outputs, job per-assignment persistent workspaces, active/waiting temp retention, explicit-output-only behavior, chat-file separation from artifacts, and workflow async context propagation.
- Review A findings: task tools currently map `workspace/` to run temp; project outputs still write under agent outputs; direct task/workflow submissions cannot attach project context except through job-derived project lookup; workflow artifacts are materialized into workflow temp; job workspaces are definition-scoped and always persistent; projects still require owner agents. Recommended first implementation step is an effective workspace resolver plus alias contract update, with API compatibility around existing `workspaceId` before changing storage paths.
- Review D findings: direct loose artifact discovery exists only in `OutputArtifactService.discoverLooseArtifacts(...)`, called by `PlanService.completeRun(...)`; `TaskService`/task tools/job plan items inherit it through task completion. Tests rely on loose file discovery in `OutputArtifactServiceAttributionTest` and `PlanServiceTest`. Current behavior is a shallow direct-file scan of run output dir, extension-inferred, artifact name `discovered_*`, with no explicit publication contract. Risk: hard removal can hide deliverables written to `outputs/`; keeping as-is violates explicit-output architecture. Recommended staged mitigation: gate current behavior, add realpath/data-root confinement, introduce explicit output publishing, update prompts/tools/docs, then default discovery off after compatibility coverage.
- Review B findings: assignment-backed workflows persist workflow `WAITING`, but `OrchestrationRunnerService.runWorkflow` marks any non-completed workflow assignment as `FAILED`, so approval workflows are not assignment-resumable. Workflow runs use `runtime/workflow-runs/<runId>` as both temp and output directory; final outputs/log artifacts are materialized into temp instead of effective durable workspace outputs. Task/plan runs allocate outputs under `agents/<agent>/workspace/outputs` or `agents/system`, even for project-scoped work; project context only creates a temp symlink. Workflow task nodes execute on async executor threads and lose `OrchestrationTaskContextHolder` context because it is a plain `ThreadLocal`. `PlanRun.workspaceId` is not populated on start; artifact attribution is reconstructed later from thread-local context or output-path fallback. Recommended constraints: introduce a shared effective-workspace/output resolver, persist run workspace/output metadata at run creation, separate workflow temp from durable outputs, propagate orchestration context explicitly through workflow execution, and preserve `PlanRun` compatibility while adding clearer task/workflow work-unit metadata.
- Planning Synthesis completed 2026-05-21: created `review-synthesis.md`, `implementation-plan.md`, `orchestration-suite.md`, and phase files `phase-01` through `phase-07`. The synthesized plan keeps implementation code edits serial and validation-gated, requires phase commits, preserves chat-file separation, stages loose artifact discovery behind confinement/gating plus explicit publishing, preserves `workspaceId` compatibility while adding `projectId`, treats project owner-agent removal as its own migration/API phase, and makes workflow `WAITING` assignment handling plus workflow context propagation concrete defect fixes.
