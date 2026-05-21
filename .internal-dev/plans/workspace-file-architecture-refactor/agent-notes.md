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

- None yet.

## Completed Work

- Created initial architecture focus note.
- Created dedicated branch: `workspace-file-architecture-refactor`.
- Created this cross-agent notes file.

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
