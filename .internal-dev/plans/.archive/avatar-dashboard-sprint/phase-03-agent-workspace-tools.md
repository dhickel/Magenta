# Phase 03 - Agent Workspace Tooling

## Context

Magenta has chat tools for files, shell, web, planning, saved plans, and task completion. It does not yet expose operational tools for agents to inspect and manage queue, inbox, schedules, jobs, projects, history, outputs, workspace metadata, and supervised Avatar-wide actions. These tools must use existing services and `ChatToolRegistry`; they must not become a generic database/API execution surface.

Relevant anchors:

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistry.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContext.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContextHolder.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/AssignmentService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/InboxService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ScheduleService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/JobService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/ProjectService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/OutputArtifactService.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`

## Goal

Add Spring AI tools that let agents inspect and manage their operational context while preserving existing service boundaries, lifecycle rules, project membership checks, path confinement, and explicit approved-tool gating. Add Avatar-only supervisory tools that are whole-system only for the configured Avatar supervisor identity.

## In Scope

- New `ai.chat.tool.orchestration` package.
- General agent tools scoped to the current `OrchestrationTaskContext`.
- Avatar supervisory tools gated by identity and explicit `avatar_*` allowlist.
- Side-panel agent chat context fix so agent-scoped tools know the active agent.
- Compact JSON tool outputs.

## Out of Scope

- Anonymous `/chat` operational tools.
- Raw SQL, arbitrary HTTP API, arbitrary filesystem browsing, or wildcard operational execution.
- Generic output write/delete tools.
- Replacing existing controllers or services.

## Implementation Steps

1. Add tool package.
   - `AgentOperationalTools`
   - `AgentOperationalToolService`
   - `AgentOperationalToolConfiguration`
   - `AgentToolAuthorizationService`
   - Package-local records for compact responses.

2. Register tools with Spring AI.
   - Follow existing file/shell/web tool configuration style.
   - Register through `ToolCallbackProvider`.
   - Tool descriptions must be explicit and model-friendly.

3. Implement `AgentToolAuthorizationService`.
   - Read `OrchestrationTaskContextHolder.current()`.
   - Reject missing agent context.
   - Resolve `AgentProfile`.
   - Validate current-agent ownership, current project, project membership, job ownership, and job-in-project access.
   - Validate Avatar supervisor identity, for example `magenta.avatar.supervisor-agent-id=avatar`.
   - Bound list limits to `1..200`.
   - Require confirmation strings for destructive operations.

4. Implement general tools as service wrappers.
   - Workspace: status, links, project release request.
   - Queue/history: list, get, cancel, pause, resume, delete, requeue workspace-blocked, transcript/diagnostics.
   - Inbox: list, send, mark read, mark handled.
   - Schedules: list, save, toggle, delete; report feature-disabled state clearly.
   - Jobs: list, get, submit run, list runs, cancel run, outputs.
   - Projects: list, get, members, workspace status, events.
   - Outputs: list and bounded read.

5. Implement Avatar supervisory tools.
   - System overview, agents list/status, assignments list/control, projects/membership, jobs, schedules, outputs, workspace release requests.
   - Keep all actions explicit and audited.
   - Do not grant these tools to non-Avatar profiles.

6. Add agent chat context support.
   - Prefer `ChatService.chatAsAgent(...)`.
   - Install `OrchestrationTaskContext` in a service wrapper and restore previous state in `finally`.
   - Update `AgentOrchestrationController` to use the wrapper.
   - Prove selected agent prompt/tools/model are used.

7. Update `ToolAccessPolicy`.
   - Operational tools are not available in anonymous planning or saved-plan drafting modes.
   - Approved names still go through `ChatToolRegistry`.
   - Tool service performs final context and authorization checks.

8. Seed/config/docs.
   - Update `config/ai-config.example.json` with explicit non-wildcard operational tool examples.
   - Document normal-agent tools separately from Avatar supervisory tools.

## Validation

Focused tests:

- `ChatToolRegistryTest` for new tool names and unknown-name rejection.
- Tool schema/description tests.
- Authorization tests for no context, current agent, wrong agent, project membership, Avatar-only supervisor tools, destructive confirmations, and list limits.
- Service wrapper tests for queue, inbox, schedules, jobs, projects, outputs, and history.
- Agent side-panel chat installs and restores context.
- Feature-disabled schedule/reaction behavior is reported truthfully.

Commands:

- `mvn -Dtest=ChatToolRegistryTest,AgentOperationalToolsTest,AgentOperationalToolServiceTest,AgentOrchestrationControllerTest test`
- `mvn -Dtest=OrchestrationRuntimeTest,JobServiceTest,ProjectServiceTest,WorkspaceServicePathTest,OutputArtifactServiceAttributionTest test`
- `mvn test`
- `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`

## Exit Criteria

- Anonymous chat cannot use operational tools.
- Normal agents can only operate inside their scoped context.
- Avatar supervisory tools require explicit identity and tool approval.
- Mutations call existing services and are auditable.
