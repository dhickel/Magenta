# Phase 02: Job Execution Read Model And Assignment-Routed Execution

## Context

Jobs are hybrid work-unit/orchestrator records. Current code has job definitions, job assignments, job runs, child task/workflow runs, persistent job workspace paths, and outputs, but no stable read model bridges those concepts. Some recurrence/direct run paths can bypass assignment execution semantics.

## Goal

Make job execution inspectable and assignment-routed. A user or UI should be able to see how a job definition, assignment, run, agent, project, effective workspace, persistent workspace, output directory, and outputs relate.

## In Scope

- Add `JobExecutionSummary` service/read model.
- Add API/fragment access to job execution summaries.
- Ensure job submit/start/recurrence paths create assignments.
- Guard direct job run allocation behind assignment context.
- Ensure job runs store/use assignment and effective workspace context.
- Define job-run cancellation compatibility with assignment lifecycle.
- Add persistent workspace tests for project and agent contexts.

## Out of Scope

- Full scheduler redesign beyond assignment routing and de-duplication safety.
- New project/job permission model.
- UI redesign beyond enough controller fragments to expose summaries in later phase.
- Optimistic concurrency on job editors.

## Implementation Steps

1. Add a `JobExecutionSummary` record in the runtime/job service package with:
   - job id/title/status.
   - assignment id/status/type/priority/model override.
   - agent id/name/status where available.
   - project id/name where available.
   - compatibility workspace id.
   - effective workspace id/kind/display path.
   - persistent workspace enabled flag.
   - persistent job workspace id/path/presence.
   - job run id/status/output directory.
   - child run ids where available.
   - output count/latest output timestamp.
   - queued/started/completed/updated timestamps.
2. Implement `JobService` read methods:
   - summaries for one job.
   - summary by assignment id.
   - latest summary for one job.
3. Use `AssignmentSummary` from phase 1 instead of parsing assignment input for project context.
4. Change job submit/start controller/service paths to return or render assignment context immediately and link to summary once a job run exists.
5. Ensure recurrence creates `JOB_RUN` assignments through `ScheduleService`/assignment creation and does not call direct run allocation from scheduler/user-facing code.
6. Guard direct `JobService.startRun` so it requires an assignment id/context or is package/private and only called by `OrchestrationRunnerService` after assignment lease acquisition.
7. Ensure job run creation records job assignment id, effective workspace id/path, output directory, and persistent job workspace path from the assignment context.
8. Verify persistent workspace key is assignment id, not job definition id.
9. Define cancellation behavior:
   - prefer cancelling the owning assignment for assignment-owned job runs.
   - if a compatibility job-run cancel route remains, update or reject consistently when assignment state is authoritative.
10. Add tests for job execution summaries before updating the broader UI.

## Validation

Run:

```bash
mvn test -Dtest=JobServiceTest,OrchestrationRuntimeTest,PublicRunSubmissionControllerTest,AgentOrchestrationControllerTest
git diff --check
```

Add/extend tests for:

- job submit creates `JOB_RUN` assignment with first-class project/effective workspace context.
- job execution summary exists before a run starts and after a run is created.
- job run summary links assignment id and job run id correctly.
- persistent job workspace is per assignment under agent workspace.
- persistent job workspace is per assignment under project workspace.
- recurrence path creates assignments and de-duplicates firings.
- direct run allocation cannot be reached from public/user-facing paths.
- cancellation behavior keeps assignment and job run states compatible.

## Exit Criteria

- Job execution can be understood from one summary contract.
- All user-facing job execution enters through assignment leasing.
- Recurrence does not bypass assignment or workspace lease semantics.
- Persistent job workspace visibility is available for the UI phase.
