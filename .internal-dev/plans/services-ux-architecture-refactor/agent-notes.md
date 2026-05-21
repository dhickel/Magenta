# Services And UX Architecture Refactor Agent Notes

## Global Assumptions

- This suite starts after the workspace/file architecture refactor completed on `workspace-file-architecture-refactor` through `e6dfe87`.
- Target branch: `services-ux-architecture-refactor`.
- Projects are durable shared workspace/visibility abstractions, not ordinary work units.
- Agents remain the execution entry point. Work can be submitted through an agent alone or through an agent with an attached project.
- Jobs are orchestration/work-unit hybrids: repeatable, optionally persistent, assignable, and capable of launching their own task/workflow work.
- Tasks/plans and workflows are work units that should surface project/agent workspace selection and output visibility accurately.
- Chat files remain separate from output artifacts.

## Active Agents

- Advanced plan synthesis pending.

## Completed Work

- Created second-suite branch and initial orchestration notes.
- Setup commit: `7e63626 plan: start services ux architecture refactor`.
- Launched initial high-reasoning read-oriented review group.
- Backend services review completed: `.internal-dev/plans/services-ux-architecture-refactor/review-backend-services.md`.
- Frontend/UX review completed: `.internal-dev/plans/services-ux-architecture-refactor/review-frontend-ux.md`.
- Integration/API review completed: `.internal-dev/plans/services-ux-architecture-refactor/review-integration-api.md`.
- Risk/testing review completed: `.internal-dev/plans/services-ux-architecture-refactor/review-risk-testing.md`.

## Validation Results

- Pending.

## Remediation Notes

- Pending.

## Blockers

- None currently known.

## Closeout Work

- Required at end: docs updates, `.internal-dev` changelog, technical changelog if implementation changes are substantive, knowledge notes for reusable architecture rules, final validation, xhigh review, and scoped commits.

## Final Validation Status

- Pending.

## Handoff Notes

- The first wave should be read-only and should identify divergence between current backend services, frontend/UX, and the project/job/workspace architecture.
- Agents must append concise findings here before finishing.

## Review Wave Synthesis Inputs

- Backend/API reviews agree that assignment `projectId` and effective workspace context need first-class durable/read-model treatment instead of living only in assignment input JSON.
- Backend/API reviews agree jobs need a stable bridge read model that ties job definition, assignment id, job run id, agent, project, workspace, persistent workspace status, and outputs together.
- Frontend/API reviews agree project/job UI controls are missing or misleading: project-scoped agent submit, job project/workspace routing, persistent job workspace toggle/status, project membership controls, output provenance, and run-to-output navigation.
- Risk review requires assignment-routed execution and clear mutation policy before expanding project/job UI controls, so implementation must avoid direct execution paths that bypass workspace leasing.
- Playwright validation is required for changed `/projects`, `/jobs`, `/outputs`, and submit-flow surfaces.
