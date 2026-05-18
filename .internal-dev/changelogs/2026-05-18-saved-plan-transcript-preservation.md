Date
2026-05-18

Change Summary
Saved-plan execution resolution no longer deletes persisted chat transcript rows before marking the plan executing. Execution instructions now describe the approved structured plan as the source of truth without claiming the visible chat context is cleared or fresh.

Files
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/PlanService.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/plan/PlanServiceTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`

Behavioral Impact
Internal saved-plan execution paths can still clear transient context usage counters, but they preserve durable user-visible and audit chat memory. Explicit user/session delete behavior is unchanged.

Risks
Execution prompts may still include existing conversation history because this subplan intentionally avoided a broader separate execution-memory redesign.

Follow-up Items
Parent validation should confirm the focused regression and decide whether a later domain needs a separate run-scoped model context store.

Validation
- `mvn -Dtest=ChatServiceTest,PlanServiceTest,FrontendControllerTest test` passed with 31 tests.
- Stale-copy search found no production/static `clearConversationForExecution`, `fresh chat context`, execution-clearing copy, or `Execute now` strings.
- `git diff --check` passed.
- Bounded Spring startup passed; logs reached `Started Magenta2Application` before the expected timeout shutdown.
