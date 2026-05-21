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

- Pending initial read-only review agents.

## Completed Work

- Created second-suite branch and initial orchestration notes.

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
