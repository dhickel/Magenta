# Runtime Agents, Settings, and Workspaces

## Context

Magenta currently treats agents as external AI config records used primarily for default model, system prompt, approved tools, and shell allowlists. The orchestration driver needs agents to become durable runtime actors with editable state, while file configuration remains responsible for model endpoint definitions and other environment-level settings.

Existing code still assumes `AiConfig.defaultAgent()` and `AiConfig.agents()` in chat model defaults, prompt resolution, context compaction fallback, tool allowlists, and shell allowlists. This phase must introduce the new database-backed model without breaking the existing `/chat` behavior.

## Goal

Introduce SQLite-backed runtime settings, agent profiles, and managed workspaces. Preserve existing chat behavior while creating the foundation for orchestration.

## In Scope

- Add database-backed runtime settings for default agent/model and top-level model choices.
- Add database-backed agent profiles with prompt, tool, shell, and model configuration.
- Seed a default `magenta` agent from the legacy config when present.
- Add managed agent and job workspace records and filesystem creation under `dataRoot`.
- Add basic settings, agent, and workspace APIs.
- Add package guides for any new orchestration packages.
- Update config loading so model definitions stay in file config and runtime agent ownership moves to SQLite.

## Out of Scope

- Durable queue execution.
- Scheduling and event reactions.
- Job item execution.
- Agent inbox processing beyond persistence needed by later phases.
- New orchestration UI pages beyond API-enabling endpoints.
- External path/repo automation outside managed workspace links.

## Implementation Steps

1. Create a new orchestration package, such as `io.mindspice.magenta2.ai.orchestration`, with package-local subpackages for agents, settings, workspaces, and repositories.
2. Add schema and repository support for:
   - `runtime_settings`: default agent id/name, default model, planning model, summary model, compaction model, context buffer percent.
   - `agent_profiles`: id, name, status, default model, system prompt text, approved tool names JSON, allowed shell commands JSON, direct-line enabled flag, created/updated timestamps.
   - `workspaces`: id, owner type (`AGENT`, `JOB`), owner id, root relative path, display name, metadata JSON, timestamps.
   - `workspace_links`: workspace id, label, link type, target path or repo metadata, read/write flags, timestamps.
3. Add a runtime settings service that resolves model choices in this order:
   - explicit request model,
   - agent default model,
   - runtime default model,
   - file-config default model.
4. Add an agent profile service:
   - CRUD profiles.
   - Clone profile.
   - Disable/delete profile. Prefer disabling if referenced by persisted work.
   - Validate model keys against file-config models.
   - Validate approved tools against `ChatToolRegistry`.
   - Validate shell command allowlists using existing shell policy conventions.
5. Add a startup seeder:
   - If no agent profiles exist, import the legacy config default agent as `magenta`.
   - If legacy config has no usable agent, seed `magenta` with runtime/default model and existing system prompt file content when available.
   - Do not overwrite existing DB agents on normal startup.
6. Refactor current default-agent consumers to read through runtime services:
   - `ChatService.defaultModel()`
   - `ChatService.defaultSystemPrompt()`
   - `ChatService.approvedTools(...)`
   - `ChatModelRouter` default model fallback.
   - `ContextManagementAdvisor` default prompt/model fallback.
   - `AgentShellToolService` allowed commands.
7. Keep legacy config agent fields temporarily for import/backward compatibility, but mark DB profiles as runtime source of truth in package guide/docs.
8. Add workspace service:
   - Agent root: `<dataRoot>/agents/{agentId}`.
   - Job root placeholder support: `<dataRoot>/jobs/{jobId}`.
   - Standalone assigned work placeholder path: `<agentRoot>/work/{assignmentId}`.
   - Create directories with `Files.createDirectories`.
   - Ensure resolved paths remain under `dataRoot`.
9. Add thin API controllers:
   - `GET/PUT /api/settings/runtime`
   - `GET/POST /api/agents`
   - `GET/PUT/DELETE /api/agents/{agentId}`
   - `POST /api/agents/{agentId}/clone`
   - `GET /api/agents/{agentId}/workspace`
   - `GET/POST/DELETE /api/workspaces/{workspaceId}/links`
10. Update `config/ai-config.example.json` to add the new `defaultModel` field and keep legacy agents only as seed/import examples.

## Validation

- Repository tests:
  - Runtime settings save/load and default fallback.
  - Agent profile CRUD, clone, disable/delete, and JSON list persistence.
  - Workspace root creation and data-root confinement.
  - Legacy seed creates one default agent only when no DB agents exist.
- Service tests:
  - Model resolution priority.
  - Tool allowlist validation.
  - Shell allowlist resolution from DB agent profile.
  - Existing `/chat` default prompt/model behavior still works after the refactor.
- Controller tests:
  - Settings and agent API happy paths and validation failures.
  - Workspace link API rejects invalid workspace ids and path escapes.
- Regression:
  - Existing chat, plan, task, workflow, model-router, context-management, and shell/file tool tests pass.
- Startup smoke:
  - `timeout 30s mvn spring-boot:run -Dspring-boot.run.arguments='--server.port=0 --spring.datasource.url=jdbc:sqlite:/tmp/magenta2-orchestration-phase01.sqlite'`

## Exit Criteria

- A fresh DB starts with a usable default `magenta` DB agent.
- Existing `/chat` works without reading runtime agent settings from file config as source of truth.
- Agents can be created, cloned, updated, disabled/deleted, and assigned managed workspaces.
- File config remains the source for model endpoint definitions.
- Runtime settings expose default top-level model choices.
