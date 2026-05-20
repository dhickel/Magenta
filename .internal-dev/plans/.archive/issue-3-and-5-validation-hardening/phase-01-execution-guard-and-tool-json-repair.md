# Phase 01 Execution Guard And Tool JSON Repair

## Context
GitHub issue #3 reported overlapping saved-plan executions where one path completed while another failed on malformed Spring AI tool-call JSON. The failure path could be audit-only, and duplicate execution could repeat side effects such as file writes.

## Goal
Ensure only one saved-plan execution can run per conversation and malformed tool-call arguments are handled inside the model/tool loop before side-effecting tool execution.

## In Scope
- Conversation-scoped plan execution guard.
- HTTP 409 for overlapping plan execution requests.
- Tool-call argument JSON preflight before `ToolCallingManager.executeToolCalls(...)`.
- Synthetic tool diagnostics and model-facing repair context.
- Focused service/controller/registry regression tests.

## Out of Scope
- Attaching duplicate callers to an active execution.
- Adding a separate malformed-JSON retry counter.
- Adding recovered malformed tool-call diagnostics to plan status or validation feedback.

## Implementation Steps
- Add a plan-execution guard to `ActiveTurnRegistry`.
- Wire both streaming and non-stream saved-plan execution routes through the guard.
- Add malformed argument detection in `ChatService` before batch tool execution.
- Persist and emit malformed-argument diagnostics through existing tool transcript surfaces.
- Leave recovered errors out of plan evidence and validation feedback.

## Validation
- `mvn -q -Dtest=ActiveTurnRegistryTest,ChatControllerTest,ChatServiceTest,AuditRepositoryTest,ToolLoopGuardTest test`
- `mvn -q test`
- Startup smoke with `timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Focused Playwright MCP validation of chat/SSE and execution guard behavior.

## Exit Criteria
- A second execution request for the same conversation is rejected before plan execution side effects.
- Malformed tool-call arguments do not execute tools before the model receives repair context.
- Recovered malformed tool-call diagnostics remain discoverable without changing plan validation state.
