# 2026-05-13 Phase 04 Docker Alpha Completion

## Scope
Implemented Phase 04 Docker alpha lifecycle completion for orchestration agents, including persistent per-agent container management, agent lifecycle semantics, and HTMX operational controls.

## Implemented
- Added persistent Docker runtime manager:
  - `AgentContainerRuntimeService`
  - `AgentContainerHandle`
  - `AgentContainerStatus`
  - `AgentExecResult`
- Added managed container lifecycle operations:
  - ensure/start/stop/restart/status
  - per-agent deterministic container naming and labels
  - app-lifetime Docker client ownership through existing `DockerRuntimeClient`
  - idle TTL stop behavior and shutdown cleanup policy
- Added lifecycle directory/workspace coupling:
  - create now ensures durable agent workspace/home/output roots
  - archive-and-disable flow
  - hard-delete flow with explicit confirmation text
- Removed clone API/service path for alpha:
  - removed `AgentProfileService.clone(...)`
  - removed `POST /api/agents/{agentId}/clone`
- Added HTMX lifecycle controls in orchestration agents UI:
  - wake/sleep/restart/refresh
  - enable/disable
  - delete confirm, archive+disable, hard-delete
- Added disabled-agent enforcement for new assignment creation and execution handling.
- Updated Docker defaults:
  - agent image default now `python:3.11`
  - added idle TTL and keep-on-shutdown configuration fields

## Validation
- `mvn -q -Dtest=DockerRuntimeClientTest,OrchestrationControllerTest test`
- `mvn -q test`
- `timeout 30s mvn -q spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
