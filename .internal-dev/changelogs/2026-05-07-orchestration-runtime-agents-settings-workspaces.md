## Date

2026-05-07

## Change Summary

Implemented phase 01 orchestration runtime support: SQLite-backed runtime settings, agent profiles, workspace metadata, startup seeding from legacy AI config, and API endpoints for settings, agents, and workspace links.

Existing chat defaults now read runtime model, prompt, tool, shell, compaction, and context-buffer settings through the orchestration services when available, while file config remains the source for model endpoint definitions.

## Files

- `src/main/java/io/mindspice/magenta2/ai/orchestration/**`
- `src/main/java/io/mindspice/magenta2/api/web/RuntimeSettingsController.java`
- `src/main/java/io/mindspice/magenta2/api/web/AgentProfileController.java`
- `src/main/java/io/mindspice/magenta2/api/web/WorkspaceController.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatService.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ChatModelRouter.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/service/ContextManagementAdvisor.java`
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/AiConfig.java`
- `src/main/java/io/mindspice/magenta2/ai/config/user/ExternalAiConfigLoader.java`
- `src/test/java/io/mindspice/magenta2/ai/orchestration/OrchestrationRuntimeTest.java`
- `config/ai-config.example.json`

## Behavioral Impact

Fresh databases seed one active `magenta` agent profile and runtime settings from legacy config. Runtime APIs allow agent CRUD/clone/disable, runtime settings updates, workspace creation for agents, and workspace link management. Existing `/chat` behavior remains compatible with remote model names and falls back to legacy config during early startup before seeding completes.

## Risks

Workspace link validation currently confines `PATH` links under `dataRoot`; richer external repository metadata is only represented as a `REPOSITORY` link target string for later phases.

## Follow-up Items

- Add UI screens for runtime settings and agent/workspace administration when there is a concrete workflow.
- Replace disable-only delete behavior with reference-aware hard delete once persisted orchestration work references exist.
