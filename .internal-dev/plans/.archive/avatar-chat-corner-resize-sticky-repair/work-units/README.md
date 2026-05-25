---
schema_version: 1
document_type: work-units
status: active
created: 2026-05-25
owner: unassigned
---

# Work Units

## Unit 1: Browser-Grounded Shell Investigation

Why: Previous static-only reasoning failed. The worker must identify whether the divider, pointer events, stale state, or sticky ancestor is the real browser failure.

Dependencies: Read current code and prior archived handoff before changing files.

Criteria mapping: AC1, AC2, AC7, AC10.

Exact areas: `AvatarDashboardComponents.java`, `avatar-dashboard.css`, `avatar-shell.js`, framework shell CSS inspection, docs/test context.

## Unit 2: Corner Resize Implementation

Why: The user wants direct bottom-right chat resizing, not a divider.

Dependencies: Unit 1 confirms the target scroll/layout container.

Criteria mapping: AC2, AC3, AC4, AC5, AC6, AC8.

Exact areas:

- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardComponents.java`
- `src/main/resources/static/css/avatar-dashboard.css`
- `src/main/resources/static/js/avatar-shell.js`
- `src/main/java/io/mindspice/magenta2/api/web/AvatarDashboardController.java` only if needed for scoped Avatar shell/content wrapper classing or asset versioning

## Unit 3: Tests And Docs

Why: Static tests should catch regression to divider behavior, and docs should not keep telling users to drag a divider.

Dependencies: Unit 2 DOM hooks and behavior finalized.

Criteria mapping: AC2, AC3, AC9.

Exact areas:

- `src/test/java/io/mindspice/magenta2/api/web/AvatarDashboardControllerTest.java`
- `docs/end-user/avatar-dashboard.md`
- `docs/technical/avatar-dashboard-fragments.md`
- `.internal-dev/changelogs/<date>-avatar-chat-corner-resize-sticky-repair.md`
- `.internal-dev/focus/unfinished-work.md` only if work is left blocked/deferred

## Unit 4: Playwright Validation

Why: Certification requires proving real browser resize and sticky behavior.

Dependencies: Implementation worker completes Unit 1-3 and code validation passes.

Criteria mapping: all ACs, especially AC4, AC5, AC7, AC8, AC10.

Exact areas: non-mutating validation only. Append evidence to `shared/implementation-notes.md` if the orchestration runtime permits evidence updates by validation worker; otherwise return a report for the orchestrator to append.

## Sequencing Rationale

This is intentionally serial. The browser failure mode informs the correct small implementation. Parallelizing implementation and validation would recreate the prior failure pattern.

## Senior Engineer Notes

The dependency graph is investigation -> implementation -> code validation -> browser validation. Do not skip the investigation simply because the target design is already clear. Sticky failures often come from ancestors, not the sticky element.
