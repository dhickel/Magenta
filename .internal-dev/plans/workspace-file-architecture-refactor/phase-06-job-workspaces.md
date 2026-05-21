# Phase 06: Job Workspace Policy

## Context

Jobs are work units and may optionally own persistent workspaces. Current behavior is definition-scoped and too easy to share accidentally across assignments or runs.

## Goal

Make persistent job workspaces explicit, project-aware, and isolated by assignment/run identity.

## In Scope

- Job definition or assignment/run persistence configuration.
- Persistent job workspace allocation under effective durable workspace `jobs/`.
- Assignment/run-keyed workspace identity.
- Job output artifact metadata updates.
- Legacy orchestration job compatibility or migration decision.
- Related job, assignment, runtime, schema, and output tests.

## Out of Scope

- Broad job scheduler redesign.
- Direct job execution outside agent runtime paths.
- Sharing one job workspace across assignments by default.
- Removing legacy job systems without compatibility proof.

## Implementation Steps

1. Add explicit persistent workspace configuration for jobs.
2. Allocate `jobs/<jobAssignmentId>/` or equivalent only when persistence is enabled.
3. Ensure two assignments of the same job definition do not share persistent workspace accidentally.
4. Publish job outputs under effective durable workspace outputs with job assignment/run metadata.
5. Reconcile `OrchestrationJobService` and `orchestration_jobs` with one controlled compatibility/migration path.
6. Add tests for project-scoped job outputs, workspace isolation, and compatibility.
7. Append phase notes and validation results.

## Validation

- Job service/repository tests.
- Assignment/runtime tests.
- Workspace schema migration tests.
- Output artifact attribution tests.
- Spring context smoke.

## Exit Criteria

- Persistent job workspaces are opt-in.
- Job workspaces are assignment/run isolated.
- Project-scoped jobs use project durable outputs.
- Legacy job behavior has a recorded compatibility or migration decision.
- Phase validation passes and the phase is committed.
