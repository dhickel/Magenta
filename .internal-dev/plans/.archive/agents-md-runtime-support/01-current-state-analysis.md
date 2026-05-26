# Current State Analysis

## Verified Repo Context

- `.internal-dev/AGENTS.md` requires task-specific spec and knowledge reads, durable specs/docs/changelog closeout, phase commits for multi-phase plans, and final plan archival after completion.
- `.internal-dev/specifications/architecture.md` currently defines workspace, Work Area, run output, and job filesystem semantics under `ARCH-20260526-01`.
- `.internal-dev/specifications/services.md` currently assigns workspace path layout and run output routing to workspace services under `SVC-20260526-01`.
- `.internal-dev/specifications/service-graph.md` requires workspace/run/output callers to use centralized workspace helpers under `SVC-20260526-01`.
- `.internal-dev/specifications/api.md` has no direct route requirement for this task unless implementation adds a visible/API surface.
- The official `AGENTS.md` source currently says files are plain Markdown with no required fields, nested files can be used for subprojects, closest files take precedence, and explicit prompts override everything. Workers and validators must re-check this during execution.

## Relevant Code Anchors

- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java:98` creates new agent profiles and calls `ensureAgentDurableStorage(...)`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/agents/AgentProfileService.java:289` ensures agent workspace directories today but does not create starter `AGENTS.md`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:56` creates or returns agent workspace records.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspaceService.java:170` creates workspace roots and persists workspace rows.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/WorkspacePathLayout.java:8` centralizes structural path constants such as `workspace`, `home`, `workareas`, `runs`, and `outputs`.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/runtime/OrchestrationTaskContext.java` carries runtime paths and selected Work Area/output route metadata.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java:51` assembles system prompt and turn instructions.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/turn/PromptContextAssembler.java:65` merges default, mode-specific, and worktype prompt fragments.
- `src/main/java/io/mindspice/magenta2/ai/chat/tool/file/AgentFileToolService.java` and `src/main/java/io/mindspice/magenta2/ai/chat/tool/shell/AgentShellToolService.java` already resolve runtime aliases from `OrchestrationTaskContext`.

## Existing Documentation Anchors

- `docs/technical/workspaces-tools-outputs.md` documents data-root layout, Work Areas, runtime aliases, output staging, and project-scoped runs.
- `docs/end-user/agents.md` documents agent workspace, exec, submit, and lifecycle behavior.
- `src/main/java/io/mindspice/magenta2/ai/orchestration/workspaces/AGENTS.md` documents workspace package ownership and alias semantics.
- `src/main/java/io/mindspice/magenta2/ai/chat/service/AGENTS.md` documents chat prompt and service behavior.

## Architecture Fit

The cleanest fit is:

- A small resolver/service near workspace/runtime concerns, preferably under `ai.orchestration.workspaces` unless code inspection shows a clearer package.
- Starter file creation owned by workspace creation or agent durable storage, not controllers.
- Prompt/context injection through `PromptContextAssembler` or a narrowly injected collaborator that it delegates to.
- Runtime path/bound-root selection based on `OrchestrationTaskContext` and existing effective workspace/Work Area semantics.

## Risks And Gaps

- The official site's closest-wins shorthand is concise. Magenta's ancestor-retention behavior must be documented as a deliberate interpretation/divergence rather than silently presented as external spec truth.
- Prompt injection can become stale or overbroad if it only resolves once per conversation. Tests must cover working path changes and no-longer-applicable nested files.
- Work Area narrowing means `workspace/` may be a selected Work Area while `root/` remains the broader owner root. Resolver root binding must be explicit for each runtime mode.
- A nested-only case is meaningful when there is no root `AGENTS.md`; resolver must still load applicable nested files within the bound root.
- Symlinks and `..` traversal are security-sensitive. Use normalized/real path confinement, not string prefix checks alone.
- Existing docs mention `workspace/<agentWorkspaceId>/home/` and `runs/<runId>/outputs/`; starter content must match current architecture and avoid legacy scratch/job-owned workspace language.

## Non-Goals

- No UI management for AGENTS.md files.
- No remote/template storage.
- No automatic migration of existing workspaces.
- No user-level global AGENTS.md.
- No changes to external coding-agent development behavior for this repository, except package guide updates if implementation changes local conventions.
