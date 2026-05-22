# Empty Job Runs Can Remain Running After Assignment Completion

## Summary

Focused job submission validation observed empty validation jobs leaving runner-created `job_runs.status=RUNNING` after their owning assignments completed.

## Scope

Job runner terminal status handling for jobs with no executable items.

## Evidence

- 2026-05-18 subplan 04 validator used isolated DB `/tmp/magenta2-cb3b6e2-playwright.sqlite`.
- Public job submission correctly created `JOB_RUN` assignments and the runner created `job_runs` rows afterward.
- The validator reported those empty-job `job_runs` stayed `RUNNING` while assignments completed.

## Impact

Run history can display a stale in-progress job run for an empty job even though the assignment lifecycle has completed.

## Status

Open.

GitHub mirror: https://github.com/dhickel/Magenta/issues/6

## Next Action

Confirm expected empty-job behavior in `OrchestrationRunnerService` and update runner/job-service terminal status handling if empty jobs should complete without items.
