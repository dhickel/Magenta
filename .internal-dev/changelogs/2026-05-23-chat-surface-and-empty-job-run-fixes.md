---
schema_version: 1
document_type: changelog
date: 2026-05-23
owner: unassigned
status: finalized
---

# Chat Surface And Empty Job Run Fixes

## Summary

Fixed the two active mirrored issue targets from GitHub and `.internal-dev/bugs`:

- GitHub #7: lowercase chat `surface` values failed enum deserialization before request handling.
- GitHub #6: empty assignment-owned job runs could remain `RUNNING` after the owning assignment completed.

## Changes

- Added JSON boundary normalization for `ChatSessionSurface`.
  - Accepts known values case-insensitively: `BROWSER`, `AVATAR`, `INTERNAL`.
  - Keeps absent `surface` optional.
  - Rejects explicit blank and unknown values.
- Added explicit successful terminal handling for job runs through `JobService.completeRun(...)`.
- Updated `OrchestrationRunnerService` so empty submitted jobs complete their assignment-owned `job_runs` row as a no-op.
- Added focused tests for case-insensitive chat surface binding, blank/unknown rejection, empty run completion, and integration-level job-run terminal status.
- Updated API/runtime technical docs for the clarified behavior.
- Archived the fixed local bug reports under `.internal-dev/bugs/.archive/`.

## Validation

- `mvn -q -Dtest=ChatControllerTest,JobServiceTest,OrchestrationRuntimeTest,PublicRunSubmissionControllerTest,OrchestrationControllerTest,PublicApiRouteBindingTest test`
- `mvn -q test`
- `git diff --check`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - The app reached `Started Magenta2Application`.
  - Exit code `124` was the expected timeout shutdown after successful startup.

## Notes

No Playwright pass was required for this remediation because the changed behavior is backend/API lifecycle and JSON binding, with no changed UI surface.
