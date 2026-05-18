# Date

2026-05-18

# Change Summary

Added controller-level SSE contract coverage for public plan run streams. The tests prove `/api/plans/{planId}/runs/stream` emits semantic `submitted` and `failed` event names for assignment submission paths and does not emit the old `TaskExecutionEvent` class-name event.

# Files

- `src/test/java/io/mindspice/magenta2/api/web/PublicRunSubmissionControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

# Behavioral Impact

No production behavior changed. The existing public saved-definition submit-to-agent behavior remains intact and is now guarded by SSE event-name regression tests.

# Risks

The new tests use Spring `SseEmitter` handler reflection, matching existing project test practice for controller-level SSE capture.

# Follow-up Items

None.

# Validation

- `mvn -Dtest=PublicRunSubmissionControllerTest,TaskStreamSupportTest test` passed with 15 tests.
- Static search confirmed no class-name SSE event path remains in `PlanController`.
- `git diff --check` passed.
- Bounded Spring startup passed; logs reached `Started Magenta2Application` before the expected timeout shutdown.
