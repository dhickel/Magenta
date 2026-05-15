# Final Alpha Remediation Readiness Review

## Date
2026-05-13

## Scope
Final validation gate (Phase 5) for the Docker-Backed Alpha Remediation plan. Covers all five phase validation steps: automated tests, startup, Docker execution, workflow gates, jobs/projects, Playwright MCP browser validation, and chat/SSE regression.

## Exit Criteria Checklist

| Criterion | Status | Notes |
|-----------|--------|-------|
| mvn test passes | PASS | 427/427, 0 failures, 0 errors |
| Bounded startup reaches healthy Spring Boot | PASS | Started in 3.054s, Docker daemon OK, image verified |
| Podman/Docker daemon verified | PASS | Podman socket at /run/user/1000/podman/podman.sock, daemon ping OK |
| Task execution uses container for agent-context runs | **FAIL** | Execution runs through `agent=system`, not Docker container |
| Required files land in /output | **FAIL** | Files write to `.magenta/root/` (host root), not `/output` |
| Output content viewable and downloadable | PASS | DEFECT-07-01 fixed: `_content` and `/download` endpoints work |
| Workflow task nodes are real | **BLOCKED** | OrchestrationRunnerService uses wrong WorkflowService (table mismatch) |
| Approval rejection blocks continuation | **BLOCKED** | Cannot test — workflow execution fails on table mismatch |
| Jobs update status and history surfaces agree | PASS | DRAFT->QUEUED->RUNNING->COMPLETED, outputs/evidence correct |
| Plan editor and model routing defects fixed | PASS | Plan CRUD works, model dropdowns use canonical aliases |
| Agent chat reachable from operational UI | PASS | Chat tab present, Agent Chat panel with "Open" button |
| Playwright MCP browser checks | PASS | All pages load, zero console errors, HTMX pattern correct |

## Defect Status Summary

| Defect | Status | Evidence |
|--------|--------|----------|
| DEFECT-03-03 (wrong output path) | **PARTIALLY REOPENED** | Execution uses system agent, writes to host root not /output |
| DEFECT-04-01 (resume ignores rejection) | Fixed (unit tests) | WorkflowRunnerTest confirms fix; live validation blocked |
| DEFECT-04-02 (task nodes are no-ops) | Fixed (unit tests) | WorkflowRunnerTest confirms fix; live validation blocked |
| DEFECT-07-01 (no output content view) | **FIXED** | `_content` and `/download` endpoints confirmed working |

## Blockers

### BLOCKER 1: Docker execution routing (DEFECT-03-03 partial regression)
**Severity: Alpha blocker**
- Plan/task execution submitted to Docker-backed "magenta" agent runs through `agent=system`
- Files write to `.magenta/root/` (host root) instead of the container `/output` mount
- Container `/output` remains empty after plan execution
- The model hallucinates/verifies file creation during execution but writes to the wrong path
- Output artifacts registered but contain metadata stubs, not actual file content

**Root cause**: The OrchestrationRunnerService dispatches task execution through PlanService.startRun() which uses the "system" agent path. The Docker container execution path is not connected to the orchestration task runner.

### BLOCKER 2: Workflow repository table name mismatch
**Severity: Alpha blocker**
- `OrchestrationRunnerService` imports `ai.chat.workflow.WorkflowService` which queries `ai_workflow_definitions`
- Schema has table `workflow_definitions` (no `ai_` prefix)
- All workflow submissions fail: `SQLITE_ERROR: no such table: ai_workflow_definitions`
- The correct `ai.orchestration.workflow.WorkflowService` (bean name `orchestrationWorkflowService`) uses the correct table name but is not wired into the runner service

**Root cause**: Import/bean wiring mismatch. The runner service uses the legacy chat-scoped WorkflowService instead of the orchestration-scoped one.

## Non-Blocker Findings

1. **Chat `/switch` command removed**: The `/commands` endpoint only supports `/new` and `/plan`. Session switching is handled through the browser client (HTMX), not the commands API. The knowledge file reference to `/switch` is outdated.

2. **Session mutation endpoints use PATCH not PUT**: Correct endpoints are `PATCH /api/chat/{id}/title`, `PATCH /api/chat/{id}/favorite`, `PATCH /api/chat/{id}/archive`. Previous validation may have used incorrect methods.

3. **Agent chat panel is collapsed by default**: Per Phase 4 design, the chat panel is attached to the agent detail page as a sidebar element but starts in a collapsed state. Users click "Open" to expand.

## Readiness Decision

**BLOCKED — alpha cannot be declared ready.**

Two alpha blockers remain:

1. **Docker execution routing**: Tasks submitted to Docker-backed agents execute through the system agent path, writing files to the host root rather than the container `/output` mount. DEFECT-03-03 is not fully resolved.

2. **Workflow table name mismatch**: Workflow submissions fail because the runner service uses the wrong repository (with wrong table name). The orchestration WorkflowService with the correct table name exists but is not wired.

### Remediation Path
1. Fix Docker execution routing: Wire the OrchestrationRunnerService task dispatch through the Docker container runtime for Docker-backed agents. Ensure the execution context uses container-relative paths (`/output`, `/workspace`).
2. Fix workflow wiring: Change `OrchestrationRunnerService` to import and use `io.mindspice.magenta2.ai.orchestration.workflow.WorkflowService` instead of `io.mindspice.magenta2.ai.chat.workflow.WorkflowService`.

### Items Passing
- All 427 automated tests (0 failures)
- Spring Boot startup with Docker daemon ping
- Podman container lifecycle (start, exec, mounts)
- Job creation, submission, status transitions (DRAFT->QUEUED->RUNNING->COMPLETED)
- Plan editor CRUD and finalization
- Output content viewing/downloading (DEFECT-07-01 fixed)
- Model dropdown canonical aliases in settings and plan editor
- Playwright MCP browser validation (all pages, zero console errors)
- Chat SSE stream (start->context->chunk->done), history persistence, active-stream conflict detection
