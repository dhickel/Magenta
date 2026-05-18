## Date

2026-05-18

## Change Summary

Public job run starts now submit high-priority `JOB_RUN` assignments instead of directly creating queued `job_run` rows. The operational `Start Run` control renders assignment details, the job submit form defaults to priority `9`, and the REST job run endpoint returns the queued assignment.

## Files

- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/JobController.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

## Behavioral Impact

Operators and API callers no longer create orphan job runs through public routes. Actual `job_run` rows are still created by the runner while processing queued `JOB_RUN` assignments, preserving run history behavior for executed jobs.

## Risks

`POST /api/jobs/{jobId}/runs` now returns a `WorkAssignment` rather than a `JobRun`, which is an intentional public contract change for alpha remediation.

## Validation

Validated on commit `cb3b6e2` with focused controller tests, static scan for public direct `jobService.startRun` calls, `git diff --check`, bounded Spring startup, and a live `/jobs` Playwright CLI check that confirmed `Start Run` renders a `JOB_RUN` assignment with priority `9`.
