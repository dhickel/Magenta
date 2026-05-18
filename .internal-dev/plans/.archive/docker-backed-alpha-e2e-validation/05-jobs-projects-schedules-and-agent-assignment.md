# Phase 05: Jobs, Projects, Schedules, And Agent Assignment

## Context

Jobs and projects wrap larger work units and assign agents to work. Alpha validation must prove these surfaces are not just editable shells: they must connect to agents, workspaces, schedules, and execution.

## Goal

Validate through Playwright that projects and jobs can be created, linked, assigned to agents, scheduled or queued, executed through Docker-backed agents, and tracked to completion.

## In Scope

- Project creation/editing.
- Project owner agent and assigned agents.
- Job creation/editing.
- Job/project workspace association.
- Submit job to agent.
- Agent queue and assignment status.
- Schedules and event reactions UI if present.
- Dashboard active work and recent event panels.
- Project/job chat or agent consultation surfaces if present.

## Out of Scope

- Sprint/milestone systems not implemented for alpha.
- Cron reliability over long wall-clock intervals; use short deterministic schedules where possible.

## Implementation Steps

1. Create a project with the phase `02` agent as owner.
2. Add at least one additional agent membership if the UI supports it.
3. Create a job linked to the project with a small Docker-backed task/workflow item.
4. Verify model/settings overrides at project/job level persist and are used or inherited visibly.
5. Submit the job to the agent from both job UI and agent detail if both surfaces exist.
6. Verify agent queue shows the assignment and status transitions.
7. Execute the job and verify Docker-backed output materialization.
8. Create or edit an agent schedule through the UI if the feature is exposed.
9. Create or edit an event reaction through the UI if the feature is exposed.
10. Verify dashboard links to job/project pages do not navigate to 405/error pages.

## Validation

Required Playwright checks:
- Project/job creation and edit forms persist all visible fields.
- Owner/assigned agent controls work and validate missing/invalid agents.
- Job submission creates a visible assignment.
- Agent queue and job detail agree on status.
- Outputs are attributable to job and project.
- Schedule/reaction CRUD works or is logged as a missing alpha surface.
- Dashboard active work and recent events show real data or intentionally absent states, not permanent placeholders.

## Exit Criteria

- `.internal-dev/reviews/docker-backed-alpha-e2e-validation/05-jobs-projects-schedules-assignment-evidence.md` exists.
- At least one job/project path reaches terminal status through a Docker-backed agent.
- Any broken route, dead button, missing schedule/reaction UI, or assignment mismatch is logged.
