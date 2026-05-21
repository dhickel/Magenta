# Services And UX Review Queue

Date: 2026-05-21

## Summary

After the workspace/file architecture refactor is fully complete, run a second orchestration workflow for services, frontend, and integration alignment.

## Scope

- Review how projects are displayed, assigned, and submitted through agents.
- Review how jobs are displayed, assigned, configured, and routed.
- Verify the UX supports the intended architecture for projects, jobs, tasks/plans, workflows, agent workspaces, project workspaces, and outputs.
- Review backend services and frontend surfaces together rather than treating API and UI behavior separately.
- Identify missing or misleading functionality around project-attached execution, job persistent workspaces, output visibility, assignment flows, and workspace/status panels.

## Intended Workflow

Use the same gated orchestration pattern as the workspace/file refactor:

- Read-only review agents.
- Risk and testing synthesis.
- Advanced implementation plan.
- Serial implementation phases.
- Validation after each phase.
- Remediation loops when validation fails.
- xhigh final architecture/code/UX review.
- Documentation updates, normal changelog, deeper technical changelog, and commits.

## Status

Queued until the workspace/file architecture refactor is complete.
