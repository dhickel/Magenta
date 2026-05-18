# Subplan 04: Job Run Submission

## Goal

Replace job `Start Run` behavior with high-priority `JOB_RUN` assignment submission or remove/gate the control.

## Implementation Steps

1. Inspect job UI and `JobService` run creation path.
2. Prefer submit-to-agent assignment creation for saved job definitions.
3. Link job run history to the assignment created by the runner, not an orphan queued job run.
4. Add UI/route tests for the new behavior.

## Validation

Job Start Run no longer creates a queued job run without an assignment.
