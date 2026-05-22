# Agent Operational Tools

## Date

2026-05-22

## Change Summary

Implemented Phase 03 agent workspace tooling for the Avatar sprint. Added operational Spring AI tools for agent-scoped workspace, queue, assignment, inbox, schedule, job, project, and output workflows; added Avatar supervisor tools for cross-agent operational views; and wired side-panel agent chat through an agent-scoped chat path.

## Files

- `src/main/java/io/mindspice/magenta2/ai/chat/tool/orchestration/**`
- `src/test/java/io/mindspice/magenta2/ai/chat/tool/orchestration/**`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicy.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentOrchestrationController.java`
- `src/test/java/io/mindspice/magenta2/api/web/AgentOrchestrationControllerTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/ChatServiceTest.java`
- `src/test/java/io/mindspice/magenta2/ai/chat/service/turn/ToolAccessPolicyTest.java`
- `docs/technical/workspaces-tools-outputs.md`
- `pom.xml`

## Behavioral Impact

Agent side-panel chat now calls `ChatService.chatAsAgent(...)`, which marks the conversation as agent-origin, uses the agent profile model and explicit approved-tool names, and installs an `OrchestrationTaskContext` inside the queued chat turn. PLAN/TASK drafting modes keep operational `agent_` and `avatar_` tools unavailable. Normal agent tools require active agent context; Avatar supervisor tools require the Avatar profile identity and exact tool approval.

## Risks

Operational tools expose lifecycle mutations such as assignment cancellation, pause/resume, deletion, requeue, inbox handling, schedule changes, and workspace release. The implementation keeps these behind owner/project membership checks, exact confirmation for destructive deletion, bounded limits, explicit tool names, and Avatar identity checks. Browser/UI exposure still depends on later Avatar dashboard and assistant behavior phases.

## Follow-up Items

- Phase 04 should use these tools for Avatar assistant behaviors instead of adding a second runtime.
- UI phases should surface operational tool failures as controlled HTMX/chat messages.
- Final integration should include broader `/agents`, `/projects`, `/jobs`, `/outputs`, and `/chat` validation after the remaining Avatar lanes land.
