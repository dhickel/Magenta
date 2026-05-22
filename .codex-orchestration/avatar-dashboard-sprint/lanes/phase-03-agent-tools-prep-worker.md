# Phase 03 Agent Workspace Tools Prep

## Scope And Status

- Lane: Phase 03 exploration and implementation prep only.
- Owned write path for this pass: `.codex-orchestration/avatar-dashboard-sprint/lanes/phase-03-agent-tools-prep-worker.md`.
- Production/test code was not edited.
- Initial `git status --short` was clean.
- Blocking dependency: Phase 01 must establish the Avatar profile contract, especially whether the supervisor profile id is exactly `avatar` and whether any profile seeding/config defaults change.

## Required Reading Completed

- `AGENTS.md`
- `.internal-dev/AGENTS.md`
- `.internal-dev/focus/AGENTS.md`
- `.internal-dev/focus/current-focus.md`
- `.internal-dev/focus/unfinished-work.md`
- `.internal-dev/focus/architecture-focus.md`
- `.internal-dev/focus/decisions.md`
- `.internal-dev/notes/current-architecture-focus.md`
- `.internal-dev/plans/avatar-dashboard-sprint/phase-03-agent-workspace-tools.md`
- `.internal-dev/plans/avatar-dashboard-sprint/final-orchestration-plan.md`
- Relevant package guides:
  - `src/main/java/io/mindspice/magenta2/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
  - `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`

## Current-State Anchors

- Tool discovery is centralized by `ChatToolRegistry`, which gathers direct `ToolCallback` beans plus every `ToolCallbackProvider`, then rejects unknown approved names. New operational tools should follow the existing `AgentFileToolConfiguration` / `AgentShellToolConfiguration` pattern using `MethodToolCallbackProvider`.
- `ToolAccessPolicy` currently blocks plan/task authoring tools in normal/execution modes, but it does not know any operational tool names yet. Phase 03 must explicitly prevent operational tools from anonymous planning and saved-plan drafting.
- Existing side-panel agent chat is `AgentOrchestrationController.chat(...)` at `/api/agents/{agentId}/chat/stream`. It fetches the `AgentProfile`, builds an agent prompt, then calls `chatService.chat(new ChatRequest.MsgRequest(...))` and only later calls `chatService.markAgentConversation(...)`. This means the turn currently uses system/default approved tools and has no `OrchestrationTaskContext` during tool execution.
- `ChatService.approvedTools(...)` currently prefers `runtimeSettingsService.approvedTools()` when present; otherwise it falls back to default file-config agent approved tools. There is no current path that resolves tools from the selected `AgentProfile`.
- `OrchestrationTaskContextHolder` is a plain thread-local. Any side-panel wrapper must capture the previous context, set the agent context before `ChatService.chat(...)`, and restore the previous state in `finally`.
- Existing file and shell tools already read `OrchestrationTaskContextHolder.current()` for alias/path behavior; operational tools should use the same context holder for identity/scope, not a request parameter supplied by the model.

## Proposed New Classes

Fully disjoint production files suitable for a later Phase 03 worker after Phase 01 profile contract is known:

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AGENTS.md`
  - New package guide: owns model-visible operational tools, compact JSON outputs, authorization checks, and service-wrapper boundaries.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolConfiguration.java`
  - `@Configuration` with `@Bean ToolCallbackProvider agentOperationalToolCallbackProvider(AgentOperationalTools tools)`.
  - Use `MethodToolCallbackProvider.builder().toolObjects(tools).build()`.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalTools.java`
  - `@Component` facade with `@Tool` methods only.
  - Delegates to `AgentOperationalToolService`.
  - Serializes return values with injected `ObjectMapper`, matching `AgentFileTools` style.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolService.java`
  - `@Service` use-case wrapper around existing orchestration/workspace services.
  - No persistence logic and no raw repository access unless an existing service cannot expose required data.
  - Converts service records into compact package-local response records to avoid dumping huge nested objects into tool output.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentToolAuthorizationService.java`
  - Reads `OrchestrationTaskContextHolder.current()`.
  - Resolves current `AgentProfile` via `AgentProfileService.get(ctx.agentId())`.
  - Enforces current-agent ownership, project membership, job ownership/project access, Avatar supervisor identity, destructive confirmations, and list limit bounds.
  - Reads `@Value("${magenta.avatar.supervisor-agent-id:avatar}")` unless Phase 01 creates a typed Avatar settings/properties class.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolResponses.java`
  - Package-local final class with nested records for compact outputs, or separate package-private records if preferred by the implementing worker.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/AgentScopedChatService.java`
  - New service wrapper for side-panel agent chat.
  - Builds/restores `OrchestrationTaskContext` and calls a new `ChatService.chatAsAgent(...)` or equivalent.
  - This is a new file, but it sits in a shared package and must serialize with any concurrent `ChatService` work.

## Proposed Tool Names

Normal agent tools, scoped to the current context:

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

Avatar supervisor tools, gated by supervisor identity and explicit `avatar_*` approval:

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

Do not use `avatar_*` names for Phase 03 general-agent tools except the supervisor set. Phase 04 also defines Avatar personal-assistant tools with names such as `avatar_todo_list`, `avatar_calendar_upsert`, and `avatar_submit_task`; keep this Phase 03 supervisor set operational/system-oriented to avoid semantic overlap.

## Existing Methods To Wrap Later

Workspace and outputs:

- `AgentWorkspaceStatusService.statusFor(String agentId)` for `agent_workspace_status` and `avatar_agent_status`.
- `WorkspaceService.agentWorkspace(String agentId, String displayName)` plus `WorkspaceService.links(String workspaceId)` for `agent_workspace_links`.
- `WorkspaceService.projectWorkspace(String projectId, String displayName)` plus `WorkspaceService.activeLeases(String workspaceId)` where project workspace metadata needs lease visibility.
- `ProjectService.workspaceSummary(String projectId)` for project workspace status.
- `ProjectService.requestWorkspaceRelease(String projectId)` for release requests.
- `OutputArtifactService.query(OutputArtifactQuery query)` for output listing.
- `OutputArtifactService.loadContent(String artifactId, long maxBytes)` for bounded output read.
- `OutputArtifactService.getArtifact(String artifactId)` and metadata fields on `RunOutputArtifact` for authorization before read.

Queue/history/assignments:

- `AssignmentService.assignments(String agentId)`
- `AssignmentService.queueAssignments(String agentId)`
- `AssignmentService.historyAssignments(String agentId)`
- `AssignmentService.get(String assignmentId)`
- `AssignmentService.cancel(String agentId, String assignmentId)`
- `AssignmentService.pause(String agentId, String assignmentId)`
- `AssignmentService.resume(String agentId, String assignmentId)`
- `AssignmentService.delete(String agentId, String assignmentId)`
- `AssignmentService.requeueWorkspaceBlockedAssignment(String assignmentId)`
- `AssignmentService.requeueWorkspaceBlockedAssignments(int limit)`
- `AssignmentService.diagnostics(String assignmentId)`
- `AssignmentService.transcript(String agentId, String assignmentId)`
- `AssignmentService.summariesForAgent(String agentId)`
- `AssignmentService.activeSummariesForProject(String projectId)`
- `AssignmentService.activeSummariesForEffectiveWorkspace(String workspaceId)`

Inbox:

- `InboxService.messages(String agentId)`
- `InboxService.send(String toAgentId, InboxMessage message)`
- `InboxService.markRead(String messageId)`
- `InboxService.markHandled(String messageId)`
- Important gap: `markRead` and `markHandled` accept only message id. Authorization service must fetch/list and prove the message belongs to the current agent before mutating, or `InboxService` should later grow ownership-checking overloads under serialization.

Schedules:

- `ScheduleService.schedules(String agentId)`
- `ScheduleService.schedule(String agentId, String scheduleId)`
- `ScheduleService.save(String agentId, AgentSchedule schedule)`
- `ScheduleService.toggle(String agentId, String scheduleId)`
- `ScheduleService.delete(String agentId, String scheduleId)`
- Feature flag behavior is currently controller-only in `AgentOrchestrationController` through `magenta.features.schedules-enabled`. Tool service must inject the same property and return a truthful disabled response instead of exposing mutations when disabled.

Jobs:

- `JobService.listDefinitions(String agentId, String projectId, String status)`
- `JobService.getDefinition(String id)`
- `JobService.listRuns(String jobId)`
- `JobService.cancelRun(String runId)`
- `JobService.executionSummaries(String jobId)`
- `JobService.latestExecutionSummary(String jobId)`
- `JobService.outputRunIds(String jobId)`
- `AssignmentService.create(AssignmentRequest request)` for `agent_job_submit_run`, not `JobService.startRun(...)`, because direct run allocation intentionally requires an assignment context.

Projects:

- `ProjectService.listProjects()`
- `ProjectService.getProject(String id)`
- `ProjectService.listAgentProjects(String agentId)`
- `ProjectService.isMember(String projectId, String agentId)`
- `ProjectService.listMembers(String projectId)`
- `ProjectService.listEvents(String projectId)`
- `ProjectService.workspaceSummary(String projectId)`
- `ProjectService.requestWorkspaceRelease(String projectId)`

Agent profiles:

- `AgentProfileService.get(String id)` for current context identity and profile existence.
- `AgentProfileService.list()` for Avatar supervisor `avatar_agent_list`.
- `AgentProfile.approvedTools()` will matter once `ChatService.chatAsAgent(...)` is implemented.

Side-panel chat:

- Modify `AgentOrchestrationController.donePayload(...)` to use the wrapper instead of calling `chatService.chat(...)` directly.
- Add `ChatService.chatAsAgent(AgentProfile agent, ChatRequest.MsgRequest request)` or `ChatService.chatAsAgent(String agentId, ChatRequest.MsgRequest request)` so approved tools come from that profile and the conversation is marked as agent-origin before/with the turn.
- If the wrapper is outside `ChatService`, it still needs a service-level API for profile-approved tool resolution; do not duplicate `ChatService` internals in the wrapper.

Tool policy:

- Update `ToolAccessPolicy` with an operational tool name set.
- In `PLAN` and `TASK` modes, only existing planning/task authoring tool allowlists should pass; operational tools should be excluded even if approved.
- In `NORMAL`, `EXECUTE_PLAN`, and `EXECUTE_TASK`, operational tools may pass only by explicit approved name. Wildcard should remain dangerous; prefer profile seeding/config examples with exact names.

## Existing Routes To Modify Later

- `POST /api/agents/{agentId}/chat/stream`
  - Existing method: `AgentOrchestrationController.chat(...)`.
  - Existing helper: `AgentOrchestrationController.donePayload(...)`.
  - Required change: install agent-scoped chat context before the model turn and resolve selected agent approved tools/model/prompt.

No new HTTP routes are required for Phase 03 operational tools. They are Spring AI tools and should wrap existing services directly. Existing controller routes under `/api/agents/{agentId}/inbox`, `/assignments`, `/assignment-history`, `/schedules`, and `/event-reactions` are useful behavioral references but should not be called over HTTP from tools.

## Authorization Rules

- Missing context: reject every `agent_*` and `avatar_*` tool when `OrchestrationTaskContextHolder.current()` is null or has no `agentId`.
- Current agent: normal `agent_*` tools operate as `ctx.agentId()` only. Do not accept an arbitrary `agentId` argument on normal-agent tools except where a recipient id is required, such as inbox send.
- Agent profile: `AgentProfileService.get(ctx.agentId())` must succeed; disabled/archived policy should follow the existing profile status contract when the later worker inspects `AgentProfileStatus`.
- Project access: a normal agent may inspect/mutate a project only if `ProjectService.isMember(projectId, ctx.agentId())` is true or the project id equals `ctx.projectId()` and membership is confirmed. Avatar supervisor tools bypass membership only after supervisor identity and explicit tool approval pass.
- Job access: a normal agent may inspect/cancel job runs only when the job `ownerAgentId` equals `ctx.agentId()`, the job project is accessible by membership, or the linked assignment belongs to `ctx.agentId()`. For job outputs, authorize both job access and artifact attribution.
- Assignment access: normal agents may only get/cancel/pause/resume/delete/transcript assignments whose `assignment.agentId()` equals `ctx.agentId()`. Project-scoped visibility should not imply mutation authority unless a specific tool is Avatar-only.
- Inbox access: normal agents may list their own inbox, send only as/from their current identity, and mark read/handled only for messages addressed to `ctx.agentId()`.
- Schedule access: normal agents may list/save/toggle/delete schedules only for `ctx.agentId()`. Tool outputs must report schedules disabled if `magenta.features.schedules-enabled=false`.
- Output access: normal agents may list/read artifacts where `artifact.agentId()` equals `ctx.agentId()`, or where `artifact.projectId()` is a project the agent belongs to, or where the artifact links to a job/assignment already authorized. Reads must use `OutputArtifactService.loadContent(...)` with a strict max byte cap.
- List limits: all list tools should normalize to `1..200`; use defaults around `25` or `50` for compact output.
- Destructive operations: require exact confirmations:
  - assignment delete: `DELETE <assignmentId>`
  - schedule delete: `DELETE <scheduleId>`
  - job run cancel through supervisor path: `CANCEL <runId>`
  - workspace release request: `REQUEST RELEASE <projectId>`
- Avatar supervisor: require `ctx.agentId().equals(supervisorAgentId)` and the actual selected profile approved-tools list contains the specific `avatar_*` tool name. Do not treat wildcard as sufficient for Avatar supervisory tools.

## Compact Output Shape

Recommended response records:

- `ToolResult(boolean ok, String message, Object data)`
- `PagedListResult(int count, int limit, List<?> items)`
- `AssignmentItem(String id, String agentId, String jobId, String projectId, String type, String status, int priority, String modelOverride, String effectiveWorkspaceId, String updatedAt)`
- `InboxItem(String id, String fromId, String messageType, boolean read, boolean handled, String createdAt, String bodyPreview)`
- `ScheduleItem(String id, String jobId, String cronExpression, String timezone, boolean enabled, String nextRunAt)`
- `JobItem(String id, String title, String ownerAgentId, String projectId, String status, boolean persistentWorkspaceEnabled, int itemCount)`
- `JobRunItem(String id, String jobId, String assignmentId, String workspaceId, String status, String startedAt, String completedAt)`
- `ProjectItem(String id, String name, String ownerAgentId, String model, String updatedAt)`
- `OutputItem(String id, String runId, String planId, String agentId, String jobId, String projectId, String artifactType, String fileName, String createdAt)`
- `WorkspaceStatusItem(...)` can mirror the compact subset of `AgentWorkspaceStatus` and `ProjectWorkspaceSummary`.

Avoid returning raw `AuditEvent` lists unbounded. Transcript/diagnostics tools should cap events and include sequence/type/message summaries.

## Tests To Add Or Update

New focused tests:

- `src/test/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentToolAuthorizationServiceTest.java`
  - no context rejected
  - current agent accepted
  - wrong assignment agent rejected
  - project membership accepted/rejected
  - job owner/project access accepted/rejected
  - Avatar supervisor id accepted/rejected
  - wildcard not sufficient for `avatar_*`
  - destructive confirmation exact-match checks
  - list limit bounds
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolServiceTest.java`
  - queue/history/list/get wrappers
  - inbox list/send/read/handled ownership
  - schedules disabled response and enabled service calls
  - job submit creates `AssignmentRequest` and does not call `JobService.startRun(...)`
  - output read uses `OutputArtifactService.loadContent(...)` cap and enforces attribution
  - project workspace release requires membership or Avatar supervisor
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/orchestration/AgentOperationalToolsTest.java`
  - tool methods serialize compact JSON
  - tool descriptions and names are stable/model-friendly
- `src/test/java/io/mindspice/magenta2/ai/chat/service/AgentScopedChatServiceTest.java`
  - installs context with selected agent id/name/project/workspace if supplied
  - restores previous context on success and exception
  - marks agent conversation before/with turn
  - selected profile approved tools are passed into chat resolution

Existing tests to update:

- `src/test/java/io/mindspice/magenta2/ai/chat/tool/ChatToolRegistryTest.java`
  - new operational provider names are registered
  - unknown operational name still rejected
  - exact `avatar_*` names resolve only when present
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
  - `chatAsAgent(...)` uses profile tools/model and does not fall back to runtime default tools for agent side-panel chat.
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
  - side-panel chat uses wrapper, selected agent prompt/model/tools, and context restoration.
- Regression commands from phase file:
  - `mvn -Dtest=ChatToolRegistryTest,AgentOperationalToolsTest,AgentOperationalToolServiceTest,AgentOrchestrationControllerTest test`
  - `mvn -Dtest=OrchestrationRuntimeTest,JobServiceTest,ProjectServiceTest,WorkspaceServicePathTest,OutputArtifactServiceAttributionTest test`

## Coordinator-Owned Or Serialized Files

These should be coordinator-owned or edited only under a serialization gate:

- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
  - Shared with Phase 04 profile-scoped Avatar behavior.
  - Needed for `chatAsAgent(...)` and profile-approved tool resolution.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`
  - Shared mode-gating file; operational tool allowlist changes affect all chat paths.
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
  - Existing side-panel chat and orchestration API controller; also likely touched by UI/Avatar behavior lanes.
- `config/ai-config.example.json`
  - Contains existing tool examples and model/provider examples. Later edits must avoid committing real secrets and should add only explicit non-wildcard operational tool examples.
- `docs/technical/workspaces-tools-outputs.md`
- `docs/technical/orchestration-runtime.md`
- `docs/technical/api-reference.md` if tool names/API behavior are documented there.
- `.internal-dev/**`
  - Coordinator-owned during parallel implementation unless explicitly assigned.
- Potentially `src/main/resources/application.yml`
  - Avoid editing if `@Value("${magenta.avatar.supervisor-agent-id:avatar}")` is enough. If Phase 01 adds typed Avatar properties, serialize with Phase 01/04.

## Overlap Risks

- Phase 01:
  - Blocks final Avatar supervisor gating until the `avatar` profile id/name/tool seed contract is known.
  - If Phase 01 introduces typed Avatar properties, use those instead of ad hoc `@Value`.
- Phase 02:
  - Output access depends on output attribution staying stable. If Phase 02 changes `OutputArtifactService`, `RunOutputArtifact`, or `OutputArtifactQuery`, Phase 03 must recheck authorization fields and tests.
  - Workspace release and output read behavior should not bypass Phase 02 path confinement/publication changes.
- Phase 04:
  - High overlap on `ChatService`, selected-profile chat behavior, and `avatar_*` tool names.
  - Reserve Phase 04 personal assistant tool names (`avatar_todo_*`, `avatar_calendar_*`, `avatar_note_*`, `avatar_submit_*`) for Avatar personal workflows. Phase 03 supervisor tools should stay operational/system-facing.
- Phase 05:
  - Side-panel chat fixes may affect UI assumptions and `/avatar` compact chat if Phase 05 reuses agent chat patterns.
  - Do not load or modify UI assets in Phase 03 except through serialized controller/service contracts.

## Blockers And Dependencies

- Need Phase 01 profile contract before finalizing `magenta.avatar.supervisor-agent-id` default and config examples.
- Need a serialized decision on whether `ChatService.chatAsAgent(...)` lives in `ChatService` directly or a new wrapper receives a package-private/profile-aware hook. Avoid duplicating approved-tool resolution outside `ChatService`.
- Need schedule feature flag behavior duplicated in service/tool layer or lifted from controller into shared service policy.
- Need an ownership-safe way to mark inbox messages read/handled. Current `InboxService.markRead(String messageId)` and `markHandled(String messageId)` do not take an agent id.
- Need output/job authorization tests after Phase 02 lands, because output attribution is a moving target in the sprint graph.

## Validation Plan

After implementation:

- Run focused tests named above.
- Run phase regression suite:
  - `mvn -Dtest=ChatToolRegistryTest,AgentOperationalToolsTest,AgentOperationalToolServiceTest,AgentOrchestrationControllerTest test`
  - `mvn -Dtest=OrchestrationRuntimeTest,JobServiceTest,ProjectServiceTest,WorkspaceServicePathTest,OutputArtifactServiceAttributionTest test`
- Run broader backend validation:
  - `mvn test`
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=0`
- Add an agent-side chat integration check proving:
  - selected agent model/prompt/tools are used
  - normal agents cannot call operational tools without context
  - normal agents cannot cross-agent mutate assignments/inbox/schedules/jobs
  - Avatar supervisor can call only explicitly approved `avatar_*` tools
  - wildcard approval does not grant Avatar supervisory tools

No Playwright validation is required for this prep artifact. If later implementation changes visible side-panel behavior, browser validation should be delegated per repo rules.
