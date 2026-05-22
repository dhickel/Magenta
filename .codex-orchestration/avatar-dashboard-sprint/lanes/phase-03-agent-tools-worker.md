# Phase 03 Agent Tools Worker Handoff

## Scope

- Lane: Phase 03 operational tool package implementation.
- Branch observed before edits: `feature/avatar-dashboard-sprint`.
- Owned paths edited:
  - `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/**`
  - `src/test/java/io/mindspice/magenta2/ai/chat/tool/orchestration/**`
  - `.codex-orchestration/avatar-dashboard-sprint/lanes/phase-03-agent-tools-worker.md`
- No edits were made to `ChatService.java`, `ToolAccessPolicy.java`, `AgentOrchestrationController.java`, runtime services, config examples, or docs.

## Implemented Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolConfiguration.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalTools.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentToolAuthorizationService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolResponses.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolConfigurationTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentToolAuthorizationServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolServiceTest.java`

## Tool Names Implemented

Normal scoped tools:

- `agent_workspace_status`
- `agent_workspace_links`
- `agent_project_release_workspace`
- `agent_queue_list`
- `agent_assignment_get`
- `agent_assignment_cancel`
- `agent_assignment_pause`
- `agent_assignment_resume`
- `agent_assignment_delete`
- `agent_assignment_requeue_workspace_blocked`
- `agent_assignment_diagnostics`
- `agent_assignment_transcript`
- `agent_inbox_list`
- `agent_inbox_send`
- `agent_inbox_mark_read`
- `agent_inbox_mark_handled`
- `agent_schedule_list`
- `agent_schedule_save`
- `agent_schedule_toggle`
- `agent_schedule_delete`
- `agent_job_list`
- `agent_job_get`
- `agent_job_submit_run`
- `agent_job_run_list`
- `agent_job_run_cancel`
- `agent_job_outputs`
- `agent_project_list`
- `agent_project_get`
- `agent_project_members`
- `agent_project_workspace_status`
- `agent_project_events`
- `agent_output_list`
- `agent_output_read`

Avatar supervisor tools:

- `avatar_system_overview`
- `avatar_agent_list`
- `avatar_agent_status`
- `avatar_assignment_list`
- `avatar_assignment_cancel`
- `avatar_assignment_pause`
- `avatar_assignment_resume`
- `avatar_assignment_requeue_workspace_blocked`
- `avatar_project_list`
- `avatar_project_members`
- `avatar_project_release_workspace`
- `avatar_job_list`
- `avatar_job_run_list`
- `avatar_job_run_cancel`
- `avatar_schedule_list`
- `avatar_output_list`
- `avatar_output_read`

## Behavior Notes

- All normal `agent_*` tools use `OrchestrationTaskContextHolder.current()` for the acting agent identity.
- Missing context rejects all tools through `AgentToolAuthorizationService`.
- Disabled profiles are rejected before operational tool execution.
- Normal project, assignment, job, run, inbox, and output access is scoped through existing services.
- Avatar supervisor tools require:
  - current context agent id equal to `magenta.avatar.supervisor-agent-id`, default `avatar`;
  - the selected profile approved-tools list to contain the exact `avatar_*` tool name.
- Wildcard approval is not sufficient for Avatar supervisor tools.
- List limits are normalized to `1..200`; default is `50`.
- Output reads use `OutputArtifactService.loadContent(...)` with default `65536` bytes and cap `1048576` bytes.
- Schedule tools return a truthful disabled result when `magenta.features.schedules-enabled=false`.
- Destructive confirmations implemented:
  - assignment delete: `DELETE <assignmentId>`
  - schedule delete: `DELETE <scheduleId>`
  - job run cancel: `CANCEL <runId>`
  - project workspace release request: `REQUEST RELEASE <projectId>`

## Validation

- Passed: `mvn -DskipTests compile`
- Passed: `mvn -Dtest=AgentOperationalToolConfigurationTest,AgentToolAuthorizationServiceTest,AgentOperationalToolServiceTest test`
- Passed: `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
  - The command exited `124` from the timeout after healthy startup on random port `43557` and graceful shutdown.

The focused tests set `net.bytebuddy.experimental=true` inside the new test classes because this checkout is running Java 25 and the bundled Mockito/Byte Buddy version otherwise refuses inline mocks.

## Coordinator Integration Requests

- `ToolAccessPolicy.java`: add operational tool-name filtering so `agent_*` and `avatar_*` tools are unavailable in anonymous planning and saved-plan drafting modes, even if present in configured approved tools.
- `ChatService.java`: add an agent-scoped chat entrypoint such as `chatAsAgent(AgentProfile agent, ChatRequest.MsgRequest request)` that resolves model, prompt, and approved tools from the selected agent profile.
- `AgentOrchestrationController.java`: route side-panel agent chat through the agent-scoped chat wrapper and install an `OrchestrationTaskContext` before invoking the model turn, restoring the previous context in `finally`.
- Config/docs closeout: add explicit non-wildcard operational tool examples to `config/ai-config.example.json` and document normal agent tools separately from Avatar supervisor tools.

## Known Gaps

- The tools are registered and self-authorizing, but upstream chat policy integration is still required before the phase exit criterion "anonymous chat cannot use operational tools" is fully satisfied at policy level. Without that integration, missing orchestration context still blocks actual tool execution.
- Side-panel chat does not yet install the context needed for these tools because the required controller/service edits are coordinator-owned.
- Broader runtime, controller, startup, and full-suite validation were not run in this lane.
