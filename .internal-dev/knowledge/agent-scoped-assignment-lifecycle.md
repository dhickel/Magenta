# Agent-Scoped Assignment Lifecycle

## Topic

Route-agent ownership checks for assignment lifecycle controls.

## Source References

- `.internal-dev/plans/public-alpha-remediation/01-security-access-control/subplan-04-agent-scoped-lifecycle.md`
- `.internal-dev/bugs/public-alpha-quality-review/bug-12-high-assignment-lifecycle-not-agent-scoped/report.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/main/java/io/mindspice/magenta2/api/web/OrchestrationController.java`

## Key Takeaways

- Agent-scoped assignment routes must call service methods that accept both `agentId` and `assignmentId` before mutation.
- Ownership mismatch should be treated like not found at the route boundary so callers cannot control another agent's assignment by id.
- Keep assignment-id-only lifecycle methods only for internal runtime flows that already operate from assignment context.
- HTMX lifecycle handlers should return a non-2xx status while still rendering the current route agent queue with an error message.

## Engine Relevance

Future lifecycle controls should follow the same pattern used for cancel, pause, resume, delete, transcript, and force interrupt: validate the route agent, load the assignment, compare `assignment.agentId()`, and only then mutate or reveal route-scoped data.

## Open Questions

- None for subplan 04.
