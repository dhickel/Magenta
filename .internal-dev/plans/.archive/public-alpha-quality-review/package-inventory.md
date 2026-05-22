# Package Inventory

## Agent

- Agent: main Codex campaign coordinator
- Model / reasoning: current parent Codex session
- Scope: package and test-surface inventory
- Commands: `rg --files -g 'AGENTS.md'`, `find src/main/java/io/mindspice/magenta2 -maxdepth 5 -type d`, `rg --files src/test`

## Package Guides Present

- `src/main/java/io/mindspice/magenta2/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/api/web/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/agent/job/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/config/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/model/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/plan/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/rendering/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/repository/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/core/AGENTS.md`
- `src/main/java/io/mindspice/magenta2/core/util/AGENTS.md`

## Main Package Groups

- `io.mindspice.magenta2.api.web`: frontend routes, fragments, REST/SSE controllers, orchestration UI.
- `io.mindspice.magenta2.ai.chat`: chat service/model/repository/tool/plan/task/workflow packages.
- `io.mindspice.magenta2.ai.orchestration`: runtime assignments, workflow engine, settings, agents, workspaces.
- `io.mindspice.magenta2.ai.agent.job`: legacy agent job package.
- `io.mindspice.magenta2.ai.config.user`: external AI config loading and model/agent seed config.
- `io.mindspice.magenta2.ai.execution`: active turn and work execution coordination.
- `io.mindspice.magenta2.core`: shared utility/core support.

## Test Surface

- API/UI controller tests: `AgentOrchestrationControllerTest`, `AgentProfileControllerTest`, `ChatControllerTest`, `FrontendControllerTest`, `GlobalExceptionHandlerTest`, `OperationalUiContractControllerTest`, `OrchestrationControllerTest`, `WorkspaceControllerTest`.
- SSE/stream tests: `ChatStreamSupportTest`, `SseStreamLifecycleTest`, `TaskStreamSupportTest`.
- Chat/model/tool tests: `PlanRepositoryTest`, `PlanServiceTest`, `ChatModelRouterTest`, `ContextManagementAdvisorTest`, `ToolLoopGuardTest`, `AgentShellToolServiceTest`, `AgentWebToolServiceTest`, file/plan/tool transcript tests.
- Runtime/persistence tests: `OrchestrationRuntimeTest`, `JobRepositoryTest`, `JobServiceTest`, `ProjectRepositoryTest`, `ProjectServiceTest`, `SettingsPrecedenceTest`, workflow repository/runner tests, workspace lease/output attribution tests.
