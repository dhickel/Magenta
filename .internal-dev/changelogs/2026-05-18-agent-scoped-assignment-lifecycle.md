# Agent-Scoped Assignment Lifecycle

## Date

2026-05-18

## Change Summary

Implemented public-alpha security subplan 04 for bug-12. Assignment lifecycle mutation routes now pass the route `agentId` into scoped `AssignmentService` methods before cancel, pause, resume, or force interrupt changes are applied.

## Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/api/web/OrchestrationControllerTest.java`
- `.internal-dev/plans/public-alpha-remediation/progress.md`
- `.internal-dev/plans/public-alpha-remediation/implementation_notes.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-12-high-assignment-lifecycle-not-agent-scoped/report.md`
- `.internal-dev/knowledge/agent-scoped-assignment-lifecycle.md`

## Behavioral Impact

Same-agent lifecycle controls keep their previous queue/history behavior. Cross-agent route attempts now fail with non-2xx lifecycle errors and leave the assignment state unchanged.

## Risks

Focused runtime/controller tests and bounded Spring Boot startup smoke passed. External subplan validation and full security-domain validation are still pending.

## Follow-up Items

- Run the external subplan validation gate before starting the security domain gate.
- No out-of-scope bugs or deferred feature ideas were identified.
