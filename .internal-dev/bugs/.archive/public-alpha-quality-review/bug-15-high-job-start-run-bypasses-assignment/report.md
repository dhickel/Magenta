# Job Start Run UI Bypasses Assignment Submission

## Summary

The job editor exposes `Start Run`, which creates a queued `job_run` without submitting a `JOB_RUN` assignment.

## Scope

Job editor and `JobService.startRun`.

## Reproduction

1. Create or open a job in `/jobs`.
2. Click `Start Run`.
3. Observe a `job_run` row is created without corresponding assignment submission.

## Expected

Public run controls should submit to an agent assignment queue.

## Actual

`Start Run` directly calls `jobService.startRun(jobId)`.

## Evidence

- `OrchestrationController.java:3586` renders `Start Run`.
- `OrchestrationController.java:3696` handles it.
- `JobService.java:191` persists the run as queued.
- `OrchestrationRunnerService.java:374` only creates/marks job runs while executing a `JOB_RUN` assignment on the queued path.

## Impact

High: UI can create queued job runs with no agent assignment to execute them, confusing operators and history.

## Status

Open.

## Next Action

Remove/gate `Start Run` or make it submit a high-priority job assignment.
