# 2026-05-23 Submit Work Area Routing Controls

## Summary

Added Work Area/output routing fields to assignment submit surfaces and direct API payloads while keeping plan-chat routes unchanged.

## Changes

- Added compact Work Area/output routing fields to operational plan, workflow, job, and agent submit forms.
- Propagated `selectedWorkAreaId`, `outputRouteType`, `outputWorkAreaId`, and `outputDirectRelativePath` from HTMX submit forms into `AssignmentRequest`.
- Added the same fields to plan/task/workflow/job/agent direct submit request DTOs.
- Preserved existing nested DTO constructors for tests and source compatibility.
- Updated API and technical docs.

## Validation

- `mvn -DskipTests compile`
- `mvn -Dtest='io.mindspice.magenta2.api.web.OrchestrationControllerTest,io.mindspice.magenta2.api.web.AgentOrchestrationControllerTest,io.mindspice.magenta2.api.web.PublicRunSubmissionControllerTest' test`

Result: 132 tests passed, 0 failures, 0 errors.

## Deferred

- Browse/picker modal UX for Work Area selection, pending the file explorer route/component phase.
- Playwright screenshot validation for submit forms, pending a running app validation pass.
