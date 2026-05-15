# Changelog

## Date
2026-05-13

## Change Summary
Final validation gate (Phase 5) of the Docker-Backed Alpha Remediation plan. Ran all automated tests, bounded startup with Podman, Docker/Podman live execution, workflow gate testing, job/project/status validation, Playwright MCP browser checks, and chat/SSE regression. Documented all evidence and produced a final readiness decision.

## Files
### Evidence created
- `.internal-dev/reviews/docker-backed-alpha-remediation/01-automated-test-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-remediation/02-startup-and-docker-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-remediation/03-workflow-gate-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-remediation/04-job-status-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-remediation/05-browser-validation-evidence.md`
- `.internal-dev/reviews/docker-backed-alpha-remediation/final-alpha-remediation-readiness.md`

### Knowledge created
- `.internal-dev/knowledge/docker-backed-task-execution.md`

### Knowledge updated
- `.internal-dev/knowledge/live-chat-mcp-workflow-testing.md`

## Behavioral Impact
- DEFECT-07-01 (no output content view): FIXED — content viewable and downloadable via `_content` and `/download` endpoints
- DEFECT-03-03 (wrong output path): PARTIALLY REOPENED — execution still routes through system agent, writes to host root
- Two new blockers discovered: Docker execution routing and workflow table name mismatch

## Risks
1. **Docker execution routing**: Tasks submitted to Docker-backed agents execute through the system agent path. Files are written to the host root rather than the container `/output`. This blocks the core value proposition of Docker-backed agent execution.
2. **Workflow table name mismatch**: `OrchestrationRunnerService` imports the wrong WorkflowService bean with incorrect table name `ai_workflow_definitions`. All workflow submissions fail. The correct orchestration-scoped bean exists but is not wired.
3. Both blockers require code changes in the orchestration runner layer — not validation-only fixes.

## Follow-up Items
1. Wire Docker container execution into OrchestrationRunnerService task dispatch
2. Fix OrchestrationRunnerService WorkflowService import to use orchestration-scoped bean
3. Re-run workflow gate validation (approval rejection/approval paths)
4. Re-run Docker execution verification (confirm files land in `/output`)
5. Consider adding integration tests that catch table name mismatches (not just unit tests with mocks)
